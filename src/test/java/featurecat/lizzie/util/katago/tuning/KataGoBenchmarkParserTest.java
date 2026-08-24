package featurecat.lizzie.util.katago.tuning;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class KataGoBenchmarkParserTest {
  @Test
  void parsesCrLfMetricsMuxMarkersAndBaselineRecommendation() {
    String output =
        "You are currently using the Metal version of KataGo.\r\n"
            + "Your GTP config is currently set to use numSearchThreads = 6\n"
            + "Metal backend 0: GPU mode - using MPSGraph (GPU)\r"
            + "Metal backend 2: Mux ANE mode - using CoreML (CPU+ANE)\n"
            + "numSearchThreads =  1: 0 / 6 positions, visits/s = nan (0.0 secs)\r"
            + "numSearchThreads =  1: 6 / 6 positions, visits/s = 74.25 nnEvals/s = 63.50 "
            + "nnBatches/s = 40.00 avgBatchSize = 1.59 (64.6 secs) (EloDiff baseline)\n"
            + "numSearchThreads =  1: (baseline) (recommended)\n";

    KataGoBenchmarkObservation observation = KataGoBenchmarkParser.parse(output, 0);

    assertEquals("Metal", observation.backend());
    assertEquals(6, observation.currentThreads());
    assertEquals(1, observation.recommendedThreads());
    assertTrue(observation.mixedMetalInitialized());
    assertFalse(observation.failureDetected());
    assertEquals(1, observation.metrics().size());
    KataGoBenchmarkObservation.ThreadMetrics metrics = observation.metrics().get(0);
    assertEquals(74.25, metrics.visitsPerSecond());
    assertEquals(63.50, metrics.nnEvalsPerSecond());
    assertEquals(40.00, metrics.nnBatchesPerSecond());
    assertEquals(1.59, metrics.averageBatchSize());
  }

  @Test
  void explicitSingleThreadBenchmarkDoesNotNeedRecommendedMarker() {
    String output =
        "numSearchThreads =  7: 3 / 3 positions, visits/s = 120.0 nnEvals/s = 100.0 "
            + "nnBatches/s = 25.0 avgBatchSize = 4.0 (10.0 secs) (EloDiff baseline)";

    KataGoBenchmarkObservation observation = KataGoBenchmarkParser.parse(output, 7);

    assertEquals(7, observation.recommendedThreads());
    assertTrue(observation.recommendedMetric().isPresent());
  }

  @Test
  void detectsHardProcessFailureWithoutInventingMetrics() {
    KataGoBenchmarkObservation observation =
        KataGoBenchmarkParser.parse("Segmentation fault: 11\n", 4);

    assertTrue(observation.failureDetected());
    assertFalse(observation.hasMetrics());
    assertEquals(4, observation.recommendedThreads());
  }

  @Test
  void parsesKataGo118FinalGpuRecommendationsWithoutReplacingSearchMetrics() {
    String output =
        "You are currently using the CUDA version of KataGo.\n"
            + "numSearchThreads = 10: 6 / 6 positions, visits/s = 386.0 nnEvals/s = 320.0 "
            + "nnBatches/s = 42.0 avgBatchSize = 7.6 (10.0 secs)\n"
            + "numSearchThreads = 10: +72 Elo (recommended)\n"
            + "Running additional tests of a few other settings at numSearchThreads = 10.\n"
            + "Re-measuring the current recommendation as a baseline:\n"
            + "numSearchThreads = 10: 6 / 6 positions, visits/s = 380.0 nnEvals/s = 315.0 "
            + "nnBatches/s = 41.0 avgBatchSize = 7.7 (10.0 secs)\n"
            + "Testing 2 NN server threads per GPU. This also uses more GPU memory:\n"
            + "numSearchThreads = 10: 6 / 6 positions, visits/s = 401.0 nnEvals/s = 330.0 "
            + "nnBatches/s = 45.0 avgBatchSize = 7.3 (10.0 secs)\n"
            + "2 NN server threads per GPU was 5.5% faster, will recommend it.\n"
            + "Testing a max batch size of 5, half the search threads:\n"
            + "numSearchThreads = 10: 6 / 6 positions, visits/s = 420.0 nnEvals/s = 350.0 "
            + "nnBatches/s = 49.0 avgBatchSize = 5.0 (10.0 secs)\n"
            + "Half batch size was 4.7% faster, will recommend it.\n"
            + "ADDITIONAL RECOMMENDATION: 2 NN server threads per GPU measured faster. "
            + "To use this, set numNNServerThreadsPerModel = 2 in your config.\n"
            + "ADDITIONAL RECOMMENDATION: a smaller batch size measured faster. "
            + "To use this, set nnMaxBatchSize = 5 in your config.\n";

    KataGoBenchmarkObservation observation = KataGoBenchmarkParser.parse(output, 0);

    assertEquals(10, observation.recommendedThreads());
    assertEquals(2, observation.recommendedNnServerThreadsPerModel());
    assertEquals(5, observation.recommendedMaxBatchSize());
    assertTrue(observation.hasAdditionalGpuRecommendation());
    assertEquals(1, observation.metrics().size());
    assertEquals(
        386.0,
        observation.recommendedMetric().orElseThrow().visitsPerSecond(),
        "The selected search row must not be overwritten by an extra comparison run.");
  }

  @Test
  void rejectedKataGo118ExtraTestsKeepBackendDefaults() {
    String output =
        "numSearchThreads = 10: 6 / 6 positions, visits/s = 386.0 nnEvals/s = 320.0 "
            + "nnBatches/s = 42.0 avgBatchSize = 7.6\n"
            + "numSearchThreads = 10: +72 Elo (recommended)\n"
            + "Running additional tests of a few other settings at numSearchThreads = 10.\n"
            + "2 NN server threads per GPU was not at least 3% faster (measured +0.4%), keeping 1.\n"
            + "Half batch size was not at least 3% faster (measured +2.8%), keeping the default.\n";

    KataGoBenchmarkObservation observation = KataGoBenchmarkParser.parse(output, 0);

    assertEquals(0, observation.recommendedNnServerThreadsPerModel());
    assertEquals(0, observation.recommendedMaxBatchSize());
    assertFalse(observation.hasAdditionalGpuRecommendation());
  }

  @Test
  void parsesKataGo118ExplicitMultiGpuServerThreadRecommendationBlock() {
    String output =
        "numSearchThreads = 12: +80 Elo (recommended)\n"
            + "ADDITIONAL RECOMMENDATION: 2 NN server threads per GPU measured faster. "
            + "To use this, set the following in your config "
            + "(note that it also increases GPU memory usage):\n"
            + "  numNNServerThreadsPerModel = 4\n"
            + "  cudaDeviceToUseThread0 = 0\n"
            + "  cudaDeviceToUseThread1 = 0\n"
            + "  cudaDeviceToUseThread2 = 1\n"
            + "  cudaDeviceToUseThread3 = 1\n";

    KataGoBenchmarkObservation observation = KataGoBenchmarkParser.parse(output, 0);

    assertEquals(12, observation.recommendedThreads());
    assertEquals(4, observation.recommendedNnServerThreadsPerModel());
  }
}
