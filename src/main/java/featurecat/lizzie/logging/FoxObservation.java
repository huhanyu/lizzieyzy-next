package featurecat.lizzie.logging;

import featurecat.lizzie.analysis.FoxEngineSession;
import featurecat.lizzie.fox.FoxGameState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Bounded semantic events on the existing durable engine channel; no raw peer or account data. */
public final class FoxObservation {
  private static final Logger LOG = LoggerFactory.getLogger(LogCategories.ENGINE);

  private FoxObservation() {}

  public static void state(FoxGameState.Snapshot state) {
    if (!LOG.isInfoEnabled()) return;
    LOG.info(
        "fox state connection={} game={} revision={} ai={} turn={} status={} clock={} deadlineNanos={} observedNanos={}",
        state.connection(),
        state.game(),
        state.revision(),
        state.ai() == 0 ? "unset" : Character.toString(state.ai()),
        state.toPlay(),
        state.statusKey(),
        state.clock(),
        state.submitDeadlineNanos(),
        System.nanoTime());
  }

  public static void search(
      String engine,
      Object reader,
      FoxEngineSession.SearchIdentity identity,
      FoxGameState.Snapshot state,
      String phase,
      double searchLimitSeconds) {
    if (!LOG.isInfoEnabled()) return;
    long now = System.nanoTime();
    EngineObservation.inContext(
        engine,
        null,
        () ->
            LOG.info(
                "fox search identity={} reader={} phase={} clock={} clockRefreshed={} deadlineNanos={} observedNanos={} elapsedSinceClockNanos={} remainingNanos={} searchLimitSeconds={} turnStartedNanos={} elapsedTurnNanos={}",
                identity,
                Integer.toHexString(System.identityHashCode(reader)),
                phase,
                state.clock(),
                state.clockRefreshed(),
                state.submitDeadlineNanos(),
                now,
                state.clock() == null ? 0 : now - state.clock().receivedNanos(),
                state.submitDeadlineNanos() - now,
                searchLimitSeconds,
                state.turnStartedNanos(),
                state.ownTurn() ? now - state.turnStartedNanos() : 0));
  }

  public static void selection(
      String engine, FoxEngineSession.SearchIdentity identity, String vertex, long completedNanos) {
    EngineObservation.inContext(
        engine,
        null,
        () ->
            LOG.info(
                "fox selection identity={} vertex={} completedNanos={}",
                identity,
                vertex,
                completedNanos));
  }

  public static void write(FoxGameState.Submission pending, String outcome) {
    LOG.info(
        "fox write connection={} game={} revision={} search={} action={} preparedNanos={} deadlineNanos={} writeReturnedNanos={} outcome={}",
        pending.connection(),
        pending.game(),
        pending.revision(),
        pending.search(),
        pending.action(),
        pending.preparedNanos(),
        pending.deadlineNanos(),
        System.nanoTime(),
        outcome);
  }

  public static void submission(FoxGameState.SubmissionFact fact) {
    FoxGameState.Submission s = fact.submission();
    LOG.info(
        "fox submission connection={} game={} revision={} search={} action={} preparedNanos={} deadlineNanos={} writtenNanos={} resolvedNanos={} outcome={} reason={}",
        s.connection(),
        s.game(),
        s.revision(),
        s.search(),
        s.action(),
        s.preparedNanos(),
        s.deadlineNanos(),
        fact.writtenNanos(),
        fact.resolvedNanos(),
        fact.outcome(),
        fact.reason());
  }

  public static void failure(String phase, Throwable failure) {
    LOG.warn("fox failure phase={} type={}", phase, failure.getClass().getSimpleName());
  }
}
