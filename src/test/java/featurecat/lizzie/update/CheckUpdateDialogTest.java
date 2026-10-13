package featurecat.lizzie.update;

import static org.junit.jupiter.api.Assertions.*;

import featurecat.lizzie.ConfigTestHelper;
import featurecat.lizzie.Lizzie;
import java.awt.Component;
import java.awt.Container;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.JCheckBox;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CheckUpdateDialogTest {
  @TempDir Path directory;

  @Test
  void reminderIsNonmodalNonFocusRequestingAndCanRecoverAfterClose() throws Exception {
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
        coordinator.applicationReady(true, false, UpdateCheckSelection.of(UpdateChannel.STABLE, UpdateSource.GITHUB, "next-2026-08-01.1"));
        first = new CheckUpdateDialog(null, coordinator, true);
        first.setVisible(true);
        assertFalse(first.isModal());
        assertFalse(first.isAutoRequestFocus());
        assertTrue(first.isShowing());
        String message = find(first, JTextArea.class).getText();
        assertFalse(message.isBlank());
        first.dispose();
        reopened = new CheckUpdateDialog(null, coordinator, false);
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
