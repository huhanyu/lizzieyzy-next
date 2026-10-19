package featurecat.lizzie.update;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

class PlatformUpdateServiceTest {
  @org.junit.jupiter.api.io.TempDir java.nio.file.Path directory;

  @Test
  void resumesVerifiedIdentityReusesFullFileAndSeparatesChangedAsset() throws Exception {
    byte[] body = "signed package bytes".getBytes(java.nio.charset.StandardCharsets.UTF_8);
    String hash =
        java.util.HexFormat.of()
            .formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(body));
    java.util.List<String> ranges = new java.util.ArrayList<>();
    var server =
        com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/asset",
        exchange -> {
          String range = exchange.getRequestHeaders().getFirst("Range");
          ranges.add(range == null ? "full" : range);
          int offset = range == null ? 0 : Integer.parseInt(range.substring(6, range.length() - 1));
          if (range != null)
            exchange
                .getResponseHeaders()
                .add(
                    "Content-Range",
                    "bytes " + offset + "-" + (body.length - 1) + "/" + body.length);
          exchange.sendResponseHeaders(range == null ? 200 : 206, body.length - offset);
          exchange.getResponseBody().write(body, offset, body.length - offset);
          exchange.close();
        });
    server.start();
    String prior = System.getProperty(PlatformUpdateService.DOWNLOAD_DIR_PROPERTY);
    System.setProperty(PlatformUpdateService.DOWNLOAD_DIR_PROPERTY, directory.toString());
    try {
      JSONObject asset = packageAsset("linux", "x64", "opencl", "same-name.zip");
      asset.put("downloadUrl", "http://127.0.0.1:" + server.getAddress().getPort() + "/asset");
      asset.put("sha256", hash).put("sizeBytes", body.length);
      UpdateManifest manifest = manifest(asset);
      PackageUpdatePlan plan =
          new PackageUpdatePlan(manifest, manifest.packages.get(0), "v", "linux", "x64", "opencl");
      java.nio.file.Path identity = java.nio.file.Files.createDirectories(directory.resolve(hash));
      String oldHash = "0".repeat(64);
      java.nio.file.Files.createDirectories(directory.resolve(oldHash));
      java.nio.file.Files.writeString(
          directory.resolve(oldHash).resolve("same-name.zip.part"), "stale");
      java.nio.file.Files.write(
          identity.resolve("same-name.zip.part"), java.util.Arrays.copyOf(body, 7));
      PlatformUpdateService service = new PlatformUpdateService();
      java.nio.file.Path output = service.download(plan, new ResumableDownloader.Control(), null);
      org.junit.jupiter.api.Assertions.assertArrayEquals(
          body, java.nio.file.Files.readAllBytes(output));
      assertEquals(java.util.List.of("bytes=7-"), ranges);
      new PlatformUpdateService().download(plan, new ResumableDownloader.Control(), null);
      assertEquals(1, ranges.size());
      java.nio.file.Files.writeString(output, "corrupted");
      service.download(plan, new ResumableDownloader.Control(), null);
      assertEquals(java.util.List.of("bytes=7-", "full"), ranges);
      assertEquals(
          "stale",
          java.nio.file.Files.readString(directory.resolve(oldHash).resolve("same-name.zip.part")));
    } finally {
      server.stop(0);
      if (prior == null) System.clearProperty(PlatformUpdateService.DOWNLOAD_DIR_PROPERTY);
      else System.setProperty(PlatformUpdateService.DOWNLOAD_DIR_PROPERTY, prior);
    }
  }

  @Test
  void selectsExactArchitectureAndFlavorBeforeAllFallbacks() {
    UpdateManifest manifest =
        manifest(
            packageAsset("macos", "x64", "with-katago", "intel.dmg"),
            packageAsset("macos", "arm64", "all", "arm-all.dmg"),
            packageAsset("macos", "arm64", "with-katago", "arm.dmg"));

    UpdateManifest.PackageAsset selected =
        PlatformUpdateService.selectPackage(manifest, "macos", "arm64", "with-katago");

    assertEquals("arm.dmg", selected.assetName);
  }

  @Test
  void keepsLinuxFlavorAndRejectsWrongArchitecture() {
    UpdateManifest manifest =
        manifest(
            packageAsset("linux", "x64", "opencl", "linux-opencl.zip"),
            packageAsset("linux", "arm64", "opencl", "linux-arm.zip"));

    assertEquals(
        "linux-opencl.zip",
        PlatformUpdateService.selectPackage(manifest, "linux", "x64", "opencl").assetName);
    assertNull(PlatformUpdateService.selectPackage(manifest, "linux", "x64", "nvidia"));
  }

  @Test
  void legacyNvidia50FlavorSelectsTheUnifiedNvidiaPackage() {
    assertEquals("nvidia", PlatformUpdateService.normalizeFlavor("nvidia50.cuda"));
    assertEquals("nvidia", PlatformUpdateService.normalizeFlavor("NVIDIA50-CUDA"));
    assertEquals("nvidia", PlatformUpdateService.normalizeFlavor("nvidia.50.cuda"));
  }

  private UpdateManifest manifest(JSONObject... packages) {
    JSONObject payload = SignedUpdateEnvelopeTest.validPayload();
    payload.put("packages", new JSONArray(List.of(packages)));
    return UpdateManifest.parse(payload);
  }

  private JSONObject packageAsset(String platform, String arch, String flavor, String name) {
    JSONObject json = new JSONObject();
    json.put("platform", platform);
    json.put("arch", arch);
    json.put("flavor", flavor);
    json.put("installMode", "download-archive");
    json.put("assetName", name);
    json.put("sizeBytes", 10L);
    json.put("sha256", "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");
    json.put("downloadUrl", "https://download.goagent.top/" + name);
    json.put("mirrorUrls", new JSONArray());
    return json;
  }
}
