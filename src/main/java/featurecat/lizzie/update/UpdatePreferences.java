package featurecat.lizzie.update;

import featurecat.lizzie.Lizzie;
import java.io.IOException;

/** Saved startup policy; enabling it never schedules work in the current process. */
final class UpdatePreferences {
  static final String AUTO_CHECK = "auto-check-update";
  static final String AUTO_DOWNLOAD = "auto-download-update";

  private UpdatePreferences() {}

  static boolean autoCheck() {
    return Lizzie.config != null && Lizzie.config.uiConfig != null
        && Lizzie.config.uiConfig.optBoolean(AUTO_CHECK, false);
  }

  static boolean autoDownload() {
    return autoCheck() && Lizzie.config.uiConfig.optBoolean(AUTO_DOWNLOAD, false);
  }

  static void persistAutoDownload(boolean enabled) {
    if (Lizzie.config == null || Lizzie.config.uiConfig == null) return;
    Lizzie.config.uiConfig.put(AUTO_DOWNLOAD, enabled && autoCheck());
    if (!enabled) WindowsUpdateController.automaticDownloadDisabled();
    save();
  }

  static void persistAutoCheck(boolean enabled) {
    if (Lizzie.config == null || Lizzie.config.uiConfig == null) return;
    Lizzie.config.uiConfig.put(AUTO_CHECK, enabled);
    if (!enabled) {
      Lizzie.config.uiConfig.put(AUTO_DOWNLOAD, false);
      WindowsUpdateController.automaticCheckDisabled();
    }
    save();
  }

  private static void save() {
    if (Lizzie.config.config == null) return;
    try {
      Lizzie.config.save();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }
}
