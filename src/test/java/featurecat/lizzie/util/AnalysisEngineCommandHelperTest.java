package featurecat.lizzie.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import featurecat.lizzie.gui.EngineData;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AnalysisEngineCommandHelperTest {
  @TempDir Path tempDir;

  @Test
  void convertsSavedKataGoEngineAndCreatesMissingAnalysisConfig() throws Exception {
    Path gtpConfig = tempDir.resolve("katago_configs").resolve("default_gtp.cfg");
    Path analysisConfig = gtpConfig.resolveSibling("analysis.cfg");
    Files.createDirectories(gtpConfig.getParent());
    Files.writeString(gtpConfig, "gtp config", StandardCharsets.UTF_8);
    EngineData engine =
        engine(
            "KataGo",
            quote(tempDir.resolve("katago.exe"))
                + " gtp -model "
                + quote(tempDir.resolve("weights").resolve("model.bin.gz"))
                + " -config "
                + quote(gtpConfig));

    AnalysisEngineCommandHelper.Result result = AnalysisEngineCommandHelper.fromSavedEngine(engine);

    assertTrue(result.isSuccess(), result.getMessage());
    List<String> parts = Utils.splitCommand(result.getCommand());
    assertEquals("analysis", parts.get(1));
    assertEquals(analysisConfig.toString(), parts.get(parts.indexOf("-config") + 1));
    assertTrue(parts.contains("-quit-without-waiting"));
    assertTrue(result.generatedConfig());
    assertEquals(analysisConfig, result.getAnalysisConfigPath());
    assertTrue(Files.exists(analysisConfig));
    assertTrue(
        Files.readString(analysisConfig, StandardCharsets.UTF_8)
            .contains("Config for KataGo C++ Analysis engine"));
    assertTrue(result.getMessage().contains("analysis.cfg"));
    assertTrue(result.getMessage().contains(analysisConfig.toString()));
  }

  @Test
  void missingEngineDirectoryDoesNotCreatePartialAnalysisConfigTree() {
    Path gtpConfig = tempDir.resolve("missing-engine").resolve("configs").resolve("gtp.cfg");
    Path analysisConfig = gtpConfig.resolveSibling("analysis.cfg");
    EngineData engine =
        engine(
            "Missing KataGo",
            quote(tempDir.resolve("missing-engine").resolve("katago.exe"))
                + " gtp -model model.bin.gz -config "
                + quote(gtpConfig));

    AnalysisEngineCommandHelper.Result result =
        AnalysisEngineCommandHelper.fromSavedEngine(engine);

    assertFalse(result.isSuccess());
    assertFalse(Files.exists(analysisConfig));
    assertFalse(Files.exists(gtpConfig.getParent()));
  }

  @Test
  void doesNotReplaceGtpInsidePathsAndDoesNotDuplicateQuitFlag() throws Exception {
    Path executable = tempDir.resolve("tools with gtp").resolve("katago.exe");
    Path gtpConfig = tempDir.resolve("katago_configs").resolve("gtp.cfg");
    Path analysisConfig = gtpConfig.resolveSibling("analysis.cfg");
    Files.createDirectories(executable.getParent());
    Files.createDirectories(gtpConfig.getParent());
    Files.writeString(analysisConfig, "existing analysis config", StandardCharsets.UTF_8);
    EngineData engine =
        engine(
            "KataGo",
            quote(executable)
                + " gtp -model model.bin.gz -config "
                + quote(gtpConfig)
                + " -quit-without-waiting");

    AnalysisEngineCommandHelper.Result result = AnalysisEngineCommandHelper.fromSavedEngine(engine);

    assertTrue(result.isSuccess(), result.getMessage());
    List<String> parts = Utils.splitCommand(result.getCommand());
    assertEquals(executable.toString(), parts.get(0));
    assertEquals("analysis", parts.get(1));
    assertEquals(analysisConfig.toString(), parts.get(parts.indexOf("-config") + 1));
    assertEquals(1, parts.stream().filter("-quit-without-waiting"::equals).count());
    assertFalse(result.generatedConfig());
    assertEquals(
        "existing analysis config", Files.readString(analysisConfig, StandardCharsets.UTF_8));
  }

  @Test
  void rejectsRemoteSavedEngines() {
    EngineData engine = engine("Remote", "katago gtp -model model.bin.gz -config gtp.cfg");
    engine.useJavaSSH = true;

    AnalysisEngineCommandHelper.Result result = AnalysisEngineCommandHelper.fromSavedEngine(engine);

    assertFalse(result.isSuccess());
    String message = result.getMessage().toLowerCase(java.util.Locale.ROOT);
    assertTrue(message.contains("remote") || message.contains("远程"));
  }

  @Test
  void rejectsCommandsWithoutStandaloneGtp() {
    AnalysisEngineCommandHelper.Result result =
        AnalysisEngineCommandHelper.fromSavedEngine(
            engine("No gtp", "katago analysis -model model.bin.gz -config analysis.cfg"));

    assertFalse(result.isSuccess());
    assertTrue(result.getMessage().contains("gtp"));
  }

  @Test
  void rejectsCommandsWithoutConfig() {
    AnalysisEngineCommandHelper.Result result =
        AnalysisEngineCommandHelper.fromSavedEngine(
            engine("No config", "katago gtp -model model.bin.gz"));

    assertFalse(result.isSuccess());
    assertTrue(result.getMessage().contains("config"));
  }

  @Test
  void convertsCurrentDefaultEngineWhenFlashCommandIsNotCustomized() throws Exception {
    Path firstConfig = tempDir.resolve("first").resolve("gtp.cfg");
    Path defaultConfig = tempDir.resolve("default").resolve("gtp.cfg");
    Files.createDirectories(firstConfig.getParent());
    Files.createDirectories(defaultConfig.getParent());
    ArrayList<EngineData> engines = new ArrayList<>();
    engines.add(engine("first", "katago gtp -model first.bin.gz -config " + quote(firstConfig)));
    engines.add(
        engine("default", "katago gtp -model default.bin.gz -config " + quote(defaultConfig)));
    engines.get(1).isDefault = true;

    AnalysisEngineCommandHelper.Result result =
        AnalysisEngineCommandHelper.fromDefaultEngine(engines);

    assertTrue(result.isSuccess(), result.getMessage());
    List<String> parts = Utils.splitCommand(result.getCommand());
    assertEquals("default.bin.gz", parts.get(parts.indexOf("-model") + 1));
    assertEquals(
        defaultConfig.resolveSibling("analysis.cfg").toString(),
        parts.get(parts.indexOf("-config") + 1));
  }

  @Test
  void convertsCurrentEngineBeforeDefaultEngineWhenFlashCommandIsNotCustomized()
      throws Exception {
    Path currentConfig = tempDir.resolve("current").resolve("gtp.cfg");
    Path defaultConfig = tempDir.resolve("default-current").resolve("gtp.cfg");
    Files.createDirectories(currentConfig.getParent());
    Files.createDirectories(defaultConfig.getParent());
    ArrayList<EngineData> engines = new ArrayList<>();
    engines.add(
        engine("current", "katago gtp -model current.bin.gz -config " + quote(currentConfig)));
    engines.add(
        engine("default", "katago gtp -model default.bin.gz -config " + quote(defaultConfig)));
    engines.get(1).isDefault = true;

    AnalysisEngineCommandHelper.Result result =
        AnalysisEngineCommandHelper.fromCurrentEngine(engines, 0);

    assertTrue(result.isSuccess(), result.getMessage());
    List<String> parts = Utils.splitCommand(result.getCommand());
    assertEquals("current.bin.gz", parts.get(parts.indexOf("-model") + 1));
    assertEquals(
        currentConfig.resolveSibling("analysis.cfg").toString(),
        parts.get(parts.indexOf("-config") + 1));
  }

  @Test
  void fallsBackToDefaultEngineWhenNoCurrentEngineIsLoaded() throws Exception {
    Path defaultConfig = tempDir.resolve("fallback-default").resolve("gtp.cfg");
    Files.createDirectories(defaultConfig.getParent());
    ArrayList<EngineData> engines = new ArrayList<>();
    engines.add(engine("first", "katago gtp -model first.bin.gz -config first.cfg"));
    engines.add(
        engine("default", "katago gtp -model default.bin.gz -config " + quote(defaultConfig)));
    engines.get(1).isDefault = true;

    AnalysisEngineCommandHelper.Result result =
        AnalysisEngineCommandHelper.fromCurrentEngine(engines, -1);

    assertTrue(result.isSuccess(), result.getMessage());
    List<String> parts = Utils.splitCommand(result.getCommand());
    assertEquals("default.bin.gz", parts.get(parts.indexOf("-model") + 1));
    assertEquals(
        defaultConfig.resolveSibling("analysis.cfg").toString(),
        parts.get(parts.indexOf("-config") + 1));
  }

  @Test
  void detectsLegacyCustomizedAnalysisCommandsConservatively() {
    assertFalse(AnalysisEngineCommandHelper.isLegacyAnalysisCommandCustomized(""));
    assertFalse(
        AnalysisEngineCommandHelper.isLegacyAnalysisCommandCustomized(
            "katago analysis -model model.bin.gz -config analysis.cfg -quit-without-waiting"));
    assertTrue(
        AnalysisEngineCommandHelper.isLegacyAnalysisCommandCustomized(
            "katago analysis -model custom.bin.gz -config analysis.cfg"));
    assertFalse(AnalysisEngineCommandHelper.isAnalysisCommandCustomized(true, false, "custom"));
    assertTrue(AnalysisEngineCommandHelper.isAnalysisCommandCustomized(true, true, ""));
  }

  @Test
  void humanSlRebasesAStaleBundledCommandToTheCurrentInstallation() throws Exception {
    Path currentRoot = tempDir.resolve("当前 LizzieYzy Next.app").resolve("Contents").resolve("app");
    Path engine = writeFile(currentRoot.resolve("engines/katago/macos-arm64/katago"));
    Path config = writeFile(currentRoot.resolve("engines/katago/configs/analysis.cfg"));
    Path weight = writeFile(currentRoot.resolve("weights/default.bin.gz"));
    Path staleRoot = tempDir.resolve("old build").resolve("LizzieYzy Next.app/Contents/app");
    String staleCommand =
        quote(staleRoot.resolve("engines/katago/macos-arm64/katago"))
            + " analysis -model "
            + quote(staleRoot.resolve("weights/default.bin.gz"))
            + " -config "
            + quote(staleRoot.resolve("engines/katago/configs/analysis.cfg"))
            + " -analysis-threads 3";

    AnalysisEngineCommandHelper.Result result =
        AnalysisEngineCommandHelper.resolveHumanSlCommand(staleCommand, engine, config, weight);

    assertTrue(result.isSuccess(), result.getMessage());
    List<String> parts = Utils.splitCommand(result.getCommand());
    assertEquals(engine.toString(), parts.get(0));
    assertEquals("analysis", parts.get(1));
    assertEquals(weight.toString(), parts.get(parts.indexOf("-model") + 1));
    assertEquals(config.toString(), parts.get(parts.indexOf("-config") + 1));
    assertTrue(parts.contains("-analysis-threads"));
    assertTrue(parts.contains("-quit-without-waiting"));
  }

  @Test
  void humanSlNeverMixesAStaleEngineAndConfigWithTheCurrentBundledWeight() throws Exception {
    Path currentRoot = tempDir.resolve("installed app");
    Path engine = writeFile(currentRoot.resolve("engines/katago/macos-arm64/katago"));
    Path config = writeFile(currentRoot.resolve("engines/katago/configs/analysis.cfg"));
    Path weight = writeFile(currentRoot.resolve("weights/default.bin.gz"));
    Path staleRoot = tempDir.resolve("deleted developer app image");
    String mixedCommand =
        quote(staleRoot.resolve("engines/katago/macos-arm64/katago"))
            + " analysis -model "
            + quote(weight)
            + " -config "
            + quote(staleRoot.resolve("engines/katago/configs/analysis.cfg"))
            + " -quit-without-waiting";

    AnalysisEngineCommandHelper.Result result =
        AnalysisEngineCommandHelper.resolveHumanSlCommand(mixedCommand, engine, config, weight);

    assertTrue(result.isSuccess(), result.getMessage());
    List<String> parts = Utils.splitCommand(result.getCommand());
    assertEquals(engine.toString(), parts.get(0));
    assertEquals(weight.toString(), parts.get(parts.indexOf("-model") + 1));
    assertEquals(config.toString(), parts.get(parts.indexOf("-config") + 1));
    assertFalse(result.getCommand().contains(staleRoot.toString()));
  }

  @Test
  void humanSlKeepsAWorkingExternalAnalysisCommandUntouched() throws Exception {
    Path engine = writeFile(tempDir.resolve("自定义引擎/katago"));
    Path config = writeFile(tempDir.resolve("自定义引擎/analysis.cfg"));
    Path weight = writeFile(tempDir.resolve("自定义权重/model.bin.gz"));
    String customCommand =
        quote(engine)
            + " analysis -model "
            + quote(weight)
            + " -config "
            + quote(config)
            + " -custom-option enabled";

    AnalysisEngineCommandHelper.Result result =
        AnalysisEngineCommandHelper.resolveHumanSlCommand(
            customCommand,
            tempDir.resolve("unused/engines/katago/katago"),
            tempDir.resolve("unused/analysis.cfg"),
            tempDir.resolve("unused/default.bin.gz"));

    assertTrue(result.isSuccess(), result.getMessage());
    assertEquals(customCommand, result.getCommand());
  }

  @Test
  void humanSlDoesNotReplaceAUserExternalCommandThatCannotBeFound() {
    String customCommand =
        quote(tempDir.resolve("missing custom/katago"))
            + " analysis -model missing.bin.gz -config missing.cfg";

    AnalysisEngineCommandHelper.Result result =
        AnalysisEngineCommandHelper.resolveHumanSlCommand(
            customCommand,
            tempDir.resolve("current/engines/katago/katago"),
            tempDir.resolve("current/analysis.cfg"),
            tempDir.resolve("current/default.bin.gz"));

    assertTrue(result.isSuccess(), result.getMessage());
    assertEquals(customCommand, result.getCommand());
  }

  @Test
  void humanSlReportsNoEngineWhenBundledRecoveryIsIncomplete() {
    String staleCommand =
        quote(tempDir.resolve("old/engines/katago/macos-arm64/katago"))
            + " analysis -model "
            + quote(tempDir.resolve("old/weights/default.bin.gz"))
            + " -config "
            + quote(tempDir.resolve("old/engines/katago/configs/analysis.cfg"));

    AnalysisEngineCommandHelper.Result result =
        AnalysisEngineCommandHelper.resolveHumanSlCommand(
            staleCommand,
            tempDir.resolve("current/engines/katago/katago"),
            tempDir.resolve("current/analysis.cfg"),
            tempDir.resolve("current/default.bin.gz"));

    assertFalse(result.isSuccess());
    assertTrue(result.getCommand().isEmpty());
  }

  @Test
  void bundledAnalysisConfigTemplateIsAvailable() throws Exception {
    assertNotNull(
        AnalysisEngineCommandHelperTest.class
            .getClassLoader()
            .getResource("katago/analysis_example.cfg"));
  }

  private static EngineData engine(String name, String command) {
    EngineData engine = new EngineData();
    engine.name = name;
    engine.commands = command;
    return engine;
  }

  private static String quote(Path path) {
    return "\"" + path.toString() + "\"";
  }

  private static Path writeFile(Path path) throws Exception {
    Files.createDirectories(path.getParent());
    return Files.write(path, new byte[] {1});
  }
}
