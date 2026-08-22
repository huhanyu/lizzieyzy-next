package featurecat.lizzie.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

class ReadBoardLoggingHandshakeTest {
  private static final String CAPABILITY =
      "readboardLoggingV1 dGVzdFByb2Nlc3NJRA on off off healthy 0";

  @Test
  void parseLineCapabilityEstablishesProcessSessionWithoutSyncDispatch() throws Exception {
    ReadBoard board = allocateBoard();
    ReadBoardLoggingControl control =
        new ReadBoardLoggingControl(ReadBoardLoggingControl.Desired.launchDefaults(false), true);
    setField(board, "loggingControl", control);
    setField(board, "tempcount", new java.util.ArrayList<Integer>());

    board.parseLine(CAPABILITY);
    board.parseLine("re=" + String.join(",", java.util.Collections.nCopies(19, "1")));

    assertEquals("dGVzdFByb2Nlc3NJRA", control.processSessionId());
    assertEquals(ReadBoardLoggingControl.Status.CAPABILITY_READY, control.status());
  }

  @Test
  void requestLoggingSetWritesFrozenSetLine() throws Exception {
    ReadBoard board = allocateBoard();
    ReadBoardLoggingControl control =
        new ReadBoardLoggingControl(ReadBoardLoggingControl.Desired.launchDefaults(false), true);
    setField(board, "loggingControl", control);
    setField(board, "usePipe", true);
    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    BufferedOutputStream output = new BufferedOutputStream(buffer);
    setField(board, "outputStream", output);
    board.parseLine(CAPABILITY);

    assertTrue(board.requestLoggingSet(true, false, true));
    output.flush();

    String sent = buffer.toString("UTF-8").trim();
    assertTrue(sent.startsWith("readboardLoggingSet "), sent);
    assertTrue(sent.endsWith(" on off on"), sent);
    ReadBoardLoggingProtocol.SetRequest parsed = ReadBoardLoggingProtocol.tryParseSet(sent);
    assertEquals(ReadBoardLoggingProtocol.Toggle.ON, parsed.diagnostics);
    assertEquals(ReadBoardLoggingProtocol.Toggle.OFF, parsed.capture);
    assertEquals(ReadBoardLoggingProtocol.Toggle.ON, parsed.trace);
  }

  @Test
  void completeLaunchArgumentsAppendsCaptureOffOnWindowsPath() throws Exception {
    List<String> positional = Arrays.asList("yzy", " ", " ", " ", "0", "cn", "-1");
    List<String> args =
        ReadBoardLoggingControl.appendNamedLoggingArguments(
            positional, "C:\\work\\logs\\readboard", "dGVzdEhvc3RTZXNzaW9u", false);
    assertEquals(17, args.size());
    assertEquals("off", args.get(16));
    assertFalse(args.contains("--trace"));
  }

  @Test
  void loggingSnapshotIsDetachedUntilControlExists() throws Exception {
    ReadBoard board = allocateBoard();

    ReadBoardLoggingSnapshot snapshot = board.loggingSnapshot();

    assertFalse(snapshot.attached());
    assertEquals(
        ReadBoardLoggingControl.Presentation.UNKNOWN, snapshot.diagnosticsPresentation());
    assertEquals("no capture session", snapshot.captureSummary());
  }


  @Test
  void requestLoggingSetRequiresProcessSession() throws Exception {
    ReadBoard board = allocateBoard();
    ReadBoardLoggingControl control =
        new ReadBoardLoggingControl(ReadBoardLoggingControl.Desired.launchDefaults(false), true);
    setField(board, "loggingControl", control);
    setField(board, "usePipe", true);
    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    BufferedOutputStream output = new BufferedOutputStream(buffer);
    setField(board, "outputStream", output);

    assertFalse(board.requestLoggingSet(false, true, true));
    output.flush();
    assertEquals("", buffer.toString("UTF-8"));

    board.parseLine(CAPABILITY);
    assertTrue(board.requestLoggingSet(true, false, false));
    output.flush();
    assertTrue(buffer.toString("UTF-8").contains("readboardLoggingSet "));
  }

  @Test
  void setAcknowledgementTimeoutLeavesUnknownNotSuccess() throws Exception {
    ReadBoard board = allocateBoard();
    ReadBoardLoggingControl control =
        new ReadBoardLoggingControl(ReadBoardLoggingControl.Desired.launchDefaults(false), true);
    setField(board, "loggingControl", control);
    control.onCapability(ReadBoardLoggingProtocol.tryParseCapability(CAPABILITY));
    control.beginSet(true, false, false);
    board.markCapabilityTimeout();
    assertEquals(ReadBoardLoggingControl.Status.UNKNOWN, control.status());
    assertEquals(ReadBoardLoggingControl.Presentation.UNKNOWN, control.presentation(true, ReadBoardLoggingProtocol.Toggle.ON, ReadBoardLoggingProtocol.Persistence.HEALTHY));
  }

  @Test
  void loggingTimeoutDoesNotUseBlockedReaderExecutor() throws Exception {
    ReadBoard board = allocateBoard();
    ReadBoardLoggingControl control =
        new ReadBoardLoggingControl(ReadBoardLoggingControl.Desired.launchDefaults(false), true);
    setField(board, "loggingControl", control);
    setField(board, "usePipe", true);
    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    setField(board, "outputStream", new BufferedOutputStream(buffer));
    ScheduledExecutorService reader = Executors.newSingleThreadScheduledExecutor();
    setField(board, "executor", reader);
    CountDownLatch block = new CountDownLatch(1);
    reader.execute(
        () -> {
          try {
            block.await();
          } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
          }
        });
    board.parseLine(CAPABILITY);
    assertTrue(board.requestLoggingSet(true, false, false));
    ScheduledExecutorService timeoutExecutor =
        (ScheduledExecutorService) getField(board, "loggingTimeoutExecutor");
    assertTrue(timeoutExecutor != null);
    assertTrue(timeoutExecutor != reader);
    block.countDown();
    reader.shutdownNow();
    timeoutExecutor.shutdownNow();
  }

  private static ReadBoard allocateBoard() throws Exception {
    Field field = Unsafe.class.getDeclaredField("theUnsafe");
    field.setAccessible(true);
    Unsafe unsafe = (Unsafe) field.get(null);
    return (ReadBoard) unsafe.allocateInstance(ReadBoard.class);
  }

  private static void setField(Object target, String name, Object value) throws Exception {
    Field field = ReadBoard.class.getDeclaredField(name);
    field.setAccessible(true);
    field.set(target, value);
  }

  private static Object getField(Object target, String name) throws Exception {
    Field field = ReadBoard.class.getDeclaredField(name);
    field.setAccessible(true);
    return field.get(target);
  }
}

