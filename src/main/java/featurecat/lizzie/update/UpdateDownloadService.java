package featurecat.lizzie.update;

import featurecat.lizzie.Lizzie;
import java.io.IOException;
import java.nio.file.Path;

/** Preparation never grants permission to launch an installer or an external application. */
final class UpdateDownloadService implements UpdateCheckCoordinator.DownloadService {
  private final WindowsUpdateService windows = new WindowsUpdateService();
  private final PlatformUpdateService packages = new PlatformUpdateService();

  @Override
  public Path prepare(
      UpdateCheckResult result,
      ResumableDownloader.Control control,
      ResumableDownloader.ProgressListener progress)
      throws IOException {
    return result.windowsPlan != null
        ? windows.downloadAndPrepare(result.windowsPlan, control, progress)
        : packages.download(result.packagePlan, control, progress);
  }

  @Override
  public void apply(UpdateCheckResult result, Path prepared) throws IOException {
    if (result.windowsPlan != null) {
      windows.launchHelper(prepared);
      Lizzie.shutdown();
    } else {
      packages.openDownloadedPackage(result.packagePlan, prepared);
    }
  }
}
