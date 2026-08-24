package featurecat.lizzie.util.katago.tuning;

import java.util.List;
import java.util.Optional;

/** Structured, process-independent data parsed from one KataGo benchmark invocation. */
public record KataGoBenchmarkObservation(
    String backend,
    int currentThreads,
    int recommendedThreads,
    int recommendedNnServerThreadsPerModel,
    int recommendedMaxBatchSize,
    List<ThreadMetrics> metrics,
    boolean mpsGraphInitialized,
    boolean coreMlInitialized,
    boolean failureDetected) {

  public KataGoBenchmarkObservation {
    backend = backend == null ? "" : backend.trim();
    if (currentThreads < 0 || recommendedThreads < 0) {
      throw new IllegalArgumentException("thread counts must not be negative");
    }
    if (recommendedNnServerThreadsPerModel < 0 || recommendedNnServerThreadsPerModel > 256) {
      throw new IllegalArgumentException("NN server thread count must be between 0 and 256");
    }
    if (recommendedMaxBatchSize < 0 || recommendedMaxBatchSize > 65536) {
      throw new IllegalArgumentException("max batch size must be between 0 and 65536");
    }
    metrics = metrics == null ? List.of() : List.copyOf(metrics);
  }

  /** Backwards-compatible constructor for benchmark output before KataGo 1.18 extra tuning. */
  public KataGoBenchmarkObservation(
      String backend,
      int currentThreads,
      int recommendedThreads,
      List<ThreadMetrics> metrics,
      boolean mpsGraphInitialized,
      boolean coreMlInitialized,
      boolean failureDetected) {
    this(
        backend,
        currentThreads,
        recommendedThreads,
        0,
        0,
        metrics,
        mpsGraphInitialized,
        coreMlInitialized,
        failureDetected);
  }

  public boolean hasMetrics() {
    return !metrics.isEmpty();
  }

  public boolean mixedMetalInitialized() {
    return mpsGraphInitialized && coreMlInitialized;
  }

  public boolean hasAdditionalGpuRecommendation() {
    return recommendedNnServerThreadsPerModel > 0 || recommendedMaxBatchSize > 0;
  }

  public Optional<ThreadMetrics> metricForThreads(int numSearchThreads) {
    return metrics.stream()
        .filter(metric -> metric.numSearchThreads() == numSearchThreads)
        .findFirst();
  }

  public Optional<ThreadMetrics> recommendedMetric() {
    return recommendedThreads <= 0 ? Optional.empty() : metricForThreads(recommendedThreads);
  }

  /** Per-thread-count metrics from a completed detailed benchmark row. */
  public record ThreadMetrics(
      int numSearchThreads,
      int positionsCompleted,
      int positionsTotal,
      double visitsPerSecond,
      double nnEvalsPerSecond,
      double nnBatchesPerSecond,
      double averageBatchSize) {

    public ThreadMetrics {
      if (numSearchThreads <= 0) {
        throw new IllegalArgumentException("numSearchThreads must be positive");
      }
      if (positionsCompleted < 0 || positionsTotal < 0 || positionsCompleted > positionsTotal) {
        throw new IllegalArgumentException("invalid position counts");
      }
    }

    public boolean validForThroughputSelection() {
      return positionsTotal > 0
          && positionsCompleted == positionsTotal
          && Double.isFinite(visitsPerSecond)
          && visitsPerSecond > 0.0;
    }
  }
}
