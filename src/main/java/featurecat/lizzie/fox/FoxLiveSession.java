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
  private final JDialog dialog;
  private final JLabel status = new JLabel();
  private final JTextArea output = new JTextArea(10, 56);
  private final JSpinner port = new JSpinner(new SpinnerNumberModel(7000, 1, 65535, 1));
  private final JComboBox<String> rules = new JComboBox<>(new String[] {"chinese", "japanese", "aga", "tromp-taylor"});
  private final JButton start = new JButton(text("Fox.start"));
  private final JButton stop = new JButton(text("Fox.stop"));
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
    content.add(status, BorderLayout.SOUTH);
    dialog.setContentPane(content);
    dialog.pack();
    dialog.setLocationRelativeTo(Lizzie.frame);
    start.addActionListener(e -> start());
    stop.addActionListener(e -> stop());
    showStatus("Fox.waiting");
    output.setText(text("Fox.preview"));
    stop.setEnabled(false);
  }

  public static void showDialog() {
    if (!SwingUtilities.isEventDispatchThread()) {
      SwingUtilities.invokeLater(FoxLiveSession::showDialog); return;
    }
    if (instance == null) instance = new FoxLiveSession();
    instance.dialog.setVisible(true);
  }

  /** Central semantic admission: 04 will replace navigation rejection with display-only routing. */
  public static boolean rejectOrdinaryMutation() {
    FoxLiveSession current = instance;
    if (current == null || !current.ownsBoard || OWNED_MUTATION.get()) return false;
    SwingUtilities.invokeLater(() -> current.showStatus("Fox.conflict"));
    return true;
  }

  public static boolean isActive() { return instance != null && instance.ownsBoard; }
  public BoardHistoryNode liveTail() { return liveTail; }
  public BoardHistoryNode displayCursor() { return displayCursor; }
  public FoxEngineSession.Candidates liveCandidates() { return liveCandidates; }
  public FoxGameState.Snapshot snapshot() { return game.snapshot(); }

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
          invalidate();
          game.disconnected();
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
    else if (before.revision() != state.revision()) update(state);
  }

  private void showCandidates(FoxEngineSession.Candidates candidates) {
    StringBuilder display = new StringBuilder(text("Fox.preview")).append("\n\n");
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
            if (engine == owner && game.snapshot().searchable()) update(game.snapshot());
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
    FoxEngineSession owner = engine;
    long generation = workGeneration;
    worker.execute(() -> {
      if (generation != workGeneration) return;
      try { owner.analyze(state); }
      catch (Exception failure) {
        System.err.println("Fox engine search failed: " + failure);
        onEdt(() -> {
          if (engine == owner && generation == workGeneration) {
            blockedGame = game.snapshot().game();
            release("Fox.engineError", false);
          }
        });
      }
    });
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
    displayCursor = liveTail;
    Lizzie.frame.refresh();
  }

  private void invalidate() {
    workGeneration++;
    liveCandidates = null;
    output.setText(text("Fox.preview"));
    if (engine != null) engine.invalidate();
  }

  private void release(String finalStatus, boolean keepConfirmed) {
    invalidate();
    if (!keepConfirmed && !game.snapshot().complete() && originalBoard != null) {
      owned(() -> Lizzie.board.restoreClearState(originalBoard));
      liveHistory = null;
      adoptedGame = -1;
    }
    if (engine == null || restoring) {
      if (engine == null && !game.snapshot().active()) ownsBoard = false;
      showStatus(finalStatus);
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
    if (connection == null) start.setEnabled(success);
    else if (success && game.snapshot().searchable() && blockedGame != game.snapshot().game()) {
      acquire(game.snapshot());
    }
  }

  private void stop() {
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
