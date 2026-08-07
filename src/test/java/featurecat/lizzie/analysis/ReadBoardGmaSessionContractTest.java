package featurecat.lizzie.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Deterministic contract tests for the narrow ReadBoard GMA session module. They observe state
 * transitions and ordered effects only — never field counts, private flags, or source shape.
 */
class ReadBoardGmaSessionContractTest {
  private static final Object INCARNATION = new Object();

  @Test
  void sessionStartsInPreparingWithHelperAndReleaseCapabilities() {
    RecordingPorts ports = new RecordingPorts();
    ReadBoardGmaSession session = createSession(ports);

    assertInstanceOf(ReadBoardGmaSession.Preparing.class, session.state());
    assertFalse(session.retired());
    assertNotNull(session.helperCapability());
    assertTrue(ports.calls.isEmpty());
  }

  @Test
  void admitGmaTransitionsToGmaInFlightAndIssuesTerminalCapability() {
    RecordingPorts ports = new RecordingPorts();
    ReadBoardGmaSession session = createSession(ports);
    Object intent = new Object();

    ReadBoardGmaSession.GmaTerminalCapability capability =
        session.admitGma(
            session.helperCapability(), intent, ReadBoardGmaSession.RuntimeSnapshot.empty());

    ReadBoardGmaSession.GmaInFlight gma =
        assertInstanceOf(ReadBoardGmaSession.GmaInFlight.class, session.state());
    assertSame(intent, gma.authoritativeRestoreIntent());
    assertFalse(gma.authorization().invalidated());
    assertEquals(0, capability.attempt());
    assertTrue(ports.calls.isEmpty());
  }

  @Test
  void admitGmaFailsFastWithoutRestoreIntentOrRuntimeSnapshot() {
    ReadBoardGmaSession session = createSession(new RecordingPorts());
    ReadBoardGmaSession.HelperCapability helper = session.helperCapability();

    assertThrows(
        NullPointerException.class,
        () -> session.admitGma(helper, null, ReadBoardGmaSession.RuntimeSnapshot.empty()));
    assertThrows(NullPointerException.class, () -> session.admitGma(helper, new Object(), null));
    assertInstanceOf(ReadBoardGmaSession.Preparing.class, session.state());
  }

  @Test
  void admitGmaIsAbsorbedAfterCancellationOrWhenAlreadyAdmitted() {
    RecordingPorts ports = new RecordingPorts();
    ReadBoardGmaSession session = createSession(ports);
    ReadBoardGmaSession.HelperCapability helper = session.helperCapability();
    Object intent = new Object();

    session.retire(helper);
    assertNull(session.admitGma(helper, intent, ReadBoardGmaSession.RuntimeSnapshot.empty()));
    assertEquals(
        ReadBoardGmaSession.SessionOutcome.CANCELLED_NO_EFFECT, terminalOf(session).outcome());

    ReadBoardGmaSession second = createSession(new RecordingPorts());
    ReadBoardGmaSession.HelperCapability secondHelper = second.helperCapability();
    assertNotNull(
        second.admitGma(secondHelper, intent, ReadBoardGmaSession.RuntimeSnapshot.empty()));
    assertNull(second.admitGma(secondHelper, intent, ReadBoardGmaSession.RuntimeSnapshot.empty()));
    assertInstanceOf(ReadBoardGmaSession.GmaInFlight.class, second.state());
  }

  @Test
  void updateRestoreIntentIsLatestWinsInsideGmaInFlightOnly() {
    RecordingPorts ports = new RecordingPorts();
    ReadBoardGmaSession session = createSession(ports);
    ReadBoardGmaSession.HelperCapability helper = session.helperCapability();
    Object firstIntent = new Object();
    Object latestIntent = new Object();
    ReadBoardGmaSession.GmaTerminalCapability terminalCapability =
        session.admitGma(helper, firstIntent, ReadBoardGmaSession.RuntimeSnapshot.empty());

    session.updateRestoreIntent(helper, latestIntent);

    ReadBoardGmaSession.GmaInFlight gma =
        assertInstanceOf(ReadBoardGmaSession.GmaInFlight.class, session.state());
    assertSame(latestIntent, gma.authoritativeRestoreIntent());
    assertTrue(ports.calls.isEmpty());

    // After retirement the intent freezes; the session-owned terminal capability is not revoked.
    session.retire(helper);
    session.updateRestoreIntent(helper, new Object());
    assertSame(
        latestIntent,
        assertInstanceOf(ReadBoardGmaSession.GmaInFlight.class, session.state())
            .authoritativeRestoreIntent());

    // The frozen latest intent is what the exact participant captures at terminal.
    session.consumeGmaTerminal(terminalCapability, ReadBoardGmaSession.GmaTerminal.PLAYED);
    assertSame(
        latestIntent,
        assertInstanceOf(ReadBoardGmaSession.RestoringExact.class, session.state())
            .capturedExactOperation()
            .restoreIntent());
  }

