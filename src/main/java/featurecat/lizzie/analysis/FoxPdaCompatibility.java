package featurecat.lizzie.analysis;

import featurecat.lizzie.util.CommandLaunchHelper;
import featurecat.lizzie.util.katago.tuning.KataGoCommandSpec;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

/** Captured at physical local launch, not reconstructed from subsequently edited config files. */
final class FoxPdaCompatibility {
  private static final String DYNAMIC = "dynamicPlayoutDoublingAdvantageCapPerOppLead";

  private FoxPdaCompatibility() {}

  static String inspect(List<String> command, Path directory) {
    try {
      if (command.size() < 2
          || !command.get(1).equals("gtp")
          || CommandLaunchHelper.isIndirectLauncher(command.get(0))) return "Fox.pdaUnknown";
      boolean configSeen = false;
      boolean dynamic = KataGoCommandSpec.parse(command).effectiveOverrides().containsKey(DYNAMIC);
      Set<String> valued =
          Set.of(
              "-model",
              "--model",
              "-weights",
              "--weights",
              "-config",
              "--config",
              "-override-config",
              "--override-config");
      for (int i = 2; i < command.size(); i++) {
        String option = command.get(i);
        if (option.equals("-quit-without-waiting")) continue;
        if (!valued.contains(option) || ++i >= command.size()) return "Fox.pdaUnknown";
        if (!option.equals("-config") && !option.equals("--config")) continue;
        configSeen = true;
        Path file = directory.resolve(command.get(i));
        if (Files.size(file) > 4 * 1024 * 1024) return "Fox.pdaUnknown";
        for (String raw : Files.readAllLines(file)) {
          String line = raw.split("#", 2)[0].trim();
          if (line.isEmpty()) continue;
          // Includes or unrecognized syntax cannot prove absence of a dynamic override.
          int equals = line.indexOf('=');
          if (line.startsWith("@") || equals <= 0) return "Fox.pdaUnknown";
          if (line.substring(0, equals).trim().equals(DYNAMIC)) dynamic = true;
        }
      }
      // Even an explicit zero leaves KataGo's static-precedence flag false.
      return dynamic ? "Fox.pdaDynamic" : configSeen ? "" : "Fox.pdaUnknown";
    } catch (Exception unavailable) {
      return "Fox.pdaUnknown";
    }
  }
}
