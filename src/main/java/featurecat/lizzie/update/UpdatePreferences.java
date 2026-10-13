package featurecat.lizzie.update;

import featurecat.lizzie.Lizzie;
import java.io.IOException;

/** Saved startup policy; enabling it never schedules work in the current process. */
final class UpdatePreferences {
  static final String AUTO_CHECK = "auto-check-update";

  private UpdatePreferences() {}

  static boolean autoCheck() {
    return Lizzie.config != null && Lizzie.config.uiConfig != null
        && Lizzie.config.uiConfig.optBoolean(AUTO_CHECK, false);
  }

  static void persistAutoCheck(boolean enabled) {
    if (Lizzie.config == null || Lizzie.config.uiConfig == null) return;
    Lizzie.config.uiConfig.put(AUTO_CHECK, enabled);
    if (!enabled) WindowsUpdateController.automaticPolicyChanged();
    if (Lizzie.config.config == null) return;
    try {
      Lizzie.config.save();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }
}