  @Test
  void invalidateAuthorizationIsStickyAndEffectFree() {
    RecordingPorts ports = new RecordingPorts();
    ReadBoardGmaSession session = createSession(ports);
    ReadBoardGmaSession.HelperCapability helper = session.helperCapability();
    Object intent = new Object();
    ReadBoardGmaSession.GmaTerminalCapability terminalCapability =
        session.admitGma(helper, intent, nonEmptySnapshot());

    session.invalidateAuthorization(helper);
    session.invalidateAuthorization(helper);

    ReadBoardGmaSession.GmaInFlight gma =
        assertInstanceOf(ReadBoardGmaSession.GmaInFlight.class, session.state());
    assertTrue(gma.authorization().invalidated());
    assertTrue(ports.calls.isEmpty());

    // The physical request keeps converging; success still publishes, continues, and releases.
    session.consumeGmaTerminal(terminalCapability, ReadBoardGmaSession.GmaTerminal.PLAYED);
    session.completeExact(
        ports.exactStarts.get(0), new ReadBoardGmaSession.ParticipantResult.Succeeded());
    session.completeRuntime(
        ports.runtimeStarts.get(0), new ReadBoardGmaSession.ParticipantResult.Succeeded());
    assertEquals(ReadBoardGmaSession.SessionOutcome.SUCCEEDED, terminalOf(session).outcome());
    assertEquals(1, ports.continuations);
    assertEquals(1, ports.publications.size());
    assertEquals(1, ports.releases.size());
  }

  @ParameterizedTest
  @EnumSource(ReadBoardGmaSession.GmaTerminal.class)
  void everyGmaTerminalVariantRunsTheFullExactThenRuntimeRecoveryContract(
      ReadBoardGmaSession.GmaTerminal terminal) {
    RecordingPorts ports = new RecordingPorts();
    ReadBoardGmaSession session = createSession(ports);
    Object intent = new Object();
    ReadBoardGmaSession.GmaTerminalCapability terminalCapability =
        session.admitGma(session.helperCapability(), intent, nonEmptySnapshot());
    ReadBoardGmaSession.GmaAuthorization authorization =
        ((ReadBoardGmaSession.GmaInFlight) session.state()).authorization();

    session.consumeGmaTerminal(terminalCapability, terminal);

    // The logical placement authorization expires when the GMA terminal line is consumed.
    assertTrue(authorization.invalidated());
    ReadBoardGmaSession.RestoringExact exact =
        assertInstanceOf(ReadBoardGmaSession.RestoringExact.class, session.state());
    assertSame(intent, exact.capturedExactOperation().restoreIntent());
    assertEquals(1, ports.exactStarts.size());
    assertEquals(1, ports.exactStarts.get(0).attempt());

    session.completeExact(
        ports.exactStarts.get(0), new ReadBoardGmaSession.ParticipantResult.Succeeded());
    assertInstanceOf(ReadBoardGmaSession.RestoringRuntime.class, session.state());
    assertEquals(1, ports.runtimeStarts.size());
    assertEquals(2, ports.runtimeStarts.get(0).attempt());

    session.completeRuntime(
        ports.runtimeStarts.get(0), new ReadBoardGmaSession.ParticipantResult.Succeeded());
    assertEquals(ReadBoardGmaSession.SessionOutcome.SUCCEEDED, terminalOf(session).outcome());
    assertEquals(
        List.of(
            "startExact",
            "startRuntime",
            "publishTerminal",
            "continueNormal",
            "requestReservationRelease"),
        ports.calls);
    assertEquals(1, ports.publications.size());
    assertEquals(1, ports.releases.size());
  }

  @Test
  void duplicateGmaTerminalIsAbsorbedAndStartsExactOnce() {
    RecordingPorts ports = new RecordingPorts();
    ReadBoardGmaSession session = createSession(ports);
    ReadBoardGmaSession.GmaTerminalCapability terminalCapability =
        session.admitGma(
            session.helperCapability(), new Object(), ReadBoardGmaSession.RuntimeSnapshot.empty());

    session.consumeGmaTerminal(terminalCapability, ReadBoardGmaSession.GmaTerminal.PLAYED);
    session.consumeGmaTerminal(terminalCapability, ReadBoardGmaSession.GmaTerminal.PLAYED);

    assertEquals(1, ports.exactStarts.size());
    assertEquals(List.of("startExact"), ports.calls);
  }

  @Test
  void gmaTerminalFromAnotherSessionOrIncarnationIsAbsorbedWithZeroEffects() {
    RecordingPorts ports = new RecordingPorts();
    ReadBoardGmaSession session = createSession(ports);
    ReadBoardGmaSession.GmaTerminalCapability terminalCapability =
        session.admitGma(
            session.helperCapability(), new Object(), ReadBoardGmaSession.RuntimeSnapshot.empty());
    session.consumeGmaTerminal(terminalCapability, ReadBoardGmaSession.GmaTerminal.PLAYED);
    assertEquals(1, ports.exactStarts.size());

    // Another session with the same engine incarnation: identity guard fires.
    ReadBoardGmaSession twin = createSession(INCARNATION, new RecordingPorts());
    ReadBoardGmaSession.GmaTerminalCapability twinTerminalCapability =
        twin.admitGma(
            twin.helperCapability(), new Object(), ReadBoardGmaSession.RuntimeSnapshot.empty());
    twin.consumeGmaTerminal(twinTerminalCapability, ReadBoardGmaSession.GmaTerminal.PASS);
    session.consumeGmaTerminal(twinTerminalCapability, ReadBoardGmaSession.GmaTerminal.PLAYED);

    // Another session with a different engine incarnation: incarnation guard fires too.
    ReadBoardGmaSession foreign = createSession(new Object(), new RecordingPorts());
    ReadBoardGmaSession.GmaTerminalCapability foreignTerminalCapability =
        foreign.admitGma(
            foreign.helperCapability(), new Object(), ReadBoardGmaSession.RuntimeSnapshot.empty());
    foreign.consumeGmaTerminal(foreignTerminalCapability, ReadBoardGmaSession.GmaTerminal.RESIGN);
    session.consumeGmaTerminal(foreignTerminalCapability, ReadBoardGmaSession.GmaTerminal.PLAYED);

    assertInstanceOf(ReadBoardGmaSession.RestoringExact.class, session.state());
    assertEquals(1, ports.exactStarts.size());
    assertEquals(List.of("startExact"), ports.calls);
  }

