package featurecat.lizzie.gui;

import java.util.Locale;
import java.util.Map;

/** Selects the safest main-menu implementation for the current desktop session. */
public enum MenuPresentationMode {
  CUSTOM_STRIP("custom"),
  NATIVE_MENU_BAR("native");

  public static final String OVERRIDE_PROPERTY = "lizzie.menu.presentation";
  public static final String ACTIVE_PROPERTY = "lizzie.menu.presentation.active";

  private final String id;

  MenuPresentationMode(String id) {
    this.id = id;
  }

  public String id() {
    return id;
  }

  public boolean usesNativeMenuBar() {
    return this == NATIVE_MENU_BAR;
  }

  public int contentOffset(int customStripHeight) {
    return usesNativeMenuBar() ? 0 : Math.max(0, customStripHeight);
  }

  public static MenuPresentationMode detectCurrent() {
    return detect(
        System.getProperty("os.name", ""),
        System.getenv(),
        System.getProperty(OVERRIDE_PROPERTY, "auto"));
  }

  static MenuPresentationMode detect(
      String osName, Map<String, String> environment, String override) {
    String requested = normalize(override);
    if ("native".equals(requested)) {
      return NATIVE_MENU_BAR;
    }
    if ("custom".equals(requested)) {
      return CUSTOM_STRIP;
    }
    return isLinuxWayland(osName, environment) ? NATIVE_MENU_BAR : CUSTOM_STRIP;
  }

  static boolean isLinuxWayland(String osName, Map<String, String> environment) {
    if (!normalize(osName).contains("linux")) {
      return false;
    }
    Map<String, String> values = environment == null ? Map.of() : environment;
    if ("wayland".equals(normalize(values.get("XDG_SESSION_TYPE")))) {
      return true;
    }
    return !normalize(values.get("WAYLAND_DISPLAY")).isEmpty();
  }

  static String desktopSession(Map<String, String> environment) {
    Map<String, String> values = environment == null ? Map.of() : environment;
    String session = normalize(values.get("XDG_SESSION_TYPE"));
    if (!session.isEmpty()) {
      return session;
    }
    return !normalize(values.get("WAYLAND_DISPLAY")).isEmpty() ? "wayland" : "unknown";
  }

  private static String normalize(String value) {
    return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
  }
}
