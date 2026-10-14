package featurecat.lizzie.fox;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Pure protocol state. Placement numbering never substitutes for MOVE/PASS history. */
public final class FoxGameState {
  public record Placement(int number, int x, int y, char color) {
    public String vertex(int size) {
      return "ABCDEFGHJKLMNOPQRSTUVWXYZ".charAt(x) + Integer.toString(size - y);
    }
  }
  public record Rules(int size, int mainSeconds, int byoSeconds, int periods,
      double komi, List<Placement> setup) {}
  public record Clock(int mainSeconds, int byoSeconds, int periods, long receivedNanos) {}
  public record Action(Placement placement, char color, int afterPlacement) {
    public boolean isPass() { return placement == null; }
    public String vertex(int size) { return isPass() ? "pass" : placement.vertex(size); }
  }
  public enum SubmissionOutcome { PENDING, CONFIRMED, FAILED, CANCELLED }
  public record Submission(long connection, long game, long revision, long search,
      Action action, String body, long preparedNanos, long deadlineNanos) {}
  public record SubmissionFact(Submission submission, SubmissionOutcome outcome,
      long writtenNanos, long resolvedNanos, String reason) {}
  public record Snapshot(long connection, long game, long revision, Rules rules,
      Clock clock, char ai, Character toPlay, List<Placement> placements,
      List<Action> actions, boolean initialized, boolean complete, boolean active,
      String statusKey, boolean clockRefreshed, long submitDeadlineNanos, Submission pending,
      boolean automationBlocked, long turnStartedNanos, long foxDeadlineNanos) {
    public boolean searchable() { return initialized && complete && active && !automationBlocked; }
    public boolean ownTurn() { return toPlay != null && toPlay == ai; }
  }

  private long connection;
  private long game;
  private long revision;
  private Rules rules;
  private boolean awaitingRules = true;
  private Clock clock;
  private char ai;
  private Character toPlay;
  private List<Placement> placements = List.of();
  private final List<Action> actions = new ArrayList<>();
  private boolean statusSeen;
  private boolean complete;
  private boolean active;
  private boolean disconnectedGame;
  private String statusKey = "Fox.waiting";
  private long turnStartedNanos;
  private long submitDeadlineNanos;
  private double directMaxTime = 1e20;

  /** Configured before connecting; every search consumes the original authoritative turn. */
  public synchronized void setDirectMaxTime(double seconds) {
    if (!Double.isFinite(seconds) || seconds <= 0) throw new IllegalArgumentException("maxTime");
    directMaxTime = seconds;
  }

  private long effectiveDeadlineNanos() {
    if (toPlay == null || toPlay != ai) return submitDeadlineNanos;
    double nanos = directMaxTime * 1e9;
    long duration = nanos >= Long.MAX_VALUE ? Long.MAX_VALUE : (long) nanos;
    long directDeadline = turnStartedNanos > Long.MAX_VALUE - duration
        ? Long.MAX_VALUE : turnStartedNanos + duration;
    return Math.min(submitDeadlineNanos, directDeadline);
  }
  private long refreshStartedNanos;
  private boolean refreshRequested;
  private boolean clockRefreshed;
  private boolean automationBlocked;
  private String automationStopReason;
  private Submission pending;
  private boolean written;
  private final List<SubmissionFact> submissions = new ArrayList<>();
  private Snapshot checkpoint;
  private String recoveryFailure = "Fox.noCheckpoint";
  private boolean recoveryContradiction;
  private RecoveryConfirmation confirmation;
  public record RecoveryConfirmation(long connection, long game, long revision) {}
  public record RecoveryAssertion(RecoveryConfirmation confirmation, boolean sameGameNoOfflineActions) {}
  private RecoveryAssertion recoveryAssertion;
  public synchronized RecoveryAssertion recoveryAssertion() { return recoveryAssertion; }

  public synchronized boolean hasCheckpoint() { return checkpoint != null; }