  @Test
  void retirementDuringGmaInFlightKeepsConvergingAndBlocksHelperEvents() {
    RecordingPorts ports = new RecordingPorts();
    ReadBoardGmaSession session = createSession(ports);
    ReadBoardGmaSession.HelperCapability helper = session.helperCapability();
    Object intent = new Object();
    ReadBoardGmaSession.GmaTerminalCapability terminalCapability =
        session.admitGma(helper, intent, ReadBoardGmaSession.RuntimeSnapshot.empty());
    ReadBoardGmaSession.GmaAuthorization authorization =
        ((ReadBoardGmaSession.GmaInFlight) session.state()).authorization();

    session.retire(helper);
    session.retire(helper);

    assertTrue(session.retired());
    assertTrue(authorization.invalidated());
    assertInstanceOf(ReadBoardGmaSession.GmaInFlight.class, session.state());
    assertTrue(ports.calls.isEmpty());
    // The helper capability is revoked: no new admission, no new intent capture.
    assertNull(session.admitGma(helper, new Object(), ReadBoardGmaSession.RuntimeSnapshot.empty()));
    session.updateRestoreIntent(helper, new Object());
    assertSame(
        intent,
        assertInstanceOf(ReadBoardGmaSession.GmaInFlight.class, session.state())
            .authoritativeRestoreIntent());
    // The session-owned terminal capability is not revoked: exact restore still starts.
    session.consumeGmaTerminal(terminalCapability, ReadBoardGmaSession.GmaTerminal.PLAYED);
    assertInstanceOf(ReadBoardGmaSession.RestoringExact.class, session.state());
    assertEquals(1, ports.exactStarts.size());
  }

  @Test
  void exactSuccessStartsRuntimeExactlyOnceWithCapturedSnapshot() {
    RecordingPorts ports = new RecordingPorts();
    ReadBoardGmaSession session = createSession(ports);
    Object intent = new Object();
    Object latestIntent = new Object();
    List<Object> capturedParams = new ArrayList<>();
    capturedParams.add(new Object());
    ReadBoardGmaSession.RuntimeSnapshot snapshot =
        ReadBoardGmaSession.RuntimeSnapshot.of(capturedParams);
    ReadBoardGmaSession.GmaTerminalCapability terminalCapability =
        session.admitGma(session.helperCapability(), intent, snapshot);
    session.updateRestoreIntent(session.helperCapability(), latestIntent);

    session.consumeGmaTerminal(terminalCapability, ReadBoardGmaSession.GmaTerminal.PLAYED);
    session.completeExact(
        ports.exactStarts.get(0), new ReadBoardGmaSession.ParticipantResult.Succeeded());

    ReadBoardGmaSession.RestoringRuntime runtime =
        assertInstanceOf(ReadBoardGmaSession.RestoringRuntime.class, session.state());
    assertSame(snapshot, runtime.capturedRuntimeSnapshot());
    assertSame(latestIntent, ports.exactIntents.get(0));
    assertEquals(List.of("startExact", "startRuntime"), ports.calls);
    assertEquals(1, ports.runtimeStarts.size());
    assertEquals(2, ports.runtimeStarts.get(0).attempt());
    assertSame(snapshot, ports.runtimeSnapshots.get(0));
    assertSame(capturedParams.get(0), snapshot.parameters().get(0));
    assertTrue(ports.publications.isEmpty());
  }

  @Test
  void exactSuccessWithEmptyRuntimeSnapshotTerminatesSucceededImmediately() {
    RecordingPorts ports = new RecordingPorts();
    ReadBoardGmaSession session = createSession(ports);
    ReadBoardGmaSession.GmaTerminalCapability terminalCapability =
        session.admitGma(
            session.helperCapability(), new Object(), ReadBoardGmaSession.RuntimeSnapshot.empty());

    session.consumeGmaTerminal(terminalCapability, ReadBoardGmaSession.GmaTerminal.PLAYED);
    session.completeExact(
        ports.exactStarts.get(0), new ReadBoardGmaSession.ParticipantResult.Succeeded());

    assertEquals(ReadBoardGmaSession.SessionOutcome.SUCCEEDED, terminalOf(session).outcome());
    assertEquals(
        List.of("startExact", "publishTerminal", "continueNormal", "requestReservationRelease"),
        ports.calls);
    assertEquals(0, ports.runtimeStarts.size());
    assertEquals(1, ports.publications.size());
    assertEquals(1, ports.releases.size());
  }

  @Test
  void runtimeSuccessPublishesContinuesAndReleasesExactlyOnceInOrder() {
    RecordingPorts ports = new RecordingPorts();
    ReadBoardGmaSession session = createSession(ports);
    ReadBoardGmaSession.GmaTerminalCapability terminalCapability =
        session.admitGma(session.helperCapability(), new Object(), nonEmptySnapshot());

    session.consumeGmaTerminal(terminalCapability, ReadBoardGmaSession.GmaTerminal.PLAYED);
    session.completeExact(
        ports.exactStarts.get(0), new ReadBoardGmaSession.ParticipantResult.Succeeded());
    session.completeRuntime(
        ports.runtimeStarts.get(0), new ReadBoardGmaSession.ParticipantResult.Succeeded());

    assertEquals(ReadBoardGmaSession.SessionOutcome.SUCCEEDED, terminalOf(session).outcome());
    assertEquals(
        List.of(
            "startExact",
            "startRuntime",
            "publishTerminal",
            "continueNormal",
            "requestReservationRelease"),
        ports.calls);
    assertEquals(1, ports.publications.size());
    assertEquals(1, ports.continuations);
    assertEquals(1, ports.releases.size());
  }

