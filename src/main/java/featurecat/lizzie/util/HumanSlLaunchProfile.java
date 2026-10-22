package featurecat.lizzie.util;

import java.util.List;

/** How the AI Coach's HumanSL analysis engine is launched, whatever config it inherits. */
final class HumanSlLaunchProfile {
  private HumanSlLaunchProfile() {}

  /**
   * One analysis thread with eight search threads, a small NN cache, and a board-sized buffer on
   * 19×19.
   *
   * <p>On Apple Silicon the Metal backend compiles a GPU program for each new batch size, about
   * three seconds each, so with dynamic batches of up to eight the first moves of a game could
   * stall eight times. With {@code metalPadBatch} every batch is padded to four and one program is
   * compiled, by the readiness query while the setup dialog says 准备中. Padding costs about 12% of
   * the search speed. An engine without the patch warns about the key, ignores it, and still meets
   * at most four batch sizes.
   */
  static void apply(List<String> command, boolean appleSilicon, boolean nineteenByNineteen) {
    KataGoRuntimeHelper.setOverrideConfig(command, "numAnalysisThreads=1");
    // Remove the GTP alias from inherited configs without changing the user's file.
    KataGoRuntimeHelper.setOverrideConfig(command, "numSearchThreads=");
    KataGoRuntimeHelper.setOverrideConfig(command, "numSearchThreadsPerAnalysisThread=8");
    KataGoRuntimeHelper.setOverrideConfig(
        command, appleSilicon ? "nnMaxBatchSize=4" : "nnMaxBatchSize=8");
    if (appleSilicon) {
      KataGoRuntimeHelper.setOverrideConfig(command, "metalPadBatch=true");
    }
    KataGoRuntimeHelper.setOverrideConfig(command, "nnCacheSizePowerOfTwo=20");
    if (nineteenByNineteen) {
      KataGoRuntimeHelper.setOverrideConfig(command, "maxBoardXSizeForNNBuffer=19");
      KataGoRuntimeHelper.setOverrideConfig(command, "maxBoardYSizeForNNBuffer=19");
      KataGoRuntimeHelper.setOverrideConfig(command, "requireMaxBoardSize=true");
    }
  }
}
