package featurecat.lizzie.fox;

import featurecat.lizzie.Lizzie;
import featurecat.lizzie.analysis.FoxEngineSession;
import featurecat.lizzie.analysis.KataGoRules;
import featurecat.lizzie.analysis.Leelaz;
import featurecat.lizzie.analysis.MoveData;
import featurecat.lizzie.gui.AppleStyleSupport;
import featurecat.lizzie.gui.LizzieFrame;
import featurecat.lizzie.logging.FoxObservation;
import featurecat.lizzie.rules.Board;
import featurecat.lizzie.rules.BoardHistoryList;
import featurecat.lizzie.rules.BoardHistoryNode;
import featurecat.lizzie.rules.SGFParser;
import featurecat.lizzie.rules.Stone;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
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
  private final JLabel clockStatus = new DetailLabel();
  private boolean engineReady;
  private long scheduledRevision = -1;
  private long refreshDeadlineNanos;
  private final JDialog dialog;
  private final JLabel status = new JLabel();
  private String statusKey = "";
  private final JTextArea output = new JTextArea(14, 56);
  private final JSpinner port = new JSpinner(new SpinnerNumberModel(7000, 1, 65535, 1));
  private final JTextArea rules = new WrappedValue();
  private final JTextField maxTime = new JTextField(8);
  private final JTextField maxVisits = new JTextField(12);
  private FoxEngineSession.Limits selectedLimits;
  private Leelaz limitsEngine;
  private Object limitsReader;
  private boolean queryingLimits;
  private boolean limitsConfirmed;
  private boolean queryingRules;
  private String initializedTime = "";
  private String initializedVisits = "";
  private final JButton start = new JButton(text("Fox.start"));
  private final JButton stop = new JButton(text("Fox.stop"));
  private final JButton returnLive = new JButton(text("Fox.returnLive"));
  private final JLabel browsingStatus = new DetailLabel();
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
  private Leelaz retainedEngine;
  private Object retainedReader;
  private boolean retainedHistory;
  private boolean recoveryAdmission;
  private final JLabel pdaStatus = new DetailLabel();
  private boolean pdaUpdating;

  /** True means Fox consumed this toolbar intent, including refusal and invalid drafts. */
  public static boolean submitPda(String draft) {
    FoxLiveSession session = instance;
    if (session == null || session.engine == null) return false;
    session.changePda(draft);
    return true;
  }

  private void changePda(String draft) {
    if (restoring || !engineReady) {
      pdaStatus.setText(text("Fox.pdaNotReady"));
      return;
    }
    double value;
    try { value = Double.parseDouble(draft); }
    catch (NumberFormatException invalid) { return; }
    if (!Double.isFinite(value) || value < -3 || value > 3) return;
    String reason = engine.pdaUnavailableReason();
    if (!reason.isEmpty()) {
      pdaStatus.setText(text(reason));
      FoxEngineSession owner = engine;
      onEdt(() -> { if (engine == owner) publishConfirmedPda(owner); });
      return;
    }
    engine.desirePda(value);
    showPdaIntent();
    if (Double.compare(value, engine.confirmedPda()) == 0 && !pdaUpdating) return;
    if (!game.cancelUnwrittenForPda(System.nanoTime())) return;
    invalidate();
    schedulePdaUpdate();
  }

  private void showPdaIntent() {
    pdaStatus.setText(java.text.MessageFormat.format(text("Fox.pdaState"),
        Double.toString(engine.desiredPda()), Double.toString(engine.confirmedPda())));
  }

  private void publishConfirmedPda(FoxEngineSession owner) {
    String value = Double.toString(owner.confirmedPda());
    Lizzie.config.txtKataEnginePDA = value;
    Lizzie.config.chkKataEnginePDA = owner.confirmedPda() != 0;
    Lizzie.config.uiConfig.put("txt-kata-engine-pda", value);
    Lizzie.config.uiConfig.put("chk-kata-engine-pda", Lizzie.config.chkKataEnginePDA);
    LizzieFrame.menu.setUseGfPda(Lizzie.config.chkKataEnginePDA, value);
  }

  private void schedulePdaUpdate() {
    if (pdaUpdating || restoring || !engineReady || game.snapshot().pending() != null) return;
    pdaUpdating = true;
    FoxEngineSession owner = engine;
    worker.execute(() -> {
      try {
        owner.applyDesiredPda();
        onEdt(() -> {
          if (engine != owner) return;
          pdaUpdating = false;
          publishConfirmedPda(owner);
          showPdaIntent();
          if (Double.compare(owner.desiredPda(), owner.confirmedPda()) != 0) schedulePdaUpdate();
          else update(game.snapshot());
        });
      } catch (Exception failure) {
        FoxObservation.failure("pda", failure);
        onEdt(() -> {
          if (engine != owner) return;
          pdaUpdating = false;
          publishConfirmedPda(owner);
          pdaStatus.setText(text("Fox.pdaFailed"));
          fail("Fox.pdaFailed");
        });
      }
    });
  }

  private boolean recoveryEngineCurrent() {
    return retainedEngine != null && Lizzie.leelaz == retainedEngine
        && retainedEngine.isCurrentEngineIncarnationToken(retainedReader);
  }


  /** Statuses that stop automation or need the user's hand; everything else is idle or live. */
  private static final Set<String> ATTENTION_STATUS = Set.of(
      "Fox.portBusy", "Fox.invalidPort", "Fox.invalidLimits", "Fox.rulesUnavailable",
      "Fox.engineUnavailable", "Fox.engineError", "Fox.unrestored", "Fox.connectionError",
      "Fox.protocolError", "Fox.disconnected", "Fox.incomplete", "Fox.clockRisk",
      "Fox.confirmationFailed", "Fox.officialAction", "Fox.conflict", "Fox.pdaFailed",
      "Fox.noCheckpoint", "Fox.recoveryMismatch", "Fox.recoveryUnconfirmed",
      "Fox.recoveryEngineChanged");
  private static final int STATUS_DOT = 8;
  private static final int ROW_GAP = 8;
  private static final int GROUP_GAP = 16;

  private FoxLiveSession() {
    dialog = new JDialog(Lizzie.frame, text("Fox.title"), false);
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
    portEditor.getTextField().setColumns(5);
    port.setEditor(portEditor);
    rules.setName("fox-rules");
    maxTime.setName("fox-max-time");
    maxVisits.setName("fox-max-visits");
    pdaStatus.setName("fox-pda-status");

    Color ink = AppleStyleSupport.dialogTextColor();
    Color muted = AppleStyleSupport.workspaceMuted();
    status.setFont(status.getFont().deriveFont(Font.BOLD, status.getFont().getSize2D() + 2f));
    status.setForeground(ink);
    status.setIconTextGap(8);
    clockStatus.setForeground(ink);
    browsingStatus.setForeground(ink);
    pdaStatus.setForeground(muted);
    styleButton(returnLive, 88);
    returnLive.setVisible(false);

    JPanel statusCard = new JPanel(new GridBagLayout());
    statusCard.setBackground(AppleStyleSupport.workspaceSurface());
    statusCard.setBorder(BorderFactory.createCompoundBorder(
        BorderFactory.createLineBorder(AppleStyleSupport.workspaceBorder()),
        BorderFactory.createEmptyBorder(10, 12, 10, 12)));
    int indent = STATUS_DOT + status.getIconTextGap();
    statusCard.add(status, cell(0, 0, 2, 1, new Insets(0, 0, 0, 0)));
    statusCard.add(clockStatus, cell(0, 1, 2, 1, new Insets(6, indent, 0, 0)));
    statusCard.add(pdaStatus, cell(0, 2, 2, 1, new Insets(4, indent, 0, 0)));
    statusCard.add(browsingStatus, cell(0, 3, 1, 1, new Insets(6, indent, 0, 8)));
    GridBagConstraints returnCell = cell(1, 3, 1, 0, new Insets(6, 0, 0, 0));
    returnCell.fill = GridBagConstraints.NONE;
    returnCell.anchor = GridBagConstraints.EAST;
    statusCard.add(returnLive, returnCell);

    JPanel form = new JPanel(new GridBagLayout());
    form.setOpaque(false);
    formRow(form, 0, "Fox.port", port);
    formRow(form, 1, "Fox.maxTime", maxTime);
    formRow(form, 2, "Fox.maxVisits", maxVisits);
    form.add(note("Fox.limitUnlimited", muted), cell(1, 3, 1, 1, new Insets(6, 0, 0, 0)));
    form.add(new JLabel(text("Fox.rules")), cell(0, 4, 2, 1, new Insets(GROUP_GAP, 0, 0, 0)));
    form.add(rules, cell(0, 5, 2, 1, new Insets(4, 0, 0, 0)));
    form.add(note("Fox.breadthUnavailable", muted), cell(0, 6, 2, 1, new Insets(ROW_GAP, 0, 0, 0)));

    styleButton(start, 88);
    styleButton(stop, 88);
    JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
    actions.setOpaque(false);
    actions.add(start);
    actions.add(Box.createHorizontalStrut(ROW_GAP));
    actions.add(stop);

    JPanel setup = new JPanel();
    setup.setOpaque(false);
    setup.setLayout(new BoxLayout(setup, BoxLayout.Y_AXIS));
    setup.add(statusCard);
    setup.add(Box.createVerticalStrut(GROUP_GAP));
    setup.add(form);
    setup.add(Box.createVerticalStrut(12));
    setup.add(actions);

    output.setEditable(false);
    output.setName("fox-transcript");
    output.setLineWrap(false);
    output.setFont(new Font(Font.MONOSPACED, Font.PLAIN, output.getFont().getSize()));
    output.setMargin(new Insets(6, 8, 6, 8));
    JScrollPane communication = new JScrollPane(output);
    communication.setBorder(BorderFactory.createLineBorder(AppleStyleSupport.workspaceBorder()));
    // The pane keeps the 14-row opening size; the area itself only claims its content, so the
    // scrollbar appears when the transcript overflows, not whenever the status card grows.
    communication.setPreferredSize(communication.getPreferredSize());
    output.setRows(0);
    output.setColumns(0);
    JPanel transcript = new JPanel(new BorderLayout(0, 6));
    transcript.setOpaque(false);
    transcript.setBorder(BorderFactory.createEmptyBorder(GROUP_GAP, 0, 0, 0));
    transcript.add(note("Fox.communication", muted), BorderLayout.NORTH);
    transcript.add(communication, BorderLayout.CENTER);

    JPanel content = new JPanel(new BorderLayout());
    content.setBackground(AppleStyleSupport.workspaceBackground());
    content.setBorder(BorderFactory.createEmptyBorder(GROUP_GAP, GROUP_GAP, GROUP_GAP, GROUP_GAP));
    content.add(setup, BorderLayout.NORTH);
    content.add(transcript, BorderLayout.CENTER);
    dialog.setContentPane(content);
    // The status line must hold text before pack, or the transcript is sized one line short.
    showStatus("Fox.waiting");
    dialog.pack();
    dialog.setMinimumSize(dialog.getSize());
    dialog.setLocationRelativeTo(Lizzie.frame);
    start.addActionListener(e -> start());
    stop.addActionListener(e -> stop());
    returnLive.addActionListener(e -> {
      if (!rejectHistoryNavigation() && liveHistory != null) {
        Lizzie.board.navigateToNode(liveTail);
      }
    });
    returnLive.setEnabled(false);
    stop.setEnabled(false);
    new Timer(100, e -> tick()).start();
  }

  private static GridBagConstraints cell(int x, int y, int width, double weightx, Insets insets) {
    GridBagConstraints c = new GridBagConstraints();
    c.gridx = x;
    c.gridy = y;
    c.gridwidth = width;
    c.weightx = weightx;
    c.fill = GridBagConstraints.HORIZONTAL;
    c.anchor = GridBagConstraints.WEST;
    c.insets = insets;
    return c;
  }

  private static void formRow(JPanel form, int row, String labelKey, JComponent field) {
    int top = row == 0 ? 0 : ROW_GAP;
    GridBagConstraints label = cell(0, row, 1, 0, new Insets(top, 0, 0, 12));
    label.fill = GridBagConstraints.NONE;
    form.add(new JLabel(text(labelKey)), label);
    GridBagConstraints value = cell(1, row, 1, 1, new Insets(top, 0, 0, 0));
    value.fill = GridBagConstraints.NONE;
    form.add(field, value);
  }

  private static JLabel note(String key, Color color) {
    JLabel note = new JLabel(text(key));
    note.setForeground(color);
    return note;
  }

  private static void styleButton(JButton button, int minWidth) {
    AppleStyleSupport.installButtonStyle(button);
    Dimension size = button.getPreferredSize();
    button.setPreferredSize(new Dimension(Math.max(minWidth, size.width + 16), 28));
  }

  /** Secondary status rows take no space until they carry text. */
  private static final class DetailLabel extends JLabel {
    @Override public void setText(String text) {
      super.setText(text);
      setVisible(text != null && !text.isBlank());
    }
  }

  /** Read-only wrapped value: claims the dialog's text width and grows in height with its text. */
  private static final class WrappedValue extends JTextArea {
    private static final int WRAP_WIDTH = 720;
    private int laidOutWidth = -1;

    WrappedValue() {
      setEditable(false);
      setFocusable(false);
      setOpaque(false);
      setLineWrap(true);
      setWrapStyleWord(true);
      setHighlighter(null);
      setFont(UIManager.getFont("Label.font"));
      setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 0));
    }

    @Override public Dimension getPreferredSize() {
      int width = getWidth() > 0 ? getWidth() : WRAP_WIDTH;
      Insets insets = getInsets();
      // Swing's wrapped view, minus the caret margin BasicTextUI reserves even when read-only.
      javax.swing.text.View view = getUI().getRootView(this);
      view.setSize(Math.max(1, width - insets.left - insets.right - 1), Integer.MAX_VALUE);
      int height = (int) Math.ceil(view.getPreferredSpan(javax.swing.text.View.Y_AXIS));
      return new Dimension(WRAP_WIDTH, insets.top + insets.bottom
          + Math.max(getFontMetrics(getFont()).getHeight(), height));
    }

    @Override public void setBounds(int x, int y, int width, int height) {
      super.setBounds(x, y, width, height);
      // Height depends on the width just assigned; lay out once more after a width change.
      if (width > 0 && width != laidOutWidth) {
        laidOutWidth = width;
        SwingUtilities.invokeLater(this::revalidate);
      }
    }
  }

  private record StatusDot(Color color) implements Icon {
    @Override public void paintIcon(Component c, Graphics g, int x, int y) {
      Graphics2D g2 = (Graphics2D) g.create();
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g2.setColor(color);
      g2.fillOval(x, y, STATUS_DOT, STATUS_DOT);
      g2.dispose();
    }
    @Override public int getIconWidth() { return STATUS_DOT; }
    @Override public int getIconHeight() { return STATUS_DOT; }
  }

  public static void showDialog() {
    if (!SwingUtilities.isEventDispatchThread()) {
      SwingUtilities.invokeLater(FoxLiveSession::showDialog); return;
    }
    if (instance == null) instance = new FoxLiveSession();
    if (instance.connection == null && !instance.restoring) instance.displayCurrentRules();
    instance.dialog.setVisible(true);
    instance.queryCurrentRules();
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
    browsingStatus.setText(browsing ? text(browsingStatusKey()) : "");
    returnLive.setVisible(browsing);
    returnLive.setEnabled(browsing && !restoring);
  }

  private KataGoRules currentRules() {
    BoardHistoryList.SessionRulesTarget target = Lizzie.board.getHistory().captureSessionRules();
    if (target.kind() != BoardHistoryList.SessionRulesKind.UNSPECIFIED)
      return target.parsedRules().filter(KataGoRules::hasRequiredFields).orElse(null);
    if (Lizzie.leelaz == null || !Lizzie.leelaz.engineRulesResult().isConfirmed()) return null;
    KataGoRules observed = Lizzie.leelaz.engineRulesResult().observed();
    return observed != null && observed.hasRequiredFields() ? observed : null;
  }

  private void displayCurrentRules() {
    KataGoRules current = currentRules();
    rules.setText(current == null ? text("Fox.rulesUnavailable") : FoxRulesText.describe(current, Lizzie.resourceBundle));
  }

  private void queryCurrentRules() {
    if (connection != null || engine != null || restoring || queryingRules || currentRules() != null
        || Lizzie.leelaz == null
        || Lizzie.board.getHistory().captureSessionRules().kind()
            != BoardHistoryList.SessionRulesKind.UNSPECIFIED) return;
    Leelaz target = Lizzie.leelaz;
    Object reader = target.engineIncarnationToken();
    queryingRules = true;
    rules.setText(text("SetKataRules.status.pending"));
    showStatus("SetKataRules.status.pending");
    refreshLimitControls();
    worker.execute(() -> {
      if (Lizzie.leelaz != target || !target.isCurrentEngineIncarnationToken(reader)
          || target.isRulesMutationOccupied()) {
        onEdt(() -> currentRulesQueried(target, reader));
        return;
      }
      Leelaz.EngineRulesOperation operation = target.engineRulesOperation();
      if (operation == null || operation.isDone()) operation = target.queryEngineRulesOperation();
      operation.onComplete(result -> onEdt(() -> currentRulesQueried(target, reader)));
    });
  }

  private void currentRulesQueried(Leelaz target, Object reader) {
    queryingRules = false;
    if (connection != null || engine != null || restoring) return;
    displayCurrentRules();
    if (Lizzie.leelaz != target || !target.isCurrentEngineIncarnationToken(reader)
        || currentRules() == null) showStatus("Fox.rulesUnavailable");
    else showStatus(queryingLimits || limitsConfirmed ? "Fox.waiting" : "Fox.limitsUnavailable");
    refreshLimitControls();
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
        // A failed earlier query or premature Start left this notice; confirmation supersedes it.
        if (statusKey.equals("Fox.limitsUnavailable")) showStatus("Fox.waiting");
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
    start.setEnabled(available && !queryingRules && currentRules() != null);
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
    statusKey = key;
    status.setText(text(key));
    status.setIcon(new StatusDot(ATTENTION_STATUS.contains(key) ? AppleStyleSupport.workspaceError()
        : connection != null ? AppleStyleSupport.workspaceSuccess() : AppleStyleSupport.workspaceMuted()));
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
    rules.setText(FoxRulesText.describe(selectedRules, Lizzie.resourceBundle));
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
          clockStatus.setText(" ");
          showStatus("Fox.syncing");
        }); }
        public void received(long token, String body) { onEdt(() -> receive(token, body)); }
        public void disconnected(long token) { onEdt(() -> {
          if (connection == null || token != connectionToken) return;
          game.disconnected();
          clockStatus.setText(" ");
          retainedHistory = liveHistory != null;
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
      observe(state.statusKey());
      return;
    }
    if (!state.complete()) {
      if (game.hasCheckpoint() && !recoveryEngineCurrent())
        game.revokeRecovery("Fox.recoveryEngineChanged");
      if (!restoring && game.recoverHistory(System.nanoTime())) {
        state = game.snapshot();
        adoptedGame = state.game();
        recoveryAdmission = true;
        invalidate();
      } else {
        String reason = game.recoveryStatus();
        if (!retainedHistory) release(reason, false);
        showStatus(reason);
        refreshDisplay();
        return;
      }
    }
    if (restoring || blockedGame == state.game()) return;
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
            if (engine == owner) {
              engineReady = true;
              showPdaIntent();
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
      game.reject(); observe("Fox.incomplete"); return;
    }
    if (state.pending() != null) return;
    if (pdaUpdating) return;
    if (engineReady && Double.compare(engine.desiredPda(), engine.confirmedPda()) != 0) {
      schedulePdaUpdate();
      return;
    }
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
            fail("Fox.engineError");
          }
        });
      }
    });
  }

  private void finishSearch(FoxEngineSession owner, FoxGameState.Snapshot searched,
      long generation, FoxEngineSession.Selection selection) {
    if (engine != owner || generation != workGeneration || restoring) return;
    if (selection != null && selection.vertex().equalsIgnoreCase("resign")) {
      fail("Fox.officialAction"); return;
    }
    if (!searched.ownTurn()) return;
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
      appendConfirmed(liveHistory, state.actions().get(appliedActions),
          selectedRules.bool(KataGoRules.SUICIDE));
    }
    liveTail = liveHistory.getCurrentHistoryNode();
    if (following) displayCursor = liveTail;
    retainedHistory = true;
    liveHistory.setHead(displayCursor);
    refreshDisplay();
    Lizzie.frame.refresh();
  }

  /** Confirmed Fox actions follow the session's Go rules, never the ordinary no-capture setting. */
  static void appendConfirmed(BoardHistoryList history, FoxGameState.Action action,
      boolean multiStoneSuicide) {
    BoardHistoryNode prior = history.getCurrentHistoryNode();
    Stone color = action.color() == 'B' ? Stone.BLACK : Stone.WHITE;
    if (action.isPass()) history.pass(color);
    else history.place(action.placement().x(), action.placement().y(), color,
        false, false, false, false, multiStoneSuicide);
    if (history.getCurrentHistoryNode() == prior) throw new IllegalStateException("Illegal Fox action");
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
    if (engineReady) publishConfirmedPda(owner);
    pdaUpdating = false;
    engine = null;
    restoring = false;
    ownsBoard = connection != null && (game.snapshot().active() || retainedHistory);
    if (!success) game.revokeRecovery("Fox.unrestored");
    showStatus(game.hasCheckpoint() && game.snapshot().initialized() && game.snapshot().active()
        ? game.recoveryStatus() : key);
    refreshDisplay();
    if (success && LizzieFrame.menu != null && Lizzie.leelaz != null) {
      LizzieFrame.menu.toggleEngineMenuStatus(Lizzie.leelaz.isPondering(), Lizzie.leelaz.isThinking);
    }
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
