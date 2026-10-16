package featurecat.lizzie.update;

import featurecat.lizzie.Lizzie;
import java.awt.Component;
import javax.swing.SwingUtilities;

public final class WindowsUpdateController {
  private static final UpdateCheckCoordinator COORDINATOR =
      new UpdateCheckCoordinator(UpdateDiscovery::check, new ProductionHandoff(),
          new SwingRunner(), state -> coordinator().openOffer());
  private static CheckUpdateDialog page;

  private WindowsUpdateController() {}

  public static void openCheckUpdatePage(Component parent) {
    SwingUtilities.invokeLater(() -> showPage(parent));
  }

  public static void captureStartupPolicy() {
    COORDINATOR.captureStartupPolicy(
        UpdatePreferences.autoCheck(), UpdatePreferences.autoDownload(), UpdateCheckSelection.of(
            UpdateChannel.current(), UpdateSource.current(), Lizzie.nextVersion));
  }

  public static void applicationReady() {
    SwingUtilities.invokeLater(COORDINATOR::applicationReady);
  }

  static UpdateCheckCoordinator coordinator() { return COORDINATOR; }

  static void automaticPolicyChanged() {
    COORDINATOR.invalidateAutomatic();
  }

  static void automaticCheckDisabled() {
    COORDINATOR.disableAutomaticCheck();
  }

  static void automaticDownloadDisabled() {
    COORDINATOR.disableAutomaticDownload();
  }

  private static void showPage(Component parent) {
    if (page != null && page.isDisplayable()) {
      page.setVisible(true);
      page.toFront();
      return;
    }
    page = new CheckUpdateDialog(parent, COORDINATOR);
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
