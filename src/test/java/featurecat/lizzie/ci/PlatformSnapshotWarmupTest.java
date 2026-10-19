package featurecat.lizzie.ci;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.jna.Platform;
import org.junit.jupiter.api.Test;

class PlatformSnapshotWarmupTest {
  @Test
  void jnaPlatformKeepsTheRealOperatingSystemWhileATestFakesOsName() {
    assertTrue(PlatformSnapshotWarmup.PLATFORM_SNAPSHOT_CLASSES.contains("com.sun.jna.Platform"));
    String real = System.getProperty("os.name");
    PlatformSnapshotWarmup.warmUp();
    System.setProperty("os.name", real.startsWith("Windows") ? "Mac OS X" : "Windows 11");
    try {
      assertEquals(real.startsWith("Windows"), Platform.isWindows());
      assertEquals(real.startsWith("Mac"), Platform.isMac());
    } finally {
      System.setProperty("os.name", real);
    }
  }
}
