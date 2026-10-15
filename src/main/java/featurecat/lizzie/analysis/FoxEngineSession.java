package featurecat.lizzie.analysis;

import featurecat.lizzie.Lizzie;
import featurecat.lizzie.fox.FoxGameState;
import featurecat.lizzie.logging.EngineObservation;
import featurecat.lizzie.logging.FoxObservation;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/** Long-lived Fox owner; all physical GTP I/O remains owned and reader-fenced by Leelaz. */
public final class FoxEngineSession {
  public record SearchIdentity(long connection, long game, long revision, long search) {}
  public record Candidates(SearchIdentity identity, String info) {}
  public record Selection(SearchIdentity identity, String vertex, long completedNanos) {}
  public record Limits(double maxTime, long maxVisits) {
    public Limits {
      if (!Double.isFinite(maxTime) || maxTime <= 0 || maxTime > 1e20
          || maxVisits <= 0 || maxVisits > 1125899906842624L)
        throw new IllegalArgumentException("Invalid Fox search limits");
    }
    /** Ordinary KataGo limit controls use zero to mean unlimited. */
    public static Limits parse(String time, String visits) {
      double seconds = Double.parseDouble(time.trim());
      long count = Long.parseLong(visits.trim());
      return new Limits(seconds == 0 ? 1e20 : seconds,
          count == 0 ? 1125899906842624L : count);
    }
  }
  private static final AtomicInteger COMMAND = new AtomicInteger(700000000);
  private final Leelaz engine;
  private final Object readerIdentity;
  private final String loggingEngineId;
  private final Object owner = this;
  final FoxGtpTranscript transcript = new FoxGtpTranscript();
  private final BlockingQueue<String> lines = new LinkedBlockingQueue<>(4096);
  private final AtomicLong search = new AtomicLong();
  private final Consumer<Candidates> candidates;
  private final Consumer<String> failure;
  private volatile boolean closing;
  private volatile boolean searching;
  private volatile boolean prepared;
  private Limits limits;
  private java.util.function.LongSupplier nanoTime = System::nanoTime;
  private final Object searchLock = new Object();
  private volatile int pendingStop;
  private volatile boolean commandFailed;
  private volatile double desiredPda;
  private volatile double confirmedPda;

  public String pdaUnavailableReason() { return engine.foxPdaUnavailableReason(); }
  public double desiredPda() { return desiredPda; }
  public double confirmedPda() { return confirmedPda; }

  public void desirePda(double value) {
    if (!Double.isFinite(value) || value < -3 || value > 3)
      throw new IllegalArgumentException("PDA outside supported range");
    desiredPda = value;
  }

  /** Serialized with search/replay on the adapter worker. Stop ACK precedes every setter. */
  public void applyDesiredPda() throws Exception {
    if (!pdaUnavailableReason().isEmpty()) throw new IllegalStateException(pdaUnavailableReason());
    try {
      drainStop();
      do {
        double target = desiredPda;
        request("kata-set-param playoutDoublingAdvantage " + target, null, 5000);
        double actual = Double.parseDouble(request("kata-get-param playoutDoublingAdvantage", null, 5000).trim());
        if (Double.compare(actual, target) != 0)
          throw new IllegalStateException("Fox PDA readback differs from target");
        confirmedPda = actual;
        engine.confirmFoxPda(actual);
      } while (Double.compare(desiredPda, confirmedPda) != 0);
    } catch (Exception failure) {
      commandFailed = true;
      engine.markFoxPdaUnconfirmed(owner);
      throw failure;
    }
  }

  private void drainStop() throws Exception {
    int id = pendingStop;
    if (id == 0) return;
    try {
      awaitResponse(id, "stop", null, 5000);
      synchronized (searchLock) { if (pendingStop == id) pendingStop = 0; }
    } catch (Exception failure) {
      commandFailed = true;
      throw failure;
    }
  }

  public boolean claimSubmission(Selection selection, java.util.function.BooleanSupplier claim) {
    synchronized (searchLock) {
      return !closing && selection.identity.search == search.get() && Lizzie.leelaz == engine
          && engine.claimFoxSubmission(owner, claim);
    }
  }

  public FoxEngineSession(Leelaz engine, Object readerIdentity,
      Consumer<Candidates> candidates, Consumer<String> failure) {
    this.engine = engine;
    this.readerIdentity = readerIdentity;
    this.loggingEngineId = EngineObservation.identityFor(engine);
    this.candidates = candidates;
    this.failure = failure;
  }

  public void beginTranscript(long connection) { transcript.begin(connection); }
  public String drainTranscript() { return transcript.drain(); }

  public Leelaz.ExclusiveGtpLeaseAvailability acquire(Runnable ready) {
    if (!engine.isCurrentEngineIncarnationToken(readerIdentity))
      return Leelaz.ExclusiveGtpLeaseAvailability.ENGINE_NOT_READY;
    return engine.beginForegroundAnalysisLease(owner,
        line -> {
          if (!lines.offer(line)) failure.accept("Fox.engineError");
        }, ready, () -> { if (!closing) failure.accept("Fox.engineError"); });
  }

