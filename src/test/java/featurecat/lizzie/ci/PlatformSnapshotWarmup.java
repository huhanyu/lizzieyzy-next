package featurecat.lizzie.ci;

import java.util.List;
import org.junit.platform.launcher.LauncherSession;
import org.junit.platform.launcher.LauncherSessionListener;

/**
 * Initializes classes that read {@code os.name} once, before any test fakes the platform.
 *
 * <p>Several tests temporarily set {@code os.name} to simulate Windows or macOS. Some JDK classes
 * choose their platform implementation the first time they are initialized: if that happens while a
 * test is faking the OS, the whole test JVM is left unable to start processes ("Windows 11 is not a
 * supported OS platform") or to open sockets ({@code jdk.net.WindowsSocketOptions} not found).
 * JNA's {@code Platform} does the same, and logging uses it (for example {@code
 * LogArchiveBoundary}) from background threads at any moment; once it believes it runs on Windows,
 * later tests on Linux take Windows-only paths. Initializing these classes when the launcher
 * session opens pins them to the real platform.
 */
public final class PlatformSnapshotWarmup implements LauncherSessionListener {
  static final List<String> PLATFORM_SNAPSHOT_CLASSES =
      List.of("java.lang.ProcessImpl", "jdk.net.ExtendedSocketOptions", "com.sun.jna.Platform");

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