  @Test
  void exactFailureLocksFirstFailureAndNeverStartsRuntime() {
    RecordingPorts ports = new RecordingPorts();
    ReadBoardGmaSession session = createSession(ports);
    ReadBoardGmaSession.GmaTerminalCapability terminalCapability =
        session.admitGma(session.helperCapability(), new Object(), nonEmptySnapshot());
    ReadBoardGmaSession.ParticipantFailure failure =
        new ReadBoardGmaSession.ParticipantFailure(
            ReadBoardGmaSession.FailureCategory.GTP_ERROR, INCARNATION, "loadsgf rejected");

    session.consumeGmaTerminal(terminalCapability, ReadBoardGmaSession.GmaTerminal.PLAYED);
    session.completeExact(
        ports.exactStarts.get(0), new ReadBoardGmaSession.ParticipantResult.Failed(failure));
    // Late success through the same capability is absorbed; the first failure stays locked.
    session.completeExact(
        ports.exactStarts.get(0), new ReadBoardGmaSession.ParticipantResult.Succeeded());

    ReadBoardGmaSession.Terminal terminal = terminalOf(session);
    assertEquals(ReadBoardGmaSession.SessionOutcome.FAILED, terminal.outcome());
    assertSame(failure, terminal.firstFailure());
    assertEquals(
        List.of("startExact", "publishTerminal", "handleFailure", "requestReservationRelease"),
        ports.calls);
    assertEquals(0, ports.runtimeStarts.size());
    assertEquals(0, ports.continuations);
    assertEquals(1, ports.releases.size());
    assertSame(failure, ports.failures.get(0));
  }

  @Test
  void runtimeFailureLocksFirstFailureAcrossAbsorbedLateEvents() {
    RecordingPorts ports = new RecordingPorts();
    ReadBoardGmaSession session = createSession(ports);
    ReadBoardGmaSession.GmaTerminalCapability terminalCapability =
        session.admitGma(session.helperCapability(), new Object(), nonEmptySnapshot());
    ReadBoardGmaSession.ParticipantFailure failure =
        new ReadBoardGmaSession.ParticipantFailure(
            ReadBoardGmaSession.FailureCategory.TIMEOUT, INCARNATION, "restore response timeout");

    session.consumeGmaTerminal(terminalCapability, ReadBoardGmaSession.GmaTerminal.PLAYED);
    session.completeExact(
        ports.exactStarts.get(0), new ReadBoardGmaSession.ParticipantResult.Succeeded());
    session.completeRuntime(
        ports.runtimeStarts.get(0), new ReadBoardGmaSession.ParticipantResult.Failed(failure));
    // Late duplicate success and the already-consumed exact capability cannot rewrite the lock.
    session.completeRuntime(
        ports.runtimeStarts.get(0), new ReadBoardGmaSession.ParticipantResult.Succeeded());
    session.completeExact(
        ports.exactStarts.get(0), new ReadBoardGmaSession.ParticipantResult.Succeeded());

    ReadBoardGmaSession.Terminal terminal = terminalOf(session);
    assertEquals(ReadBoardGmaSession.SessionOutcome.FAILED, terminal.outcome());
    assertSame(failure, terminal.firstFailure());
    assertEquals(1, ports.publications.size());
    assertEquals(1, ports.failures.size());
    assertEquals(1, ports.releases.size());
    assertEquals(0, ports.continuations);
  }

  @Test
  void preparingRetirementCancelsNoEffectWithoutQuarantineOrContinuation() {
    RecordingPorts ports = new RecordingPorts();
    ReadBoardGmaSession session = createSession(ports);

    session.retire(session.helperCapability());
    session.retire(session.helperCapability());

    ReadBoardGmaSession.Terminal terminal = terminalOf(session);
    assertEquals(ReadBoardGmaSession.SessionOutcome.CANCELLED_NO_EFFECT, terminal.outcome());
    assertNull(terminal.firstFailure());
    assertTrue(session.retired());
    assertEquals(List.of("publishTerminal", "requestReservationRelease"), ports.calls);
    assertEquals(0, ports.failures.size());
    assertEquals(0, ports.continuations);
    assertEquals(1, ports.releases.size());
  }

