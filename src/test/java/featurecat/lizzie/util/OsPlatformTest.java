package featurecat.lizzie.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Locale;
import org.junit.jupiter.api.Test;

class OsPlatformTest {
  @Test
  void matchesTheRunningOperatingSystem() {
    String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
    assertEquals(osName.contains("windows"), OsPlatform.isWindows());
    assertEquals(osName.contains("mac"), OsPlatform.isMac());
    assertEquals(osName.contains("linux"), OsPlatform.isLinux());
  }
}
