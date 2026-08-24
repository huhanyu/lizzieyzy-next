package featurecat.lizzie.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import featurecat.lizzie.util.NvidiaGpuDetector.CudaCompatibility;
import featurecat.lizzie.util.NvidiaGpuDetector.GpuInfo;
import featurecat.lizzie.util.NvidiaGpuDetector.TensorRtRecommendation;
import org.junit.jupiter.api.Test;

class NvidiaGpuDetectorTest {
  @Test
  void driverPolicyUsesOfficialCuda128CompatibilityThresholds() {
    assertEquals(CudaCompatibility.SUPPORTED, NvidiaGpuDetector.cudaCompatibility("570.65"));
    assertEquals(CudaCompatibility.SUPPORTED, NvidiaGpuDetector.cudaCompatibility("576.80"));
    assertEquals(CudaCompatibility.PROBE_REQUIRED, NvidiaGpuDetector.cudaCompatibility("560.76"));
    assertEquals(CudaCompatibility.PROBE_REQUIRED, NvidiaGpuDetector.cudaCompatibility("528.33"));
    assertEquals(CudaCompatibility.UNSUPPORTED, NvidiaGpuDetector.cudaCompatibility("528.32"));
    assertEquals(CudaCompatibility.UNKNOWN, NvidiaGpuDetector.cudaCompatibility("N/A"));
  }

  @Test
  void tensorRtIsOptionalForRtx30AndEarlierButNotModernRtx() {
    assertEquals(
        TensorRtRecommendation.ALLOWED,
        NvidiaGpuDetector.recommend(gpu("NVIDIA GeForce RTX 3070", 8, 6)));
    assertEquals(
        TensorRtRecommendation.ALLOWED,
        NvidiaGpuDetector.recommend(gpu("NVIDIA GeForce RTX 2080", 7, 5)));
    assertEquals(
        TensorRtRecommendation.ALLOWED,
        NvidiaGpuDetector.recommend(gpu("NVIDIA GeForce GTX 1660", 7, 5)));
    assertEquals(
        TensorRtRecommendation.NOT_RECOMMENDED,
        NvidiaGpuDetector.recommend(gpu("NVIDIA GeForce RTX 4090", 8, 9)));
    assertEquals(
        TensorRtRecommendation.NOT_RECOMMENDED,
        NvidiaGpuDetector.recommend(gpu("NVIDIA GeForce RTX 5090", 12, 0)));
    assertEquals(
        TensorRtRecommendation.NOT_RECOMMENDED,
        NvidiaGpuDetector.recommend(gpu("NVIDIA GeForce GTX 1080", 6, 1)));
  }

  private static GpuInfo gpu(String name, int major, int minor) {
    return new GpuInfo(name, major, minor, "570.65", 8192L, "test");
  }
}
