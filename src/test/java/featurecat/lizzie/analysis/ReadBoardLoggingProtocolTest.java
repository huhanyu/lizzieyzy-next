package featurecat.lizzie.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ReadBoardLoggingProtocolTest {
  private static final String PROCESS_SESSION_ID = "dGVzdFByb2Nlc3NJRA";
  private static final String HOST_SESSION_ID = "dGVzdEhvc3RTZXNzaW9u";
  private static final String REQUEST_ONE = "cmVxdWVzdDE";
  private static final String REQUEST_TWO = "cmVxdWVzdDI";
  private static final String CAPABILITY_LINE =
      "readboardLoggingV1 dGVzdFByb2Nlc3NJRA on off off healthy 0";
  private static final String SET_LINE = "readboardLoggingSet cmVxdWVzdDE on off on";
  private static final String OBSERVED_LINE =
      "readboardLoggingObserved cmVxdWVzdDE dGVzdFByb2Nlc3NJRA on off on degraded 3 writer-fault";

  @Test
  void capabilitySetAndObservedRoundTripIdenticalFields() {
    ReadBoardLoggingProtocol.Capability capability =
        ReadBoardLoggingProtocol.tryParseCapability(CAPABILITY_LINE);
    assertNotNull(capability);
    assertEquals(PROCESS_SESSION_ID, capability.processSessionId);
    assertEquals(ReadBoardLoggingProtocol.Toggle.ON, capability.diagnostics);
    assertEquals(ReadBoardLoggingProtocol.Toggle.OFF, capability.capture);
    assertEquals(ReadBoardLoggingProtocol.Toggle.OFF, capability.trace);
    assertEquals(ReadBoardLoggingProtocol.Persistence.HEALTHY, capability.persistence);
    assertEquals(0, capability.dropCount);
    assertEquals(CAPABILITY_LINE, ReadBoardLoggingProtocol.formatCapability(capability));

    ReadBoardLoggingProtocol.SetRequest set = ReadBoardLoggingProtocol.tryParseSet(SET_LINE);
    assertNotNull(set);
    assertEquals(REQUEST_ONE, set.requestId);
    assertEquals(ReadBoardLoggingProtocol.Toggle.ON, set.diagnostics);
    assertEquals(ReadBoardLoggingProtocol.Toggle.OFF, set.capture);
    assertEquals(ReadBoardLoggingProtocol.Toggle.ON, set.trace);
    assertEquals(SET_LINE, ReadBoardLoggingProtocol.formatSet(set));

    ReadBoardLoggingProtocol.Observed observed =
        ReadBoardLoggingProtocol.tryParseObserved(OBSERVED_LINE);
    assertNotNull(observed);
    assertEquals(REQUEST_ONE, observed.requestId);
    assertEquals(PROCESS_SESSION_ID, observed.processSessionId);
    assertEquals(ReadBoardLoggingProtocol.Toggle.ON, observed.diagnostics);
    assertEquals(ReadBoardLoggingProtocol.Toggle.OFF, observed.capture);
    assertEquals(ReadBoardLoggingProtocol.Toggle.ON, observed.trace);
    assertEquals(ReadBoardLoggingProtocol.Persistence.DEGRADED, observed.persistence);
    assertEquals(3, observed.dropCount);
    assertEquals(ReadBoardLoggingProtocol.Reason.WRITER_FAULT, observed.reason);
    assertEquals(OBSERVED_LINE, ReadBoardLoggingProtocol.formatObserved(observed));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "readboardLoggingV1 dGVzdFByb2Nlc3NJRA on off off healthy",
        "readboardLoggingV1 dGVzdFByb2Nlc3NJRA unknown off off healthy 0",
        "readboardLoggingSet cmVxdWVzdDE on off",
        "readboardLoggingSet cmVxdWVzdDE unknown off off",
        "readboardLoggingObserved cmVxdWVzdDE dGVzdFByb2Nlc3NJRA on off off healthy 0",
        "readboardLoggingObserved cmVxdWVzdDE dGVzdFByb2Nlc3NJRA on off off healthy 0 java.io.IOException: C:\\x",
        "readboardLoggingObserved cmVxdWVzdDE dGVzdFByb2Nlc3NJRA on off off healthy -1 writer-fault"
      })
  void illegalLoggingLinesAreRejected(String line) {
    assertNull(ReadBoardLoggingProtocol.tryParseCapability(line));
    assertNull(ReadBoardLoggingProtocol.tryParseSet(line));
    assertNull(ReadBoardLoggingProtocol.tryParseObserved(line));
    assertTrue(ReadBoardLoggingProtocol.isControlLine(line));
  }

  @Test
  void reasonsNeverIncludeRawPathsOrExceptionText() {
    ReadBoardLoggingProtocol.Observed observed =
        new ReadBoardLoggingProtocol.Observed(
            REQUEST_ONE,
            PROCESS_SESSION_ID,
            ReadBoardLoggingProtocol.Toggle.ON,
            ReadBoardLoggingProtocol.Toggle.ON,
            ReadBoardLoggingProtocol.Toggle.OFF,
            ReadBoardLoggingProtocol.Persistence.UNAVAILABLE,
            0,
            ReadBoardLoggingProtocol.Reason.PATH_UNAVAILABLE);
    String line = ReadBoardLoggingProtocol.formatObserved(observed);
    assertEquals(
        "readboardLoggingObserved cmVxdWVzdDE dGVzdFByb2Nlc3NJRA on on off unavailable 0 path-unavailable",
        line);
    assertFalse(line.contains("C:\\"));
    assertFalse(line.contains("IOException"));
  }

  @Test
  void persistenceIsWorstWriterHealthAndDropCountIgnoresCapture() {
    assertEquals(
        ReadBoardLoggingProtocol.Persistence.UNAVAILABLE,
        ReadBoardLoggingProtocol.worstPersistence(
            ReadBoardLoggingProtocol.Persistence.HEALTHY,
            ReadBoardLoggingProtocol.Persistence.DEGRADED,
            ReadBoardLoggingProtocol.Persistence.UNAVAILABLE,
            ReadBoardLoggingProtocol.Persistence.HEALTHY));
    assertEquals(8, ReadBoardLoggingProtocol.combineDropCount(3, 5));
    assertEquals(3, ReadBoardLoggingProtocol.combineDropCount(3, 0));
  }

  @Test
  void newestRequestIdWinsAndTogglesApplyIndependently() {
    ReadBoardLoggingProtocol.SetRequest first =
        ReadBoardLoggingProtocol.tryParseSet("readboardLoggingSet cmVxdWVzdDE on off off");
    ReadBoardLoggingProtocol.SetRequest second =
        ReadBoardLoggingProtocol.tryParseSet("readboardLoggingSet cmVxdWVzdDI off on on");
    ReadBoardLoggingProtocol.Observed firstObserved =
        ReadBoardLoggingProtocol.applySetIndependently(
            first,
            PROCESS_SESSION_ID,
            ReadBoardLoggingProtocol.Persistence.HEALTHY,
            ReadBoardLoggingProtocol.Persistence.HEALTHY,
            ReadBoardLoggingProtocol.Persistence.HEALTHY,
            ReadBoardLoggingProtocol.Persistence.DEGRADED,
            1,
            2,
            ReadBoardLoggingProtocol.Reason.WRITER_FAULT);
    ReadBoardLoggingProtocol.Observed secondObserved =
        ReadBoardLoggingProtocol.applySetIndependently(
            second,
            PROCESS_SESSION_ID,
            ReadBoardLoggingProtocol.Persistence.HEALTHY,
            ReadBoardLoggingProtocol.Persistence.HEALTHY,
            ReadBoardLoggingProtocol.Persistence.HEALTHY,
            ReadBoardLoggingProtocol.Persistence.HEALTHY,
            1,
            2,
            ReadBoardLoggingProtocol.Reason.APPLIED);

    ReadBoardLoggingProtocol.RequestGate gate = new ReadBoardLoggingProtocol.RequestGate();
    gate.noteRequest(first.requestId);
    gate.noteRequest(second.requestId);

    assertFalse(gate.acceptObserved(firstObserved));
    assertTrue(gate.acceptObserved(secondObserved));
    assertEquals(REQUEST_TWO, gate.latestRequestId());
    assertEquals(ReadBoardLoggingProtocol.Toggle.OFF, secondObserved.diagnostics);
    assertEquals(ReadBoardLoggingProtocol.Toggle.ON, secondObserved.capture);
    assertEquals(ReadBoardLoggingProtocol.Toggle.ON, secondObserved.trace);
    assertEquals(ReadBoardLoggingProtocol.Toggle.ON, firstObserved.diagnostics);
    assertEquals(ReadBoardLoggingProtocol.Persistence.DEGRADED, firstObserved.persistence);
    assertEquals(3, firstObserved.dropCount);
  }

  @Test
  void appendLaunchArgumentsKeepsSevenPositionalsAndAddsNamedSuffix() {
    List<String> positional = Arrays.asList("yzy", "10", "20", "policy", "1", "en", "9527");
    List<String> args =
        ReadBoardLoggingProtocol.appendLaunchArguments(
            positional, "C:\\work\\logs\\readboard", HOST_SESSION_ID, false, false);

    assertEquals(positional, args.subList(0, 7));
    assertEquals(
        Arrays.asList(
            "--log-dir",
            "C:\\work\\logs\\readboard",
            "--host-session-id",
            HOST_SESSION_ID,
            "--logging-contract",
            "1",
            "--diagnostics",
            "off",
            "--capture",
            "off"),
        args.subList(7, args.size()));
  }

  @Test
  void appendLaunchArgumentsRejectsRelativeLogDir() {
    IllegalArgumentException error =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                ReadBoardLoggingProtocol.appendLaunchArguments(
                    Arrays.asList("yzy", "10", "20", "policy", "1", "en", "9527"),
                    "logs\\readboard",
                    HOST_SESSION_ID,
                    false,
                    false));
    assertEquals("invalid-request", error.getMessage());
  }

  @Test
  void privacyTokensRoundTripAndRejectUnknown() {
    assertEquals(
        ReadBoardLoggingProtocol.Privacy.SAFE,
        ReadBoardLoggingProtocol.tryParsePrivacy("safe"));
    assertEquals("localPath", ReadBoardLoggingProtocol.formatPrivacy(ReadBoardLoggingProtocol.Privacy.LOCAL_PATH));
    assertEquals(
        ReadBoardLoggingProtocol.Privacy.LOCAL_URL,
        ReadBoardLoggingProtocol.tryParsePrivacy("localUrl"));
    assertEquals(
        ReadBoardLoggingProtocol.Privacy.USER_TEXT,
        ReadBoardLoggingProtocol.tryParsePrivacy("userText"));
    assertEquals(
        ReadBoardLoggingProtocol.Privacy.SESSION_ID,
        ReadBoardLoggingProtocol.tryParsePrivacy("sessionId"));
    assertEquals(
        ReadBoardLoggingProtocol.Privacy.SECRET,
        ReadBoardLoggingProtocol.tryParsePrivacy("secret"));
    assertNull(ReadBoardLoggingProtocol.tryParsePrivacy("SAFE"));
    assertNull(ReadBoardLoggingProtocol.tryParsePrivacy("path"));
  }

  @Test
  void appendLaunchArgumentsAcceptsUncShareAndRejectsRootRelative() {
    List<String> positional = Arrays.asList("yzy", "10", "20", "policy", "1", "en", "9527");
    List<String> args =
        ReadBoardLoggingProtocol.appendLaunchArguments(
            positional, "\\\\server\\share\\logs\\readboard", HOST_SESSION_ID, false, false);
    assertEquals("\\\\server\\share\\logs\\readboard", args.get(8));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            ReadBoardLoggingProtocol.appendLaunchArguments(
                positional, "/tmp/logs/readboard", HOST_SESSION_ID, false, false));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            ReadBoardLoggingProtocol.appendLaunchArguments(
                positional, "\\\\server", HOST_SESSION_ID, false, false));
  }

  @Test
  void oldHostShapeHasNoLoggingSuffix() {
    List<String> oldHost = Arrays.asList("yzy", "10", "20", "policy", "1", "en", "9527");
    assertEquals(7, oldHost.size());
    assertFalse(oldHost.contains("--log-dir"));
    assertFalse(oldHost.contains("--logging-contract"));
  }
}