  @Test
  void retirementDuringExactOrRuntimeKeepsConvergingWithoutContinuation() {
    RecordingPorts exactPhasePorts = new RecordingPorts();
    ReadBoardGmaSession exactPhase = createSession(exactPhasePorts);
    ReadBoardGmaSession.GmaTerminalCapability exactPhaseTerminal =
        exactPhase.admitGma(exactPhase.helperCapability(), new Object(), nonEmptySnapshot());
    exactPhase.consumeGmaTerminal(exactPhaseTerminal, ReadBoardGmaSession.GmaTerminal.PLAYED);
    exactPhase.retire(exactPhase.helperCapability());
    assertInstanceOf(ReadBoardGmaSession.RestoringExact.class, exactPhase.state());
    assertEquals(List.of("startExact"), exactPhasePorts.calls);

    exactPhase.completeExact(
        exactPhasePorts.exactStarts.get(0), new ReadBoardGmaSession.ParticipantResult.Succeeded());
    exactPhase.completeRuntime(
        exactPhasePorts.runtimeStarts.get(0),
        new ReadBoardGmaSession.ParticipantResult.Succeeded());
    assertEquals(ReadBoardGmaSession.SessionOutcome.SUCCEEDED, terminalOf(exactPhase).outcome());
    assertEquals(
        List.of("startExact", "startRuntime", "publishTerminal", "requestReservationRelease"),
        exactPhasePorts.calls);
    assertEquals(0, exactPhasePorts.continuations);
    assertEquals(1, exactPhasePorts.releases.size());

    RecordingPorts runtimePhasePorts = new RecordingPorts();
    ReadBoardGmaSession runtimePhase = createSession(runtimePhasePorts);
    ReadBoardGmaSession.GmaTerminalCapability runtimePhaseTerminal =
        runtimePhase.admitGma(runtimePhase.helperCapability(), new Object(), nonEmptySnapshot());
    runtimePhase.consumeGmaTerminal(runtimePhaseTerminal, ReadBoardGmaSession.GmaTerminal.PLAYED);
    runtimePhase.completeExact(
        runtimePhasePorts.exactStarts.get(0),
        new ReadBoardGmaSession.ParticipantResult.Succeeded());
    runtimePhase.retire(runtimePhase.helperCapability());
    assertInstanceOf(ReadBoardGmaSession.RestoringRuntime.class, runtimePhase.state());

    runtimePhase.completeRuntime(
        runtimePhasePorts.runtimeStarts.get(0),
        new ReadBoardGmaSession.ParticipantResult.Succeeded());
    assertEquals(1, runtimePhasePorts.publications.size());
    assertEquals(1, runtimePhasePorts.releases.size());
    assertEquals(0, runtimePhasePorts.continuations);
  }

  @Test
  void retiredSessionFailureStillHandlesFailureAndReleasesExactlyOnce() {
    RecordingPorts ports = new RecordingPorts();
    ReadBoardGmaSession session = createSession(ports);
    ReadBoardGmaSession.GmaTerminalCapability terminalCapability =
        session.admitGma(session.helperCapability(), new Object(), nonEmptySnapshot());
    ReadBoardGmaSession.ParticipantFailure failure =
        new ReadBoardGmaSession.ParticipantFailure(
            ReadBoardGmaSession.FailureCategory.PROCESS_TERMINATED, INCARNATION, "engine exited");

    session.consumeGmaTerminal(terminalCapability, ReadBoardGmaSession.GmaTerminal.PLAYED);
    session.retire(session.helperCapability());
    session.completeExact(
        ports.exactStarts.get(0), new ReadBoardGmaSession.ParticipantResult.Failed(failure));

    ReadBoardGmaSession.Terminal terminal = terminalOf(session);
    assertEquals(ReadBoardGmaSession.SessionOutcome.FAILED, terminal.outcome());
    assertSame(failure, terminal.firstFailure());
    assertEquals(
        List.of("startExact", "publishTerminal", "handleFailure", "requestReservationRelease"),
        ports.calls);
    assertEquals(0, ports.continuations);
  }

  @Test
  void exactStartPortRejectionConvertsToTypedFailureAndFailCloses() {
    RejectingExactStartPorts ports = new RejectingExactStartPorts();
    ReadBoardGmaSession session = createSession(ports);
    ReadBoardGmaSession.GmaTerminalCapability terminalCapability =
        session.admitGma(session.helperCapability(), new Object(), nonEmptySnapshot());

    session.consumeGmaTerminal(terminalCapability, ReadBoardGmaSession.GmaTerminal.PLAYED);

    ReadBoardGmaSession.Terminal terminal = terminalOf(session);
    assertEquals(ReadBoardGmaSession.SessionOutcome.FAILED, terminal.outcome());
    assertEquals(
        ReadBoardGmaSession.FailureCategory.START_REJECTED, terminal.firstFailure().category());
    assertSame(INCARNATION, terminal.firstFailure().engineIncarnation());
    assertEquals(
        List.of("startExact", "publishTerminal", "handleFailure", "requestReservationRelease"),
        ports.calls);
    assertEquals(0, ports.runtimeStarts.size());
    assertEquals(0, ports.continuations);
    assertEquals(1, ports.publications.size());
    assertEquals(1, ports.releases.size());
  }

  @Test
  void runtimeStartPortRejectionConvertsToTypedFailureAndFailCloses() {
    RejectingRuntimeStartPorts ports = new RejectingRuntimeStartPorts();
    ReadBoardGmaSession session = createSession(ports);
    ReadBoardGmaSession.GmaTerminalCapability terminalCapability =
        session.admitGma(session.helperCapability(), new Object(), nonEmptySnapshot());

    session.consumeGmaTerminal(terminalCapability, ReadBoardGmaSession.GmaTerminal.PLAYED);
    session.completeExact(
        ports.exactStarts.get(0), new ReadBoardGmaSession.ParticipantResult.Succeeded());

    ReadBoardGmaSession.Terminal terminal = terminalOf(session);
    assertEquals(ReadBoardGmaSession.SessionOutcome.FAILED, terminal.outcome());
    assertEquals(
        ReadBoardGmaSession.FailureCategory.START_REJECTED, terminal.firstFailure().category());
    assertEquals(
        List.of(
            "startExact",
            "startRuntime",
            "publishTerminal",
            "handleFailure",
            "requestReservationRelease"),
        ports.calls);
    assertEquals(1, ports.runtimeStarts.size());
    assertEquals(1, ports.publications.size());
    assertEquals(1, ports.releases.size());
  }

