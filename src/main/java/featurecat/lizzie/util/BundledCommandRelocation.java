package featurecat.lizzie.util;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Points a bundled KataGo command at the app folder that is running now, for configurations saved
 * by a copy of the app that has since been moved, updated or deleted.
 */
public final class BundledCommandRelocation {
  private BundledCommandRelocation() {}

  /**
   * {@code command} with its executable, {@code -model} and {@code -config} paths moved from the
   * bundle they name to {@code currentRoot}, wherever the moved file exists. Other options and
   * paths outside that bundle are kept; commands that do not run a bundled KataGo are unchanged.
   */
  public static String rebind(String command, Path currentRoot) {
    List<String> parts = Utils.splitCommand(command);
    if (parts.size() < 2 || !("gtp".equals(parts.get(1)) || "analysis".equals(parts.get(1))))
      return command;
    Path executable = Path.of(parts.get(0));
    Path platform = executable.getParent();
    Path katago = platform == null ? null : platform.getParent();
    Path engines = katago == null ? null : katago.getParent();
    Path previousRoot = engines == null ? null : engines.getParent();
    if (previousRoot == null
        || previousRoot.equals(currentRoot)
        || !"katago".equals(katago.getFileName().toString())
        || !"engines".equals(engines.getFileName().toString())) return command;

    // Auto Setup quotes these paths. Replace only whole path tokens, retaining all other options.
    for (int i = 0; i < parts.size(); i++) {
      if (i != 0 && !"-model".equals(parts.get(i - 1)) && !"-config".equals(parts.get(i - 1)))
        continue;
      Path original = Path.of(parts.get(i));
      if (!original.isAbsolute() || !original.startsWith(previousRoot)) continue;
      Path relocated = currentRoot.resolve(previousRoot.relativize(original));
      if (Files.isRegularFile(relocated)) {
        command = command.replace('"' + parts.get(i) + '"', quote(relocated));
      }
    }
    return command;
  }

  /**
   * A customised analysis command whose bundled KataGo is gone, pointed at {@code currentRoot}; any
   * other command unchanged. Such a command cannot start, and automatic analysis would fail on
   * every game, so only its paths change and the user's options stay.
   */
  public static String repairGoneBundle(String command, Path currentRoot) {
    return AnalysisEngineCommandHelper.isMissingLocalExecutable(command)
        ? rebind(command, currentRoot)
        : command;
  }

  private static String quote(Path path) {
    return "\"" + path.toAbsolutePath().normalize() + "\"";
  }
}
