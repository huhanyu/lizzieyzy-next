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
  private static final AtomicInteger COMMAND = new AtomicInteger(700000000);
  private final Leelaz engine;
  private final Object owner = new Object();
  private final BlockingQueue<String> lines = new LinkedBlockingQueue<>(4096);
  private final AtomicLong search = new AtomicLong();
  private final Consumer<Candidates> candidates;
  private final Consumer<String> failure;
  private volatile boolean closing;
  private volatile boolean searching;
  private volatile boolean prepared;

  public FoxEngineSession(Leelaz engine, Consumer<Candidates> candidates, Consumer<String> failure) {
    this.engine = engine;
    this.candidates = candidates;
    this.failure = failure;
  }

  public Leelaz.ExclusiveGtpLeaseAvailability acquire(Runnable ready) {
    return engine.beginForegroundAnalysisLease(owner,
        line -> {
          if (!lines.offer(line)) failure.accept("Fox.engineError");
        }, ready, () -> { if (!closing) failure.accept("Fox.engineError"); });
  }

  public void invalidate() {
    search.incrementAndGet();
    if (searching) engine.sendExclusiveGtpCommand(owner, COMMAND.getAndIncrement() + " stop");
  }

  public boolean isCurrent(Candidates value) {
    return !closing && value.identity().search() == search.get() && Lizzie.leelaz == engine
        && engine.hasExclusiveGtpLeaseOwnedBy(owner);
  }

  /** Runs on the adapter worker, never on EDT or Leelaz's reader. */
  public void prepare(String selectedRules) throws Exception {
    String commands = request("list_commands", null, 5000);
    for (String required : List.of("kata-search_analyze_cancellable", "kata-get-rules",
        "kata-set-rules", "kata-get-param", "kata-set-param", "kgs-time_settings",
        "set_free_handicap", "stop")) {
      if (!List.of(commands.split("\\s+")).contains(required)) {
        throw new IllegalStateException("Unsupported Fox engine capability: " + required);
      }
    }
    String rules = request("kata-get-rules", null, 5000).trim();
    double maxTime = Double.parseDouble(request("kata-get-param maxTime", null, 5000).trim());
    if (!engine.setFoxRestoreState(owner, rules, maxTime)) {
      throw new IllegalStateException("Fox owner lost before runtime capture");
    }
    request("kata-set-rules " + selectedRules, null, 5000);
    request("kata-get-rules", null, 5000);
    request("kata-set-param maxTime " + Math.min(maxTime, 5.0), null, 5000);
    prepared = true;
  }

  public void analyze(FoxGameState.Snapshot state) throws Exception {
    if (closing || !prepared || !state.searchable()) return;
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
      if (search.get() != generation || closing) return;
      request("play " + action.color() + " " + action.vertex(state.rules().size()), null, 5000);
    }
    if (search.get() != generation || closing) return;
    searching = true;
    try {
      String result = request("kata-search_analyze_cancellable " + state.toPlay() + " 10",
          line -> {
            if (!closing && search.get() == generation && Lizzie.leelaz == engine
                && engine.hasExclusiveGtpLeaseOwnedBy(owner)) {
              candidates.accept(new Candidates(identity, line));
            }
          }, 7000);
      if (search.get() == generation && result.contains("resign")) failure.accept("Fox.officialAction");
    } finally {
      searching = false;
    }
  }

  private String request(String command, Consumer<String> info, long timeout) throws Exception {
    int id = COMMAND.getAndIncrement();
    if (closing || Lizzie.leelaz != engine
        || !engine.sendExclusiveGtpCommand(owner, id + " " + command)) {
      throw new IllegalStateException("Fox engine ownership unavailable");
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
