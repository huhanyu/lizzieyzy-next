package featurecat.lizzie.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReadBoardLoggingControlTest {
  private static final String PROCESS_SESSION = "dGVzdFByb2Nlc3NJRA";
  private static final String HOST_SESSION = "dGVzdEhvc3RTZXNzaW9u";
  private static final String CAPABILITY =
      "readboardLoggingV1 dGVzdFByb2Nlc3NJRA on off off healthy 0";
  private static final String OBSERVED =
      "readboardLoggingObserved cmVxdWVzdDE dGVzdFByb2Nlc3NJRA on off off healthy 0 applied";

  @Test
  void appendNamedArgumentsKeepsPositionalsAndForcesCaptureOff() {
    List<String> positional = Arrays.asList("yzy", "10", "20", "policy", "0", "cn", "-1");
    List<String> args =
        ReadBoardLoggingControl.appendNamedLoggingArguments(
            positional, "C:\\work\\logs\\readboard", HOST_SESSION, true);

    assertEquals(positional, args.subList(0, 7));
    assertEquals("--log-dir", args.get(7));
    assertEquals("C:\\work\\logs\\readboard", args.get(8));
    assertEquals("--host-session-id", args.get(9));
    assertEquals(HOST_SESSION, args.get(10));
    assertEquals("--logging-contract", args.get(11));
    assertEquals("1", args.get(12));
    assertEquals("--diagnostics", args.get(13));
    assertEquals("on", args.get(14));
    assertEquals("--capture", args.get(15));
    assertEquals("off", args.get(16));
  }

  @Test
  void appendNamedArgumentsSkipsInvalidUnixOrRelativePath() {
    List<String> positional = Arrays.asList("yzy", " ", " ", " ", "0", "cn", "-1");
    List<String> unix =
        ReadBoardLoggingControl.appendNamedLoggingArguments(
            positional, "/tmp/logs/readboard", HOST_SESSION, true);
    List<String> relative =
        ReadBoardLoggingControl.appendNamedLoggingArguments(
            positional, "logs\\readboard", HOST_SESSION, true);

    assertEquals(positional, unix);
    assertEquals(positional, relative);
  }

  @Test
  void readBoardLogDirectoryIsLogsReadboardChild() {
    Path logs = Path.of("work", "logs");
    assertEquals(
        logs.resolve("readboard").toAbsolutePath().toString(),
        ReadBoardLoggingControl.readBoardLogDirectory(logs));
  }

  @Test
  void capabilityEstablishesProcessSessionAndInitialObserved() {
    ReadBoardLoggingControl control =
        new ReadBoardLoggingControl(ReadBoardLoggingControl.Desired.launchDefaults(true), true);

    control.onReady();
    control.onCapability(ReadBoardLoggingProtocol.tryParseCapability(CAPABILITY));

    assertEquals(ReadBoardLoggingControl.Status.CAPABILITY_READY, control.status());
    assertEquals(PROCESS_SESSION, control.processSessionId());
    assertEquals(ReadBoardLoggingProtocol.Toggle.ON, control.observed().diagnostics);
    assertEquals(ReadBoardLoggingProtocol.Toggle.OFF, control.observed().capture);
    assertEquals(ReadBoardLoggingProtocol.Toggle.OFF, control.observed().trace);
  }

  @Test
  void newestRequestIdWinsAndLateAckIsIgnored() {
    ReadBoardLoggingControl control =
        new ReadBoardLoggingControl(ReadBoardLoggingControl.Desired.launchDefaults(false), true);
    control.onCapability(ReadBoardLoggingProtocol.tryParseCapability(CAPABILITY));

    ReadBoardLoggingProtocol.SetRequest first = control.beginSet(true, false, false);
    ReadBoardLoggingProtocol.SetRequest second = control.beginSet(false, false, true);
    assertNotEquals(first.requestId, second.requestId);

    ReadBoardLoggingProtocol.Observed stale =
        new ReadBoardLoggingProtocol.Observed(
            first.requestId,
            PROCESS_SESSION,
            ReadBoardLoggingProtocol.Toggle.ON,
            ReadBoardLoggingProtocol.Toggle.OFF,
            ReadBoardLoggingProtocol.Toggle.OFF,
            ReadBoardLoggingProtocol.Persistence.HEALTHY,
            0,
            ReadBoardLoggingProtocol.Reason.APPLIED);
    ReadBoardLoggingProtocol.Observed current =
        new ReadBoardLoggingProtocol.Observed(
            second.requestId,
            PROCESS_SESSION,
            ReadBoardLoggingProtocol.Toggle.OFF,
            ReadBoardLoggingProtocol.Toggle.OFF,
            ReadBoardLoggingProtocol.Toggle.ON,
            ReadBoardLoggingProtocol.Persistence.HEALTHY,
            0,
            ReadBoardLoggingProtocol.Reason.APPLIED);

    control.onObserved(stale);
    assertEquals(ReadBoardLoggingControl.Status.APPLYING, control.status());
    control.onObserved(current);

    assertEquals(ReadBoardLoggingControl.Status.OBSERVED, control.status());
    assertEquals(ReadBoardLoggingProtocol.Toggle.ON, control.observed().trace);
    assertEquals(ReadBoardLoggingProtocol.Toggle.OFF, control.observed().diagnostics);
    assertFalse(control.desired().capture);
    assertTrue(control.desired().trace);
  }

  @Test
  void resetForNewProcessClearsObservedAndDoesNotReplayCaptureOrTrace() {
    ReadBoardLoggingControl control =
        new ReadBoardLoggingControl(ReadBoardLoggingControl.Desired.launchDefaults(true), true);
    control.onCapability(ReadBoardLoggingProtocol.tryParseCapability(CAPABILITY));
    control.beginSet(true, true, true);
    control.onObserved(ReadBoardLoggingProtocol.tryParseObserved(OBSERVED));

    control.resetForNewProcess();

    assertEquals(ReadBoardLoggingControl.Status.STARTING, control.status());
    assertNull(control.processSessionId());
    assertNull(control.observed());
    assertTrue(control.desired().diagnostics);
    assertFalse(control.desired().capture);
    assertFalse(control.desired().trace);
  }

  @Test
  void timeoutAndDisconnectAreUnknownNotSuccess() {
    ReadBoardLoggingControl contract =
        new ReadBoardLoggingControl(ReadBoardLoggingControl.Desired.launchDefaults(false), true);
    contract.onReady();
    contract.onCapabilityTimeout();
    assertEquals(ReadBoardLoggingControl.Status.UNKNOWN, contract.status());
    assertNull(contract.observed());
    assertEquals(
        ReadBoardLoggingControl.Presentation.UNKNOWN,
        contract.presentation(
            true,
            ReadBoardLoggingProtocol.Toggle.UNKNOWN,
            ReadBoardLoggingProtocol.Persistence.HEALTHY));

    ReadBoardLoggingControl afterReady =
        new ReadBoardLoggingControl(ReadBoardLoggingControl.Desired.launchDefaults(false), true);
    afterReady.onCapability(ReadBoardLoggingProtocol.tryParseCapability(CAPABILITY));
    afterReady.onDisconnect();
    assertEquals(ReadBoardLoggingControl.Status.UNKNOWN, afterReady.status());
    assertNull(afterReady.observed());
  }

  @Test
  void oldHelperReadyWithoutCapabilityIsLegacyUnconfirmed() {
    ReadBoardLoggingControl control =
        new ReadBoardLoggingControl(ReadBoardLoggingControl.Desired.launchDefaults(false), false);
    control.onReady();
    control.onCapabilityTimeout();

    assertEquals(ReadBoardLoggingControl.Status.LEGACY_UNCONFIRMED, control.status());
    assertEquals(
        ReadBoardLoggingControl.Presentation.LEGACY_UNCONFIRMED,
        control.presentation(
            true,
            ReadBoardLoggingProtocol.Toggle.OFF,
            ReadBoardLoggingProtocol.Persistence.HEALTHY));
  }

  @Test
  void desiredOnObservedOffIsNotAppliedAndDegradedIsNotUnknown() {
    ReadBoardLoggingControl control =
        new ReadBoardLoggingControl(ReadBoardLoggingControl.Desired.launchDefaults(true), true);
    control.onCapability(ReadBoardLoggingProtocol.tryParseCapability(CAPABILITY));

    assertEquals(
        ReadBoardLoggingControl.Presentation.NOT_APPLIED,
        control.presentation(
            true,
            ReadBoardLoggingProtocol.Toggle.OFF,
            ReadBoardLoggingProtocol.Persistence.HEALTHY));
    assertEquals(
        ReadBoardLoggingControl.Presentation.ON_STORAGE_DEGRADED,
        control.presentation(
            true,
            ReadBoardLoggingProtocol.Toggle.ON,
            ReadBoardLoggingProtocol.Persistence.DEGRADED));
    assertEquals(
        ReadBoardLoggingControl.Presentation.ON_STORAGE_UNAVAILABLE,
        control.presentation(
            true,
            ReadBoardLoggingProtocol.Toggle.ON,
            ReadBoardLoggingProtocol.Persistence.UNAVAILABLE));
    assertNotEquals(
        ReadBoardLoggingControl.Presentation.UNKNOWN,
        control.presentation(
            true,
            ReadBoardLoggingProtocol.Toggle.ON,
            ReadBoardLoggingProtocol.Persistence.UNAVAILABLE));
  }

  @Test
  void beginSetFormatsFrozenWireAndIsIdempotent() {
    ReadBoardLoggingControl control =
        new ReadBoardLoggingControl(ReadBoardLoggingControl.Desired.launchDefaults(false), true);
    ReadBoardLoggingProtocol.SetRequest first = control.beginSet(true, false, false);
    ReadBoardLoggingProtocol.SetRequest second = control.beginSet(true, false, false);

    assertTrue(ReadBoardLoggingProtocol.isOpaqueId(first.requestId));
    assertEquals(
        "readboardLoggingSet " + first.requestId + " on off off",
        ReadBoardLoggingProtocol.formatSet(first));
    assertEquals(ReadBoardLoggingProtocol.Toggle.ON, second.diagnostics);
    assertEquals(ReadBoardLoggingProtocol.Toggle.OFF, second.capture);
    assertEquals(ReadBoardLoggingProtocol.Toggle.OFF, second.trace);
    assertNotNull(second.requestId);
  }
}
