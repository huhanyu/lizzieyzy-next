package featurecat.lizzie.update;

import featurecat.lizzie.Lizzie;
import java.awt.Component;
import javax.swing.SwingUtilities;

public final class WindowsUpdateController {
  private static final UpdateCheckCoordinator COORDINATOR =
      new UpdateCheckCoordinator(UpdateDiscovery::check, new ProductionHandoff(),
          new SwingRunner(), state -> showPage(Lizzie.frame, true));
  private static CheckUpdateDialog page;

  private WindowsUpdateController() {}

  public static void openCheckUpdatePage(Component parent) {
    SwingUtilities.invokeLater(() -> showPage(parent, false));
  }

  public static void applicationReady() {
    SwingUtilities.invokeLater(() -> COORDINATOR.applicationReady(
        UpdatePreferences.autoCheck(), UpdatePreferences.autoDownload(), UpdateCheckSelection.of(
            UpdateChannel.current(), UpdateSource.current(), Lizzie.nextVersion)));
  }

  static UpdateCheckCoordinator coordinator() { return COORDINATOR; }

  static void automaticPolicyChanged() {
    COORDINATOR.invalidateAutomatic();
    if (page != null && page.isAutomaticReminder()) page.dispose();
  }

  static void automaticDownloadDisabled() {
    COORDINATOR.disableAutomaticDownload();
  }

  private static void showPage(Component parent, boolean automatic) {
    if (page != null && page.isDisplayable()) {
      if (!automatic) {
        page.setVisible(true);
        page.toFront();
      }
      return;
    }
    page = new CheckUpdateDialog(parent, COORDINATOR, automatic);
    page.setVisible(true);
  }

  private static final class ProductionHandoff implements UpdateCheckCoordinator.OfferHandoff {
    @Override
    public void openWindows(UpdateCheckSelection selection, UpdateCheckResult result) {
      if (page != null) page.dispose();
      new WindowsUpdateDialog(
              Lizzie.frame, COORDINATOR, result, UpdateCheckFeedback.warning(result))
          .setVisible(true);
    }

    @Override
    public void openPackage(UpdateCheckSelection selection, UpdateCheckResult result) {
      if (page != null) page.dispose();
      new PackageUpdateDialog(
              Lizzie.frame, COORDINATOR, result, UpdateCheckFeedback.warning(result))
          .setVisible(true);
    }
  }

  private static final class SwingRunner implements UpdateCheckCoordinator.Runner {
    @Override
    public void runBackground(Runnable work) {
      Thread thread = new Thread(work, "lizzie-update-discovery");
      thread.setDaemon(true);
      thread.start();
    }

    @Override
    public void runOnEdt(Runnable work) {
      SwingUtilities.invokeLater(work);
    }
  }
}