  @Test
  void terminalAbsorbsEveryStaleEvent() {
    RecordingPorts ports = new RecordingPorts();
    ReadBoardGmaSession session = createSession(ports);
    ReadBoardGmaSession.GmaTerminalCapability terminalCapability =
        session.admitGma(session.helperCapability(), new Object(), nonEmptySnapshot());

    session.consumeGmaTerminal(terminalCapability, ReadBoardGmaSession.GmaTerminal.PLAYED);
    session.completeExact(
        ports.exactStarts.get(0), new ReadBoardGmaSession.ParticipantResult.Succeeded());
    ReadBoardGmaSession.RuntimeParticipantCapability runtimeCapability = ports.runtimeStarts.get(0);
    session.completeRuntime(
        runtimeCapability, new ReadBoardGmaSession.ParticipantResult.Succeeded());
    int callsAfterSuccess = ports.calls.size();

    session.consumeGmaTerminal(terminalCapability, ReadBoardGmaSession.GmaTerminal.PLAYED);
    session.completeExact(
        ports.exactStarts.get(0), new ReadBoardGmaSession.ParticipantResult.Succeeded());
    session.completeRuntime(
        runtimeCapability, new ReadBoardGmaSession.ParticipantResult.Succeeded());
    session.retire(session.helperCapability());
    session.updateRestoreIntent(session.helperCapability(), new Object());
    session.invalidateAuthorization(session.helperCapability());
    assertNull(
        session.admitGma(
            session.helperCapability(), new Object(), ReadBoardGmaSession.RuntimeSnapshot.empty()));

    assertEquals(ReadBoardGmaSession.SessionOutcome.SUCCEEDED, terminalOf(session).outcome());
    assertEquals(callsAfterSuccess, ports.calls.size());
    assertEquals(1, ports.publications.size());
    assertEquals(1, ports.releases.size());
    assertEquals(1, ports.continuations);
  }

  @Test
  void participantCapabilitiesArePhaseBoundAndAttemptBound() {
    RecordingPorts ports = new RecordingPorts();
    ReadBoardGmaSession session = createSession(ports);
    ReadBoardGmaSession.GmaTerminalCapability terminalCapability =
        session.admitGma(session.helperCapability(), new Object(), nonEmptySnapshot());
    assertEquals(0, terminalCapability.attempt());

    session.consumeGmaTerminal(terminalCapability, ReadBoardGmaSession.GmaTerminal.PLAYED);
    assertEquals(1, ports.exactStarts.get(0).attempt());
    session.completeExact(
        ports.exactStarts.get(0), new ReadBoardGmaSession.ParticipantResult.Succeeded());
    assertEquals(2, ports.runtimeStarts.get(0).attempt());
    assertInstanceOf(ReadBoardGmaSession.RestoringRuntime.class, session.state());

    // Cross-phase delivery is absorbed: the exact capability cannot act while RestoringRuntime.
    session.completeExact(
        ports.exactStarts.get(0),
        new ReadBoardGmaSession.ParticipantResult.Failed(
            new ReadBoardGmaSession.ParticipantFailure(
                ReadBoardGmaSession.FailureCategory.GTP_ERROR, INCARNATION, null)));
    assertInstanceOf(ReadBoardGmaSession.RestoringRuntime.class, session.state());
    assertEquals(List.of("startExact", "startRuntime"), ports.calls);

    // A runtime capability issued by another session cannot advance this session's exact phase.
    RecordingPorts otherPorts = new RecordingPorts();
    ReadBoardGmaSession other = createSession(otherPorts);
    ReadBoardGmaSession.GmaTerminalCapability otherTerminal =
        other.admitGma(other.helperCapability(), new Object(), nonEmptySnapshot());
    other.consumeGmaTerminal(otherTerminal, ReadBoardGmaSession.GmaTerminal.PLAYED);
    other.completeRuntime(
        ports.runtimeStarts.get(0), new ReadBoardGmaSession.ParticipantResult.Succeeded());
    assertInstanceOf(ReadBoardGmaSession.RestoringExact.class, other.state());
    assertEquals(List.of("startExact"), otherPorts.calls);
  }

  @Test
  void staleSessionEventsDoNotAffectNewerSession() {
    RecordingPorts olderPorts = new RecordingPorts();
    ReadBoardGmaSession older = createSession(olderPorts);
    ReadBoardGmaSession.GmaTerminalCapability olderTerminal =
        older.admitGma(older.helperCapability(), new Object(), nonEmptySnapshot());
    older.consumeGmaTerminal(olderTerminal, ReadBoardGmaSession.GmaTerminal.PLAYED);

    RecordingPorts newerPorts = new RecordingPorts();
    ReadBoardGmaSession newer = createSession(newerPorts);
    ReadBoardGmaSession.GmaTerminalCapability newerTerminal =
        newer.admitGma(
            newer.helperCapability(), new Object(), ReadBoardGmaSession.RuntimeSnapshot.empty());
    newer.consumeGmaTerminal(newerTerminal, ReadBoardGmaSession.GmaTerminal.PLAYED);
    newer.completeExact(
        newerPorts.exactStarts.get(0), new ReadBoardGmaSession.ParticipantResult.Succeeded());

    assertEquals(1, newerPorts.publications.size());
    assertEquals(0, olderPorts.publications.size());

    // The old session's in-flight participant still converges to its own terminal.
    older.completeExact(
        olderPorts.exactStarts.get(0), new ReadBoardGmaSession.ParticipantResult.Succeeded());
    older.completeRuntime(
        olderPorts.runtimeStarts.get(0), new ReadBoardGmaSession.ParticipantResult.Succeeded());

    assertEquals(ReadBoardGmaSession.SessionOutcome.SUCCEEDED, terminalOf(older).outcome());
    assertEquals(1, olderPorts.publications.size());
    assertEquals(1, olderPorts.releases.size());
    assertEquals(1, newerPorts.releases.size());
    assertNotSame(olderPorts.releases.get(0), newerPorts.releases.get(0));
    assertEquals(1, olderPorts.continuations);
    assertEquals(1, newerPorts.continuations);
  }

