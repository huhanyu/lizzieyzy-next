package featurecat.lizzie.util;

import java.util.Locale;

/** Operating-system checks, evaluated once from {@code os.name}. */
public final class OsPlatform {
  private static final String OS_NAME = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);

  private OsPlatform() {}

  public static boolean isWindows() {
    return OS_NAME.contains("windows");
  }

  public static boolean isMac() {
    return OS_NAME.contains("mac");
  }

  public static boolean isLinux() {
    return OS_NAME.contains("linux");
  }
}
