package featurecat.lizzie.ci;

import java.util.List;
import org.junit.platform.launcher.LauncherSession;
import org.junit.platform.launcher.LauncherSessionListener;

/**
 * Initializes JDK classes that read {@code os.name} once, before any test fakes the platform.
 *
 * <p>Several tests temporarily set {@code os.name} to simulate Windows or macOS. Some JDK classes
 * choose their platform implementation the first time they are initialized: if that happens while a
 * test is faking the OS, the whole test JVM is left unable to start processes ("Windows 11 is not a
 * supported OS platform") or to open sockets ({@code jdk.net.WindowsSocketOptions} not found). Test
 * JVMs are forked per test class, so the first use can fall inside such a test; initializing these
 * classes when the launcher session opens pins them to the real platform.
 */
public final class PlatformSnapshotWarmup implements LauncherSessionListener {
  static final List<String> PLATFORM_SNAPSHOT_CLASSES =
      List.of("java.lang.ProcessImpl", "jdk.net.ExtendedSocketOptions");

  @Override
  public void launcherSessionOpened(LauncherSession session) {
    warmUp();
  }

  static void warmUp() {
    for (String name : PLATFORM_SNAPSHOT_CLASSES) {
      try {
        Class.forName(name, true, ClassLoader.getSystemClassLoader());
      } catch (ClassNotFoundException | LinkageError unavailable) {
        // Not present on this JDK or platform; nothing to pin.
      }
    }
  }
}