  @Test
  void reservationReleaseCapabilityIsBoundAndDeliveredOncePerSession() {
    RecordingPorts portsA = new RecordingPorts();
    ReadBoardGmaSession sessionA = createSession(portsA);
    ReadBoardGmaSession.GmaTerminalCapability terminalA =
        sessionA.admitGma(
            sessionA.helperCapability(), new Object(), ReadBoardGmaSession.RuntimeSnapshot.empty());
    sessionA.consumeGmaTerminal(terminalA, ReadBoardGmaSession.GmaTerminal.PLAYED);
    sessionA.completeExact(
        portsA.exactStarts.get(0), new ReadBoardGmaSession.ParticipantResult.Succeeded());

    RecordingPorts portsB = new RecordingPorts();
    ReadBoardGmaSession sessionB = createSession(portsB);
    ReadBoardGmaSession.GmaTerminalCapability terminalB =
        sessionB.admitGma(
            sessionB.helperCapability(), new Object(), ReadBoardGmaSession.RuntimeSnapshot.empty());
    sessionB.consumeGmaTerminal(terminalB, ReadBoardGmaSession.GmaTerminal.REQUEST_ERROR);
    sessionB.completeExact(
        portsB.exactStarts.get(0), new ReadBoardGmaSession.ParticipantResult.Succeeded());

    ReadBoardGmaSession.ReservationReleaseCapability releaseA = portsA.releases.get(0);
    ReadBoardGmaSession.ReservationReleaseCapability releaseB = portsB.releases.get(0);
    assertNotSame(releaseA, releaseB);
    assertEquals(1, portsA.releases.size());
    assertEquals(1, portsB.releases.size());
    assertSame(INCARNATION, releaseA.engineIncarnation());
    assertSame(INCARNATION, releaseB.engineIncarnation());
  }

  @Test
  void foreignIncarnationFailureIsAbsorbedAndCannotLockTheSession() {
    RecordingPorts exactPorts = new RecordingPorts();
    ReadBoardGmaSession session = createSession(exactPorts);
    ReadBoardGmaSession.GmaTerminalCapability terminalCapability =
        session.admitGma(session.helperCapability(), new Object(), nonEmptySnapshot());
    session.consumeGmaTerminal(terminalCapability, ReadBoardGmaSession.GmaTerminal.PLAYED);

    // A failure payload bound to a foreign engine incarnation cannot lock this session.
    session.completeExact(
        exactPorts.exactStarts.get(0),
        new ReadBoardGmaSession.ParticipantResult.Failed(
            new ReadBoardGmaSession.ParticipantFailure(
                ReadBoardGmaSession.FailureCategory.GTP_ERROR, new Object(), "foreign")));
    assertInstanceOf(ReadBoardGmaSession.RestoringExact.class, session.state());
    assertEquals(List.of("startExact"), exactPorts.calls);

    // The same session and incarnation still converge normally afterwards.
    session.completeExact(
        exactPorts.exactStarts.get(0), new ReadBoardGmaSession.ParticipantResult.Succeeded());
    session.completeRuntime(
        exactPorts.runtimeStarts.get(0), new ReadBoardGmaSession.ParticipantResult.Succeeded());
    assertEquals(ReadBoardGmaSession.SessionOutcome.SUCCEEDED, terminalOf(session).outcome());
    assertEquals(1, exactPorts.publications.size());
    assertEquals(1, exactPorts.releases.size());

    RecordingPorts runtimePorts = new RecordingPorts();
    ReadBoardGmaSession runtimeSession = createSession(runtimePorts);
    ReadBoardGmaSession.GmaTerminalCapability runtimeTerminal =
        runtimeSession.admitGma(
            runtimeSession.helperCapability(), new Object(), nonEmptySnapshot());
    runtimeSession.consumeGmaTerminal(runtimeTerminal, ReadBoardGmaSession.GmaTerminal.PLAYED);
    runtimeSession.completeExact(
        runtimePorts.exactStarts.get(0), new ReadBoardGmaSession.ParticipantResult.Succeeded());
    runtimeSession.completeRuntime(
        runtimePorts.runtimeStarts.get(0),
        new ReadBoardGmaSession.ParticipantResult.Failed(
            new ReadBoardGmaSession.ParticipantFailure(
                ReadBoardGmaSession.FailureCategory.TIMEOUT, new Object(), "foreign")));
    assertInstanceOf(ReadBoardGmaSession.RestoringRuntime.class, runtimeSession.state());
    assertEquals(List.of("startExact", "startRuntime"), runtimePorts.calls);
  }

