package featurecat.lizzie.fox;

import featurecat.lizzie.Lizzie;
import featurecat.lizzie.analysis.FoxEngineSession;
import featurecat.lizzie.analysis.KataGoRules;
import featurecat.lizzie.analysis.Leelaz;
import featurecat.lizzie.analysis.MoveData;
import featurecat.lizzie.gui.LizzieFrame;
import featurecat.lizzie.logging.FoxObservation;
import featurecat.lizzie.rules.Board;
import featurecat.lizzie.rules.BoardHistoryList;
import featurecat.lizzie.rules.BoardHistoryNode;
import featurecat.lizzie.rules.SGFParser;
import featurecat.lizzie.rules.Stone;
import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
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
  private final JTextField rules = new JTextField();
  private final JTextField maxTime = new JTextField(8);
  private final JTextField maxVisits = new JTextField(12);
  private FoxEngineSession.Limits selectedLimits;
  private Leelaz limitsEngine;
  private Object limitsReader;
  private boolean queryingLimits;
  private boolean limitsConfirmed;
  private String initializedTime = "";
  private String initializedVisits = "";
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
  private List<MoveData> liveMoves = List.of();
  private FoxGameState.SubmissionFact loggedSubmission;
  private KataGoRules selectedRules;
  private final JButton recover = new JButton(text("Fox.recoveryConfirm"));
  private Leelaz retainedEngine;
  private Object retainedReader;
  private boolean retainedHistory;
  private boolean recoveryAdmission;

  private boolean recoveryEngineCurrent() {
    return retainedEngine != null && Lizzie.leelaz == retainedEngine
        && retainedEngine.isCurrentEngineIncarnationToken(retainedReader);
  }

  private void confirmReconnect() {
    if (restoring || !recoveryEngineCurrent()) return;
    FoxGameState.RecoveryConfirmation token = game.requestRecoveryConfirmation();
    if (token == null) return;
    long generation = workGeneration;
    int answer = JOptionPane.showConfirmDialog(dialog, text("Fox.recoveryAssertion"),
        text("Fox.title"), JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
    if (generation != workGeneration) return;
    if (!recoveryEngineCurrent()) game.revokeRecovery("Fox.recoveryEngineChanged");
    if (game.confirmRecovery(token, answer == JOptionPane.YES_OPTION)) {
      adoptedGame = game.snapshot().game();
      recoveryAdmission = true;
      invalidate();
      recover.setEnabled(false);
      showStatus("Fox.restoring");
      if (engine == null) acquire(game.snapshot());
      update(game.snapshot());
    } else {
      recover.setEnabled(false);
      showStatus(game.recoveryStatus());
    }
  }

  private FoxLiveSession() {
    dialog = new JDialog(Lizzie.frame, text("Fox.title"), false);
    JPanel controls = new JPanel(new GridLayout(4, 3, 8, 8));
    controls.add(new JLabel(text("Fox.port")));
    controls.add(port);
    controls.add(start);
    controls.add(new JLabel(text("Fox.rules")));
    JSpinner.NumberEditor portEditor = new JSpinner.NumberEditor(port, "0");
    javax.swing.text.NumberFormatter portFormat = new javax.swing.text.NumberFormatter(
        new java.text.DecimalFormat("0")) {
      @Override public Object stringToValue(String text) throws java.text.ParseException {
        if (!text.matches("[0-9]+")) throw new java.text.ParseException("Invalid port", 0);
        return super.stringToValue(text);
      }
    };
    portFormat.setValueClass(Integer.class);
    portFormat.setMinimum(1);
    portFormat.setMaximum(65535);
    portEditor.getTextField().setFormatterFactory(new javax.swing.text.DefaultFormatterFactory(portFormat));
    port.setEditor(portEditor);
    rules.setEditable(false);
    controls.add(rules);
    controls.add(stop);
    maxTime.setName("fox-max-time");
    maxVisits.setName("fox-max-visits");
    controls.add(new JLabel(text("Fox.maxTime")));
    controls.add(maxTime);
    controls.add(new JLabel(text("Fox.limitUnlimited")));
    controls.add(new JLabel(text("Fox.maxVisits")));
    controls.add(maxVisits);
    controls.add(new JLabel(text("Fox.limitUnlimited")));
    output.setEditable(false);
    output.setLineWrap(false);
    JPanel content = new JPanel(new BorderLayout(8, 8));
    content.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
    content.add(controls, BorderLayout.NORTH);
    JScrollPane communication = new JScrollPane(output);
    communication.setBorder(BorderFactory.createTitledBorder(text("Fox.communication")));
    content.add(communication, BorderLayout.CENTER);
    JPanel statePanel = new JPanel(new GridLayout(6, 1));
    statePanel.add(new JLabel(text("Fox.breadthUnavailable")));
    statePanel.add(status);
    statePanel.add(clockStatus);
    statePanel.add(browsingStatus);
    statePanel.add(returnLive);
    statePanel.add(recover);
    recover.setEnabled(false);
    recover.addActionListener(e -> confirmReconnect());
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
    stop.setEnabled(false);
    new Timer(100, e -> tick()).start();
  }

  public static void showDialog() {
    if (!SwingUtilities.isEventDispatchThread()) {
      SwingUtilities.invokeLater(FoxLiveSession::showDialog); return;
    }
    if (instance == null) instance = new FoxLiveSession();
    if (instance.connection == null && !instance.restoring) instance.displayCurrentRules();
    instance.dialog.setVisible(true);
    instance.queryLimits();
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
    if (featurecat.lizzie.gui.LizzieFrame.menu != null)
      featurecat.lizzie.gui.LizzieFrame.menu.refreshBreadthAdmission();
    boolean browsing = ownsBoard && liveHistory != null && displayCursor != liveTail;
    browsingStatus.setText(browsing ? text(browsingStatusKey()) : " ");
    returnLive.setEnabled(browsing && !restoring);
  }

  private KataGoRules currentRules() {
    BoardHistoryList.SessionRulesTarget target = Lizzie.board.getHistory().captureSessionRules();
    if (target.kind() != BoardHistoryList.SessionRulesKind.UNSPECIFIED)
      return target.parsedRules().filter(KataGoRules::hasRequiredFields).orElse(null);
    if (Lizzie.leelaz == null || !Lizzie.leelaz.engineRulesResult().isConfirmed()) return null;
    return Lizzie.leelaz.engineRulesResult().observed();
  }

  private void displayCurrentRules() {
    KataGoRules current = currentRules();
    rules.setText(current == null ? text("Fox.rulesUnavailable") : current.fieldSummary());
    rules.setCaretPosition(0);
  }

  private void queryLimits() {
    if (connection != null || engine != null || restoring || queryingLimits || Lizzie.leelaz == null) return;
    Leelaz target = Lizzie.leelaz;
    Object reader = target.engineIncarnationToken();
    boolean replaceTime = maxTime.getText().equals(initializedTime);
    boolean replaceVisits = maxVisits.getText().equals(initializedVisits);
    queryingLimits = true;
    limitsConfirmed = false;
    refreshLimitControls();
    target.queryFoxLimits().whenComplete((limits, failure) -> onEdt(() -> {
      queryingLimits = false;
      if (connection != null || engine != null || restoring) return;
      if (failure != null || Lizzie.leelaz != target || !target.isCurrentEngineIncarnationToken(reader)) {
        showStatus("Fox.limitsUnavailable");
      } else {
        limitsEngine = target;
        limitsReader = reader;
        limitsConfirmed = true;
        initializedTime = limits.maxTime() == 1e20 ? "0" : Double.toString(limits.maxTime());
        initializedVisits = limits.maxVisits() == 1125899906842624L ? "0" : Long.toString(limits.maxVisits());
        if (replaceTime) maxTime.setText(initializedTime);
        if (replaceVisits) maxVisits.setText(initializedVisits);
      }
      refreshLimitControls();
    }));
  }

  private void refreshLimitControls() {
    boolean available = connection == null && engine == null && !restoring && !queryingLimits
        && limitsConfirmed && Lizzie.leelaz == limitsEngine
        && limitsEngine.isCurrentEngineIncarnationToken(limitsReader)
        && limitsEngine.previewForegroundAnalysisLeaseAvailability()
            == Leelaz.ExclusiveGtpLeaseAvailability.AVAILABLE;
    maxTime.setEnabled(available);
    maxVisits.setEnabled(available);
    start.setEnabled(available);
  }
  public static boolean isActive() { return instance != null && instance.ownsBoard; }
  public static boolean ownsEngine() { return instance != null && instance.engine != null; }
  public static String browsingStatusKey() {
    return instance != null && instance.game.snapshot().searchable()
        && instance.blockedGame != instance.game.snapshot().game()
        ? "Fox.browsing" : "Fox.retainedObservation";
  }
  public static boolean isBrowsingHistory() {
    FoxLiveSession current = instance;
    return current != null && current.ownsBoard && current.liveHistory != null
        && current.displayCursor != current.liveTail;
  }

  /** Display-only results; never install these in a historical BoardData analysis slot. */
  public static List<MoveData> displayedCandidates(BoardHistoryNode node) {
    FoxLiveSession current = instance;
    if (current == null || !current.ownsBoard || current.restoring
        || node != current.liveTail || current.displayCursor != current.liveTail
        || current.liveCandidates == null || current.engine == null
        || Lizzie.board.getHistory() != current.liveHistory
        || !current.engine.isCurrent(current.liveCandidates)) return List.of();
    FoxGameState.Snapshot state = current.game.snapshot();
    FoxEngineSession.SearchIdentity identity = current.liveCandidates.identity();
    return state.searchable() && identity.connection() == state.connection()
        && identity.game() == state.game() && identity.revision() == state.revision()
        ? current.liveMoves : List.of();
  }
  public BoardHistoryNode liveTail() { return liveTail; }
  public BoardHistoryNode displayCursor() { return displayCursor; }
  public FoxEngineSession.Candidates liveCandidates() { return liveCandidates; }
  public FoxGameState.Snapshot snapshot() { return game.snapshot(); }
  public java.util.List<FoxGameState.SubmissionFact> submissions() { return game.submissions(); }

  private static String text(String key) { return Lizzie.resourceBundle.getString(key); }
  private void showStatus(String key) {
    status.setText(text(key));
    FoxGameState.SubmissionFact fact = game.latestSubmission();
    if (fact != null && fact != loggedSubmission) {
      FoxObservation.submission(fact);
      loggedSubmission = fact;
    }
  }
  private void owned(Runnable action) {
    OWNED_MUTATION.set(true);
    try { action.run(); } finally { OWNED_MUTATION.remove(); }
  }

  private void start() {
    if (connection != null || restoring) return;
    if (!limitsConfirmed || Lizzie.leelaz != limitsEngine
        || !limitsEngine.isCurrentEngineIncarnationToken(limitsReader)) {
      queryLimits(); showStatus("Fox.limitsUnavailable"); return;
    }
    try { selectedLimits = FoxEngineSession.Limits.parse(maxTime.getText(), maxVisits.getText()); }
    catch (IllegalArgumentException invalid) { showStatus("Fox.invalidLimits"); return; }
    try { port.commitEdit(); }
    catch (java.text.ParseException invalid) { showStatus("Fox.invalidPort"); return; }
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
    selectedRules = currentRules();
    if (selectedRules == null || !selectedRules.hasRequiredFields()) {
      showStatus("Fox.rulesUnavailable"); return;
    }
    rules.setText(selectedRules.fieldSummary());
    rules.setCaretPosition(0);
    output.setText("");
    game.setDirectMaxTime(selectedLimits.maxTime());
    originalBoard = Lizzie.board.captureClearState();
    try {
      connection = new FoxConnection((Integer) port.getValue(), new FoxConnection.Listener() {
        public void connected(long token) { onEdt(() -> {
          if (connection == null) return;
          connectionToken = token;
          invalidate();
          game.connected();
          output.setText("");
          if (engine != null) engine.beginTranscript(game.snapshot().connection());
          recover.setEnabled(false);
          clockStatus.setText(" ");
          showStatus("Fox.syncing");
        }); }
        public void received(long token, String body) { onEdt(() -> receive(token, body)); }
        public void disconnected(long token) { onEdt(() -> {
          if (connection == null || token != connectionToken) return;
          game.disconnected();
          clockStatus.setText(" ");
          retainedHistory = liveHistory != null;
          recover.setEnabled(false);
          observe("Fox.disconnected");
        }); }
        public void problem(String key) { onEdt(() -> showStatus(key)); }
      });
      ownsBoard = true;
      start.setEnabled(false);
      stop.setEnabled(true);
      port.setEnabled(false);
      refreshLimitControls();
      showStatus("Fox.listening");
      Lizzie.frame.refresh();
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
    FoxObservation.state(state);
    if (state.active()) ownsBoard = true;
    if (before.revision() != state.revision()) invalidate();
    showStatus(state.statusKey());
    if (!state.active()) {
      if (state.statusKey().equals("Fox.terminal")) {
        retainedHistory = false;
        release(state.statusKey(), true);
      } else if (!retainedHistory) release(state.statusKey(), true);
      return;
    }
    if (!state.initialized()) return;
    if (state.automationBlocked()) {
      recover.setEnabled(false);
      observe(state.statusKey());
      return;
    }
    if (!state.complete()) {
      if (game.hasCheckpoint() && !recoveryEngineCurrent())
        game.revokeRecovery("Fox.recoveryEngineChanged");
      String reason = game.recoveryStatus();
      if (!retainedHistory) release(reason, false);
      showStatus(reason);
      recover.setEnabled(!restoring && reason.equals("Fox.recoveryConfirm"));
      refreshDisplay();
      return;
    }
    if (restoring || blockedGame == state.game()) return;
    recover.setEnabled(false);
    if (adoptedGame != state.game()) recoveryAdmission = false;
    if (engine == null) acquire(state);
    update(state);
  }


  private void acquire(FoxGameState.Snapshot state) {
    if (Lizzie.leelaz == null) { showStatus("Fox.engineUnavailable"); return; }
    engineReady = false;
    long ownerToken = ++ownerGeneration;
    if (!recoveryAdmission) {
      retainedEngine = Lizzie.leelaz;
      retainedReader = retainedEngine.engineIncarnationToken();
    }
    FoxEngineSession owner = new FoxEngineSession(Lizzie.leelaz,
        retainedReader,
        candidates -> onEdt(() -> {
          FoxGameState.Snapshot current = game.snapshot();
          if (ownerToken != ownerGeneration || engine == null || !engine.isCurrent(candidates)
              || !current.searchable()
              || candidates.identity().connection() != current.connection()
              || candidates.identity().game() != current.game()
              || candidates.identity().revision() != current.revision()) return;
          liveCandidates = candidates;
          List<MoveData> moves = new ArrayList<>();
          for (String part : candidates.info().split("info ")) {
            if (part.startsWith("move ")) moves.add(MoveData.fromInfoKatago(part));
          }
          liveMoves = List.copyOf(moves);
          showStatus(current.statusKey());
          Lizzie.frame.refresh();
        }), key -> onEdt(() -> {
          if (ownerToken != ownerGeneration || engine == null) return;
          blockedGame = game.snapshot().game();
          release(key, true);
        }));
    engine = owner;
    refreshDisplay();
    output.setText("");
    owner.beginTranscript(state.connection());
    worker.execute(() -> {
      Leelaz.ExclusiveGtpLeaseAvailability availability = owner.acquire(() -> worker.execute(() -> {
        try {
          owner.prepare(selectedRules.toGtpArgument(), selectedLimits);
          onEdt(() -> {
            if (engine == owner && game.snapshot().searchable()) {
              engineReady = true;
              update(game.snapshot());
            }
          });
        } catch (Exception failure) {
          FoxObservation.failure("prepare", failure);
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
        FoxObservation.failure("search", failure);
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
        FoxObservation.write(pending, sent ? "sent" : "not-claimed");
        if (!sent) onEdt(() -> { if (generation == workGeneration) fail("Fox.confirmationFailed"); });
      } catch (IOException failure) {
        FoxObservation.write(pending, "io-failure");
        FoxObservation.failure("write", failure);
        onEdt(() -> { if (generation == workGeneration) fail("Fox.confirmationFailed"); });
      }
    });
  }

  private void fail(String reason) {
    blockedGame = game.snapshot().game();
    game.stopAutomation(reason, System.nanoTime());
    observe(reason);
  }

  /** Held observation still owns display-only navigation and its eventual confirmed handback. */
  private void observe(String reason) {
    invalidate();
    if (liveHistory == null) {
      release(reason, false);
      return;
    }
    retainedHistory = true;
    showStatus(reason);
    Lizzie.frame.refresh();
  }

  private void tick() {
    refreshLimitControls();
    if (featurecat.lizzie.gui.LizzieFrame.menu != null)
      featurecat.lizzie.gui.LizzieFrame.menu.refreshBreadthAdmission();
    drainTranscript();
    FoxGameState.Snapshot state = game.snapshot();
    if (game.hasCheckpoint() && !recoveryEngineCurrent()) {
      game.revokeRecovery("Fox.recoveryEngineChanged");
      recover.setEnabled(false);
      showStatus("Fox.recoveryEngineChanged");
    }
    if (connection == null || !state.initialized() || !state.active()) return;
    long now = System.nanoTime();
    FoxGameState.Clock clock = state.clock();
    clockStatus.setText(java.text.MessageFormat.format(text("Fox.clock"), clock.mainSeconds(),
        clock.byoSeconds(), clock.periods(), String.format(java.util.Locale.ROOT, "%.1f",
            state.searchable() && state.ownTurn() && blockedGame != state.game()
                ? Math.max(0, (state.submitDeadlineNanos() - now) / 1e9) : 0)));
    if (!state.searchable() || restoring || blockedGame == state.game()) return;
    if (state.pending() != null) {
      if (now - state.pending().preparedNanos() >= 5_000_000_000L) fail("Fox.confirmationFailed");
    } else if (state.ownTurn() && ((!state.clockRefreshed() && refreshDeadlineNanos != 0
        && now >= refreshDeadlineNanos) || now >= state.submitDeadlineNanos())) fail("Fox.clockRisk");
  }

  private void apply(FoxGameState.Snapshot state) {
    if (adoptedGame != state.game() || liveHistory == null) {
      StringBuilder sgf = new StringBuilder("(;GM[1]FF[4]SZ[").append(state.rules().size())
          .append("]KM[").append(state.rules().komi()).append("]RU[")
          .append(selectedRules.toGtpArgument().replace("\\", "\\\\").replace("]", "\\]"))
          .append("]PL[").append(state.rules().setup().isEmpty() ? 'B' : 'W').append(']');
      if (!state.rules().setup().isEmpty()) {
        sgf.append("HA[").append(state.rules().setup().size()).append("]AB");
        for (FoxGameState.Placement p : state.rules().setup()) {
          sgf.append('[').append((char) ('a' + p.x())).append((char) ('a' + p.y())).append(']');
        }
      }
      liveHistory = SGFParser.parseSgf(sgf.append(')').toString(), true);
      if (liveHistory == null) throw new IllegalStateException("Invalid Fox starting position");
      liveHistory.publishExternalRules(selectedRules.toGtpArgument());
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
    retainedHistory = true;
    liveHistory.setHead(displayCursor);
    refreshDisplay();
    Lizzie.frame.refresh();
  }

  private void invalidate() {
    workGeneration++;
    scheduledRevision = -1;
    refreshDeadlineNanos = 0;
    liveCandidates = null;
    liveMoves = List.of();
    refreshDisplay();
    if (engine != null) engine.invalidate();
    Lizzie.frame.refresh();
  }

  private void release(String finalStatus, boolean keepConfirmed) {
    if (game.snapshot().active() || game.snapshot().pending() != null)
      game.stopAutomation(finalStatus, System.nanoTime());
    invalidate();
    if (!keepConfirmed && !game.snapshot().complete() && originalBoard != null) {
      owned(() -> Lizzie.board.restoreClearState(originalBoard));
      liveHistory = null;
      retainedHistory = false;
      adoptedGame = -1;
    }
    if (engine == null || restoring) {
      if (engine == null && !game.snapshot().active() && !retainedHistory) ownsBoard = false;
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
    drainTranscript();
    if (engine != owner) return;
    engine = null;
    restoring = false;
    ownsBoard = connection != null && (game.snapshot().active() || retainedHistory);
    if (!success) game.revokeRecovery("Fox.unrestored");
    showStatus(game.hasCheckpoint() && game.snapshot().initialized() && game.snapshot().active()
        ? game.recoveryStatus() : key);
    recover.setEnabled(success && game.recoveryStatus().equals("Fox.recoveryConfirm"));
    refreshDisplay();
    Lizzie.frame.refresh();
    refreshLimitControls();
    if (connection != null && success && game.snapshot().searchable() && blockedGame != game.snapshot().game()) {
      acquire(game.snapshot());
    }
  }

  private void stop() {
    game.stopAutomation("Fox.stopped", System.nanoTime());
    invalidate();
    if (connection != null) { connection.close(); connection = null; }
    connectionToken++;
    release("Fox.stopped", game.snapshot().complete() || retainedHistory);
    retainedHistory = false;
    game.revokeRecovery("Fox.noCheckpoint");
    recover.setEnabled(false);
    if (engine == null) ownsBoard = false;
    stop.setEnabled(false);
    port.setEnabled(true);
    refreshLimitControls();
  }

  private void drainTranscript() {
    if (engine == null) return;
    if (!recoveryEngineCurrent()) {
      engine.drainTranscript();
      output.setText("");
      return;
    }
    String batch = engine.drainTranscript();
    if (batch.isEmpty()) return;
    output.append(batch);
    int excess = output.getDocument().getLength() - 65536;
    if (excess > 0) {
      var lines = output.getDocument().getDefaultRootElement();
      int end = lines.getElement(lines.getElementIndex(excess)).getEndOffset();
      output.replaceRange("", 0, Math.min(end, output.getDocument().getLength()));
    }
    output.setCaretPosition(output.getDocument().getLength());
  }

  private static void onEdt(Runnable action) { SwingUtilities.invokeLater(action); }
}
