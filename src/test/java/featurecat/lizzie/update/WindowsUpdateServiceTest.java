package featurecat.lizzie.update;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import featurecat.lizzie.ConfigTestHelper;
import featurecat.lizzie.Lizzie;
import featurecat.lizzie.util.NetworkProxy;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WindowsUpdateServiceTest {
  @TempDir Path tempDir;

  @AfterEach
  void tearDown() {
    System.clearProperty(WindowsUpdatePaths.APP_ROOT_PROPERTY);
    System.clearProperty(WindowsUpdatePaths.APP_DIR_PROPERTY);
    System.clearProperty(WindowsUpdatePaths.WORK_DIR_PROPERTY);
    System.clearProperty(WindowsUpdatePaths.CURRENT_JAR_PROPERTY);
    Lizzie.config = null;
  }

  @Test
  void downloadAndPrepareUsesConfiguredProxy() throws Exception {
    byte[] archive = "downloaded archive".getBytes(StandardCharsets.UTF_8);
    try (OneShotHttp proxy = new OneShotHttp(archive)) {
      useManualProxy(proxy.port());
      WindowsUpdatePlan plan = updatePlan("http://example.invalid/core.zip", archive);
      configureUpdatePaths();

      Path requestPath = new WindowsUpdateService().downloadAndPrepare(plan, null);

      Path archivePath = requestPath.getParent().resolve(sha256(archive))
          .resolve("2026-06-12-windows64.core-update.zip");
      assertEquals("downloaded archive", Files.readString(archivePath));
      JSONObject request = new JSONObject(Files.readString(requestPath));
      assertEquals(
          "app/lizzie-yzy2.5.3-shaded.jar",
          request.getJSONArray("components").getJSONObject(0).getString("sourcePath"));
      assertEquals(1, proxy.requests.get());
      assertTrue(proxy.lastRequestLine.contains("http://example.invalid/core.zip"));
      request.put("mainPid", -1L);
      request.put("workDir", "stale");
      Files.writeString(requestPath, request.toString());
      new WindowsUpdateService().downloadAndPrepare(plan, null);
      JSONObject refreshed = new JSONObject(Files.readString(requestPath));
      assertEquals(ProcessHandle.current().pid(), refreshed.getLong("mainPid"));
      assertEquals(tempDir.resolve("work").toString(), refreshed.getString("workDir"));
      assertEquals(1, proxy.requests.get(), "Verified cache must not transfer again");
    }
  }

  @Test
  void automaticWindowsPreparationNeedsExplicitActionForCurrentRequest() throws Exception {
    byte[] archive = "verified Windows component".getBytes(StandardCharsets.UTF_8);
    try (OneShotHttp proxy = new OneShotHttp(archive)) {
      useManualProxy(proxy.port());
      configureUpdatePaths();
      WindowsUpdatePlan plan = updatePlan("http://example.invalid/core.zip", archive);
      UpdateCheckResult offer = UpdateCheckResult.offerWindows(plan);
      AtomicInteger launches = new AtomicInteger();
      java.util.concurrent.atomic.AtomicReference<Path> prepared = new java.util.concurrent.atomic.AtomicReference<>();
      UpdateCheckCoordinator coordinator = new UpdateCheckCoordinator(s -> offer,
          new UpdateCheckCoordinator.OfferHandoff() {
            public void openWindows(UpdateCheckSelection s, UpdateCheckResult r) {
              throw new AssertionError("Automatic preparation must not open a manual dialog");
            }
            public void openPackage(UpdateCheckSelection s, UpdateCheckResult r) {
              throw new AssertionError("Wrong platform");
            }
          }, new UpdateCheckCoordinator.Runner() {
            public void runBackground(Runnable r) { r.run(); }
            public void runOnEdt(Runnable r) { r.run(); }
          }, s -> {}, new UpdateCheckCoordinator.DownloadService() {
            public Path prepare(UpdateCheckResult r, ResumableDownloader.Control control,
                ResumableDownloader.ProgressListener listener) throws Exception {
              Path request = new WindowsUpdateService().downloadAndPrepare(r.windowsPlan, control, listener);
              prepared.set(request);
              return request;
            }
            public void apply(UpdateCheckResult r, Path request) throws Exception {
              assertEquals(prepared.get(), request);
              assertEquals(ProcessHandle.current().pid(), new JSONObject(Files.readString(request)).getLong("mainPid"));
              launches.incrementAndGet();
            }
          });
      coordinator.captureStartupPolicy(true, true, UpdateCheckSelection.of(
          UpdateChannel.STABLE, UpdateSource.OFFICIAL_SITE, "next-2026-06-01.1"));
      coordinator.applicationReady();
      assertTrue(coordinator.state().ready());
      assertEquals(0, launches.get());
      assertEquals(1, proxy.requests.get());
      coordinator.openOffer();
      assertEquals(1, launches.get());
    }
  }

  @Test
  void legacyNvidia50PackageUsesUnifiedNvidiaUpdateFlavor() throws Exception {
    Path fromManifest = Files.createDirectories(tempDir.resolve("legacy-manifest").resolve("app"));
    Files.writeString(
        fromManifest.resolve("lizzieyzy-next-installed-manifest.json"),
        "{\"flavor\":\"nvidia50.cuda\"}");
    assertEquals("nvidia", WindowsUpdatePaths.detectFlavor(fromManifest));

    Path fromBackend = Files.createDirectories(tempDir.resolve("legacy-backend").resolve("app"));
    Path engineDir =
        Files.createDirectories(fromBackend.resolve("engines/katago/windows-x64"));
    Files.writeString(engineDir.resolve("lizzieyzy-next-engine-backend.txt"), "nvidia50-cuda");
    assertEquals("nvidia", WindowsUpdatePaths.detectFlavor(fromBackend));
  }

  private void useManualProxy(int port) {
    Lizzie.config = ConfigTestHelper.createForTests(tempDir.resolve("config"));
    Lizzie.config.uiConfig = new JSONObject();
    Lizzie.config.uiConfig.put(NetworkProxy.KEY_PROXY_MODE, NetworkProxy.MODE_MANUAL);
    Lizzie.config.uiConfig.put(NetworkProxy.KEY_PROXY_HOST, "127.0.0.1");
    Lizzie.config.uiConfig.put(NetworkProxy.KEY_PROXY_PORT, port);
  }

  private WindowsUpdatePlan updatePlan(String downloadUrl, byte[] archive) throws Exception {
    JSONObject manifest = UpdateManifestTest.validManifest();
    JSONObject component = manifest.getJSONArray("components").getJSONObject(0);
    component.put("downloadUrl", downloadUrl);
    component.put("sizeBytes", archive.length);
    component.put("sha256", sha256(archive));
    return WindowsUpdatePlan.create(
        UpdateManifest.parse(manifest),
        InstalledUpdateState.empty("next-2026-06-01.1", "windows", "opencl"),
        "next-2026-06-01.1",
        "opencl");
  }

  private void configureUpdatePaths() throws IOException {
    Path appRoot = Files.createDirectories(tempDir.resolve("app-root"));
    Path appDir = Files.createDirectories(appRoot.resolve("app"));
    Path workDir = Files.createDirectories(tempDir.resolve("work"));
    Path currentJar = appDir.resolve("lizzie-yzy2.5.3-shaded.jar");
    Files.writeString(currentJar, "old jar");
    System.setProperty(WindowsUpdatePaths.APP_ROOT_PROPERTY, appRoot.toString());
    System.setProperty(WindowsUpdatePaths.APP_DIR_PROPERTY, appDir.toString());
    System.setProperty(WindowsUpdatePaths.WORK_DIR_PROPERTY, workDir.toString());
    System.setProperty(WindowsUpdatePaths.CURRENT_JAR_PROPERTY, currentJar.toString());
  }

  private String sha256(byte[] bytes) throws Exception {
    return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
  }

  private static final class OneShotHttp implements AutoCloseable {
    final AtomicInteger requests = new AtomicInteger();
    final ServerSocket server;
    final ExecutorService executor = Executors.newSingleThreadExecutor();
    final byte[] body;
    volatile String lastRequestLine = "";

    OneShotHttp(String body) throws IOException {
      this(body.getBytes(StandardCharsets.UTF_8));
    }

    OneShotHttp(byte[] body) throws IOException {
      this.body = body;
      this.server = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
      executor.submit(this::serve);
    }

    int port() {
      return server.getLocalPort();
    }

    private void serve() {
      try {
        while (!server.isClosed()) {
          handle(server.accept());
        }
      } catch (IOException ignored) {
      }
    }

    private void handle(Socket socket) throws IOException {
      requests.incrementAndGet();
      try (socket) {
        BufferedReader reader =
            new BufferedReader(
                new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
        lastRequestLine = reader.readLine();
        String line;
        while ((line = reader.readLine()) != null && !line.isEmpty()) {}
        OutputStream out = socket.getOutputStream();
        out.write(
            ("HTTP/1.1 200 OK\r\nContent-Length: "
                    + body.length
                    + "\r\nConnection: close\r\n\r\n")
                .getBytes(StandardCharsets.US_ASCII));
        out.write(body);
      }
    }

    @Override
    public void close() throws IOException {
      server.close();
      executor.shutdownNow();
    }
  }
}
