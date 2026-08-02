package featurecat.lizzie.analysis;

import featurecat.lizzie.Lizzie;
import featurecat.lizzie.util.GraphicsDriverDiagnostics;
import java.awt.GraphicsEnvironment;

public final class SyncDiagnosticsEnvironment {
  private final String appVersion;
  private final String javaVersion;
  private final String osName;
  private final String osVersion;
  private final String osArch;
  private final String desktopSession;
  private final boolean waylandDisplayPresent;
  private final String desktopName;
  private final String menuPresentation;
  private final String graphicsDevice;
  private final String graphicsDriver;
  private final String userDirSanitized;
  private final long timestampMillis;

  private SyncDiagnosticsEnvironment(
      String appVersion,
      String javaVersion,
      String osName,
      String osVersion,
      String osArch,
      String desktopSession,
      boolean waylandDisplayPresent,
      String desktopName,
      String menuPresentation,
      String graphicsDevice,
      String graphicsDriver,
      String userDirSanitized,
      long timestampMillis) {
    this.appVersion = SyncDecisionTrace.normalize(appVersion, "unknown");
    this.javaVersion = SyncDecisionTrace.normalize(javaVersion, "unknown");
    this.osName = SyncDecisionTrace.normalize(osName, "unknown");
    this.osVersion = SyncDecisionTrace.normalize(osVersion, "unknown");
    this.osArch = SyncDecisionTrace.normalize(osArch, "unknown");
    this.desktopSession = SyncDecisionTrace.normalize(desktopSession, "unknown");
    this.waylandDisplayPresent = waylandDisplayPresent;
    this.desktopName = SyncDecisionTrace.normalize(desktopName, "unknown");
    this.menuPresentation = SyncDecisionTrace.normalize(menuPresentation, "unknown");
    this.graphicsDevice = SyncDecisionTrace.normalize(graphicsDevice, "unknown");
    this.graphicsDriver = SyncDecisionTrace.normalize(graphicsDriver, "unknown");
    this.userDirSanitized = SyncDecisionTrace.normalize(userDirSanitized, "unknown");
    this.timestampMillis = timestampMillis;
  }

  public static SyncDiagnosticsEnvironment capture() {
    return new SyncDiagnosticsEnvironment(
        Lizzie.lizzieVersion,
        System.getProperty("java.version", "unknown"),
        System.getProperty("os.name", "unknown"),
        System.getProperty("os.version", "unknown"),
        System.getProperty("os.arch", "unknown"),
        safeEnvironmentValue("XDG_SESSION_TYPE"),
        hasEnvironmentValue("WAYLAND_DISPLAY"),
        safeEnvironmentValue("XDG_CURRENT_DESKTOP"),
        System.getProperty("lizzie.menu.presentation.active", "unknown"),
        captureGraphicsDevice(),
        GraphicsDriverDiagnostics.summary(),
        sanitizePath(System.getProperty("user.dir", "")),
        System.currentTimeMillis());
  }

  static SyncDiagnosticsEnvironment of(
      String appVersion,
      String javaVersion,
      String osName,
      String osVersion,
      String osArch,
      String userDirSanitized,
      long timestampMillis) {
    return new SyncDiagnosticsEnvironment(
        appVersion,
        javaVersion,
        osName,
        osVersion,
        osArch,
        "unknown",
        false,
        "unknown",
        "unknown",
        "unknown",
        "not-probed",
        userDirSanitized,
        timestampMillis);
  }

  static SyncDiagnosticsEnvironment of(
      String appVersion,
      String javaVersion,
      String osName,
      String osVersion,
      String osArch,
      String desktopSession,
      boolean waylandDisplayPresent,
      String desktopName,
      String menuPresentation,
      String graphicsDevice,
      String graphicsDriver,
      String userDirSanitized,
      long timestampMillis) {
    return new SyncDiagnosticsEnvironment(
        appVersion,
        javaVersion,
        osName,
        osVersion,
        osArch,
        desktopSession,
        waylandDisplayPresent,
        desktopName,
        menuPresentation,
        graphicsDevice,
        graphicsDriver,
        userDirSanitized,
        timestampMillis);
  }

  public static String sanitizePath(String path) {
    if (path == null || path.trim().isEmpty()) {
      return "unknown";
    }
    String value = path.trim();
    String normalized = value.replace('\\', '/');

    if (normalized.matches("^[A-Za-z]:/Users/[^/]+(/.*)?$")) {
      return value.substring(0, 1).toUpperCase() + ":\\Users\\<user>";
    }
    if (normalized.matches("^/mnt/[A-Za-z]/Users/[^/]+(/.*)?$")) {
      String drive = normalized.substring("/mnt/".length(), "/mnt/".length() + 1);
      return "/mnt/" + drive + "/Users/<user>";
    }
    if (normalized.matches("^//wsl\\.localhost/[^/]+/home/[^/]+(/.*)?$")) {
      String[] parts = normalized.split("/");
      return "\\\\wsl.localhost\\" + parts[3] + "\\home\\<user>";
    }
    if (normalized.matches("^/home/[^/]+(/.*)?$")) {
      return "/home/<user>";
    }
    if (normalized.matches("^/Users/[^/]+(/.*)?$")) {
      return "/Users/<user>";
    }
    if (isAbsolutePath(value, normalized)) {
      return "<redacted-path>";
    }
    return basename(normalized);
  }

  public String getAppVersion() {
    return appVersion;
  }

  public String getJavaVersion() {
    return javaVersion;
  }

  public String getOsName() {
    return osName;
  }

  public String getOsVersion() {
    return osVersion;
  }

  public String getOsArch() {
    return osArch;
  }

  public String getDesktopSession() {
    return desktopSession;
  }

  public boolean isWaylandDisplayPresent() {
    return waylandDisplayPresent;
  }

  public String getDesktopName() {
    return desktopName;
  }

  public String getMenuPresentation() {
    return menuPresentation;
  }

  public String getGraphicsDevice() {
    return graphicsDevice;
  }

  public String getGraphicsDriver() {
    return graphicsDriver;
  }

  public String getUserDirSanitized() {
    return userDirSanitized;
  }

  public long getTimestampMillis() {
    return timestampMillis;
  }

  private static boolean hasEnvironmentValue(String name) {
    String value = System.getenv(name);
    return value != null && !value.isBlank();
  }

  private static String safeEnvironmentValue(String name) {
    String value = System.getenv(name);
    if (value == null || value.isBlank()) {
      return "unknown";
    }
    String compact = value.trim().replaceAll("[^A-Za-z0-9 ._:+/-]", "");
    if (compact.isEmpty()) {
      return "present";
    }
    return compact.length() <= 80 ? compact : compact.substring(0, 80);
  }

  private static String captureGraphicsDevice() {
    try {
      if (GraphicsEnvironment.isHeadless()) {
        return "headless";
      }
      return GraphicsEnvironment.getLocalGraphicsEnvironment()
          .getDefaultScreenDevice()
          .getIDstring();
    } catch (RuntimeException e) {
      return "unavailable";
    }
  }

  private static boolean isAbsolutePath(String original, String normalized) {
    return normalized.startsWith("/")
        || original.startsWith("\\\\")
        || normalized.matches("^[A-Za-z]:/.*$");
  }

  private static String basename(String normalized) {
    String value = normalized;
    while (value.endsWith("/") && value.length() > 1) {
      value = value.substring(0, value.length() - 1);
    }
    int lastSlash = value.lastIndexOf('/');
    return lastSlash >= 0 ? value.substring(lastSlash + 1) : value;
  }
}
