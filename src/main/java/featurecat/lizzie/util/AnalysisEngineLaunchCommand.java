package featurecat.lizzie.util;

import featurecat.lizzie.util.katago.tuning.KataGoCommandSpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The analysis engine's launch command: KataGoRuntimeHelper's thread and PV settings, then a batch
 * size that fits the threads on backends that pad every batch.
 *
 * <p>KataGo's ONNX backend pads each batch up to {@code nnMaxBatchSize} on DirectML and on OpenVINO
 * NPUs, which recompile for every new input shape. The bundled {@code analysis.cfg} is KataGo's
 * {@code analysis_example.cfg}, whose 64 is meant for dynamic batching, while whole-game quick
 * analysis runs two search threads: every batch of one or two positions would run 64 rows. KataGo's
 * analysis mode requires the size from the config, so it cannot be dropped; it is lowered to what
 * KataGo itself chooses for a padding backend, about half the concurrent evaluations.
 */
public final class AnalysisEngineLaunchCommand {
  private static final String BATCH_SIZE = "nnMaxBatchSize";

  private AnalysisEngineLaunchCommand() {}

  public static String build(
      String engineCommand, int maxVisits, boolean isBatchAnalysisMode, boolean wholeGame) {
    return sizePaddedBatches(
        KataGoRuntimeHelper.optimizeAnalysisEngineCommand(
            engineCommand, maxVisits, isBatchAnalysisMode, wholeGame));
  }

  /**
   * Lowers the configured batch size of an analysis command that pads every batch to fit its
   * threads. Leaves the command unchanged when it sets the size itself, does not pad, or its thread
   * counts cannot be read.
   */
  static String sizePaddedBatches(String engineCommand) {
    if (engineCommand == null || engineCommand.isBlank()) {
      return engineCommand;
    }
    List<String> tokens = Utils.splitCommand(engineCommand);
    KataGoCommandSpec spec;
    try {
      spec = KataGoCommandSpec.parse(tokens);
    } catch (RuntimeException unparsable) {
      return engineCommand;
    }
    Map<String, String> overrides = spec.effectiveOverrides();
    if (overrides.containsKey(BATCH_SIZE) || !padsEveryBatch(overrides)) {
      return engineCommand;
    }
    Map<String, String> config = readConfig(tokens);
    Integer analysisThreads = effectiveInt("numAnalysisThreads", overrides, config);
    Integer searchThreads = effectiveInt("numSearchThreadsPerAnalysisThread", overrides, config);
    if (searchThreads == null) {
      searchThreads = effectiveInt("numSearchThreads", overrides, config);
    }
    if (analysisThreads == null) {
      return engineCommand;
    }
    int concurrent = analysisThreads * (searchThreads == null ? 1 : searchThreads);
    // Setup::computeFixedShapeMaxBatchSize for one device.
    int fitted = Math.max(2, (concurrent + 1) / 2);
    Integer configured = effectiveInt(BATCH_SIZE, overrides, config);
    if (configured != null && configured <= fitted) {
      return engineCommand;
    }
    return AnalysisEngineCommandHelper.buildCommandLine(
        spec.withManagedOverrides(Map.of(BATCH_SIZE, Integer.toString(fitted))));
  }

  /**
   * Mirrors the pinned KataGo's {@code padsBatchForDevice} (ONNX) and {@code metalPadBatch} for the
   * backend a command selects with overrides, the way the app's setup selects DirectML and
   * OpenVINO.
   */
  static boolean padsEveryBatch(Map<String, String> overrides) {
    String onnxPadBatch = lowerCase(overrides.get("onnxPadBatch"));
    if ("true".equals(onnxPadBatch) || "true".equals(lowerCase(overrides.get("metalPadBatch")))) {
      return true;
    }
    if ("false".equals(onnxPadBatch)) {
      return false;
    }
    String provider = lowerCase(overrides.get("onnxProvider"));
    if ("directml".equals(provider)) {
      return true;
    }
    if (!"openvino".equals(provider)) {
      return false;
    }
    // Like KataGo, match NPU anywhere in the default or any per-thread device string.
    for (Map.Entry<String, String> entry : overrides.entrySet()) {
      if (entry.getKey().startsWith("onnxOpenVINODeviceType")
          && entry.getValue().toUpperCase(Locale.ROOT).contains("NPU")) {
        return true;
      }
    }
    return false;
  }

  /** An override wins over the config; an empty override deletes the key, as in KataGo. */
  private static Integer effectiveInt(
      String key, Map<String, String> overrides, Map<String, String> config) {
    String value = overrides.containsKey(key) ? overrides.get(key) : config.get(key);
    if (value == null || value.isBlank()) {
      return null;
    }
    try {
      int parsed = Integer.parseInt(value.trim());
      return parsed > 0 ? parsed : null;
    } catch (NumberFormatException notAnInteger) {
      return null;
    }
  }

  /** The {@code key = value} lines of the command's {@code -config} file, without includes. */
  private static Map<String, String> readConfig(List<String> command) {
    Map<String, String> values = new HashMap<>();
    int index = command.indexOf("-config");
    if (index < 0 || index + 1 >= command.size()) {
      return values;
    }
    List<String> lines;
    try {
      lines = Files.readAllLines(Path.of(command.get(index + 1)), StandardCharsets.UTF_8);
    } catch (IOException | RuntimeException unreadable) {
      return values;
    }
    for (String line : lines) {
      int comment = line.indexOf('#');
      String content = comment < 0 ? line : line.substring(0, comment);
      int separator = content.indexOf('=');
      if (separator > 0) {
        values.put(content.substring(0, separator).trim(), content.substring(separator + 1).trim());
      }
    }
    return values;
  }

  private static String lowerCase(String value) {
    return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
  }
}
