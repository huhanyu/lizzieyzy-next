package featurecat.lizzie.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class GraphicsDriverDiagnosticsTest {
  @Test
  void driverSummaryKeepsOnlyCompactNonPersonalGpuData() {
    assertEquals(
        "NVIDIA GeForce RTX 5080, 580.88;NVIDIA RTX A4000, 570.10",
        GraphicsDriverDiagnostics.sanitizeSummary(
            "NVIDIA GeForce RTX 5080, 580.88\nNVIDIA RTX A4000, 570.10\n"));
  }

  @Test
  void emptyDriverOutputIsReportedAsUnavailable() {
    assertEquals("unavailable", GraphicsDriverDiagnostics.sanitizeSummary(" \n "));
  }
}
