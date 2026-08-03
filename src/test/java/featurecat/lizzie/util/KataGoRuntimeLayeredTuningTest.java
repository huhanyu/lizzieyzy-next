package featurecat.lizzie.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import featurecat.lizzie.Config;
import featurecat.lizzie.ConfigTestHelper;
import featurecat.lizzie.Lizzie;
import featurecat.lizzie.util.KataGoAutoSetupHelper.SetupSnapshot;
import featurecat.lizzie.util.katago.tuning.KataGoCommandSpec;
import featurecat.lizzie.util.katago.tuning.KataGoTuningCandidate;
import featurecat.lizzie.util.katago.tuning.KataGoTuningProfile;
import featurecat.lizzie.util.katago.tuning.KataGoTuningStore;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class KataGoRuntimeLayeredTuningTest {
  @TempDir Path temporaryDirectory;

  @Test
  void layeredBenchmarkFixesTopologyAndBatchWhileLeavingThreadsToKataGo() throws IOException {
    SetupSnapshot snapshot = createSnapshot();
    KataGoTuningCandidate candidate = new KataGoTuningCandidate("GGA", List.of(0, 0, 100), 3);

    List<String> command =
        KataGoRuntimeHelper.buildLayeredBenchmarkCommand(snapshot, candidate, 0, 3, 600);
    KataGoCommandSpec spec = KataGoCommandSpec.parse(command);

    assertTrue(command.contains("-s"));
    assertFalse(command.contains("-t"));
    assertEquals("3", optionValue(command, "-fixed-batch-size"));
    assertEquals("19", optionValue(command, "-boardsize"));
    assertEquals("3", spec.overrideValue("numNNServerThreadsPerModel").orElseThrow());
    assertEquals("0", spec.overrideValue("metalDeviceToUseModel0Thread0").orElseThrow());
    assertEquals("0", spec.overrideValue("metalDeviceToUseModel0Thread1").orElseThrow());
    assertEquals("100", spec.overrideValue("metalDeviceToUseModel0Thread2").orElseThrow());
    assertEquals("true", spec.overrideValue("metalUseFP16-0").orElseThrow());
    assertTrue(spec.overrideValue("nnMaxBatchSize").isEmpty());
    assertTrue(spec.overrideValue("numSearchThreads").isEmpty());
  }

  @Test
  void smokeBenchmarkUsesAnExplicitCommonThreadCount() throws IOException {
    SetupSnapshot snapshot = createSnapshot();
    KataGoTuningCandidate candidate = new KataGoTuningCandidate("GA", List.of(0, 100), 2);

    List<String> command =
        KataGoRuntimeHelper.buildLayeredBenchmarkCommand(snapshot, candidate, 6, 1, 200);

    assertEquals("6", optionValue(command, "-t"));
    assertFalse(command.contains("-s"));
    assertEquals("2", optionValue(command, "-fixed-batch-size"));
  }

  @Test
  void explicitThreadsBlockOnlyTheStoredProfileThreadGroup() {
    KataGoTuningCandidate candidate = new KataGoTuningCandidate("GA", List.of(0, 100), 2);

    List<String> merged =
        KataGoRuntimeHelper.mergeStoredAppleTuningProfile(
            List.of("katago", "gtp", "--override-config", "userSetting=keep,numSearchThreads=11"),
            candidate,
            7);
    KataGoCommandSpec spec = KataGoCommandSpec.parse(merged);

    assertEquals("11", spec.overrideValue("numSearchThreads").orElseThrow());
    assertEquals("2", spec.overrideValue("numNNServerThreadsPerModel").orElseThrow());
    assertEquals("0", spec.overrideValue("metalDeviceToUseModel0Thread0").orElseThrow());
    assertEquals("100", spec.overrideValue("metalDeviceToUseModel0Thread1").orElseThrow());
    assertEquals("2", spec.overrideValue("nnMaxBatchSize").orElseThrow());
    assertEquals("keep", spec.overrideValue("userSetting").orElseThrow());
  }

  @Test
  void everyKataGoMetalAliasMakesTheStoredTopologyAtomic() {
    List<String> aliases =
        List.of(
            "numNNServerThreadsPerModel",
            "metalDeviceToUseThread0",
            "metalGpuToUseModel0Thread0",
            "deviceToUseThread0",
            "gpuToUse",
            "metalUseFP16",
            "useFP16Model0");
    KataGoTuningCandidate candidate = new KataGoTuningCandidate("GGA", List.of(0, 0, 100), 3);

    for (String alias : aliases) {
      KataGoCommandSpec spec =
          KataGoCommandSpec.parse(
              KataGoRuntimeHelper.mergeStoredAppleTuningProfile(
                  List.of("katago", "gtp", "-override-config", alias + "=explicit"), candidate, 7));
      Map<String, String> overrides = spec.effectiveOverrides();

      assertEquals("explicit", overrides.get(alias), alias);
      if (!"numNNServerThreadsPerModel".equals(alias)) {
        assertFalse(overrides.containsKey("numNNServerThreadsPerModel"), alias);
      }
      assertFalse(overrides.containsKey("metalDeviceToUseModel0Thread0"), alias);
      assertFalse(overrides.containsKey("metalDeviceToUseModel0Thread1"), alias);
      assertFalse(overrides.containsKey("metalDeviceToUseModel0Thread2"), alias);
      assertFalse(overrides.containsKey("metalUseFP16-0"), alias);
      assertEquals("3", overrides.get("nnMaxBatchSize"), alias);
      assertEquals("7", overrides.get("numSearchThreads"), alias);
    }
  }

  @Test
  void effectiveLaunchThreadDetectionHandlesLongOptionAndCase() {
    assertTrue(
        KataGoRuntimeHelper.hasEffectiveNumSearchThreadsOverride(
            List.of("katago", "gtp", "--override-config", "other=keep,NumSearchThreads=9")));
    assertFalse(
        KataGoRuntimeHelper.hasEffectiveNumSearchThreadsOverride(
            List.of("katago", "gtp", "-override-config", "nnMaxBatchSize=3")));
  }

  @Test
  void layeredResultDoesNotEnableGlobalThreadControl() throws Exception {
    Config previousConfig = Lizzie.config;
    try {
      Config config =
          ConfigTestHelper.createForTests(
              Files.createDirectories(temporaryDirectory.resolve("layered-config")));
      Lizzie.config = config;
      initializeConfigJson(config);
      config.chkKataEngineThreads = false;
      config.autoLoadKataEngineThreads = false;
      config.txtKataEngineThreads = "13";
      config.uiConfig.put("chk-kata-engine-threads", false);
      config.uiConfig.put("autoload-kata-engine-threads", false);
      config.uiConfig.put("txt-kata-engine-threads", "13");

      KataGoTuningProfile profile =
          new KataGoTuningProfile(
              "test-fingerprint",
              List.of(0, 100),
              2,
              7,
              new KataGoTuningProfile.Metrics(3, 3, 120.0, 100.0, 25.0, 4.0),
              "Metal",
              123L);
      KataGoRuntimeHelper.applyBenchmarkResult(layeredResult(profile));

      assertFalse(config.chkKataEngineThreads);
      assertFalse(config.autoLoadKataEngineThreads);
      assertEquals("13", config.txtKataEngineThreads);
      assertFalse(config.uiConfig.getBoolean("chk-kata-engine-threads"));
      assertFalse(config.uiConfig.getBoolean("autoload-kata-engine-threads"));
      assertEquals("13", config.uiConfig.getString("txt-kata-engine-threads"));
      assertEquals(7, config.uiConfig.getInt("katago-benchmark-threads"));
      assertTrue(new KataGoTuningStore(config.uiConfig).hasStoredProfile());
    } finally {
      Lizzie.config = previousConfig;
    }
  }

  @Test
  void legacyResultStillEnablesGlobalThreadControl() throws Exception {
    Config previousConfig = Lizzie.config;
    try {
      Config config =
          ConfigTestHelper.createForTests(
              Files.createDirectories(temporaryDirectory.resolve("legacy-config")));
      Lizzie.config = config;
      initializeConfigJson(config);
      config.chkKataEngineThreads = false;
      config.autoLoadKataEngineThreads = false;
      config.txtKataEngineThreads = "";

      KataGoRuntimeHelper.applyBenchmarkResult(legacyResult(5));

      assertTrue(config.chkKataEngineThreads);
      assertTrue(config.autoLoadKataEngineThreads);
      assertEquals("5", config.txtKataEngineThreads);
      assertTrue(config.uiConfig.getBoolean("chk-kata-engine-threads"));
      assertTrue(config.uiConfig.getBoolean("autoload-kata-engine-threads"));
      assertEquals("5", config.uiConfig.getString("txt-kata-engine-threads"));
    } finally {
      Lizzie.config = previousConfig;
    }
  }

  private SetupSnapshot createSnapshot() throws IOException {
    Path engine = Files.writeString(temporaryDirectory.resolve("katago"), "engine");
    Path gtp = Files.writeString(temporaryDirectory.resolve("gtp.cfg"), "numSearchThreads=6");
    Files.writeString(temporaryDirectory.resolve("analysis.cfg"), "numAnalysisThreads=2");
    Path model = Files.writeString(temporaryDirectory.resolve("model.bin.gz"), "model");
    return KataGoAutoSetupHelper.inspectSelectedLocalKataGo(engine, gtp, model).toSnapshot();
  }

  private static String optionValue(List<String> command, String option) {
    int index = command.indexOf(option);
    assertTrue(index >= 0 && index + 1 < command.size(), "Missing option " + option);
    return command.get(index + 1);
  }

  private static KataGoRuntimeHelper.BenchmarkResult layeredResult(KataGoTuningProfile profile)
      throws Exception {
    Constructor<KataGoRuntimeHelper.BenchmarkResult> constructor =
        KataGoRuntimeHelper.BenchmarkResult.class.getDeclaredConstructor(
            int.class,
            int.class,
            String.class,
            String.class,
            long.class,
            String.class,
            int.class,
            double.class,
            double.class,
            double.class,
            KataGoTuningProfile.class);
    constructor.setAccessible(true);
    return constructor.newInstance(
        profile.threads(), 1, "Metal", "layered", 123L, "GA", 2, 120.0, 100.0, 4.0, profile);
  }

  private static KataGoRuntimeHelper.BenchmarkResult legacyResult(int threads) throws Exception {
    Constructor<KataGoRuntimeHelper.BenchmarkResult> constructor =
        KataGoRuntimeHelper.BenchmarkResult.class.getDeclaredConstructor(
            int.class, int.class, String.class, String.class, long.class);
    constructor.setAccessible(true);
    return constructor.newInstance(threads, 1, "Metal", "legacy", 123L);
  }

  private static void initializeConfigJson(Config config) {
    config.uiConfig = new JSONObject();
    config.config = new JSONObject();
    config.leelazConfig = new JSONObject();
  }
}
