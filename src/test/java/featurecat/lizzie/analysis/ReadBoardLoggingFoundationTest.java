package featurecat.lizzie.analysis;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import featurecat.lizzie.logging.LoggingLimits;
import featurecat.lizzie.logging.LoggingRuntime;
import featurecat.lizzie.logging.TraceScope;
import featurecat.lizzie.logging.WorkDirectoryResolution;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ReadBoardLoggingFoundationTest {
  private static final String RAW_HELPER =
      "roomToken CANARY_TOKEN_7f3a cookie=CANARY_COOKIE_9c2e https://live.example/path?token=CANARY_QUERY";
  private static final String RAW_FRAME =
      "re=" + String.join(",", Collections.nCopies(19, "1")) + " payload=CANARY_FRAME";

  @TempDir Path tempDir;

  private final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
  private final ByteArrayOutputStream stderr = new ByteArrayOutputStream();
  private final PrintStream originalOut = System.out;
  private final PrintStream originalErr = System.err;

  @AfterEach
  void tearDown() {
    System.setOut(originalOut);
    System.setErr(originalErr);
    resetRuntime();
  }

  @Test
  void launchAndExitUseLoggerInsteadOfSystemStreams() throws Exception {
    LoggingRuntime runtime = startRuntime();
    captureStreams();
    ReadBoard board = allocateBoard();
    File cwd = tempDir.resolve("native-cwd").toFile();
    assertTrue(cwd.mkdirs());
    ProcessBuilder processBuilder =
        new ProcessBuilder(new File(cwd, "readboard.exe").getAbsolutePath(), "yzy");
    processBuilder.directory(cwd);

    invoke(
        board,
        "logNativeReadBoardLaunch",
        new Class<?>[] {ProcessBuilder.class},
        new Object[] {processBuilder});
    Process finished = new ProcessBuilder("sh", "-c", "exit 7").start();
    finished.waitFor();
    setField(board, "process", finished);
    invoke(board, "logReadBoardExit");
    awaitLogs(runtime);

    String app = readApp();
    String captured = capturedText();
    assertTrue(app.contains("readboard event=started"), app);
    assertTrue(app.contains("executable="), app);
    assertTrue(app.contains(cwd.getAbsolutePath()) || app.contains("native-cwd"), app);
    assertTrue(app.contains(runtime.applicationLogSessionId()), app);
    assertTrue(app.contains("readboard event=stopped"), app);
    assertTrue(app.contains("exitCode=7"), app);
    assertFalse(captured.contains("Starting native board synchronization tool."), captured);
    assertFalse(captured.contains("Native board synchronization process exit code"), captured);
    assertFalse(Files.exists(tempDir.resolve("logs/readboard")));
  }

  @Test
  void startFailureUsesLoggerInsteadOfSystemErr() throws Exception {
    LoggingRuntime runtime = startRuntime();
    captureStreams();
    ReadBoard board = allocateBoard();
    invoke(
        board,
        "logNativeReadBoardStartFailure",
        new Class<?>[] {ProcessBuilder.class, IOException.class},
        new Object[] {new ProcessBuilder("readboard.exe"), new IOException("boom")});
    awaitLogs(runtime);

    String app = readApp();
    String captured = capturedText();
    assertTrue(app.contains("readboard event=failed reason=native-start"), app);
    assertFalse(app.contains("readboard event=started"), app);
    assertFalse(captured.contains("Failed to start native board synchronization tool."), captured);
    assertFalse(captured.contains("start exception:"), captured);
  }

  @Test
  void helperRawLineDoesNotEnterSystemOutOrHostLogs() throws Exception {
    LoggingRuntime runtime = startRuntime();
    captureStreams();
    ReadBoard board = allocateBoard();
    invoke(board, "logReadBoardOutputLine", new Class<?>[] {String.class}, new Object[] {RAW_HELPER});
    board.parseLine(RAW_HELPER);
    awaitLogs(runtime);

    String captured = capturedText();
    String scanned = scanLogs();
    assertFalse(captured.contains("Native board synchronization output:"), captured);
    assertFalse(captured.contains(RAW_HELPER), captured);
    assertFalse(captured.contains("CANARY_TOKEN_7f3a"), captured);
    assertFalse(scanned.contains(RAW_HELPER), scanned);
    assertFalse(scanned.contains("CANARY_TOKEN_7f3a"), scanned);
    assertFalse(scanned.contains("CANARY_COOKIE_9c2e"), scanned);
    assertFalse(scanned.contains("CANARY_QUERY"), scanned);
    assertFalse(Files.exists(tempDir.resolve("logs/readboard-trace.log")));
    assertFalse(Files.exists(tempDir.resolve("logs/readboard")));
  }

  @Test
  void parseAndIoFailuresStayInsideReadLoop() throws Exception {
    LoggingRuntime runtime = startRuntime();
    captureStreams();
    ReadBoard board = allocateBoard();
    setField(board, "tempcount", new ArrayList<Integer>());
    setField(board, "shutdownStarted", true);
    String badFrame = "re=" + String.join(",", Collections.nCopies(19, "x")) + "\n";
    setField(
        board,
        "inputStream",
        new InputStreamReader(new SequenceThenThrow(badFrame, "broken-pipe"), StandardCharsets.UTF_8));

    invoke(board, "read");
    awaitLogs(runtime);

    String app = readApp();
    assertTrue(app.contains("readboard event=failed reason=parse-line"), app);
    assertTrue(app.contains("readboard event=failed reason=reader"), app);
    assertFalse(capturedText().contains(badFrame.trim()), capturedText());
  }

  @Test
  void fullTraceOffDoesNotWriteHighDensityReadboardTrace() throws Exception {
    LoggingRuntime runtime = startRuntime();
    ReadBoard board = allocateBoard();
    for (int i = 0; i < 12; i++) {
      invoke(
          board,
          "logReadBoardOutputLine",
          new Class<?>[] {String.class},
          new Object[] {"ready canary-" + i});
    }
    awaitLogs(runtime);

    assertFalse(Files.exists(tempDir.resolve("logs/readboard-trace.log")));
    String app = readApp();
    assertFalse(app.contains("direction=in"), app);
    assertFalse(app.contains("canary-0"), app);
  }

  @Test
  void fullTraceOnWritesOnlyWhitelistedSummaries() throws Exception {
    LoggingRuntime runtime = startRuntime();
    runtime.startFullTrace(EnumSet.of(TraceScope.READBOARD_YIKE));
    ReadBoard board = allocateBoard();
    invoke(board, "logReadBoardOutputLine", new Class<?>[] {String.class}, new Object[] {RAW_HELPER});
    invoke(board, "logReadBoardOutputLine", new Class<?>[] {String.class}, new Object[] {RAW_FRAME});
    board.parseLine("readboardLoggingV1 dGVzdFByb2Nlc3NJRA on off off healthy 0");
    awaitLogs(runtime);
    runtime.stopFullTrace();
    awaitLogs(runtime);

    String trace = Files.readString(tempDir.resolve("logs/readboard-trace.log"));
    assertTrue(trace.contains("direction=in"), trace);
    assertTrue(trace.contains("command=roomToken"), trace);
    assertTrue(trace.contains("command=re"), trace);
    assertTrue(trace.contains("command=readboardLoggingV1"), trace);
    assertTrue(trace.contains("outcome=control"), trace);
    assertTrue(trace.contains("persistence=healthy"), trace);
    assertFalse(trace.contains(RAW_HELPER), trace);
    assertFalse(trace.contains("CANARY_TOKEN_7f3a"), trace);
    assertFalse(trace.contains("CANARY_COOKIE_9c2e"), trace);
    assertFalse(trace.contains("CANARY_QUERY"), trace);
    assertFalse(trace.contains("CANARY_FRAME"), trace);
    assertFalse(trace.contains("payload="), trace);
    assertFalse(readApp().contains(RAW_HELPER), readApp());
  }

  @Test
  void loggerAbsenceDoesNotThrowFromLaunchOrOutputPaths() throws Exception {
    resetRuntime();
    ReadBoard board = allocateBoard();
    invoke(
        board,
        "logNativeReadBoardLaunch",
        new Class<?>[] {ProcessBuilder.class},
        new Object[] {new ProcessBuilder("readboard.exe")});
    invoke(
        board,
        "logNativeReadBoardStartFailure",
        new Class<?>[] {ProcessBuilder.class, IOException.class},
        new Object[] {new ProcessBuilder("readboard.exe"), new IOException("boom")});
    invoke(board, "logReadBoardOutputLine", new Class<?>[] {String.class}, new Object[] {RAW_HELPER});
    invoke(board, "logReadBoardExit");
    board.parseLine("readboardLoggingObserved cmVxdWVzdDE dGVzdFByb2Nlc3NJRA on off on degraded 3 writer-fault");
  }

  private LoggingRuntime startRuntime() {
    resetRuntime();
    return LoggingRuntime.initialize(
        new WorkDirectoryResolution(tempDir, List.of()),
        new LoggingLimits(64, 32, 32, 32, 7, 1_000_000, 256_000));
  }

  private void captureStreams() {
    System.setOut(new PrintStream(stdout, true, StandardCharsets.UTF_8));
    System.setErr(new PrintStream(stderr, true, StandardCharsets.UTF_8));
  }

  private String capturedText() {
    return stdout.toString(StandardCharsets.UTF_8) + stderr.toString(StandardCharsets.UTF_8);
  }

  private String readApp() throws IOException {
    return Files.readString(tempDir.resolve("logs/app.log"));
  }

  private String scanLogs() throws IOException {
    Path logs = tempDir.resolve("logs");
    if (!Files.exists(logs)) {
      return "";
    }
    StringBuilder scanned = new StringBuilder();
    try (var stream = Files.walk(logs)) {
      stream
          .filter(Files::isRegularFile)
          .forEach(
              path -> {
                try {
                  scanned.append(Files.readString(path));
                } catch (IOException e) {
                  throw new RuntimeException(e);
                }
              });
    }
    return scanned.toString();
  }

  private static void resetRuntime() {
    try {
      Method reset = LoggingRuntime.class.getDeclaredMethod("resetForTests");
      reset.setAccessible(true);
      reset.invoke(null);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(e);
    }
  }

  private static void awaitLogs(LoggingRuntime runtime) throws Exception {
    Method method = LoggingRuntime.class.getDeclaredMethod("awaitIdle");
    method.setAccessible(true);
    method.invoke(runtime);
  }

  private static ReadBoard allocateBoard() throws Exception {
    return (ReadBoard) UnsafeHolder.UNSAFE.allocateInstance(ReadBoard.class);
  }

  private static void invoke(Object target, String name) throws Exception {
    invoke(target, name, new Class<?>[] {}, new Object[] {});
  }

  private static void invoke(Object target, String name, Class<?>[] types, Object[] args)
      throws Exception {
    Method method = ReadBoard.class.getDeclaredMethod(name, types);
    method.setAccessible(true);
    method.invoke(target, args);
  }

  private static void setField(Object target, String name, Object value) throws Exception {
    Field field = ReadBoard.class.getDeclaredField(name);
    field.setAccessible(true);
    field.set(target, value);
  }

  private static final class SequenceThenThrow extends InputStream {
    private final ByteArrayInputStream prefix;
    private final String message;

    private SequenceThenThrow(String prefix, String message) {
      this.prefix = new ByteArrayInputStream(prefix.getBytes(StandardCharsets.UTF_8));
      this.message = message;
    }

    @Override
    public int read() throws IOException {
      int next = prefix.read();
      if (next >= 0) {
        return next;
      }
      throw new IOException(message);
    }
  }

  private static final class UnsafeHolder {
    private static final sun.misc.Unsafe UNSAFE = loadUnsafe();

    private static sun.misc.Unsafe loadUnsafe() {
      try {
        Field field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return (sun.misc.Unsafe) field.get(null);
      } catch (ReflectiveOperationException ex) {
        throw new IllegalStateException("Failed to access Unsafe", ex);
      }
    }
  }
}
