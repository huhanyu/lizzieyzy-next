package featurecat.lizzie.gui;

import featurecat.lizzie.Config;
import featurecat.lizzie.Lizzie;
import java.awt.AlphaComposite;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Insets;
import java.util.List;
import java.util.MissingResourceException;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.Timer;

/**
 * The AI Coach panel at the right of the window while a coaching game runs: who you play, the
 * game's state, captures and time, your win chance, your last move and, in 边下边提醒, its rating and
 * the game's record so far, then the result and the game's actions. Its buttons never take keyboard
 * focus, so the board keeps Space and its other shortcuts.
 */
public final class CoachPanel extends JPanel {
  static final int WIDTH = 360;
  private static final int GAP = 12;

  static final Color BACKGROUND = new Color(0x17, 0x19, 0x1a);
  static final Color SURFACE = new Color(0x20, 0x24, 0x26);
  static final Color RAISED = new Color(0x26, 0x2b, 0x2d);
  static final Color LINE = new Color(0x2b, 0x30, 0x32);
  static final Color TEXT = new Color(0xec, 0xef, 0xee);
  static final Color MUTED = new Color(0xa2, 0xab, 0xa7);
  static final Color ACCENT = new Color(0x3d, 0xbe, 0xaa);
  static final Color ACCENT_TEXT = new Color(0x8e, 0xe6, 0xd6);
  static final Color WARNING = new Color(0xf2, 0xc1, 0x4e);
  static final Color DANGER = new Color(0xff, 0x8a, 0x7f);

  private final LizzieFrame frame;
  private final Supplier<Config> config;
  private final CoachFocusLayout focusLayout = new CoachFocusLayout();
  private final CoachSummaryView summary = new CoachSummaryView(this::state);
  private final CoachMoveCard moveCard = new CoachMoveCard(this::state);
  private final CoachHistoryView history = new CoachHistoryView(this::state);
  private final JPanel actions;
  private final CoachButton takeBackButton = CoachButton.secondary();
  private final CoachButton passButton = CoachButton.secondary();
  private final CoachButton resignButton = CoachButton.secondary();
  private final CoachButton retryButton = CoachButton.primary();
  private final CoachButton finishButton = CoachButton.quiet();
  private final Timer refreshTimer = new Timer(500, event -> refreshFromController());
  private HumanSlGameController controller;
  private CoachPanelState state = CoachPanelState.EMPTY;

  CoachPanel(LizzieFrame frame) {
    this(frame, () -> Lizzie.config);
  }

