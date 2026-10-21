package featurecat.lizzie.util;

import static featurecat.lizzie.util.AnalysisEngineLaunchCommand.padsEveryBatch;
import static featurecat.lizzie.util.AnalysisEngineLaunchCommand.sizePaddedBatches;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AnalysisEngineLaunchCommandTest {
  // KataGo's analysis_example.cfg, as bundled.
  private static final String SHIPPED =
      "numAnalysisThreads = 2  # positions in parallel\n"
          + "numSearchThreadsPerAnalysisThread = 16\n"
          + "nnMaxBatchSize = 64\n";

  @TempDir Path root;
  private int configs;

  private String command(String config, String overrides) throws Exception {
    Path path = Files.writeString(root.resolve("analysis config " + ++configs + ".cfg"), config);
    return "katago.exe analysis -model model.bin.gz -config \""
        + path
        + "\" -override-config "
        + overrides
        + " -quit-without-waiting";
  }

  @Test
  void directMlQuickAnalysisRunsBatchesItsTwoThreadsCanFill() throws Exception {
    String quick =
        command(
            SHIPPED,
            "onnxProvider=directml,analysisPVLen=15,numSearchThreads=,"
                + "numSearchThreadsPerAnalysisThread=1");

    String sized = sizePaddedBatches(quick);

    assertEquals(
        quick.replace(
            "numSearchThreadsPerAnalysisThread=1",
            "numSearchThreadsPerAnalysisThread=1,nnMaxBatchSize=2"),
        sized);
  }

  @Test
  void theBatchFollowsTheThreadsTheCommandRuns() throws Exception {
    // The whole-game profile: 6 x 2 concurrent evaluations, padded in batches of 6.
    assertTrue(
        sizePaddedBatches(
                command(
                    SHIPPED,
                    "onnxProvider=directml,numAnalysisThreads=6,numSearchThreads=,"
                        + "numSearchThreadsPerAnalysisThread=2"))
            .contains("nnMaxBatchSize=6"));
    // The config alone: 2 x 16.
    assertTrue(
        sizePaddedBatches(command(SHIPPED, "onnxProvider=directml")).contains("nnMaxBatchSize=16"));
    // A GTP-style alias in the config counts as the per-analysis-thread search threads.
    assertTrue(
        sizePaddedBatches(
                command(
                    "numAnalysisThreads = 4\nnumSearchThreads = 3\nnnMaxBatchSize = 64\n",
                    "onnxProvider=directml"))
            .contains("nnMaxBatchSize=6"));
  }

  @Test
  void openVinoNpuIsSizedAndOpenVinoGpuIsNot() throws Exception {
    assertTrue(
        sizePaddedBatches(command(SHIPPED, "onnxProvider=openvino,onnxOpenVINODeviceType=NPU"))
            .contains("nnMaxBatchSize=16"));
    String gpu = command(SHIPPED, "onnxProvider=openvino,onnxOpenVINODeviceType=GPU");
    assertEquals(gpu, sizePaddedBatches(gpu));
  }

  @Test
  void aSizeTheUserChoseIsKept() throws Exception {
    for (String kept :
        new String[] {
          command(SHIPPED, "onnxProvider=directml,nnMaxBatchSize=8"),
          command(
              "numAnalysisThreads = 2\nnumSearchThreads = 16\nnnMaxBatchSize = 4\n",
              "onnxProvider=directml"),
        }) {
      assertEquals(kept, sizePaddedBatches(kept));
    }
  }

  @Test
  void dynamicBackendsAndUnreadableCommandsAreUnchanged() throws Exception {
    for (String unchanged :
        new String[] {
          command(SHIPPED, "analysisPVLen=15"),
          command(SHIPPED, "onnxProvider=cuda"),
          command("nnMaxBatchSize = 64\n", "onnxProvider=directml"),
          "katago.exe analysis -config missing.cfg -override-config onnxProvider=directml",
          "katago.exe analysis -override-config",
          "",
        }) {
      assertEquals(unchanged, sizePaddedBatches(unchanged));
    }
  }

  @Test
  void paddingFollowsKataGosBackendRules() {
    assertTrue(padsEveryBatch(Map.of("onnxProvider", "DirectML")));
    assertTrue(
        padsEveryBatch(
            Map.of("onnxProvider", "openvino", "onnxOpenVINODeviceTypeThread1", "BATCH:NPU")));
    assertFalse(padsEveryBatch(Map.of("onnxProvider", "openvino")));
    assertFalse(padsEveryBatch(Map.of("onnxProvider", "directml", "onnxPadBatch", "false")));
    assertTrue(padsEveryBatch(Map.of("onnxProvider", "cpu", "onnxPadBatch", "true")));
    assertTrue(padsEveryBatch(Map.of("metalPadBatch", "true")));
    // CUDA, TensorRT, OpenCL, Eigen and the bundled Metal engine batch dynamically.
    assertFalse(padsEveryBatch(Map.of()));
  }
}
