package featurecat.lizzie.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AnalysisResourceCoordinatorTest {
  @TempDir Path tempDir;

  @Test
  void foregroundReleasesOnlyIdleOrAutomaticDedicatedProcesses() {
    assertEquals(
        AnalysisResourceCoordinator.ForegroundDecision.SHARED_ENGINE,
        AnalysisResourceCoordinator.decideForegroundStart(true, false, true, true));
    assertEquals(
        AnalysisResourceCoordinator.ForegroundDecision.NONE,
        AnalysisResourceCoordinator.decideForegroundStart(false, false, true, true));
    assertEquals(
        AnalysisResourceCoordinator.ForegroundDecision.RELEASE_IDLE_SECONDARY,
        AnalysisResourceCoordinator.decideForegroundStart(false, true, false, false));
    assertEquals(
        AnalysisResourceCoordinator.ForegroundDecision.PREEMPT_AUTOMATIC_SECONDARY,
        AnalysisResourceCoordinator.decideForegroundStart(false, true, true, true));
    assertEquals(
        AnalysisResourceCoordinator.ForegroundDecision.KEEP_USER_TASK,
        AnalysisResourceCoordinator.decideForegroundStart(false, true, true, false));
  }

  @Test
  void diagnosticCommandsRedactCredentialsAndKeepTuningParameters() {
    String redacted =
        AnalysisResourceCoordinator.redactCommand(
            "remote --token=secret-token --password hunter2 wss://host/path?token=query-token");

    assertFalse(redacted.contains("secret-token"));
    assertFalse(redacted.contains("hunter2"));
    assertFalse(redacted.contains("query-token"));
    assertTrue(redacted.contains("<redacted>"));

    String diagnosticCommand =
        AnalysisResourceCoordinator.diagnosticCommand(
            "\"C:\\\\Users\\\\Player\\\\KataGo\\\\katago.exe\" gtp "
                + "-model \"C:\\\\Users\\\\Player\\\\weights\\\\model.bin.gz\" "
                + "-config \"C:\\\\Users\\\\Player\\\\configs\\\\gtp.cfg\" "
                + "-override-config homeDataDir=C:\\\\Users\\\\Player\\\\cache");
    assertFalse(diagnosticCommand.contains("Player"));
    assertTrue(diagnosticCommand.startsWith("katago.exe gtp"));
    assertTrue(diagnosticCommand.contains("-model model.bin.gz"));
    assertTrue(diagnosticCommand.contains("-config gtp.cfg"));
    assertTrue(diagnosticCommand.contains("homeDataDir=<local-path>"));

    Map<String, String> parameters =
        AnalysisResourceCoordinator.parseDynamicParameters(
            List.of(
                "kata-set-param numSearchThreads 12", "kata-set-param nnMaxBatchSize 64", "name"));
    assertEquals(Map.of("numSearchThreads", "12", "nnMaxBatchSize", "64"), parameters);
  }

  @Test
  void configHashIsStableAndMissingFilesStayEmpty() throws Exception {
    Path config = tempDir.resolve("gtp.cfg");
    Files.writeString(config, "numSearchThreads = 12\n", StandardCharsets.UTF_8);

    assertEquals(
        AnalysisResourceCoordinator.fileSha256(config),
        AnalysisResourceCoordinator.fileSha256(config));
    assertEquals(64, AnalysisResourceCoordinator.fileSha256(config).length());
    assertEquals("", AnalysisResourceCoordinator.fileSha256(tempDir.resolve("missing.cfg")));
  }

  @Test
  void throughputElapsedTimeUsesAStableNanosecondWindow() {
    assertEquals(0.25, AnalysisResourceCoordinator.elapsedSeconds(1_000L, 250_001_000L));
    assertEquals(0.0, AnalysisResourceCoordinator.elapsedSeconds(2_000L, 1_000L));
  }

  @Test
  void optInDiagnosticsAreStructuredAndNeverPersistSecrets() throws Exception {
    Path output = tempDir.resolve("analysis-resource-diagnostics.jsonl");
    System.setProperty("lizzie.analysis.diagnostics", "true");
    System.setProperty("lizzie.analysis.diagnostics.path", output.toString());
    Object owner = new Object();
    try {
      AnalysisResourceCoordinator.processStarted(
          owner,
          AnalysisResourceCoordinator.Purpose.MAIN_BOARD,
          "/Users/private-user/KataGo/katago gtp --token=private-value "
              + "-model /Users/private-user/weights/model.bin.gz",
          null);
      AnalysisResourceCoordinator.commandSent(
          owner,
          AnalysisResourceCoordinator.Purpose.MAIN_BOARD,
          "kata-set-param numSearchThreads 12");
      AnalysisResourceCoordinator.foregroundPausedForAuxiliary(
          owner, AnalysisResourceCoordinator.Purpose.AUTO_QUICK_ANALYSIS);
      AnalysisResourceCoordinator.processStopped(
          owner, AnalysisResourceCoordinator.Purpose.MAIN_BOARD, null);

      String diagnostics = Files.readString(output, StandardCharsets.UTF_8);
      assertTrue(diagnostics.contains("process-started"));
      assertTrue(diagnostics.contains("dynamic-parameter"));
      assertTrue(diagnostics.contains("foreground-paused"));
      assertTrue(diagnostics.contains("AUTO_QUICK_ANALYSIS"));
      assertTrue(diagnostics.contains("process-stopped"));
      assertTrue(diagnostics.contains("numSearchThreads"));
      assertFalse(diagnostics.contains("private-value"));
      assertFalse(diagnostics.contains("private-user"));
    } finally {
      System.clearProperty("lizzie.analysis.diagnostics");
      System.clearProperty("lizzie.analysis.diagnostics.path");
    }
  }
}