  public void invalidate() {
    synchronized (searchLock) {
      search.incrementAndGet();
      if (searching && pendingStop == 0) {
        pendingStop = COMMAND.getAndIncrement();
        if (!engine.sendExclusiveGtpCommand(owner, pendingStop + " stop")) commandFailed = true;
      }
    }
  }

  public boolean isCurrent(Candidates value) {
    return !closing && value.identity().search() == search.get() && Lizzie.leelaz == engine
        && engine.claimFoxSubmission(owner, () -> true);
  }

  /** Runs on the adapter worker, never on EDT or Leelaz's reader. */
  public void prepare(String selectedRules, Limits requestedLimits) throws Exception {
    KataGoRules requested = KataGoRules.parse(selectedRules)
        .filter(KataGoRules::hasRequiredFields)
        .orElseThrow(() -> new IllegalArgumentException("Incomplete current application rules"));
    String commands = request("list_commands", null, 5000);
    for (String required : List.of("kata-search_analyze_cancellable", "kata-get-rules",
        "kata-set-rules", "kata-get-param", "kata-set-param", "kgs-time_settings",
        "kata-time_settings", "kata-list_time_settings", "set_free_handicap", "time_left", "stop")) {
      if (!List.of(commands.split("\\s+")).contains(required)) {
        throw new IllegalStateException("Unsupported Fox engine capability: " + required);
      }
    }
    if (!List.of(request("kata-list_time_settings", null, 5000).trim().split("\\s+")).contains("none")) {
      throw new IllegalStateException("Fox requires no-clock handback capability");
    }
    String rules = request("kata-get-rules", null, 5000).trim();
    double maxTime = Double.parseDouble(request("kata-get-param maxTime", null, 5000).trim());
    long maxVisits = Long.parseLong(request("kata-get-param maxVisits", null, 5000).trim());
    Limits original = new Limits(maxTime, maxVisits);
    if (!engine.setFoxRestoreState(owner, rules, maxTime, maxVisits)) {
      throw new IllegalStateException("Fox owner lost before runtime capture");
    }
    request("kata-set-rules " + requested.toGtpArgument(), null, 5000);
    KataGoRules actual = KataGoRules.parse(request("kata-get-rules", null, 5000))
        .filter(KataGoRules::hasRequiredFields)
        .orElseThrow(() -> new IllegalStateException("Invalid Fox rules readback"));
    if (!requested.semanticallyEquals(actual)) {
      throw new IllegalStateException("Fox rules readback differs from current application rules");
    }
    limits = requestedLimits == null ? original : requestedLimits;
    request("kata-set-param maxVisits " + limits.maxVisits(), null, 5000);
    confirmedPda = Double.parseDouble(request("kata-get-param playoutDoublingAdvantage", null, 5000).trim());
    if (!Double.isFinite(confirmedPda)) throw new IllegalStateException("Invalid Fox PDA readback");
    desiredPda = confirmedPda;
    engine.confirmFoxPda(confirmedPda);
    prepared = true;
  }

  public Selection analyze(FoxGameState.Snapshot state) throws Exception {
    if (closing || !prepared || !state.searchable()) return null;
    long generation = search.incrementAndGet();
    SearchIdentity identity = new SearchIdentity(state.connection(), state.game(), state.revision(), generation);
    FoxObservation.search(loggingEngineId, readerIdentity, identity, state, "synchronizing", 0);
    request("boardsize " + state.rules().size(), null, 5000);
    request("clear_board", null, 5000);
    request("komi " + state.rules().komi(), null, 5000);
    if (!state.rules().setup().isEmpty()) {
      StringBuilder setup = new StringBuilder("set_free_handicap");
      for (FoxGameState.Placement stone : state.rules().setup()) {
        setup.append(' ').append(stone.vertex(state.rules().size()));
      }
      request(setup.toString(), null, 5000);
    }
    for (FoxGameState.Action action : state.actions()) {
      if (search.get() != generation || closing) return null;
      request("play " + action.color() + " " + action.vertex(state.rules().size()), null, 5000);
    }
    if (search.get() != generation || closing) return null;
    request("kgs-time_settings byoyomi " + state.rules().mainSeconds() + " "
        + state.rules().byoSeconds() + " " + state.rules().periods(), null, 5000);
    double seconds = state.clock().mainSeconds() > 0 ? state.clock().mainSeconds() : state.clock().byoSeconds();
    int periods = state.clock().mainSeconds() > 0 ? 0 : state.clock().periods();
    if (state.ownTurn()) {
      if (!state.clockRefreshed()) return null;
      if (remainingSeconds(state) <= 0)
        throw new IllegalStateException("Fox turn budget exhausted during synchronization");
      seconds = (state.foxDeadlineNanos() - nanoTime.getAsLong()) / 1e9;
    }
    request("time_left " + state.ai() + " " + seconds + " " + periods, null, 5000);
    double limit = state.ownTurn() ? remainingSeconds(state) : Math.min(limits.maxTime(), 5.0);
    if (limit <= 0) throw new IllegalStateException("Fox turn budget exhausted before search");
    double searchLimit = state.ownTurn() ? ownTurnSearchLimit(limits.maxTime(), limit) : limit;
    request("kata-set-param maxTime " + searchLimit, null, 5000);
    synchronized (searchLock) {
      if (search.get() != generation || closing) return null;
      searching = true;
    }
    try {
      long timeout = state.ownTurn()
          ? (long) (remainingSeconds(state) * 1000) : 7000;
      if (timeout <= 0) throw new IllegalStateException("Fox turn budget exhausted in queue");
      FoxObservation.search(loggingEngineId, readerIdentity, identity, state, "started",
          searchLimit);
      String result = request("kata-search_analyze_cancellable " + state.toPlay() + " 10",
          line -> {
            if (!closing && search.get() == generation && Lizzie.leelaz == engine
                && engine.claimFoxSubmission(owner, () -> true)) {
              candidates.accept(new Candidates(identity, line));
            }
          }, timeout, generation).trim();
      if (search.get() != generation || closing || result.equals("cancelled")) return null;
      String vertex = result.startsWith("play ") ? result.substring(5).trim() : result;
      if (vertex.equals("cancelled")) return null;
      if (!vertex.matches("(?i:pass|resign)|[A-HJ-Z][0-9]{1,2}")) {
        throw new IllegalStateException("Fox search returned no valid final choice: " + result);
      }
      long completed = nanoTime.getAsLong();
      if (state.ownTurn() && completed >= state.submitDeadlineNanos()) return null;
      FoxObservation.selection(loggingEngineId, identity, vertex, completed);
      return new Selection(identity, vertex, completed);
    } finally {
      synchronized (searchLock) { searching = false; }
      FoxObservation.search(loggingEngineId, readerIdentity, identity, state, "finished",
          searchLimit);
    }
  }

