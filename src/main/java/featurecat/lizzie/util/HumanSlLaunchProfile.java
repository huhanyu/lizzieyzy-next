package featurecat.lizzie.util;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/** How the AI Coach's HumanSL analysis engine is launched, whatever config it inherits. */
final class HumanSlLaunchProfile {
  private HumanSlLaunchProfile() {}

  /** As {@link #apply(List, boolean, boolean)}, for the bundled engine at {@code engine}. */
  static void apply(List<String> command, Path engine, boolean nineteenByNineteen) {
    apply(command, isMetalBuild(engine), nineteenByNineteen);
  }

  /** The bundled Apple Silicon engine is the Metal build. */
  static boolean isMetalBuild(Path engine) {
    return engine != null && engine.toString().toLowerCase(Locale.ROOT).contains("macos-arm64");
  }

  /**
   * One analysis thread with eight search threads, a small NN cache, and a board-sized buffer on
   * 19×19.
   *
   * <p>The Metal backend of the Apple Silicon build compiles a GPU program for each new batch size,
   * about three seconds each, so with dynamic batches of up to eight the first moves of a game
   * could stall eight times. With {@code metalPadBatch} every batch is padded to four and one
   * program is compiled, by the readiness query while the setup dialog says 准备中. Padding costs
   * about 12% of the search speed. An engine without the patch warns about the key, ignores it, and
   * still meets at most four batch sizes.
   *
   * @param metal whether the engine is the bundled Apple Silicon (Metal) build
   */
  static void apply(List<String> command, boolean metal, boolean nineteenByNineteen) {
    KataGoRuntimeHelper.setOverrideConfig(command, "numAnalysisThreads=1");
    // Remove the GTP alias from inherited configs without changing the user's file.
    KataGoRuntimeHelper.setOverrideConfig(command, "numSearchThreads=");
    KataGoRuntimeHelper.setOverrideConfig(command, "numSearchThreadsPerAnalysisThread=8");
    KataGoRuntimeHelper.setOverrideConfig(command, metal ? "nnMaxBatchSize=4" : "nnMaxBatchSize=8");
    if (metal) {
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