  @Test
  void effectPortFailureNeverSuppressesLaterEffectsAndIsRethrown() {
    FailingPublishPorts ports = new FailingPublishPorts();
    ReadBoardGmaSession session = createSession(ports);
    ReadBoardGmaSession.GmaTerminalCapability terminalCapability =
        session.admitGma(session.helperCapability(), new Object(), nonEmptySnapshot());

    session.consumeGmaTerminal(terminalCapability, ReadBoardGmaSession.GmaTerminal.PLAYED);
    session.completeExact(
        ports.exactStarts.get(0), new ReadBoardGmaSession.ParticipantResult.Succeeded());
    assertThrows(
        IllegalStateException.class,
        () ->
            session.completeRuntime(
                ports.runtimeStarts.get(0), new ReadBoardGmaSession.ParticipantResult.Succeeded()));

    // The publish failure did not suppress the continuation and release effects.
    assertEquals(
        List.of(
            "startExact",
            "startRuntime",
            "publishTerminal",
            "continueNormal",
            "requestReservationRelease"),
        ports.calls);
    assertEquals(ReadBoardGmaSession.SessionOutcome.SUCCEEDED, terminalOf(session).outcome());
    assertEquals(1, ports.continuations);
    assertEquals(1, ports.releases.size());
  }

  private static ReadBoardGmaSession createSession(ReadBoardGmaSession.Ports ports) {
    return createSession(INCARNATION, ports);
  }

  private static ReadBoardGmaSession createSession(
      Object engineIncarnation, ReadBoardGmaSession.Ports ports) {
    return ReadBoardGmaSession.create(engineIncarnation, new Object(), ports);
  }

  private static ReadBoardGmaSession.RuntimeSnapshot nonEmptySnapshot() {
    List<Object> capturedParams = new ArrayList<>();
    capturedParams.add(new Object());
    return ReadBoardGmaSession.RuntimeSnapshot.of(capturedParams);
  }

  private static ReadBoardGmaSession.Terminal terminalOf(ReadBoardGmaSession session) {
    return assertInstanceOf(ReadBoardGmaSession.Terminal.class, session.state());
  }

  /** Records every port call in arrival order with its payloads. */
  private static class RecordingPorts implements ReadBoardGmaSession.Ports {
    final List<String> calls = new ArrayList<>();
    final List<ReadBoardGmaSession.ExactParticipantCapability> exactStarts = new ArrayList<>();
    final List<Object> exactIntents = new ArrayList<>();
    final List<ReadBoardGmaSession.RuntimeParticipantCapability> runtimeStarts = new ArrayList<>();
    final List<ReadBoardGmaSession.RuntimeSnapshot> runtimeSnapshots = new ArrayList<>();
    final List<ReadBoardGmaSession.Terminal> publications = new ArrayList<>();
    final List<ReadBoardGmaSession.ParticipantFailure> failures = new ArrayList<>();
    final List<ReadBoardGmaSession.ReservationReleaseCapability> releases = new ArrayList<>();
    int continuations;

    @Override
    public void startExact(
        ReadBoardGmaSession.ExactParticipantCapability capability, Object restoreIntent) {
      calls.add("startExact");
      exactStarts.add(capability);
      exactIntents.add(restoreIntent);
    }

    @Override
    public void startRuntime(
        ReadBoardGmaSession.RuntimeParticipantCapability capability,
        ReadBoardGmaSession.RuntimeSnapshot runtimeSnapshot) {
      calls.add("startRuntime");
      runtimeStarts.add(capability);
      runtimeSnapshots.add(runtimeSnapshot);
    }

    @Override
    public void publishTerminal(ReadBoardGmaSession.Terminal terminal) {
      calls.add("publishTerminal");
      publications.add(terminal);
    }

    @Override
    public void handleFailure(ReadBoardGmaSession.ParticipantFailure firstFailure) {
      calls.add("handleFailure");
      failures.add(firstFailure);
    }

    @Override
    public void continueNormal() {
      calls.add("continueNormal");
      continuations++;
    }

    @Override
    public void requestReservationRelease(
        ReadBoardGmaSession.ReservationReleaseCapability capability) {
      calls.add("requestReservationRelease");
      releases.add(capability);
    }
  }

  /** startExact port that synchronously rejects; the module must convert it to a typed failure. */
  private static final class RejectingExactStartPorts extends RecordingPorts {
    @Override
    public void startExact(
        ReadBoardGmaSession.ExactParticipantCapability capability, Object restoreIntent) {
      super.startExact(capability, restoreIntent);
      throw new IllegalStateException("exact participant rejected");
    }
  }

  /**
   * startRuntime port that synchronously rejects; the module must convert it to a typed failure.
   */
  private static final class RejectingRuntimeStartPorts extends RecordingPorts {
    @Override
    public void startRuntime(
        ReadBoardGmaSession.RuntimeParticipantCapability capability,
        ReadBoardGmaSession.RuntimeSnapshot runtimeSnapshot) {
      super.startRuntime(capability, runtimeSnapshot);
      throw new IllegalStateException("runtime participant rejected");
    }
  }

  /**
   * publishTerminal port that fails; later effects must still apply and the failure must surface.
   */
  private static final class FailingPublishPorts extends RecordingPorts {
    @Override
    public void publishTerminal(ReadBoardGmaSession.Terminal terminal) {
      super.publishTerminal(terminal);
      throw new IllegalStateException("publish failed");
    }
  }
}
