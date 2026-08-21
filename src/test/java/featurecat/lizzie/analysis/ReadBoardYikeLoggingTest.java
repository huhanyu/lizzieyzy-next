package featurecat.lizzie.analysis;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import featurecat.lizzie.logging.DiagnosticModule;
import featurecat.lizzie.logging.LogStream;
import featurecat.lizzie.logging.ReadBoardObservation;
import featurecat.lizzie.logging.LoggingLimits;
import featurecat.lizzie.logging.LoggingRuntime;
import java.io.IOException;
import java.lang.reflect.Field;
import featurecat.lizzie.logging.LoggingSettings;
import featurecat.lizzie.logging.TraceScope;
import featurecat.lizzie.logging.WorkDirectoryResolution;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import featurecat.lizzie.util.YikeSyncDebugLog;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ReadBoardYikeLoggingTest {
  private static final String RAW_HELPER = "T04_RAW_HELPER sgf (;SZ[19]B[pd])";

  @TempDir Path tempDir;

  @AfterEach
  void tearDown() {
    LoggingRuntime.current().ifPresent(LoggingRuntime::shutdown);
    SyncDiagnosticsRecorder.clearDefaultForTests();
  }

  @Test
  void selectedDiagnosticsRecordDecisionsWithoutRawHelperTraffic() throws Exception {
    LoggingRuntime runtime = startRuntime();
    runtime.applySettings(
        LoggingSettings.defaults()
            .withDiagnosticsEnabled(true)
            .withDiagnosticModules(EnumSet.of(DiagnosticModule.READBOARD_YIKE)));

    SyncDiagnosticsRecorder.getDefault()
        .updateLatestDecision(
            SyncDecisionTrace.builder("FORCE_REBUILD", "removed_stones")
                .epoch(7)
                .platform("yike")
                .source("ReadBoard.syncBoardStones")
                .summary("FORCE_REBUILD removed_stones")
                .build());
    SyncDiagnosticsRecorder.getDefault()
        .recordProtocolEvent(SyncProtocolDiagnosticEvent.of(1L, RAW_HELPER, "ReadBoard"));
    awaitLogs(runtime);

    String app = readApp();
    assertTrue(app.contains("readboard event=decision"), app);
    assertTrue(app.contains("result=FORCE_REBUILD"), app);
    assertTrue(app.contains("reason=removed_stones"), app);
    assertTrue(app.contains("epoch=7"), app);
    assertFalse(app.contains(RAW_HELPER), app);
    assertFalse(app.contains("T04_RAW_HELPER"), app);
    assertFalse(Files.exists(tempDir.resolve("logs/readboard-trace.log")));
  }

  @Test
  void selectedDiagnosticsRecordYikeSessionWithoutFlatteningSnapshot() throws Exception {
    LoggingRuntime runtime = startRuntime();
    runtime.applySettings(
        LoggingSettings.defaults()
            .withDiagnosticsEnabled(true)
            .withDiagnosticModules(EnumSet.of(DiagnosticModule.READBOARD_YIKE)));

    YikeSessionDiagnosticsSnapshot snapshot =
        YikeSessionDiagnosticsSnapshot.builder()
            .source("OnlineDialog")
            .summary("promote-pending-session")
            .currentRouteKind("live-room")
            .currentSessionKey("live-room:186538")
            .activeSessionKey("live-room:186538")
            .activeSyncReady(true)
            .activeGeometryReady(true)
            .pendingSessionKey("none")
            .lastSessionSwitchReason("promote-pending-session")
            .build();
    SyncDiagnosticsRecorder.getDefault().updateYikeSession(snapshot);
    awaitLogs(runtime);

    String app = readApp();
    assertTrue(app.contains("readboard event=yike-session"), app);
    assertTrue(app.contains("reason=promote-pending-session"), app);
    assertTrue(app.contains("active=live-room:186538"), app);
    assertTrue(app.contains("syncReady=true"), app);
    assertTrue(app.contains("geometryReady=true"), app);
    assertFalse(app.contains("lastYikeDebugEventSummary"), app);

    YikeSessionDiagnosticsSnapshot retained =
        SyncDiagnosticsRecorder.getDefault().snapshot().getYikeSnapshot();
    assertTrue(Boolean.TRUE.equals(retained.getActiveSyncReady()));
    assertTrue(Boolean.TRUE.equals(retained.getActiveGeometryReady()));
    assertTrue("live-room:186538".equals(retained.getActiveSessionKey()));
  }

  @Test
  void selectedFullTraceWritesProtocolOnlyToReadboardTrace() throws Exception {
    LoggingRuntime runtime = startRuntime();
    runtime.applySettings(
        LoggingSettings.defaults()
            .withDiagnosticsEnabled(true)
            .withDiagnosticModules(EnumSet.of(DiagnosticModule.READBOARD_YIKE)));
    SyncDiagnosticsRecorder.getDefault()
        .recordProtocolEvent(SyncProtocolDiagnosticEvent.of(1L, RAW_HELPER, "ReadBoard"));
    awaitLogs(runtime);
    assertFalse(Files.exists(tempDir.resolve("logs/readboard-trace.log")));

    runtime.startFullTrace(EnumSet.of(TraceScope.READBOARD_YIKE));
    SyncDiagnosticsRecorder.getDefault()
        .recordProtocolEvent(SyncProtocolDiagnosticEvent.of(2L, RAW_HELPER, "ReadBoard"));
    SyncDiagnosticsRecorder.getDefault()
        .updateLatestDecision(
            SyncDecisionTrace.builder("HOLD", "conflict_repeat")
                .epoch(8)
                .platform("yike")
                .build());
    awaitLogs(runtime);
    runtime.stopFullTrace();
    awaitLogs(runtime);

    String app = readApp();
    assertTrue(app.contains("readboard event=decision"), app);
    assertFalse(app.contains("readboard event=protocol"), app);
    assertFalse(app.contains(RAW_HELPER), app);

    String trace = Files.readString(tempDir.resolve("logs/readboard-trace.log"));
    assertTrue(trace.contains("Full Trace session started"), trace);
    assertTrue(trace.contains("scope=readboard-yike"), trace);
    assertTrue(trace.contains("readboard raw protocol=" + RAW_HELPER), trace);
    assertTrue(trace.contains("Full Trace session stopped"), trace);
  }

  @Test
  void staleEventsKeepOriginalGmaAndSessionIdentities() throws Exception {
    LoggingRuntime runtime = startRuntime();
    runtime.applySettings(
        LoggingSettings.defaults()
            .withDiagnosticsEnabled(true)
            .withDiagnosticModules(EnumSet.of(DiagnosticModule.READBOARD_YIKE)));

    ReadBoardObservation.inContext(
        "eng-1",
        "gma-1",
        "live-room:1",
        () ->
            SyncDiagnosticsRecorder.getDefault()
                .updateLatestDecision(
                    SyncDecisionTrace.builder("FORCE_REBUILD", "removed_stones")
                        .epoch(3)
                        .platform("yike")
                        .build()));
    ReadBoardObservation.inContext("eng-1", "gma-2", "live-room:2", () -> {});
    ReadBoardObservation.inContext(
        "eng-1", "gma-1", "live-room:1", () -> ReadBoardObservation.recordGma("outcome", "played"));
    awaitLogs(runtime);

    String app = readApp();
    int decision = app.indexOf("readboard event=decision");
    int gma = app.indexOf("readboard event=gma");
    assertTrue(decision >= 0, app);
    assertTrue(gma >= 0, app);
    String decisionLine = app.substring(Math.max(0, decision - 160), decision + 80);
    String gmaLine = app.substring(Math.max(0, gma - 160), Math.min(app.length(), gma + 80));
    assertTrue(decisionLine.contains("gma=gma-1"), decisionLine);
    assertTrue(decisionLine.contains("yike=live-room:1"), decisionLine);
    assertTrue(gmaLine.contains("gma=gma-1"), gmaLine);
    assertTrue(gmaLine.contains("yike=live-room:1"), gmaLine);
    assertFalse(gmaLine.contains("gma=gma-2"), gmaLine);
    assertFalse(gmaLine.contains("yike=live-room:2"), gmaLine);
  }

  @Test
  void stalledReadboardTraceQueueDoesNotBlockProducer() throws Exception {
    LoggingRuntime runtime =
        LoggingRuntime.initialize(
            new WorkDirectoryResolution(tempDir, List.of()),
            new LoggingLimits(32, 1, 1, 1, 7, 10_000, 1_000));
    runtime.applySettings(LoggingSettings.defaults().withDiagnosticsEnabled(true));
    runtime.startFullTrace(EnumSet.of(TraceScope.READBOARD_YIKE));
    CountDownLatch gate = new CountDownLatch(1);
    Method block =
        LoggingRuntime.class.getDeclaredMethod(
            "blockPersistenceForTests", LogStream.class, CountDownLatch.class);
    block.setAccessible(true);
    block.invoke(runtime, LogStream.READBOARD_TRACE, gate);
    org.slf4j.LoggerFactory.getLogger("lizzie.readboard.trace").info("block-one");
    long began = System.nanoTime();
    SyncDiagnosticsRecorder.getDefault()
        .recordProtocolEvent(SyncProtocolDiagnosticEvent.of(3L, RAW_HELPER, "ReadBoard"));
    long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - began);
    gate.countDown();
    assertTrue(elapsed < 500, "producer blocked for " + elapsed + "ms");
  }

  @Test
  void engineModuleDoesNotEmitReadboardDiagnostics() throws Exception {
    LoggingRuntime runtime = startRuntime();
    runtime.applySettings(
        LoggingSettings.defaults()
            .withDiagnosticsEnabled(true)
            .withDiagnosticModules(EnumSet.of(DiagnosticModule.ENGINE)));
    SyncDiagnosticsRecorder.getDefault()
        .updateLatestDecision(
            SyncDecisionTrace.builder("NO_CHANGE", "same_position").epoch(1).build());
    awaitLogs(runtime);
    assertFalse(readApp().contains("readboard event=decision"), readApp());
  }

  @Test
  void legacyDebugWritersStopReceivingNewOutput() throws Exception {
    startRuntime();
    Path yike = Path.of("target/yike-sync-debug.log");
    Path extra = Path.of("target/yike-debug.log");
    Path local = Path.of("runtime/readboard-local-move-debug.log");
    Long yikeSize = Files.isRegularFile(yike) ? Files.size(yike) : null;
    Long extraSize = Files.isRegularFile(extra) ? Files.size(extra) : null;
    Long localSize = Files.isRegularFile(local) ? Files.size(local) : null;

    YikeSyncDebugLog.log("T04_MUST_NOT_APPEAR");
    ReadBoard.localMoveSyncDebug("T04_MUST_NOT_APPEAR");

    if (yikeSize == null) {
      assertFalse(Files.exists(yike));
    } else {
      assertEquals(yikeSize, Files.size(yike));
      assertFalse(Files.readString(yike).contains("T04_MUST_NOT_APPEAR"));
    }
    if (extraSize == null) {
      assertFalse(Files.exists(extra));
    } else {
      assertEquals(extraSize, Files.size(extra));
    }
    if (localSize == null) {
      assertFalse(Files.exists(local));
    } else {
      assertEquals(localSize, Files.size(local));
      assertFalse(Files.readString(local).contains("T04_MUST_NOT_APPEAR"));
    }
  }

  @Test
  void nativeStartFailureDoesNotRecordStarted() throws Exception {
    LoggingRuntime runtime = startRuntime();
    ReadBoard board = allocate(ReadBoard.class);
    Method fail =
        ReadBoard.class.getDeclaredMethod(
            "logNativeReadBoardStartFailure", ProcessBuilder.class, IOException.class);
    fail.setAccessible(true);
    fail.invoke(board, new ProcessBuilder("readboard.exe"), new IOException("boom"));
    awaitLogs(runtime);
    String app = readApp();
    assertFalse(app.contains("readboard event=started"), app);
    assertTrue(app.contains("readboard event=failed reason=native-start"), app);
  }

  @SuppressWarnings("unchecked")
  private static <T> T allocate(Class<T> type) throws Exception {
    Field field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
    field.setAccessible(true);
    return (T) ((sun.misc.Unsafe) field.get(null)).allocateInstance(type);
  }

  private LoggingRuntime startRuntime() {
    LoggingRuntime.current().ifPresent(LoggingRuntime::shutdown);
    return LoggingRuntime.initialize(
        new WorkDirectoryResolution(tempDir, List.of()),
        new LoggingLimits(64, 32, 32, 32, 7, 1_000_000, 256_000));
  }

  private String readApp() throws Exception {
    Path file = tempDir.resolve("logs/app.log");
    return Files.isRegularFile(file) ? Files.readString(file, StandardCharsets.UTF_8) : "";
  }

  private static void awaitLogs(LoggingRuntime runtime) throws Exception {
    Method method = LoggingRuntime.class.getDeclaredMethod("awaitIdle");
    method.setAccessible(true);
    method.invoke(runtime);
  }
}
