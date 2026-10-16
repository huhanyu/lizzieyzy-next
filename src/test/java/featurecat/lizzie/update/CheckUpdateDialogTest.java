package featurecat.lizzie.update;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import featurecat.lizzie.ConfigTestHelper;
import featurecat.lizzie.Lizzie;
import java.awt.Component;
import java.awt.Container;
import java.awt.GraphicsEnvironment;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.AbstractButton;
import javax.swing.JDialog;
import javax.swing.JCheckBox;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CheckUpdateDialogTest {
  @TempDir Path directory;

  @BeforeEach
  void requireDisplay() {
    boolean headless = GraphicsEnvironment.isHeadless();
    assertFalse(Boolean.getBoolean("lizzie.desktop.required") && headless,
        "Desktop lane requires java.awt.headless=false and an operational display");
    assumeFalse(headless);
  }

  @Test
  void preferencesAndResultSurviveClosingAndReopeningWithoutAnotherCheck() throws Exception {
    SwingUtilities.invokeAndWait(() -> {
      var previous = Lizzie.config;
      CheckUpdateDialog first = null;
      CheckUpdateDialog reopened = null;
      try {
        Lizzie.config = ConfigTestHelper.createForTests(directory);
        Lizzie.config.uiConfig = new JSONObject();
        AtomicInteger discoveries = new AtomicInteger();
        UpdateCheckCoordinator coordinator = new UpdateCheckCoordinator(s -> {
          discoveries.incrementAndGet();
          return UpdateCheckResult.offerPackage(new PackageUpdatePlan(null, null, "v", "linux", "x64", "cpu"));
        }, new UpdateCheckCoordinator.OfferHandoff() {
          public void openWindows(UpdateCheckSelection s, UpdateCheckResult r) { fail("No user action"); }
          public void openPackage(UpdateCheckSelection s, UpdateCheckResult r) { fail("No user action"); }
        }, new UpdateCheckCoordinator.Runner() {
          public void runBackground(Runnable work) { work.run(); }
          public void runOnEdt(Runnable work) { work.run(); }
        }, s -> {});
        coordinator.captureStartupPolicy(true, false, UpdateCheckSelection.of(UpdateChannel.STABLE, UpdateSource.GITHUB, "next-2026-08-01.1"));
        coordinator.applicationReady();
        first = new CheckUpdateDialog(null, coordinator);
        first.setVisible(true);
        assertFalse(first.isModal());
        assertTrue(first.isShowing());
        String message = find(first, JTextArea.class).getText();
        first.dispose();
        reopened = new CheckUpdateDialog(null, coordinator);
        reopened.setVisible(true);
        assertEquals(message, find(reopened, JTextArea.class).getText());
        JCheckBox preference = find(reopened, JCheckBox.class);
        assertFalse(preference.isSelected());
        preference.doClick();
        assertTrue(UpdatePreferences.autoCheck());
        assertEquals(1, discoveries.get());
        assertEquals(UpdateCheckCoordinator.Origin.AUTOMATIC, coordinator.state().origin());
        JCheckBox download = findDownload(reopened, preference);
        assertTrue(download.isEnabled());
        download.doClick();
        assertTrue(UpdatePreferences.autoDownload());
        preference.doClick();
        assertFalse(download.isSelected());
        assertFalse(download.isEnabled());
        preference.doClick();
        assertFalse(download.isSelected());
        assertEquals(1, discoveries.get());
      } finally {
        if (first != null) first.dispose();
        if (reopened != null) reopened.dispose();
        Lizzie.config = previous;
      }
    });
  }

  @Test
  void restartHintOnlyMarksOptionsEnabledSinceLaunch() throws Exception {
    SwingUtilities.invokeAndWait(() -> {
      var previous = Lizzie.config;
      CheckUpdateDialog launched = null;
      CheckUpdateDialog relaunched = null;
      try {
        Lizzie.config = ConfigTestHelper.createForTests(directory);
        Lizzie.config.uiConfig = new JSONObject();
        launched = new CheckUpdateDialog(null, launchedCoordinator());
        JCheckBox check = find(launched, JCheckBox.class);
        JCheckBox download = findDownload(launched, check);
        String plainCheck = check.getText();
        String plainDownload = download.getText();
        check.doClick();
        download.doClick();
        assertNotEquals(plainCheck, check.getText());
        assertNotEquals(plainDownload, download.getText());
        download.doClick();
        assertEquals(plainDownload, download.getText());
        download.doClick();

        relaunched = new CheckUpdateDialog(null, launchedCoordinator());
        JCheckBox relaunchedCheck = find(relaunched, JCheckBox.class);
        JCheckBox relaunchedDownload = findDownload(relaunched, relaunchedCheck);
        assertTrue(relaunchedCheck.isSelected());
        assertTrue(relaunchedDownload.isSelected());
        assertEquals(plainCheck, relaunchedCheck.getText());
        assertEquals(plainDownload, relaunchedDownload.getText());
        relaunchedCheck.doClick();
        assertEquals(plainCheck, relaunchedCheck.getText());
        assertEquals(plainDownload, relaunchedDownload.getText());
        relaunchedCheck.doClick();
        relaunchedDownload.doClick();
        assertNotEquals(plainCheck, relaunchedCheck.getText());
        assertNotEquals(plainDownload, relaunchedDownload.getText());
      } finally {
        if (launched != null) launched.dispose();
        if (relaunched != null) relaunched.dispose();
        Lizzie.config = previous;
      }
    });
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void changingSelectionRevokesHintsImmediatelyAndAfterReopening(boolean channel) throws Exception {
    SwingUtilities.invokeAndWait(() -> {
      var previous = Lizzie.config;
      List<JDialog> views = new ArrayList<>();
      try {
        Lizzie.config = ConfigTestHelper.createForTests(directory);
        Lizzie.config.uiConfig = new JSONObject()
            .put(UpdatePreferences.AUTO_CHECK, true).put(UpdatePreferences.AUTO_DOWNLOAD, true);
        UpdateChannel.persist(UpdateChannel.STABLE);
        UpdateSource.persist(UpdateSource.OFFICIAL_SITE);
        UpdateCheckCoordinator coordinator = launchedCoordinator();
        CheckUpdateDialog page = new CheckUpdateDialog(null, coordinator);
        views.add(page);
        page.setVisible(true);
        JCheckBox check = find(page, JCheckBox.class);
        JCheckBox download = findDownload(page, check);
        String plainCheck = check.getText();
        String plainDownload = download.getText();
        button(page, "WindowsUpdate.channel.stable", "Official").doClick();
        assertEquals(plainCheck, check.getText(), "An unchanged selection is not a revocation");
        button(page, channel ? "WindowsUpdate.channel.beta" : "WindowsUpdate.source.github",
            channel ? "Test" : "GitHub").doClick();
        assertNotEquals(plainCheck, check.getText());
        assertNotEquals(plainDownload, download.getText());
        page.validate();
        assertTrue(check.getWidth() >= check.getPreferredSize().width,
            "Pending label must fit without a manual window resize");
        button(page, channel ? "WindowsUpdate.channel.stable" : "WindowsUpdate.source.official",
            channel ? "Official" : "Official site").doClick();
        assertNotEquals(plainCheck, check.getText(), "Switching back does not reauthorize startup");
        page.dispose();
        CheckUpdateDialog reopened = new CheckUpdateDialog(null, coordinator);
        views.add(reopened);
        JCheckBox reopenedCheck = find(reopened, JCheckBox.class);
        assertEquals(check.getText(), reopenedCheck.getText());
        assertEquals(download.getText(), findDownload(reopened, reopenedCheck).getText());
        coordinator.applicationReady();
      } finally {
        views.forEach(JDialog::dispose);
        Lizzie.config = previous;
      }
    });
  }

  private static UpdateCheckCoordinator launchedCoordinator() {
    UpdateCheckCoordinator coordinator = new UpdateCheckCoordinator(
        s -> { throw new AssertionError("No discovery"); },
        new UpdateCheckCoordinator.OfferHandoff() {
          public void openWindows(UpdateCheckSelection s, UpdateCheckResult r) { fail("No offer"); }
          public void openPackage(UpdateCheckSelection s, UpdateCheckResult r) { fail("No offer"); }
        }, new UpdateCheckCoordinator.Runner() {
          public void runBackground(Runnable work) { fail("No background work"); }
          public void runOnEdt(Runnable work) { work.run(); }
        }, s -> {});
    coordinator.captureStartupPolicy(UpdatePreferences.autoCheck(), UpdatePreferences.autoDownload(),
        UpdateCheckSelection.of(UpdateChannel.STABLE, UpdateSource.GITHUB, "next-2026-08-01.1"));
    return coordinator;
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void automaticOfferIsViewOnlyUntilUpdateNowEvenAfterReopening(boolean autoDownload) throws Exception {
    SwingUtilities.invokeAndWait(() -> {
      var previous = Lizzie.config;
      List<JDialog> views = new ArrayList<>();
      try {
        Lizzie.config = ConfigTestHelper.createForTests(directory);
        Lizzie.config.uiConfig = new JSONObject();
        UpdateCheckResult offer = UpdateCheckResult.offerWindows(WindowsUpdatePlan.create(
            UpdateManifest.parse(UpdateManifestTest.validManifest()),
            InstalledUpdateState.empty("next-2026-06-01.1", "windows", "opencl"),
            "next-2026-06-01.1", "opencl"));
        ArrayDeque<Runnable> background = new ArrayDeque<>();
        AtomicInteger prepares = new AtomicInteger();
        AtomicInteger applies = new AtomicInteger();
        UpdateCheckCoordinator[] owner = new UpdateCheckCoordinator[1];
        Path prepared = directory.resolve("verified-update");
        owner[0] = new UpdateCheckCoordinator(s -> offer, new UpdateCheckCoordinator.OfferHandoff() {
          public void openWindows(UpdateCheckSelection s, UpdateCheckResult r) {
            WindowsUpdateDialog dialog = new WindowsUpdateDialog(null, owner[0], r, "");
            views.add(dialog);
            dialog.setVisible(true);
          }
          public void openPackage(UpdateCheckSelection s, UpdateCheckResult r) { fail("Wrong platform"); }
        }, new UpdateCheckCoordinator.Runner() {
          public void runBackground(Runnable work) { background.add(work); }
          public void runOnEdt(Runnable work) { work.run(); }
        }, s -> owner[0].openOffer(), new UpdateCheckCoordinator.DownloadService() {
          public Path prepare(UpdateCheckResult r, ResumableDownloader.Control c,
              ResumableDownloader.ProgressListener p) throws Exception {
            prepares.incrementAndGet();
            return Files.writeString(prepared, "verified update");
          }
          public void apply(UpdateCheckResult r, Path path) throws Exception {
            assertEquals("verified update", Files.readString(path));
            applies.incrementAndGet();
          }
        });
        UpdateCheckCoordinator coordinator = owner[0];
        coordinator.captureStartupPolicy(true, autoDownload, UpdateCheckSelection.of(
            UpdateChannel.STABLE, UpdateSource.GITHUB, "next-2026-06-01.1"));
        coordinator.applicationReady();
        background.remove().run();
        if (autoDownload) {
          assertTrue(views.isEmpty(), "Background download must not open a progress dialog");
          background.remove().run();
          assertTrue(coordinator.state().ready());
        }
        assertEquals(1, views.size());
        JDialog reminder = views.get(0);
        assertTrue(reminder.isShowing());
        assertFalse(reminder.isModal());
        assertFalse(reminder.isAutoRequestFocus());
        assertEquals(0, applies.get());
        reminder.dispose();
        coordinator.applicationReady();
        assertFalse(reminder.isShowing(), "Closing the reminder must suppress automatic reopening");
        CheckUpdateDialog page = new CheckUpdateDialog(null, coordinator);
        views.add(page);
        page.setVisible(true);
        button(page, "WindowsUpdate.viewOffer", "View update").doClick();
        assertEquals(3, views.size());
        assertEquals(0, applies.get(), "Reopening details must not install prepared files");
        assertEquals(autoDownload ? 1 : 0, prepares.get());
        button(views.get(2), "WindowsUpdate.btnUpdate", "Update now").doClick();
        if (!autoDownload) background.remove().run();
        assertEquals(1, prepares.get(), "Ready confirmation must reuse the existing download");
        assertEquals(1, applies.get(), "Only Update now grants installation authority");
        assertTrue(background.isEmpty());
      } finally {
        views.forEach(JDialog::dispose);
        Lizzie.config = previous;
      }
    });
  }

  private static AbstractButton button(Container parent, String key, String fallback) {
    String text = UpdateText.tr(key, fallback, fallback);
    for (Component child : parent.getComponents()) {
      if (child instanceof AbstractButton button && text.equals(button.getText())) return button;
      if (child instanceof Container nested) {
        AbstractButton found = button(nested, key, fallback);
        if (found != null) return found;
      }
    }
    return null;
  }

  private static JCheckBox findDownload(Container parent, JCheckBox excluded) {
    for (Component child : parent.getComponents()) {
      if (child instanceof JCheckBox box && box != excluded) return box;
      if (child instanceof Container nested) {
        JCheckBox found = findDownload(nested, excluded);
        if (found != null) return found;
      }
    }
    return null;
  }

  private static <T extends Component> T find(Container parent, Class<T> type) {
    for (Component child : parent.getComponents()) {
      if (type.isInstance(child)) return type.cast(child);
      if (child instanceof Container nested) {
        T found = find(nested, type);
        if (found != null) return found;
      }
    }
    return null;
  }
}
