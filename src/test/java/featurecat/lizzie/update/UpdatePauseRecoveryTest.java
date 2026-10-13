package featurecat.lizzie.update;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import featurecat.lizzie.ConfigTestHelper;
import featurecat.lizzie.Lizzie;
import java.awt.Component;
import java.awt.Container;
import java.awt.GraphicsEnvironment;
import java.awt.event.WindowEvent;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import javax.swing.AbstractButton;
import javax.swing.JDialog;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class UpdatePauseRecoveryTest {
  @TempDir Path directory;

  @BeforeEach
  void requireDisplay() {
    boolean headless = GraphicsEnvironment.isHeadless();
    assertFalse(Boolean.getBoolean("lizzie.desktop.required") && headless,
        "Desktop lane requires java.awt.headless=false and an operational display");
    assumeFalse(headless);
  }

  @Test
  void windowsManualDownloadCanResumeAfterClosingItsView() throws Exception {
    exercise(true, true);
  }

  @Test
  void packageManualDownloadCanResumeAfterClosingItsView() throws Exception {
    exercise(false, true);
  }

  @Test
  void recoveringAutomaticDownloadDoesNotGrantCompletionAuthority() throws Exception {
    exercise(false, false);
  }

  private void exercise(boolean windows, boolean manual) throws Exception {
    SwingUtilities.invokeAndWait(() -> {
      var previous = Lizzie.config;
      List<JDialog> views = new ArrayList<>();
      try {
        Lizzie.config = ConfigTestHelper.createForTests(directory);
        Lizzie.config.uiConfig = new JSONObject();
        UpdateCheckResult offer = offer(windows);
        ArrayDeque<Runnable> background = new ArrayDeque<>();
        ArrayDeque<Runnable> edt = new ArrayDeque<>();
        int[] prepares = {0};
        int[] applies = {0};
        int[] discoveries = {0};
        UpdateCheckCoordinator[] owner = new UpdateCheckCoordinator[1];
        Path prepared = directory.resolve("verified");
        UpdateCheckCoordinator coordinator = new UpdateCheckCoordinator(s -> {
          discoveries[0]++;
          return offer;
        }, new UpdateCheckCoordinator.OfferHandoff() {
          public void openWindows(UpdateCheckSelection s, UpdateCheckResult r) {}
          public void openPackage(UpdateCheckSelection s, UpdateCheckResult r) {}
        }, new UpdateCheckCoordinator.Runner() {
          public void runBackground(Runnable work) { background.add(work); }
          public void runOnEdt(Runnable work) { edt.add(work); }
        }, s -> {}, new UpdateCheckCoordinator.DownloadService() {
          public Path prepare(UpdateCheckResult result, ResumableDownloader.Control control,
              ResumableDownloader.ProgressListener listener) {
            prepares[0]++;
            assertSame(offer, result);
            UpdateCheckCoordinator c = owner[0];
            JDialog platform = views.get(0);
            listener.onProgress(progress(ResumableDownloader.State.DOWNLOADING));
            drain(edt);
            button(platform, "WindowsUpdate.btnPause", "Pause").doClick();
            assertTrue(control.isPaused());
            assertEquals("PAUSED", c.state().download().phase().name());
            assertTrue(status(platform).contains("paused"));
            assertTrue(button(platform, "WindowsUpdate.btnResume", "Resume").isEnabled());
            // A transfer event queued before the click must not erase the paused state.
            listener.onProgress(progress(ResumableDownloader.State.DOWNLOADING));
            drain(edt);
            assertEquals("PAUSED", c.state().download().phase().name());
            platform.dispatchEvent(new WindowEvent(platform, WindowEvent.WINDOW_CLOSING));
            assertFalse(platform.isDisplayable());
            assertFalse(control.isCancelled());
            CheckUpdateDialog page = new CheckUpdateDialog(null, c);
            views.add(page);
            page.setVisible(true);
            assertTrue(status(page).contains("paused"));
            assertTrue(c.busy());
            assertFalse(c.checkManually(c.state().selection()));
            c.retryDownload();
            c.openOffer();
            assertTrue(background.isEmpty(), "No replacement writer may be queued");
            button(page, "WindowsUpdate.btnResume", "Resume").doClick();
            assertFalse(control.isPaused(), "Resume must reach the original prepare control");
            assertFalse(control.isCancelled());
            assertEquals(UpdateCheckCoordinator.DownloadPhase.DOWNLOADING, c.state().download().phase());
            // A stale transport PAUSED event cannot revert the explicit resume command.
            listener.onProgress(progress(ResumableDownloader.State.PAUSED));
            drain(edt);
            assertFalse(status(page).contains("paused"));
            button(page, "WindowsUpdate.btnPause", "Pause").doClick();
            assertTrue(control.isPaused());
            JDialog recoveredPlatform = platform(windows, c, offer);
            views.add(recoveredPlatform);
            recoveredPlatform.setVisible(true);
            button(recoveredPlatform, "WindowsUpdate.btnResume", "Resume").doClick();
            assertFalse(control.isPaused());
            assertFalse(status(page).contains("paused"));
            if (manual) {
              c.disableAutomaticDownload();
              c.invalidateAutomatic();
              assertFalse(control.isCancelled());
            }
            assertTrue(background.isEmpty());
            return prepared;
          }
          public void apply(UpdateCheckResult result, Path path) {
            assertSame(offer, result);
            assertEquals(prepared, path);
            applies[0]++;
          }
        });
        owner[0] = coordinator;
        var selection = UpdateCheckSelection.of(UpdateChannel.STABLE, UpdateSource.GITHUB,
            "next-2026-06-01.1");
        if (manual) coordinator.checkManually(selection);
        else {
          coordinator.captureStartupPolicy(true, true, selection);
          coordinator.applicationReady();
        }
        background.remove().run();
        drain(edt);
        JDialog platform = platform(windows, coordinator, offer);
        views.add(platform);
        platform.setVisible(true);
        if (manual) button(platform, windows ? "WindowsUpdate.btnUpdate" : "WindowsUpdate.btnDownload",
            windows ? "Update now" : "Download update").doClick();
        assertEquals(1, background.size());
        background.remove().run();
        drain(edt);
        assertTrue(coordinator.state().ready());
        assertFalse(coordinator.busy());
        assertEquals(1, prepares[0]);
        assertEquals(1, discoveries[0]);
        assertEquals(manual ? 1 : 0, applies[0]);
        assertEquals(manual ? UpdateCheckCoordinator.Origin.MANUAL : UpdateCheckCoordinator.Origin.AUTOMATIC,
            coordinator.state().origin());
        if (!manual) {
          coordinator.startDownload(offer, true);
          assertEquals(1, applies[0]);
        }
      } finally {
        views.forEach(JDialog::dispose);
        Lizzie.config = previous;
      }
    });
  }

  private static JDialog platform(boolean windows, UpdateCheckCoordinator coordinator, UpdateCheckResult offer) {
    return windows ? new WindowsUpdateDialog(null, coordinator, offer, "")
        : new PackageUpdateDialog(null, coordinator, offer, "");
  }

  private static UpdateCheckResult offer(boolean windows) {
    if (windows) return UpdateCheckResult.offerWindows(WindowsUpdatePlan.create(
        UpdateManifest.parse(UpdateManifestTest.validManifest()),
        InstalledUpdateState.empty("next-2026-06-01.1", "windows", "opencl"),
        "next-2026-06-01.1", "opencl"));
    JSONObject asset = new JSONObject().put("platform", "linux").put("arch", "x64")
        .put("flavor", "opencl").put("installMode", "download-archive").put("assetName", "test.zip")
        .put("sizeBytes", 10).put("sha256", "0".repeat(64))
        .put("downloadUrl", "http://127.0.0.1/test.zip").put("mirrorUrls", new JSONArray());
    UpdateManifest manifest = UpdateManifest.parse(new JSONObject().put("schemaVersion", 2)
        .put("releaseTag", "next-2026-10-13.1").put("publishedAt", "2026-10-13T00:00:00Z")
        .put("notesUrl", "https://example.com/release").put("minUpdaterVersion", "1")
        .put("packages", new JSONArray().put(asset)));
    return UpdateCheckResult.offerPackage(new PackageUpdatePlan(manifest, manifest.packages.get(0),
        "next-2026-06-01.1", "linux", "x64", "opencl"));
  }

  private static ResumableDownloader.Progress progress(ResumableDownloader.State state) {
    return new ResumableDownloader.Progress(state, "test.zip", "local", 5, 10, 1, 5);
  }

  private static void drain(ArrayDeque<Runnable> queue) {
    while (!queue.isEmpty()) queue.remove().run();
  }

  private static AbstractButton button(Container parent, String key, String fallback) {
    String text = UpdateText.tr(key, fallback, fallback);
    return components(parent).stream().filter(c -> c instanceof AbstractButton b
        && b.isShowing() && b.getText().equals(text)).map(c -> (AbstractButton) c)
        .findFirst().orElseThrow(() -> new AssertionError("Missing visible action: " + text));
  }

  private static String status(Container parent) {
    return components(parent).stream().filter(c -> c instanceof JTextArea)
        .map(c -> ((JTextArea) c).getText()).reduce("", (a, b) -> a + "\n" + b);
  }

  private static List<Component> components(Container parent) {
    List<Component> result = new ArrayList<>();
    for (Component child : parent.getComponents()) {
      result.add(child);
      if (child instanceof Container nested) result.addAll(components(nested));
    }
    return result;
  }
}