  private double remainingSeconds(FoxGameState.Snapshot state) {
    return (state.submitDeadlineNanos() - nanoTime.getAsLong()) / 1e9;
  }

  static double ownTurnSearchLimit(double maximumSeconds, double remainingSeconds) {
    double budget = Math.min(maximumSeconds, remainingSeconds);
    // GPU batches can finish after maxTime. Reserve real delivery time even for short turns,
    // without extending the authoritative deadline or consuming all of a tiny remaining budget.
    double margin = Math.min(budget * 0.5, Math.max(0.5, budget * 0.05));
    return budget - margin;
  }

  void useClockForTest(java.util.function.LongSupplier clock) { nanoTime = clock; }

  private String request(String command, Consumer<String> info, long timeout) throws Exception {
    return request(command, info, timeout, -1);
  }

  private String request(String command, Consumer<String> info, long timeout, long generation) throws Exception {
    drainStop();
    int id = COMMAND.getAndIncrement();
    synchronized (searchLock) {
      if (closing || commandFailed || Lizzie.leelaz != engine || !engine.isCurrentEngineIncarnationToken(readerIdentity)
          || (generation >= 0 && generation != search.get())
          || !engine.sendExclusiveGtpCommand(owner, id + " " + command)) {
        throw new IllegalStateException("Fox engine ownership unavailable");
      }
    }
    try {
      return awaitResponse(id, command, info, timeout);
    } catch (Exception failure) {
      commandFailed = true;
      throw failure;
    }
  }

  private String awaitResponse(int id, String command, Consumer<String> info, long timeout) throws Exception {
    long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeout);
    boolean response = false;
    boolean error = false;
    StringBuilder payload = new StringBuilder();
    while (System.nanoTime() < deadline) {
      if (closing || !engine.hasExclusiveGtpLeaseOwnedBy(owner) || Lizzie.leelaz != engine) {
        throw new IllegalStateException("Fox engine ownership ended");
      }
      String line = lines.poll(100, TimeUnit.MILLISECONDS);
      if (line == null) continue;
      String text = line.trim();
      if (text.matches("[=?][0-9]+(?:\\s.*)?") && response)
        throw new IllegalStateException("Fox response attribution changed before completion");
      if (text.matches("[=?]" + id + "(?:\\s.*)?")) {
        response = true;
        error = text.charAt(0) == '?';
        payload.append(text.substring(Integer.toString(id).length() + 1).trim());
      } else if (text.isEmpty() && response) {
        if (error) throw new IllegalStateException("Fox GTP rejected " + command + ": " + payload);
        return payload.toString();
      } else if (response) {
        if (text.startsWith("info ")) {
          if (info != null) info.accept(text);
        } else {
          if (payload.length() > 0) payload.append('\n');
          payload.append(text);
        }
      }
    }
    engine.sendExclusiveGtpCommand(owner, COMMAND.getAndIncrement() + " stop");
    throw new IllegalStateException("Fox GTP response timeout: " + command);
  }

  /** Caller invalidates display candidates before calling this. Completion is confirmed handback. */
  public void release(Runnable restored, Runnable unrestored) {
    invalidate();
    closing = true;
    if (!engine.endForegroundAnalysisLease(owner, restored, unrestored)) unrestored.run();
  }
}
