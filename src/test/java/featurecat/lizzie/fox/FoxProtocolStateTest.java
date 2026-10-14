package featurecat.lizzie.fox;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class FoxProtocolStateTest {
  @Test void directBudgetIncludesInitializationAndRefreshAndFencesPhysicalWrite() {
    FoxGameState state = initialized();
    state.setDirectMaxTime(10);
    refresh(state, 3_000_000_000L, "60,30,3");
    assertEquals(0, state.snapshot().turnStartedNanos());
    assertEquals(10_000_000_000L, state.snapshot().submitDeadlineNanos());
    state.receive("FATIMELEFT,60,30,3", 8_000_000_000L);
    assertEquals(10_000_000_000L, state.snapshot().submitDeadlineNanos());
    var pending = state.prepareSubmission(state.snapshot(), 1, "D4", 9_000_000_000L);
    assertNotNull(pending);
    assertFalse(state.claimSubmission(pending, 10_000_000_000L));
    assertNull(state.prepareSubmission(state.snapshot(), 2, "Q4", 11_000_000_000L));

    FoxGameState tighter = initialized();
    tighter.setDirectMaxTime(120);
    refresh(tighter, 2_000_000_000L, "60,30,3");
    assertEquals(59_000_000_000L, tighter.snapshot().submitDeadlineNanos());
  }

  @Test void repeatedInitializationCannotResetIdentityBudgetHistoryOrFailedSubmission() {
    FoxGameState state = initialized();
    refresh(state, 0, "60,30,3");
    var initial = state.snapshot();
    state.receive("FARULE,19,60,30,3,650", 10_000_000_000L);
    state.receive("FASTATUS,1,B", 10_000_000_000L);
    state.receive("FATIMELEFT,60,30,3", 10_000_000_000L);
    state.receive("FAMOVE,1,B,0^0^0^N", 10_000_000_000L);
    assertEquals(initial.game(), state.snapshot().game());
    assertEquals(initial.revision(), state.snapshot().revision());
    assertEquals(initial.submitDeadlineNanos(), state.snapshot().submitDeadlineNanos());
    assertFalse(state.beginClockRefresh(10_000_000_001L));
    var pending = state.prepareSubmission(state.snapshot(), 1, "R16", 11_000_000_000L);
    assertTrue(state.claimSubmission(pending, 11_000_000_001L));
    state.stopAutomation("Fox.confirmationFailed", 16_000_000_000L);
    var failed = state.submissions();
    state.receive("FARULE,19,60,30,3,650", 20_000_000_000L);
    state.receive("FASTATUS,1,B", 20_000_000_000L);
    state.receive("FATIMELEFT,60,30,3", 20_000_000_000L);
    state.receive("FAMOVE,1,B,0^0^0^N", 20_000_000_000L);
    assertEquals(initial.game(), state.snapshot().game());
    assertEquals(initial.submitDeadlineNanos(), state.snapshot().submitDeadlineNanos());
    assertFalse(state.snapshot().searchable());
    assertEquals("Fox.confirmationFailed", state.snapshot().statusKey());
    assertEquals(failed, state.submissions());
    assertNull(state.prepareSubmission(state.snapshot(), 2, "D4", 21_000_000_000L));
    state.receive("FASTATUS,0", 22_000_000_000L);
    state.receive("FARULE,19,60,30,3,650", 23_000_000_000L);
    state.receive("FASTATUS,1,B", 23_000_000_000L);
    state.receive("FATIMELEFT,60,30,3", 23_000_000_000L);
    state.receive("FAMOVE,1,B,0^0^0^N", 23_000_000_000L);
    assertTrue(state.snapshot().game() > initial.game());
    assertTrue(state.snapshot().searchable());
    assertEquals(82_000_000_000L, state.snapshot().submitDeadlineNanos());
    assertEquals(failed, state.submissions());

    state.receive("FAMOVE,0,B,1^16^3^B", 24_000_000_000L);
    var confirmed = state.snapshot();
    state.receive("FARULE,19,60,30,3,750", 25_000_000_000L);
    assertEquals(confirmed.game(), state.snapshot().game());
    assertEquals(confirmed.rules(), state.snapshot().rules());
    assertEquals(confirmed.actions(), state.snapshot().actions());
    assertFalse(state.snapshot().searchable());
    assertEquals("Fox.protocolError", state.snapshot().statusKey());
  }

  @Test void officialOperationStopsAutomationWithoutDiscardingConfirmedHistory() {
    FoxGameState state = initialized();
    state.receive("FAMOVE,0,B,1^16^3^B", 1);
    var confirmed = state.snapshot().actions();
    state.receive("FASCORE", 2);
    state.receive("FASTATUS,1,B,1^16^3^B", 3);
    state.receive("FAMOVE,0,B,1^16^3^B", 4);
    state.receive("FATIMELEFT,59,30,3", 5);
    assertTrue(state.snapshot().complete());
    assertEquals(confirmed, state.snapshot().actions());
    assertEquals("Fox.officialAction", state.snapshot().statusKey());
    assertFalse(state.snapshot().searchable());
    assertFalse(state.beginClockRefresh(6));
    assertNull(state.prepareSubmission(state.snapshot(), 2, "pass", 6));
    state.disconnected();
    assertFalse(state.hasCheckpoint(), "official operation must not become recovery permission");
  }

  @Test void emptyPlacementReconnectPreservesObservedPassRatherThanAssumingOpeningTurn() {
    FoxGameState state = initialized();
    state.receive("FASKIP,0,B", 1);
    state.disconnected();
    state.connected();
    state.receive("FARULE,19,60,30,3,650", 100_000_000_000L);
    state.receive("FASTATUS,1,B", 100_000_000_000L);
    state.receive("FATIMELEFT,40,30,3", 100_000_000_000L);
    state.receive("FAMOVE,0,B,0^0^0^N", 100_000_000_000L);
    assertTrue(state.confirmRecovery(state.requestRecoveryConfirmation(), true));
    assertTrue(state.snapshot().placements().isEmpty());
    assertEquals(1, state.snapshot().actions().size());
    assertTrue(state.snapshot().actions().get(0).isPass());
  }
  @Test void checkpointMatchRequiresRulesSetupColorPlacementsAndAuthoritativeTurn() {
    String[][] changes = {
      {"FARULE,19,60,30,3,750", "FASTATUS,1,B,1^16^3^B,2^3^15^W", "FAMOVE,0,B,2^3^15^W"},
      {"FARULE,19,60,30,3,650,0^3^3^B", "FASTATUS,1,B,1^16^3^B,2^3^15^W", "FAMOVE,0,B,2^3^15^W"},
      {"FARULE,19,60,30,3,650", "FASTATUS,1,W,1^16^3^B,2^3^15^W", "FAMOVE,1,W,2^3^15^W"},
      {"FARULE,19,60,30,3,650", "FASTATUS,1,B,1^15^3^B,2^3^15^W", "FAMOVE,0,B,2^3^15^W"},
      {"FARULE,19,60,30,3,650", "FASTATUS,1,B,1^16^3^B,2^3^15^W", "FAMOVE,1,B,2^3^15^W"}
    };
    for (String[] messages : changes) {
      FoxGameState state = retainedPass();
      state.connected();
      for (String message : messages) state.receive(message, 100_000_000_000L);
      state.receive("FATIMELEFT,40,30,3", 101_000_000_000L);
      assertEquals("Fox.recoveryMismatch", state.recoveryStatus());
      assertNull(state.requestRecoveryConfirmation());
      assertFalse(state.snapshot().searchable());
    }
    FoxGameState missing = initialized();
    missing.receive("FASTATUS,1,B,1^16^3^B,2^3^15^W", 1);
    missing.disconnected();
    reconnectPass(missing);
    assertFalse(missing.hasCheckpoint());
    assertFalse(missing.snapshot().searchable());
  }

  @Test void staleAssertionCannotConsumeSuccessorConfirmation() {
    FoxGameState state = retainedPass();
    reconnectPass(state);
    var old = state.requestRecoveryConfirmation();
    var current = state.requestRecoveryConfirmation();
    assertFalse(state.confirmRecovery(old, false));
    assertNull(state.recoveryAssertion());
    assertTrue(state.confirmRecovery(current, true));
    assertEquals(current, state.recoveryAssertion().confirmation());
    assertTrue(state.recoveryAssertion().sameGameNoOfflineActions());
  }
  private static FoxGameState retainedPass() {
    FoxGameState state = initialized();
    state.receive("FAMOVE,0,B,1^16^3^B", 1);
    state.receive("FAMOVE,1,B,2^3^15^W", 2);
    state.receive("FASKIP,0,B", 3);
    state.disconnected();
    return state;
  }

  private static void reconnectPass(FoxGameState state) {
    state.connected();
    state.receive("FARULE,19,60,30,3,650", 100_000_000_000L);
    state.receive("FASTATUS,1,B,1^16^3^B,2^3^15^W", 100_000_000_000L);
    state.receive("FAMOVE,0,B,2^3^15^W", 100_000_000_000L);
    assertNull(state.requestRecoveryConfirmation(), "fresh clock required");
    state.receive("FATIMELEFT,40,30,3", 101_000_000_000L);
  }

  @Test void retainedPassRequiresMatchAndExplicitAssertion() {
    FoxGameState state = retainedPass();
    reconnectPass(state);
    assertFalse(state.snapshot().searchable());
    var token = state.requestRecoveryConfirmation();
    assertNotNull(token);
    assertTrue(state.confirmRecovery(token, true));
    assertTrue(state.snapshot().searchable());
    assertEquals(3, state.snapshot().actions().size());
    assertTrue(state.snapshot().actions().get(2).isPass());
    assertEquals(2, state.snapshot().placements().size());
    assertFalse(state.snapshot().ownTurn());
    assertFalse(state.confirmRecovery(token, true));
    state.receive("FAMOVE,1,B,3^15^15^W", 102_000_000_000L);
    assertNull(state.prepareSubmission(state.snapshot(), 99, "D16", 102_000_000_001L));
    assertTrue(state.beginClockRefresh(103_000_000_000L));
    state.receive("FATIMELEFT,39,30,3", 104_000_000_000L);
    assertEquals(141_000_000_000L, state.snapshot().submitDeadlineNanos());
    assertNotNull(state.prepareSubmission(state.snapshot(), 100, "D16", 105_000_000_000L));
  }

  @Test void confirmationCannotSurviveActionTerminalConnectionOrEngineRevocation() {
    for (String event : List.of("FAMOVE,1,B,3^15^15^W", "FARESULT,B+R", "connection", "engine")) {
      FoxGameState state = retainedPass();
      reconnectPass(state);
      var token = state.requestRecoveryConfirmation();
      assertNotNull(token);
      if (event.equals("connection")) state.connected();
      else if (event.equals("engine")) state.revokeRecovery("Fox.recoveryEngineChanged");
      else state.receive(event, 102_000_000_000L);
      assertFalse(state.confirmRecovery(token, true), event);
      assertFalse(state.snapshot().searchable(), event);
    }
  }

  @Test void refusalMismatchAndFailedSubmissionCannotBecomeCheckpoints() {
    FoxGameState denied = retainedPass();
    reconnectPass(denied);
    assertFalse(denied.confirmRecovery(denied.requestRecoveryConfirmation(), false));
    assertEquals("Fox.recoveryDenied", denied.recoveryStatus());
    assertFalse(denied.snapshot().searchable());
    FoxGameState mismatch = retainedPass();
    reconnectPass(mismatch);
    mismatch.receive("FASTATUS,1,B,1^16^3^B", 102_000_000_000L);
    assertNull(mismatch.requestRecoveryConfirmation());
    FoxGameState failed = initialized();
    refresh(failed, 0, "60,30,3");
    var request = failed.prepareSubmission(failed.snapshot(), 1, "R16", 1);
    assertTrue(failed.claimSubmission(request, 2));
    failed.stopAutomation("Fox.confirmationFailed", 3);
    assertNull(failed.snapshot().pending());
    failed.disconnected();
    initialize(failed);
    assertEquals("Fox.recoveryUnconfirmed", failed.recoveryStatus());
    assertNull(failed.requestRecoveryConfirmation());
    failed.receive("FASTATUS,0", 4);
    initialize(failed);
    assertTrue(failed.snapshot().searchable(), "genuinely new game remains admissible");
    assertEquals(FoxGameState.SubmissionOutcome.FAILED, failed.submissions().get(0).outcome());
  }
  @Test void fragmentedAndCoalescedFramesAreAtomic() {
    FoxProtocol codec = new FoxProtocol();
    List<String> frames = new ArrayList<>();
    byte[] first = "$FASTATUS,0*0F\r\n".getBytes(StandardCharsets.US_ASCII);
    for (int i = 0; i < first.length - 1; i++) {
      codec.accept(new byte[] {first[i]}, 1, frames::add);
      assertTrue(frames.isEmpty());
    }
    codec.accept(new byte[] {first[first.length - 1]}, 1, frames::add);
    byte[] two = ("$FASTATUS,0*0F\r\n$FASTATUS,0*0F\r\n").getBytes(StandardCharsets.US_ASCII);
    codec.accept(two, two.length, frames::add);
    assertEquals(List.of("FASTATUS,0", "FASTATUS,0", "FASTATUS,0"), frames);
    byte[] bad = "$FASTATUS,0*00\r\n".getBytes(StandardCharsets.US_ASCII);
    assertThrows(IllegalArgumentException.class, () -> codec.accept(bad, bad.length, frames::add));
    assertEquals(3, frames.size());
  }

  @Test void waitsForAllInitializationAndPreservesUnnumberedPass() {
    FoxGameState state = new FoxGameState();
    state.connected();
    state.receive("FARULE,19,3600,30,3,650", 0);
    state.receive("FASTATUS,1,B", 0);
    state.receive("FAMOVE,1,B,0^0^0^N", 0);
    assertFalse(state.snapshot().searchable());
    state.receive("FATIMELEFT,3600,30,3", 0);
    assertTrue(state.snapshot().searchable());
    state.receive("FAMOVE,0,B,1^16^3^B", 1);
    state.receive("FAMOVE,1,B,2^3^15^W", 2);
    state.receive("FASKIP,0,B", 3);
    state.receive("FASKIP,0,B", 4);
    state.receive("FASTATUS,1,B,1^16^3^B,2^3^15^W", 5);
    state.receive("FAMOVE,0,B,2^3^15^W", 6);
    assertEquals(3, state.snapshot().actions().size());
    assertEquals(2, state.snapshot().placements().size());
    assertTrue(state.snapshot().actions().get(2).isPass());
    assertEquals('W', state.snapshot().toPlay());
    assertEquals("R16", state.snapshot().placements().get(0).vertex(19));
    state.receive("FAMOVE,1,B,3^15^15^W", 7);
    assertEquals(4, state.snapshot().actions().size());
    assertTrue(state.snapshot().searchable());
  }

  @Test void coldMidgameAndMissingActionsNeverBecomeCompleteHistory() {
    FoxGameState cold = new FoxGameState();
    cold.connected();
    cold.receive("FARULE,19,60,30,3,650", 0);
    cold.receive("FASTATUS,1,B,1^16^3^B", 0);
    cold.receive("FATIMELEFT,60,30,3", 0);
    cold.receive("FAMOVE,0,B,1^16^3^B", 0);
    assertFalse(cold.snapshot().searchable());
    assertTrue(cold.snapshot().actions().isEmpty());
    FoxGameState state = initialized();
    state.receive("FAMOVE,1,B,1^3^3^W", 0);
    assertFalse(state.snapshot().complete());
    assertThrows(IllegalArgumentException.class,
        () -> state.receive("FAMOVE,0,B,3^4^4^B", 0));
    assertEquals(1, state.snapshot().placements().size());
  }

  @Test void setupIsNotAnActionAndKomiUsesHundredths() {
    FoxGameState state = new FoxGameState();
    state.connected();
    state.receive("FARULE,9,60,10,2,50,0^2^2^B,0^6^6^B", 0);
    state.receive("FASTATUS,1,W", 0);
    state.receive("FATIMELEFT,60,10,2", 0);
    state.receive("FAMOVE,1,W,0^0^0^N", 0);
    assertTrue(state.snapshot().searchable());
    assertEquals(0.5, state.snapshot().rules().komi());
    assertEquals(2, state.snapshot().rules().setup().size());
    assertTrue(state.snapshot().actions().isEmpty());
    assertEquals('W', state.snapshot().toPlay());
  }

  @Test void disconnectRequiresNewGameRatherThanEmptyReconnectAssumption() {
    FoxGameState state = initialized();
    long connection = state.snapshot().connection();
    state.disconnected();
    assertFalse(state.snapshot().searchable());
    initialize(state);
    assertTrue(state.snapshot().connection() > connection);
    assertFalse(state.snapshot().searchable());
    state.receive("FASTATUS,0", 0);
    state.receive("FARULE,19,60,30,3,650", 0);
    state.receive("FASTATUS,1,B", 0);
    state.receive("FATIMELEFT,60,30,3", 0);
    state.receive("FAMOVE,1,B,0^0^0^N", 0);
    assertTrue(state.snapshot().searchable());
  }

  @Test void refreshAndQueueTimeConsumeBudgetAndWriteIsNotConfirmation() {
    FoxGameState state = initialized();
    assertTrue(state.beginClockRefresh(2_000_000_000L));
    state.receive("FATIMELEFT,58,30,3", 3_000_000_000L);
    assertEquals(59_000_000_000L, state.snapshot().submitDeadlineNanos());
    state.receive("FATIMELEFT,58,30,3", 8_000_000_000L);
    assertEquals(59_000_000_000L, state.snapshot().submitDeadlineNanos());
    FoxGameState.Snapshot position = state.snapshot();
    FoxGameState.Submission pending = state.prepareSubmission(position, 7, "R16", 9_000_000_000L);
    assertNotNull(pending);
    assertEquals("AFPLAY,B,1^16^3^B", pending.body());
    assertTrue(state.claimSubmission(pending, 10_000_000_000L));
    assertFalse(state.claimSubmission(pending, 11_000_000_000L));
    assertTrue(state.snapshot().actions().isEmpty());
    state.receive("FASTATUS,1,B", 12_000_000_000L);
    assertNotNull(state.snapshot().pending());
    state.receive("FAMOVE,0,B,1^16^3^B", 13_000_000_000L);
    assertNull(state.snapshot().pending());
    assertEquals(1, state.snapshot().actions().size());
    assertEquals(FoxGameState.SubmissionOutcome.CONFIRMED, state.submissions().get(0).outcome());
  }

  @Test void passConfirmationConsumesActionButNotNextPlacementNumber() {
    FoxGameState state = initialized();
    refresh(state, 0, "60,30,3");
    var pass = state.prepareSubmission(state.snapshot(), 1, "pass", 1);
    assertEquals("AFSKIP,B", pass.body());
    assertTrue(state.claimSubmission(pass, 2));
    state.receive("FASTATUS,1,B", 3);
    assertNotNull(state.snapshot().pending(), "placement list cannot confirm PASS");
    state.receive("FASKIP,0,B", 4);
    state.receive("FASKIP,0,B", 5);
    assertEquals(1, state.snapshot().actions().size());
    assertTrue(state.snapshot().placements().isEmpty());
    state.receive("FAMOVE,1,B,1^3^3^W", 6);
    refresh(state, 7, "59,30,3");
    assertEquals("AFPLAY,B,2^16^3^B",
        state.prepareSubmission(state.snapshot(), 2, "R16", 8).body());
  }

  @Test void matchingFullStatusConfirmsPlacementWithoutInferringMissingPass() {
    FoxGameState state = initialized();
    refresh(state, 0, "60,30,3");
    var pending = state.prepareSubmission(state.snapshot(), 1, "D4", 1);
    assertTrue(state.claimSubmission(pending, 2));
    state.receive("FASTATUS,1,B,1^3^15^B", 3);
    state.receive("FAMOVE,0,B,1^3^15^B", 4);
    assertNull(state.snapshot().pending());
    assertEquals(1, state.snapshot().actions().size());
    assertEquals('W', state.snapshot().toPlay());
    assertTrue(state.snapshot().complete());
  }

  @Test void whiteNineByNineUsesAuthoritativeTurnAndCoordinateSpace() {
    FoxGameState state = new FoxGameState();
    state.connected();
    state.receive("FARULE,9,60,10,2,50", 0);
    state.receive("FASTATUS,1,W", 0);
    state.receive("FATIMELEFT,60,10,2", 0);
    state.receive("FAMOVE,0,W,0^0^0^N", 0);
    assertFalse(state.beginClockRefresh(1));
    assertNull(state.prepareSubmission(state.snapshot(), 1, "J9", 1));
    state.receive("FAMOVE,1,W,1^4^4^B", 2);
    refresh(state, 3, "60,10,2");
    var pending = state.prepareSubmission(state.snapshot(), 2, "J9", 4);
    assertEquals("AFPLAY,W,2^8^0^W", pending.body());
    assertTrue(state.claimSubmission(pending, 5));
    state.receive("FAMOVE,0,W,2^8^0^W", 6);
    assertEquals(2, state.snapshot().actions().size());
  }

  @Test void deadlineAndInvalidationRejectLateOrQueuedSubmissions() {
    for (String event : List.of("stop", "disconnect", "FASCORE", "FARESULT,W+R", "FAMOVE,0,B,1^3^15^B")) {
      FoxGameState state = initialized();
      refresh(state, 0, "60,30,3");
      var original = state.snapshot();
      var pending = state.prepareSubmission(original, 1, "D4", 1);
      if (event.equals("stop")) state.stopAutomation("Fox.stopped", 2);
      else if (event.equals("disconnect")) state.disconnected();
      else state.receive(event, 2);
      assertFalse(state.claimSubmission(pending, 3), event);
      assertNull(state.prepareSubmission(original, 1, "Q16", 3), event);
      assertEquals(FoxGameState.SubmissionOutcome.FAILED, state.submissions().get(0).outcome());
    }
    FoxGameState state = initialized();
    refresh(state, 0, "60,30,3");
    var pending = state.prepareSubmission(state.snapshot(), 1, "D4", 58_999_999_999L);
    assertNotNull(pending);
    assertFalse(state.claimSubmission(pending, 59_000_000_000L), "exact cutoff rejects");
    state.stopAutomation("Fox.clockRisk", 59_000_000_000L);
    assertNull(state.prepareSubmission(state.snapshot(), 2, "pass", 59_000_000_001L));
  }

  @Test void unconfirmedFailureSurvivesDisconnectAndNoResendOccurs() {
    FoxGameState state = initialized();
    refresh(state, 0, "60,30,3");
    var pending = state.prepareSubmission(state.snapshot(), 1, "D4", 1);
    assertTrue(state.claimSubmission(pending, 2));
    state.stopAutomation("Fox.confirmationFailed", 5_000_000_001L);
    state.receive("FASTATUS,1,B", 6_000_000_000L);
    assertNull(state.prepareSubmission(state.snapshot(), 2, "pass", 6_000_000_000L));
    state.disconnected();
    initialize(state);
    assertEquals(1, state.submissions().size());
    var fact = state.submissions().get(0);
    assertEquals(2, fact.writtenNanos());
    assertEquals(FoxGameState.SubmissionOutcome.FAILED, fact.outcome());
    assertEquals("Fox.confirmationFailed", fact.reason());
    assertFalse(state.snapshot().searchable());
  }

  @Test void consumedMainTimeAndPeriodsAreNotResetByTurnsOrRepeatedQueries() {
    FoxGameState state = initialized();
    refresh(state, 0, "60,30,3");
    state.receive("FASKIP,0,B", 10_000_000_000L);
    state.receive("FAMOVE,1,B,1^3^3^W", 20_000_000_000L);
    refresh(state, 21_000_000_000L, "60,30,3");
    assertEquals(69_000_000_000L, state.snapshot().submitDeadlineNanos(), "opponent time excluded, own ten seconds retained");
    state.receive("FATIMELEFT,0,30,2", 30_000_000_000L);
    assertEquals(59_000_000_000L, state.snapshot().submitDeadlineNanos());
    state.receive("FATIMELEFT,0,30,2", 40_000_000_000L);
    assertEquals(59_000_000_000L, state.snapshot().submitDeadlineNanos());
    assertThrows(IllegalArgumentException.class, () -> state.receive("FATIMELEFT,0,30,3", 41_000_000_000L));
    state.receive("FASKIP,0,B", 42_000_000_000L);
    state.receive("FAMOVE,1,B,2^4^4^W", 50_000_000_000L);
    refresh(state, 51_000_000_000L, "0,30,2");
    assertEquals(79_000_000_000L, state.snapshot().submitDeadlineNanos());
    assertEquals(2, state.snapshot().clock().periods());
  }

  @Test void exhaustedOrContradictoryClockNeverAdmitsAChoice() {
    FoxGameState state = initialized();
    refresh(state, 0, "0,1,1");
    assertNull(state.prepareSubmission(state.snapshot(), 1, "pass", 1));
    assertThrows(IllegalArgumentException.class, () -> state.receive("FATIMELEFT,1,30,1", 2));
    assertThrows(IllegalArgumentException.class, () -> state.receive("FATIMELEFT,0,31,1", 2));
  }

  private static void refresh(FoxGameState state, long now, String clock) {
    assertTrue(state.beginClockRefresh(now));
    state.receive("FATIMELEFT," + clock, now);
  }

  private static FoxGameState initialized() {
    FoxGameState state = new FoxGameState(); initialize(state); return state;
  }
  private static void initialize(FoxGameState state) {
    state.connected();
    state.receive("FARULE,19,60,30,3,650", 0);
    state.receive("FASTATUS,1,B", 0);
    state.receive("FATIMELEFT,60,30,3", 0);
    state.receive("FAMOVE,1,B,0^0^0^N", 0);
  }
}