  CoachPanel(LizzieFrame frame, Supplier<Config> config) {
    super(null);
    this.frame = frame;
    this.config = config;
    setOpaque(true);
    setBackground(BACKGROUND);
    setBorder(
        BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 1, 0, 0, LINE),
            BorderFactory.createEmptyBorder(18, 18, 18, 18)));
    setVisible(false);
    actions = buildActions();
    add(summary);
    add(moveCard);
    add(history);
    add(actions);
  }

  public void attach(HumanSlGameController value) {
    controller = value;
    if (value == null) {
      return;
    }
    focusLayout.enter(frame, config.get());
    setVisible(true);
    refreshTimer.start();
    refreshFromController();
  }

  public void detach(HumanSlGameController value) {
    if (value != null && controller != value) {
      return;
    }
    controller = null;
    refreshTimer.stop();
    setVisible(false);
    focusLayout.exit(frame, config.get());
  }

  /** Re-reads the game; the controller calls this after every change. */
  public void refresh() {
    refreshFromController();
  }

  CoachPanelState state() {
    return state;
  }

  List<CoachButton> buttons() {
    return List.of(takeBackButton, passButton, resignButton, retryButton, finishButton);
  }

  /** Top to bottom: the summary, the move card, then the buttons at the bottom edge. */
  @Override
  public void doLayout() {
    Insets insets = getInsets();
    int x = insets.left;
    int width = Math.max(0, getWidth() - insets.left - insets.right);
    int y = insets.top;
    summary.setBounds(x, y, width, 0);
    summary.setSize(width, summary.heightFor());
    y += summary.getHeight();
    moveCard.setBounds(x, y + GAP, width, 0);
    int cardHeight = moveCard.heightFor();
    moveCard.setSize(width, cardHeight);
    if (cardHeight > 0) {
      y += GAP + cardHeight;
    }
    int actionsHeight = actions.getPreferredSize().height;
    int actionsTop = getHeight() - insets.bottom - actionsHeight;
    actions.setBounds(x, actionsTop, width, actionsHeight);
    int historyTop = y + GAP;
    history.setBounds(x, historyTop, width, Math.max(0, actionsTop - GAP - historyTop));
  }

  private JPanel buildActions() {
    JPanel panel = new JPanel(new BorderLayout(0, 8));
    panel.setOpaque(false);
    JPanel grid = new JPanel(new GridLayout(0, 2, 8, 8));
    grid.setOpaque(false);
    takeBackButton.setText(text("HumanSlTraining.takeBack", "Take back"));
    passButton.setText(text("HumanSlGame.btn.pass", "Pass"));
    resignButton.setText(text("HumanSlGame.btn.resign", "Resign"));
    retryButton.setText(text("HumanSlTraining.retryAi", "Retry AI"));
    finishButton.setText(text("CoachPanel.finish", "End game"));
    takeBackButton.addActionListener(event -> withController(HumanSlGameController::takeBack));
    passButton.addActionListener(event -> withController(HumanSlGameController::humanPass));
    resignButton.addActionListener(event -> withController(HumanSlGameController::resign));
    retryButton.addActionListener(event -> withController(HumanSlGameController::retryAiMove));
    finishButton.addActionListener(
        event ->
            withController(
                active -> {
                  if (active.isStopRequested() || active.isReviewing()) {
                    active.abort();
                  } else {
                    active.finishAndReturnToBoard();
                  }
                }));
    grid.add(takeBackButton);
    grid.add(passButton);
    grid.add(resignButton);
    grid.add(retryButton);
    panel.add(grid, BorderLayout.CENTER);
    panel.add(finishButton, BorderLayout.SOUTH);
    return panel;
  }

  private void refreshFromController() {
    HumanSlGameController active = controller;
    if (active == null) {
      return;
    }
    if (active.isFinished()) {
      detach(active);
      return;
    }
    state = CoachPanelState.of(active, CoachPanel::text);
    boolean stopping = active.isStopRequested();
    takeBackButton.setEnabled(!stopping && active.canTakeBack());
    passButton.setEnabled(
        !stopping
            && active.isHumanTurn()
            && !active.isReviewing()
            && !active.isGameOver()
            && !active.isCounting());
    resignButton.setEnabled(!stopping && active.canResign());
    retryButton.setVisible(!stopping && active.hasAiFailure() && !active.isReviewing());
    if (active.isExitRecoveryPending()) {
      finishButton.setText(text("HumanSlTraining.retryCleanup", "Retry cleanup"));
      finishButton.setKind(CoachButton.Kind.PRIMARY);
    } else if (stopping) {
      finishButton.setText(text("HumanSlTraining.stopping", "Stopping..."));
      finishButton.setKind(CoachButton.Kind.QUIET);
    } else if (active.isGameOver()) {
      finishButton.setText(text("CoachPanel.review", "Start review"));
      finishButton.setKind(CoachButton.Kind.PRIMARY);
    } else {
      finishButton.setText(text("CoachPanel.finish", "End game"));
      finishButton.setKind(CoachButton.Kind.QUIET);
    }
    finishButton.setEnabled(!active.isExitInProgress());
    revalidate();
    repaint();
  }

  private void withController(Consumer<HumanSlGameController> action) {
    HumanSlGameController active = controller;
    if (active != null) {
      action.accept(active);
    }
    refreshFromController();
  }

  static String text(String key, String fallback) {
    try {
      return Lizzie.resourceBundle == null ? fallback : Lizzie.resourceBundle.getString(key);
    } catch (MissingResourceException e) {
      return fallback;
    }
  }

  /** Keeps translucent overlays from tinting the panel's own background in screenshots. */
  @Override
  protected void paintComponent(Graphics graphics) {
    Graphics2D g = (Graphics2D) graphics.create();
    try {
      g.setComposite(AlphaComposite.Src);
      g.setColor(BACKGROUND);
      g.fillRect(0, 0, getWidth(), getHeight());
    } finally {
      g.dispose();
    }
  }
}
