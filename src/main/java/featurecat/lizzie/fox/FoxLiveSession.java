package featurecat.lizzie.fox;

import featurecat.lizzie.Lizzie;
import featurecat.lizzie.analysis.FoxEngineSession;
import featurecat.lizzie.analysis.Leelaz;
import featurecat.lizzie.gui.LizzieFrame;
import featurecat.lizzie.rules.Board;
import featurecat.lizzie.rules.BoardHistoryList;
import featurecat.lizzie.rules.BoardHistoryNode;
import featurecat.lizzie.rules.SGFParser;
import featurecat.lizzie.rules.Stone;
import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.swing.*;

/** EDT-owned Fox lifecycle. Worker owns blocking GTP work; neither protocol nor state depends on UI. */
public final class FoxLiveSession {
  private static FoxLiveSession instance;
  private static final ThreadLocal<Boolean> OWNED_MUTATION = ThreadLocal.withInitial(() -> false);
  private final FoxGameState game = new FoxGameState();
  private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
    Thread t = new Thread(r, "fox-ai-engine"); t.setDaemon(true); return t;
  });
  private final ExecutorService wire = Executors.newSingleThreadExecutor(r -> {
    Thread t = new Thread(r, "fox-ai-send"); t.setDaemon(true); return t;
  });
  private final JLabel clockStatus = new JLabel();
  private boolean engineReady;
  private long scheduledRevision = -1;
  private long refreshDeadlineNanos;
  private final JDialog dialog;
  private final JLabel status = new JLabel();
  private final JTextArea output = new JTextArea(10, 56);
  private final JSpinner port = new JSpinner(new SpinnerNumberModel(7000, 1, 65535, 1));
  private final JComboBox<String> rules = new JComboBox<>(new String[] {"chinese", "japanese", "aga", "tromp-taylor"});
  private final JButton start = new JButton(text("Fox.start"));
  private final JButton stop = new JButton(text("Fox.stop"));
  private final JButton returnLive = new JButton(text("Fox.returnLive"));
  private final JLabel browsingStatus = new JLabel();
  private FoxConnection connection;
  private long connectionToken;
  private volatile long workGeneration;
  private FoxEngineSession engine;
  private boolean restoring;
  private volatile boolean ownsBoard;
  private Board.ClearStateSnapshot originalBoard;
  private BoardHistoryList liveHistory;
  private BoardHistoryNode liveTail;
  private BoardHistoryNode displayCursor;
  private int appliedActions;
  private long adoptedGame = -1;
  private long blockedGame = -1;
  private long ownerGeneration;
  private FoxEngineSession.Candidates liveCandidates;
  private String selectedRules;

  private FoxLiveSession() {
    dialog = new JDialog(Lizzie.frame, text("Fox.title"), false);
    JPanel controls = new JPanel(new GridLayout(2, 3, 8, 8));
    controls.add(new JLabel(text("Fox.port")));
    controls.add(port);
    controls.add(start);
    controls.add(new JLabel(text("Fox.rules")));
    controls.add(rules);
    controls.add(stop);
    output.setEditable(false);
    output.setLineWrap(true);
    output.setWrapStyleWord(true);
    JPanel content = new JPanel(new BorderLayout(8, 8));
    content.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
    content.add(controls, BorderLayout.NORTH);
    content.add(new JScrollPane(output), BorderLayout.CENTER);
    JPanel statePanel = new JPanel(new GridLayout(4, 1));
    statePanel.add(status);
    statePanel.add(clockStatus);
    statePanel.add(browsingStatus);
    statePanel.add(returnLive);
    content.add(statePanel, BorderLayout.SOUTH);
    dialog.setContentPane(content);
    dialog.pack();
    dialog.setLocationRelativeTo(Lizzie.frame);
    start.addActionListener(e -> start());
    stop.addActionListener(e -> stop());
    returnLive.addActionListener(e -> {
      if (!rejectHistoryNavigation() && liveHistory != null) {
        Lizzie.board.navigateToNode(liveTail);
      }
    });
    returnLive.setEnabled(false);
    showStatus("Fox.waiting");
    output.setText(text("Fox.automatic"));
    stop.setEnabled(false);
    new Timer(100, e -> tick()).start();
  }

  public static void showDialog() {
    if (!SwingUtilities.isEventDispatchThread()) {
      SwingUtilities.invokeLater(FoxLiveSession::showDialog); return;
    }
    if (instance == null) instance = new FoxLiveSession();
    instance.dialog.setVisible(true);
  }

  /** Mutations remain excluded while display-only history navigation is admitted separately. */
  public static boolean rejectOrdinaryMutation() {
    FoxLiveSession current = instance;
    if (current == null || !current.ownsBoard || OWNED_MUTATION.get()) return false;
    SwingUtilities.invokeLater(() -> current.showStatus("Fox.conflict"));
    return true;
  }

  public static boolean rejectHistoryNavigation() {
    FoxLiveSession current = instance;
    if (current == null || !current.ownsBoard || OWNED_MUTATION.get()) return false;
    return current.restoring || current.liveHistory == null
        || Lizzie.board.getHistory() != current.liveHistory;
  }

  /** Called by the adopted history's cursor boundary, never by engine or protocol workers. */
  public static void historyCursorChanged(BoardHistoryList history) {
    FoxLiveSession current = instance;
    if (current == null || !current.ownsBoard || OWNED_MUTATION.get()
        || history != current.liveHistory) return;
    current.displayCursor = history.getCurrentHistoryNode();
    current.refreshDisplay();
    Lizzie.board.clearAfterMove();
    Lizzie.frame.refresh();
  }

  private void refreshDisplay() {
    boolean browsing = ownsBoard && liveHistory != null && displayCursor != liveTail;
    browsingStatus.setText(browsing ? text("Fox.browsing") : " ");
    returnLive.setEnabled(browsing && !restoring);
    if (browsing) output.setText(text("Fox.browsing"));
    else if (liveCandidates != null) showCandidates(liveCandidates);
    else output.setText(text("Fox.automatic") + "\n\n" + text("Fox.waitingCandidate"));
  }
  public static boolean isActive() { return instance != null && instance.ownsBoard; }
  public static boolean isBrowsingHistory() {
    FoxLiveSession current = instance;
    return current != null && current.ownsBoard && current.liveHistory != null
        && current.displayCursor != current.liveTail;
  }
  public BoardHistoryNode liveTail() { return liveTail; }
  public BoardHistoryNode displayCursor() { return displayCursor; }
  public FoxEngineSession.Candidates liveCandidates() { return liveCandidates; }
  public FoxGameState.Snapshot snapshot() { return game.snapshot(); }
  public java.util.List<FoxGameState.SubmissionFact> submissions() { return game.submissions(); }

  private static String text(String key) { return Lizzie.resourceBundle.getString(key); }
  private void showStatus(String key) { status.setText(text(key)); }
  private void owned(Runnable action) {
    OWNED_MUTATION.set(true);
    try { action.run(); } finally { OWNED_MUTATION.remove(); }
  }

  private void start() {
    if (connection != null || restoring) return;
    if (Lizzie.config.isDoubleEngineMode() || Lizzie.leelaz == null
        || Lizzie.leelaz.previewForegroundAnalysisLeaseAvailability()
            != Leelaz.ExclusiveGtpLeaseAvailability.AVAILABLE) {
      showStatus("Fox.engineUnavailable"); return;
    }
    if (JOptionPane.showConfirmDialog(dialog, text("Fox.confirm"), text("Fox.title"),
        JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE) != JOptionPane.OK_OPTION) return;
    BoardHistoryList previous = Lizzie.board.getHistory();
    if (!previous.isEmptyBoard() || !previous.noStoneBoard()) {
      Object[] options = {text("Fox.save"), text("Fox.replace"), text("Fox.cancel")};
      int choice = JOptionPane.showOptionDialog(dialog, text("Fox.replaceNotice"), text("Fox.title"),
          JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE, null, options, options[2]);
      if (choice == 0) { LizzieFrame.saveFile(false); return; }
      if (choice != 1) return;
    }
    selectedRules = (String) rules.getSelectedItem();
    originalBoard = Lizzie.board.captureClearState();
    try {
      connection = new FoxConnection((Integer) port.getValue(), new FoxConnection.Listener() {
        public void connected(long token) { onEdt(() -> {
          if (connection == null) return;
          connectionToken = token;
          invalidate();
          game.connected();
          showStatus("Fox.syncing");
        }); }
        public void received(long token, String body) { onEdt(() -> receive(token, body)); }
        public void disconnected(long token) { onEdt(() -> {
          if (connection == null || token != connectionToken) return;
          game.disconnected();
          invalidate();
          release("Fox.disconnected", false);
        }); }
        public void problem(String key) { onEdt(() -> showStatus(key)); }
      });
      ownsBoard = true;
      start.setEnabled(false);
      stop.setEnabled(true);
      port.setEnabled(false);
      rules.setEnabled(false);
      showStatus("Fox.listening");
    } catch (IOException failure) {
      showStatus("Fox.portBusy");
    }
  }

  private void receive(long token, String body) {
    if (connection == null || token != connectionToken) return;
    FoxGameState.Snapshot before = game.snapshot();
    try { game.receive(body, System.nanoTime()); }
    catch (IllegalArgumentException failure) { game.reject(); }
    FoxGameState.Snapshot state = game.snapshot();
    if (state.active()) ownsBoard = true;
    if (before.revision() != state.revision()) invalidate();
    showStatus(state.statusKey());
    if (!state.active()) { release(state.statusKey(), true); return; }
    if (!state.initialized()) return;
    if (!state.complete()) {
      release(state.statusKey(), false);
      output.setText(text(state.statusKey()) + "\n" + state.placements());
      return;
    }
    if (restoring || blockedGame == state.game()) return;
    if (engine == null) acquire(state);
    update(state);
  }

  private void showCandidates(FoxEngineSession.Candidates candidates) {
    StringBuilder display = new StringBuilder(text("Fox.automatic")).append("\n\n");
    display.append(text("AnalysisFrame.column2")).append("   ")
        .append(text("AnalysisFrame.column5")).append("   ")
        .append(text("AnalysisFrame.column4")).append("   ")
        .append(text("AnalysisFrame.column8")).append('\n');
    int count = 0;
    for (String part : candidates.info().split("info ")) {
      if (!part.startsWith("move ") || count++ >= 12) continue;
      featurecat.lizzie.analysis.MoveData move =
          featurecat.lizzie.analysis.MoveData.fromInfoKatago(part);
      display.append(String.format(java.util.Locale.ROOT, "%s   %d   %.1f%%   %+.1f%n",
          move.coordinate, move.playouts, move.winrate, move.scoreMean));
    }
    output.setText(display.toString());
    output.setCaretPosition(0);
  }

  private void acquire(FoxGameState.Snapshot state) {
    if (Lizzie.leelaz == null) { showStatus("Fox.engineUnavailable"); return; }
    engineReady = false;
    long ownerToken = ++ownerGeneration;
    FoxEngineSession owner = new FoxEngineSession(Lizzie.leelaz,
        candidates -> onEdt(() -> {
          FoxGameState.Snapshot current = game.snapshot();
          if (ownerToken != ownerGeneration || engine == null || !engine.isCurrent(candidates)
              || !current.searchable()
              || candidates.identity().connection() != current.connection()
              || candidates.identity().game() != current.game()
              || candidates.identity().revision() != current.revision()) return;
          liveCandidates = candidates;
          if (displayCursor == liveTail) showCandidates(candidates);
        }), key -> onEdt(() -> {
          if (ownerToken != ownerGeneration || engine == null) return;
          blockedGame = game.snapshot().game();
          release(key, true);
        }));
    engine = owner;
    worker.execute(() -> {
      Leelaz.ExclusiveGtpLeaseAvailability availability = owner.acquire(() -> worker.execute(() -> {
        try {
          owner.prepare(selectedRules);
          onEdt(() -> {
            if (engine == owner && game.snapshot().searchable()) {
              engineReady = true;
              update(game.snapshot());
            }
          });
        } catch (Exception failure) {
          System.err.println("Fox engine preparation failed: " + failure);
          onEdt(() -> {
            if (engine == owner) {
              blockedGame = game.snapshot().game();
              release("Fox.engineError", true);
            }
          });
        }
      }));
      if (availability != Leelaz.ExclusiveGtpLeaseAvailability.AVAILABLE) onEdt(() -> {
        if (engine == owner) {
          engine = null;
          blockedGame = game.snapshot().game();
          showStatus("Fox.engineUnavailable");
        }
      });
    });
  }

  private void update(FoxGameState.Snapshot state) {
    if (!state.searchable() || engine == null || restoring) return;
    try { owned(() -> apply(state)); }
    catch (RuntimeException failure) {
      game.reject(); release("Fox.incomplete", false); return;
    }
    if (state.pending() != null) return;
    if (state.ownTurn() && !state.clockRefreshed()) {
      if (game.beginClockRefresh(System.nanoTime())) {
        showStatus("Fox.refreshingClock");
        refreshDeadlineNanos = Math.min(state.submitDeadlineNanos(), System.nanoTime() + 5_000_000_000L);
        FoxConnection transport = connection;
        long token = connectionToken;
        long generation = workGeneration;
        wire.execute(() -> {
          try {
            if (!transport.send(token, "AFSTATUS," + state.ai(), () -> generation == workGeneration))
              onEdt(() -> { if (generation == workGeneration) fail("Fox.clockRisk"); });
          } catch (IOException failure) {
            onEdt(() -> { if (generation == workGeneration) fail("Fox.connectionError"); });
          }
        });
      }
      return;
    }
    if (!engineReady || scheduledRevision == state.revision()) return;
    scheduledRevision = state.revision();
    FoxEngineSession owner = engine;
    long generation = workGeneration;
    worker.execute(() -> {
      if (generation != workGeneration) return;
      try {
        FoxEngineSession.Selection selection = owner.analyze(state);
        onEdt(() -> finishSearch(owner, state, generation, selection));
      }
      catch (Exception failure) {
        System.err.println("Fox engine search failed: " + failure);
        onEdt(() -> {
          if (engine == owner && generation == workGeneration) {
            fail(state.ownTurn() ? "Fox.clockRisk" : "Fox.engineError");
          }
        });
      }
    });
  }

  private void finishSearch(FoxEngineSession owner, FoxGameState.Snapshot searched,
      long generation, FoxEngineSession.Selection selection) {
    if (engine != owner || generation != workGeneration || restoring || !searched.ownTurn()) return;
    if (selection == null) { fail("Fox.clockRisk"); return; }
    FoxGameState.Submission pending;
    try { pending = game.prepareSubmission(searched, selection.identity().search(), selection.vertex(), System.nanoTime()); }
    catch (IllegalArgumentException invalid) { fail("Fox.engineError"); return; }
    if (pending == null) { fail("Fox.clockRisk"); return; }
    showStatus("Fox.pending");
    FoxConnection transport = connection;
    long token = connectionToken;
    wire.execute(() -> {
      try {
        boolean sent = transport.send(token, pending.body(), () -> owner.claimSubmission(selection,
            () -> generation == workGeneration && game.claimSubmission(pending, System.nanoTime())));
        System.out.println("Fox submit search=" + selection.identity() + " completed=" + selection.completedNanos()
            + " deadline=" + pending.deadlineNanos() + " writeReturned=" + System.nanoTime() + " sent=" + sent);
        if (!sent) onEdt(() -> { if (generation == workGeneration) fail("Fox.confirmationFailed"); });
      } catch (IOException failure) {
        onEdt(() -> { if (generation == workGeneration) fail("Fox.confirmationFailed"); });
      }
    });
  }

  private void fail(String reason) {
    blockedGame = game.snapshot().game();
    game.stopAutomation(reason, System.nanoTime());
    release(reason, true);
  }

  private void tick() {
    FoxGameState.Snapshot state = game.snapshot();
    if (connection == null || !state.searchable() || restoring || blockedGame == state.game()) return;
    long now = System.nanoTime();
    FoxGameState.Clock clock = state.clock();
    clockStatus.setText(java.text.MessageFormat.format(text("Fox.clock"), clock.mainSeconds(),
        clock.byoSeconds(), clock.periods(), String.format(java.util.Locale.ROOT, "%.1f",
            state.ownTurn() ? Math.max(0, (state.submitDeadlineNanos() - now) / 1e9) : 0)));
    if (state.pending() != null) {
      if (now - state.pending().preparedNanos() >= 5_000_000_000L) fail("Fox.confirmationFailed");
    } else if (state.ownTurn() && ((!state.clockRefreshed() && refreshDeadlineNanos != 0
        && now >= refreshDeadlineNanos) || now >= state.submitDeadlineNanos())) fail("Fox.clockRisk");
  }

  private void apply(FoxGameState.Snapshot state) {
    if (adoptedGame != state.game() || liveHistory == null) {
      StringBuilder sgf = new StringBuilder("(;GM[1]FF[4]SZ[").append(state.rules().size())
          .append("]KM[").append(state.rules().komi()).append("]RU[").append(selectedRules)
          .append("]PL[").append(state.rules().setup().isEmpty() ? 'B' : 'W').append(']');
      if (!state.rules().setup().isEmpty()) {
        sgf.append("HA[").append(state.rules().setup().size()).append("]AB");
        for (FoxGameState.Placement p : state.rules().setup()) {
          sgf.append('[').append((char) ('a' + p.x())).append((char) ('a' + p.y())).append(']');
        }
      }
      liveHistory = SGFParser.parseSgf(sgf.append(')').toString(), true);
      if (liveHistory == null) throw new IllegalStateException("Invalid Fox starting position");
      Lizzie.board.setHistory(liveHistory);
      adoptedGame = state.game();
      appliedActions = 0;
      liveTail = displayCursor = liveHistory.getCurrentHistoryNode();
    }
    boolean following = displayCursor == liveTail;
    liveHistory.setHead(liveTail);
    for (; appliedActions < state.actions().size(); appliedActions++) {
      FoxGameState.Action action = state.actions().get(appliedActions);
      BoardHistoryNode prior = liveHistory.getCurrentHistoryNode();
      Stone color = action.color() == 'B' ? Stone.BLACK : Stone.WHITE;
      if (action.isPass()) liveHistory.pass(color);
      else liveHistory.place(action.placement().x(), action.placement().y(), color);
      if (liveHistory.getCurrentHistoryNode() == prior) throw new IllegalStateException("Illegal Fox action");
    }
    liveTail = liveHistory.getCurrentHistoryNode();
    if (following) displayCursor = liveTail;
    liveHistory.setHead(displayCursor);
    refreshDisplay();
    Lizzie.frame.refresh();
  }

  private void invalidate() {
    workGeneration++;
    scheduledRevision = -1;
    refreshDeadlineNanos = 0;
    liveCandidates = null;
    refreshDisplay();
    if (engine != null) engine.invalidate();
  }

  private void release(String finalStatus, boolean keepConfirmed) {
    if (game.snapshot().active() || game.snapshot().pending() != null)
      game.stopAutomation(finalStatus, System.nanoTime());
    invalidate();
    if (!keepConfirmed && !game.snapshot().complete() && originalBoard != null) {
      owned(() -> Lizzie.board.restoreClearState(originalBoard));
      liveHistory = null;
      adoptedGame = -1;
    }
    if (engine == null || restoring) {
      if (engine == null && !game.snapshot().active()) ownsBoard = false;
      showStatus(finalStatus);
      refreshDisplay();
      Lizzie.frame.refresh();
      return;
    }
    restoring = true;
    showStatus("Fox.restoring");
    FoxEngineSession owner = engine;
    worker.execute(() -> owner.release(
        () -> onEdt(() -> restored(owner, finalStatus, true)),
        () -> onEdt(() -> restored(owner, "Fox.unrestored", false))));
  }

  private void restored(FoxEngineSession owner, String key, boolean success) {
    if (engine != owner) return;
    engine = null;
    restoring = false;
    ownsBoard = connection != null && game.snapshot().active();
    showStatus(key);
    refreshDisplay();
    Lizzie.frame.refresh();
    if (connection == null) start.setEnabled(success);
    else if (success && game.snapshot().searchable() && blockedGame != game.snapshot().game()) {
      acquire(game.snapshot());
    }
  }

  private void stop() {
    game.stopAutomation("Fox.stopped", System.nanoTime());
    invalidate();
    if (connection != null) { connection.close(); connection = null; }
    connectionToken++;
    release("Fox.stopped", game.snapshot().complete());
    if (engine == null) { ownsBoard = false; start.setEnabled(true); }
    stop.setEnabled(false);
    port.setEnabled(true);
    rules.setEnabled(true);
  }

  private static void onEdt(Runnable action) { SwingUtilities.invokeLater(action); }
}