  public synchronized String recoveryStatus() {
    if (!snapshot().initialized() || !active) return "Fox.syncing";
    if (checkpoint == null) return recoveryFailure;
    if (recoveryContradiction || !rules.equals(checkpoint.rules())
        || ai != checkpoint.ai() || !toPlay.equals(checkpoint.toPlay())
        || !placements.equals(checkpoint.placements())) return "Fox.recoveryMismatch";
    return "Fox.recoveryConfirm";
  }

  public synchronized RecoveryConfirmation requestRecoveryConfirmation() {
    if (!recoveryStatus().equals("Fox.recoveryConfirm")) return null;
    confirmation = new RecoveryConfirmation(connection, game, revision);
    return confirmation;
  }

  /** User assertion is separate from the pass-less protocol match; never an inferred game ID. */
  public synchronized boolean confirmRecovery(RecoveryConfirmation expected, boolean sameGame) {
    if (expected == null || expected != confirmation || expected.connection() != connection
        || expected.game() != game || expected.revision() != revision
        || !recoveryStatus().equals("Fox.recoveryConfirm")) return false;
    confirmation = null;
    recoveryAssertion = new RecoveryAssertion(expected, sameGame);
    if (!sameGame) {
      checkpoint = null;
      recoveryFailure = "Fox.recoveryDenied";
      return false;
    }
    actions.clear();
    actions.addAll(checkpoint.actions());
    complete = true;
    automationBlocked = false;
    disconnectedGame = false;
    revision++;
    statusKey = toPlay == ai ? "Fox.ourTurn" : "Fox.opponentTurn";
    checkpoint = null;
    return true;
  }

  public synchronized void revokeRecovery(String reason) {
    checkpoint = null;
    confirmation = null;
    recoveryFailure = reason;
  }

  public synchronized List<SubmissionFact> submissions() { return List.copyOf(submissions); }
  public synchronized SubmissionFact latestSubmission() {
    return submissions.isEmpty() ? null : submissions.get(submissions.size() - 1);
  }

  /** One refresh per authoritative turn; queuing and query latency belong to that turn. */
  public synchronized boolean beginClockRefresh(long now) {
    Snapshot state = snapshot();
    if (!state.searchable() || !state.ownTurn() || refreshRequested || automationBlocked) return false;
    refreshRequested = true;
    refreshStartedNanos = now;
    return true;
  }

  public synchronized Submission prepareSubmission(Snapshot expected, long search, String vertex, long now) {
    Snapshot current = snapshot();
    if (expected.connection != connection || expected.game != game || expected.revision != revision
        || !current.searchable() || !current.ownTurn() || !clockRefreshed || automationBlocked
        || pending != null || now >= effectiveDeadlineNanos()) return null;
    Placement move = null;
    if (!vertex.equalsIgnoreCase("pass")) {
      require(vertex.matches("[A-HJ-Z][0-9]{1,2}"));
      int x = "ABCDEFGHJKLMNOPQRSTUVWXYZ".indexOf(vertex.charAt(0));
      int y = rules.size - Integer.parseInt(vertex.substring(1));
      require(x >= 0 && x < rules.size && y >= 0 && y < rules.size);
      move = new Placement(placements.size() + 1, x, y, ai);
    }
    Action action = new Action(move, ai, move == null ? placements.size() : move.number);
    String body = move == null ? "AFSKIP," + ai
        : "AFPLAY," + ai + "," + move.number + "^" + move.x + "^" + move.y + "^" + ai;
    pending = new Submission(connection, game, revision, search, action, body, now, effectiveDeadlineNanos());
    written = false;
    submissions.add(new SubmissionFact(pending, SubmissionOutcome.PENDING, 0, 0, ""));
    statusKey = "Fox.pending";
    return pending;
  }

  /** Called only at the transport's write-start boundary. It consumes permission, not acceptance. */
  public synchronized boolean claimSubmission(Submission expected, long now) {
    if (pending != expected || written || automationBlocked || !active || !complete
        || expected.connection != connection || expected.game != game || expected.revision != revision
        || toPlay == null || toPlay != ai || now >= Math.min(expected.deadlineNanos, effectiveDeadlineNanos())) return false;
    written = true;
    submissions.set(submissions.size() - 1,
        new SubmissionFact(pending, SubmissionOutcome.PENDING, now, 0, ""));
    return true;
  }

