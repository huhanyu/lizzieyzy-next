package featurecat.lizzie.analysis;

/**
 * Test-only access to registry state that must be observed without triggering dead-process pruning.
 */
public final class AnalysisResourceCoordinatorTestAccess {
  private AnalysisResourceCoordinatorTestAccess() {}

  public static int rawLocalComputeProcessCount() {
    return AnalysisResourceCoordinator.rawLocalComputeProcessCountForTesting();
  }
}
