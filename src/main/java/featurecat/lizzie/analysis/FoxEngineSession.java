package featurecat.lizzie.analysis;

import featurecat.lizzie.Lizzie;
import featurecat.lizzie.fox.FoxGameState;
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
  private static final AtomicInteger COMMAND = new AtomicInteger(700000000);
  private final Leelaz engine;
  private final Object readerIdentity;
  private final Object owner = new Object();
  private final BlockingQueue<String> lines = new LinkedBlockingQueue<>(4096);
  private final AtomicLong search = new AtomicLong();
  private final Consumer<Candidates> candidates;
  private final Consumer<String> failure;
  private volatile boolean closing;
  private volatile boolean searching;
  private volatile boolean prepared;
  private double originalMaxTime;
  private final Object searchLock = new Object();

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
    this.candidates = candidates;
    this.failure = failure;
  }

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
      if (searching) engine.sendExclusiveGtpCommand(owner, COMMAND.getAndIncrement() + " stop");
    }
  }

  public boolean isCurrent(Candidates value) {
    return !closing && value.identity().search() == search.get() && Lizzie.leelaz == engine
        && engine.claimFoxSubmission(owner, () -> true);
  }

  /** Runs on the adapter worker, never on EDT or Leelaz's reader. */
  public void prepare(String selectedRules) throws Exception {
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
    if (!engine.setFoxRestoreState(owner, rules, maxTime)) {
      throw new IllegalStateException("Fox owner lost before runtime capture");
    }
    request("kata-set-rules " + selectedRules, null, 5000);
    request("kata-get-rules", null, 5000);
    originalMaxTime = maxTime;
    prepared = true;
  }

  public Selection analyze(FoxGameState.Snapshot state) throws Exception {
    if (closing || !prepared || !state.searchable()) return null;
    long generation = search.incrementAndGet();
    SearchIdentity identity = new SearchIdentity(state.connection(), state.game(), state.revision(), generation);
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
      seconds = (state.submitDeadlineNanos() - System.nanoTime()) / 1e9;
      if (seconds <= 0) throw new IllegalStateException("Fox turn budget exhausted during synchronization");
    }
    request("time_left " + state.ai() + " " + seconds + " " + periods, null, 5000);
    double limit = state.ownTurn()
        ? (state.submitDeadlineNanos() - System.nanoTime()) / 1e9 : 5.0;
    if (limit <= 0) throw new IllegalStateException("Fox turn budget exhausted before search");
    request("kata-set-param maxTime " + Math.min(originalMaxTime, limit), null, 5000);
    synchronized (searchLock) {
      if (search.get() != generation || closing) return null;
      searching = true;
    }
    try {
      long timeout = state.ownTurn()
          ? (state.submitDeadlineNanos() - System.nanoTime()) / 1_000_000L : 7000;
      if (timeout <= 0) throw new IllegalStateException("Fox turn budget exhausted in queue");
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
      if (vertex.equalsIgnoreCase("resign")) { failure.accept("Fox.officialAction"); return null; }
      if (!vertex.matches("(?i:pass)|[A-HJ-Z][0-9]{1,2}")) {
        throw new IllegalStateException("Fox search returned no valid final choice: " + result);
      }
      return new Selection(identity, vertex, System.nanoTime());
    } finally {
      searching = false;
    }
  }

  private String request(String command, Consumer<String> info, long timeout) throws Exception {
    return request(command, info, timeout, -1);
  }

  private String request(String command, Consumer<String> info, long timeout, long generation) throws Exception {
    int id = COMMAND.getAndIncrement();
    synchronized (searchLock) {
      if (closing || Lizzie.leelaz != engine || !engine.isCurrentEngineIncarnationToken(readerIdentity)
          || (generation >= 0 && generation != search.get())
          || !engine.sendExclusiveGtpCommand(owner, id + " " + command)) {
        throw new IllegalStateException("Fox engine ownership unavailable");
      }
    }
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
      if (text.startsWith("=" + id) || text.startsWith("?" + id)) {
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