  /** PDA supersession competes atomically with physical write-start, never with its outcome. */
  public synchronized boolean cancelUnwrittenForPda(long now) {
    if (pending == null) return true;
    if (written) return false;
    resolveSubmission(SubmissionOutcome.CANCELLED, "Fox.pdaSuperseded", now);
    return true;
  }

  public synchronized void stopAutomation(String reason, long now) {
    automationBlocked = true;
    automationStopReason = reason;
    statusKey = reason;
    clockRefreshed = false;
    resolveSubmission(SubmissionOutcome.FAILED, reason, now);
  }

  private void resolveSubmission(SubmissionOutcome outcome, String reason, long now) {
    if (pending == null) return;
    SubmissionFact old = submissions.get(submissions.size() - 1);
    submissions.set(submissions.size() - 1,
        new SubmissionFact(pending, outcome, old.writtenNanos, now, reason));
    pending = null;
  }

  private void confirm(Action action, long now) {
    if (pending == null) return;
    if (written && pending.action.equals(action)) resolveSubmission(SubmissionOutcome.CONFIRMED, "", now);
    else { stopAutomation("Fox.confirmationFailed", now); gap(); }
  }

  private long remainingMainNanos = Long.MAX_VALUE;

  private void endTurn(long now) {
    if (toPlay != null && toPlay == ai && clock != null && clock.mainSeconds > 0) {
      remainingMainNanos = Math.min(remainingMainNanos,
          Math.max(0, submitDeadlineNanos + 1_000_000_000L - now));
    }
  }

  private long clockNanos(Clock value) {
    int seconds = value.mainSeconds > 0 ? value.mainSeconds : value.periods > 0 ? value.byoSeconds : 0;
    long nanos = seconds * 1_000_000_000L;
    return value.mainSeconds > 0 ? Math.min(nanos, remainingMainNanos) : nanos;
  }

  private void startTurn(long now) {
    turnStartedNanos = now;
    refreshRequested = clockRefreshed = false;
    submitDeadlineNanos = clock == null ? now : now + clockNanos(clock) - 1_000_000_000L;
  }

  public synchronized void connected() {
    connection++;
    reset();
    statusKey = "Fox.syncing";
  }

  public synchronized void disconnected() {
    Snapshot retained = snapshot();
    boolean unresolved = submissions.stream().anyMatch(f -> f.submission().game() == game
        && f.outcome() != SubmissionOutcome.CONFIRMED && f.outcome() != SubmissionOutcome.CANCELLED);
    checkpoint = retained.searchable() && !unresolved ? retained : null;
    recoveryFailure = unresolved ? "Fox.recoveryUnconfirmed" : "Fox.noCheckpoint";
    confirmation = null;
    recoveryContradiction = false;
    stopAutomation("Fox.disconnected", System.nanoTime());
    disconnectedGame |= active;
    revision++;
    active = false;
    statusKey = "Fox.disconnected";
  }

  private void reset() {
    stopAutomation("Fox.stopped", System.nanoTime());
    confirmation = null;
    recoveryAssertion = null;
    automationBlocked = false;
    refreshRequested = clockRefreshed = false;
    revision++;
    rules = null;
    awaitingRules = true;
    clock = null;
    remainingMainNanos = Long.MAX_VALUE;
    ai = 0;
    toPlay = null;
    placements = List.of();
    actions.clear();
    statusSeen = complete = active = false;
  }

  public synchronized Snapshot snapshot() {
    return new Snapshot(connection, game, revision, rules, clock, ai, toPlay,
        placements, List.copyOf(actions), rules != null && statusSeen && clock != null
        && toPlay != null, complete, active, active && automationBlocked ? automationStopReason : statusKey,
        clockRefreshed, effectiveDeadlineNanos(), pending, automationBlocked, turnStartedNanos, submitDeadlineNanos);
  }

