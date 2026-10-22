package featurecat.lizzie.util;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class HumanSlLaunchProfileTest {
  private static String overrides(boolean metal, boolean nineteen) {
    List<String> command =
        new ArrayList<>(
            List.of(
                "katago",
                "analysis",
                "-config",
                "analysis.cfg",
                "-override-config",
                "nnMaxBatchSize=64,numAnalysisThreads=2"));
    HumanSlLaunchProfile.apply(command, metal, nineteen);
    return command.get(command.indexOf("-override-config") + 1);
  }

  @Test
  void theMetalBuildPadsEveryBatchToFourSoItCompilesOnce() {
    String overrides = overrides(true, true);

    assertTrue(overrides.contains("nnMaxBatchSize=4"), overrides);
    assertTrue(overrides.contains("metalPadBatch=true"), overrides);
    assertFalse(overrides.contains("nnMaxBatchSize=64"), overrides);
    assertTrue(overrides.contains("numSearchThreadsPerAnalysisThread=8"), overrides);
  }

  @Test
  void otherBackendsKeepDynamicBatchesOfEight() {
    String overrides = overrides(false, true);

    assertTrue(overrides.contains("nnMaxBatchSize=8"), overrides);
    assertFalse(overrides.contains("metalPadBatch"), overrides);
    assertTrue(overrides.contains("numAnalysisThreads=1"), overrides);
    assertTrue(overrides.contains("requireMaxBoardSize=true"), overrides);
  }

  @Test
  void smallerBoardsGetNoNineteenBuffer() {
    assertFalse(overrides(false, false).contains("maxBoardXSizeForNNBuffer"));
  }

  @Test
  void onlyTheBundledAppleSiliconEngineCountsAsTheMetalBuild() {
    assertTrue(
        HumanSlLaunchProfile.isMetalBuild(
            java.nio.file.Path.of("app", "engines", "katago", "macos-arm64", "katago")));
    assertFalse(
        HumanSlLaunchProfile.isMetalBuild(
            java.nio.file.Path.of("app", "engines", "katago", "linux-x64-eigen", "katago")));
    assertFalse(HumanSlLaunchProfile.isMetalBuild(null));
  }
}