  public synchronized void reject() {
    stopAutomation("Fox.protocolError", System.nanoTime());
    revokeRecovery("Fox.protocolError");
    complete = false;
    revision++;
    statusKey = "Fox.protocolError";
  }

  /** Each message validates its complete payload before mutating state. */
  public synchronized void receive(String body, long now) {
    String[] f = body.split(",", -1);
    switch (f[0]) {
      case "FARULE" -> {
        require(f.length >= 6);
        int size = integer(f[1], 2, 25);
        int main = integer(f[2], 0, Integer.MAX_VALUE);
        int byo = integer(f[3], 0, Integer.MAX_VALUE);
        int periods = integer(f[4], 0, Integer.MAX_VALUE);
        int komi = integer(f[5], -100000, 100000);
        List<Placement> setup = new ArrayList<>();
        Set<Integer> occupied = new HashSet<>();
        for (int i = 6; i < f.length; i++) {
          Placement p = placement(f[i], size, true);
          require(p.color == 'B' && occupied.add(p.y * size + p.x));
          setup.add(p);
        }
        Rules incoming = new Rules(size, main, byo, periods, komi / 100.0, List.copyOf(setup));
        if (!awaitingRules) {
          // FARULE is not a game identity. Only an observed terminal/idle opens a new game.
          if (!incoming.equals(rules)) {
            stopAutomation("Fox.protocolError", now);
            revokeRecovery("Fox.protocolError");
            revision++;
          }
          return;
        }
        reset();
        game++;
        rules = incoming;
        awaitingRules = false;
        statusKey = "Fox.syncing";
      }
      case "FASTATUS" -> {
        if (f.length == 2 && f[1].equals("0")) {
          stopAutomation("Fox.terminal", now);
          revision++;
          active = false;
          disconnectedGame = false;
          awaitingRules = true;
          revokeRecovery("Fox.noCheckpoint");
          statusKey = "Fox.terminal";
          return;
        }
        require(rules != null && f.length >= 3 && f[1].equals("1"));
        char color = color(f[2]);
        require(ai == 0 || ai == color);
        List<Placement> incoming = new ArrayList<>();
        for (int i = 3; i < f.length; i++) {
          Placement p = placement(f[i], rules.size, false);
          if (p == null) { require(f.length == 4); break; }
          require(p.number == incoming.size() + 1);
          incoming.add(p);
        }
        if (!statusSeen) complete = incoming.isEmpty() && !disconnectedGame;
        if (statusSeen && !incoming.equals(placements)) {
          if (pending != null && written && !pending.action.isPass()
              && incoming.size() == placements.size() + 1
              && incoming.subList(0, placements.size()).equals(placements)
              && incoming.get(incoming.size() - 1).equals(pending.action.placement)) {
            Action confirmed = pending.action;
            actions.add(confirmed);
            confirm(confirmed, now);
            endTurn(now);
            toPlay = opposite(ai);
            startTurn(now);
          } else gap();
        }
        if (!incoming.equals(placements)) revision++;
        placements = List.copyOf(incoming);
        ai = color;
        statusSeen = active = true;
        if (!complete) statusKey = "Fox.incomplete";
        else if (toPlay != null && !automationBlocked) statusKey = pending != null ? "Fox.pending" : toPlay == ai ? "Fox.ourTurn" : "Fox.opponentTurn";
      }
      case "FAMOVE", "FASKIP" -> {
        boolean pass = f[0].equals("FASKIP");
        require(rules != null && statusSeen && active && f.length == (pass ? 3 : 4));
        require(f[1].equals("0") || f[1].equals("1"));
        require(color(f[2]) == ai);
        char next = f[1].equals("1") ? ai : opposite(ai);
        if (pass) {
          char passer = opposite(next);
          Action action = new Action(null, passer, placements.size());
          if (toPlay != null && toPlay == next && !actions.isEmpty()
              && actions.get(actions.size() - 1).equals(action)) return;
          require(toPlay != null && toPlay != next);
          actions.add(action);
          confirm(action, now);
          revision++;
          if (checkpoint != null) recoveryContradiction = true;
        } else {
          Placement move = placement(f[3], rules.size, false);
          if (move == null) {
            require(placements.isEmpty());
            if (toPlay != null && toPlay != next) gap();
            if (complete && actions.isEmpty() && next != (rules.setup.isEmpty() ? 'B' : 'W')) gap();
          } else if (!placements.isEmpty() && move.equals(placements.get(placements.size() - 1))) {
            if (toPlay != null && toPlay != next) gap();
          } else {
            require(move.number == placements.size() + 1);
            if (toPlay == null || toPlay != move.color || next == move.color) gap();
            List<Placement> updated = new ArrayList<>(placements);
            updated.add(move);
            placements = List.copyOf(updated);
            actions.add(new Action(move, move.color, move.number));
            confirm(actions.get(actions.size() - 1), now);
            revision++;
            if (checkpoint != null) recoveryContradiction = true;
          }
        }
        boolean changedTurn = toPlay == null || toPlay != next;
        if (changedTurn) { revision++; endTurn(now); }
        toPlay = next;
        if (changedTurn) startTurn(now);
        if (complete && !automationBlocked) statusKey = pending != null ? "Fox.pending" : next == ai ? "Fox.ourTurn" : "Fox.opponentTurn";
      }
      case "FATIMELEFT" -> {
        require(f.length == 4 && rules != null);
        Clock next = new Clock(integer(f[1], 0, Integer.MAX_VALUE),
            integer(f[2], 0, Integer.MAX_VALUE), integer(f[3], 0, Integer.MAX_VALUE), now);
        require(next.byoSeconds <= rules.byoSeconds && next.periods <= rules.periods
            && next.mainSeconds <= rules.mainSeconds);
        require(clock == null || (next.periods <= clock.periods && next.mainSeconds <= clock.mainSeconds));
        remainingMainNanos = Math.min(remainingMainNanos, next.mainSeconds * 1_000_000_000L);
        if (toPlay != null && toPlay == ai) {
          long anchor = refreshRequested && !clockRefreshed ? refreshStartedNanos : now;
          long deadline = anchor + clockNanos(next) - 1_000_000_000L;
          submitDeadlineNanos = clock == null ? turnStartedNanos + clockNanos(next) - 1_000_000_000L
              : Math.min(submitDeadlineNanos, deadline);
          if (refreshRequested) clockRefreshed = true;
        }
        clock = next;
      }
      case "FARESULT" -> {
        require(f.length == 2 && !f[1].isEmpty());
        stopAutomation("Fox.terminal", now);
        revision++;
        active = false;
        disconnectedGame = false;
        awaitingRules = true;
        revokeRecovery("Fox.noCheckpoint");
        statusKey = "Fox.terminal";
      }
      case "FASCORE" -> {
        stopAutomation("Fox.officialAction", now);
        revision++;
        statusKey = "Fox.officialAction";
      }
      default -> throw new IllegalArgumentException("Unsupported Fox message");
    }
  }

  private void gap() {
    complete = false;
    if (checkpoint != null) recoveryContradiction = true;
    revision++;
    statusKey = "Fox.incomplete";
  }
  private static char opposite(char color) { return color == 'B' ? 'W' : 'B'; }
  private static char color(String value) {
    require(value.equals("B") || value.equals("W"));
    return value.charAt(0);
  }
  private static int integer(String value, int min, int max) {
    require(value.matches("-?[0-9]+"));
    int result = Integer.parseInt(value);
    require(result >= min && result <= max);
    return result;
  }
  private static Placement placement(String value, int size, boolean setup) {
    if (!setup && value.equals("0^0^0^N")) return null;
    String[] p = value.split("\\^", -1);
    require(p.length == 4);
    return new Placement(integer(p[0], setup ? 0 : 1, Integer.MAX_VALUE),
        integer(p[1], 0, size - 1), integer(p[2], 0, size - 1), color(p[3]));
  }
  private static void require(boolean condition) {
    if (!condition) throw new IllegalArgumentException("Invalid Fox message parameters");
  }
}
