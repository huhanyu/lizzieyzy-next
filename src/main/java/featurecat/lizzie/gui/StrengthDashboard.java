package featurecat.lizzie.gui;

import featurecat.lizzie.analysis.PlayerStrengthEstimator;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.awt.Rectangle;
import java.text.MessageFormat;
import java.util.List;
import java.util.ResourceBundle;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JViewport;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;

/**
 * The content of the player-strength dialog: the title, a toolbar with the view switch and the
 * model controls, then the assessment, match and performance views. While the figures cannot be
 * computed yet it shows what to do instead: analyze the game, or run the human-style model's
 * one-time calculation. HumanSL's background preparation appears only with that model.
 */
final class StrengthDashboard extends JPanel {
  private static final long serialVersionUID = 1L;
  static final int ASSESSMENT = 0;
  static final int PHASES = 1;
  static final int MATCH = 2;
  static final int PERFORMANCE = 3;
  private static final String[] VIEWS = {"assessment", "phases", "match", "performance"};
  private static final String ANALYSIS_CARD = "analysis";
  private static final String HUMAN_STYLE_CARD = "humanStyle";

  /** What the dashboard asks of the dialog around it. */
  interface Actions {
    void chooseModel(PlayerStrengthEstimator.StrengthModel model);

    void importModel();

    void showDetails(int view);

    /** Turns the background preparation on or off; returns whether it is on afterwards. */
    boolean setBackgroundEnabled(boolean enabled);

    void prepareNow();

    /** Starts analyzing the whole game in the main window. */
    void analyzeGame();

    /** Keeps the phase boundaries the user set for the next games. */
    void savePhaseBounds(int[] bounds);
  }

  /** How the HumanSL preparation is doing, which colours the dot before its status. */
  enum Tone {
    IDLE,
    WORKING,
    READY,
    PROBLEM
  }

  /** What the dashboard shows: the figures, or what to do before they can be computed. */
  enum Stage {
    READY,
    NEEDS_ANALYSIS,
    NEEDS_HUMAN_STYLE
  }

  /**
   * Facts about the game beyond the estimate: whether the standard model has enough analysis,
   * whether analysis is running and how far it got, and whether the human-style weights are here.
   */
  record Context(
      boolean analysisReady,
      boolean analyzing,
      int analyzedMoves,
      int totalMoves,
      boolean humanStyleInstalled) {
    static final Context NONE = new Context(true, false, 0, 0, true);
  }

  private final transient StrengthStyle style;
  private final transient ResourceBundle bundle;
  private final transient Actions actions;
  private final String blackName;
  private final String whiteName;
  private final JLabel subtitle;
  private final StrengthTabs tabs;
  private final StrengthModelPicker picker;
  private final StrengthButton detailsButton;
  private final CardLayout cards = new CardLayout();
  private final ViewStack views;
  private final StrengthBanner analysisBanner;
  private final StrengthEmptyState analysisState;
  private final StrengthEmptyState humanStyleState;
  private final JPanel footer;
  private final StrengthSwitch background;
  private final JLabel status;
  private final transient StrengthDot statusDot;
  private transient PlayerStrengthEstimator.Report report;
  private transient Context context;
  private Stage stage;
  private int[] phaseBounds;

  StrengthDashboard(
      PlayerStrengthEstimator.Report report,
      PlayerStrengthEstimator.StrengthModel[] models,
      boolean backgroundEnabled,
      int[] phaseBounds,
      Context context,
      StrengthStyle style,
      ResourceBundle bundle,
      Actions actions) {
    super(new BorderLayout());
    this.style = style;
    this.bundle = bundle;
    this.actions = actions;
    this.phaseBounds = StrengthPhases.normalize(phaseBounds);
    blackName = bundle.getString("SidebarHeader.black");
    whiteName = bundle.getString("SidebarHeader.white");
    setOpaque(true);
    setBackground(style.background);

    JLabel title = new JLabel(bundle.getString("PlayerStrengthEstimate.title"));
    title.setFont(style.font(Font.BOLD, 10));
    title.setForeground(style.text);
    subtitle = new JLabel(subtitleText(ASSESSMENT));
    subtitle.setFont(style.font(Font.PLAIN, 1));
    subtitle.setForeground(style.secondaryText);

    tabs =
        new StrengthTabs(
            List.of(
                bundle.getString("PlayerStrengthEstimate.tab.assessment"),
                bundle.getString("PlayerStrengthEstimate.tab.phases"),
                bundle.getString("PlayerStrengthEstimate.tab.match"),
                bundle.getString("PlayerStrengthEstimate.tab.performance")),
            ASSESSMENT,
            this::showView,
            style);
    picker =
        new StrengthModelPicker(
            models, report.model, bundle, style, actions::chooseModel, actions::importModel);
    picker.setName("strength40-selector");
    detailsButton =
        new StrengthButton(
            bundle.getString("PlayerStrengthEstimate.detailData"),
            StrengthButton.Glyph.DETAILS,
            style);
    detailsButton.addActionListener(event -> actions.showDetails(tabs.selectedIndex()));

    JPanel controls = new JPanel(new FlowLayout(FlowLayout.RIGHT, style.px(8), 0));
    controls.setOpaque(false);
    controls.add(picker);
    controls.add(detailsButton);
    JPanel header = new JPanel(new HeaderLayout(title, subtitle, tabs, controls));
    header.setOpaque(false);
    header.setBorder(
        BorderFactory.createEmptyBorder(style.px(22), style.px(28), style.px(18), style.px(28)));
    header.add(title);
    header.add(subtitle);
    header.add(tabs);
    header.add(controls);
    add(header, BorderLayout.NORTH);

    analysisState = new StrengthEmptyState(StrengthEmptyState.Kind.ANALYSIS, false, bundle, style);
    analysisState.actionButton().addActionListener(event -> actions.analyzeGame());
    humanStyleState =
        new StrengthEmptyState(
            StrengthEmptyState.Kind.HUMAN_STYLE, backgroundEnabled, bundle, style);
    humanStyleState.actionButton().addActionListener(event -> actions.prepareNow());
    views = new ViewStack(cards);
    JScrollPane scroll =
        new JScrollPane(
            views,
            JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
            JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
    scroll.setBorder(BorderFactory.createEmptyBorder());
    scroll.setOpaque(false);
    scroll.getViewport().setOpaque(false);
    scroll.getVerticalScrollBar().setUI(new SlimScrollBarUI(style.tertiaryText));
    scroll.getVerticalScrollBar().setOpaque(false);
    scroll.getVerticalScrollBar().setUnitIncrement(style.px(16));
    analysisBanner =
        new StrengthBanner(bundle.getString("PlayerStrengthEstimate.empty.analysis.action"), style);
    analysisBanner.actionButton().addActionListener(event -> actions.analyzeGame());
    JPanel body = new JPanel(new BorderLayout(0, style.px(12)));
    body.setOpaque(false);
    body.setBorder(BorderFactory.createEmptyBorder(0, style.px(28), style.px(18), style.px(28)));
    body.add(analysisBanner, BorderLayout.NORTH);
    body.add(scroll, BorderLayout.CENTER);
    add(body, BorderLayout.CENTER);

    background =
        new StrengthSwitch(
            bundle.getString("HumanSL40.backgroundToggle"), backgroundEnabled, style);
    background.setName("strength40-background");
    background.setToolTipText(bundle.getString("HumanSL40.backgroundToggle.description"));
    statusDot = new StrengthDot(style, style.px(7));
    status = new JLabel(" ", statusDot, SwingConstants.LEADING);
    status.setName("strength40-status");
    status.setIconTextGap(style.px(7));
    status.setFont(style.font(Font.PLAIN, 0));
    status.setForeground(style.secondaryText);
    for (StrengthSwitch control : List.of(background, humanStyleState.backgroundSwitch())) {
      control.addActionListener(
          event -> showBackgroundEnabled(actions.setBackgroundEnabled(control.isSelected())));
    }
    footer =
        new JPanel() {
          private static final long serialVersionUID = 1L;

          @Override
          protected void paintComponent(Graphics graphics) {
            graphics.setColor(style.border);
            graphics.fillRect(0, 0, getWidth(), 1);
          }
        };
    footer.setLayout(new BoxLayout(footer, BoxLayout.X_AXIS));
    footer.setOpaque(false);
    footer.setBorder(
        BorderFactory.createEmptyBorder(style.px(12), style.px(28), style.px(12), style.px(28)));
    footer.add(status);
    footer.add(Box.createHorizontalGlue());
    footer.add(background);
    footer.add(Box.createRigidArea(new Dimension(0, style.px(26))));
    add(footer, BorderLayout.SOUTH);

    showReport(report, models, context);
  }

  /** Shows a new estimate in place, keeping the selected view. */
  void showReport(
      PlayerStrengthEstimator.Report report,
      PlayerStrengthEstimator.StrengthModel[] models,
      Context context) {
    this.report = report;
    this.context = context == null ? Context.NONE : context;
    picker.setModels(models, report.model);
    Stage next = stageFor(report, this.context);
    if (next == Stage.READY) {
      buildViews();
    } else if (stage == Stage.READY || stage == null) {
      clearViews();
    }
    stage = next;
    boolean ready = stage == Stage.READY;
    tabs.setEnabled(ready);
    detailsButton.setEnabled(ready);
    footer.setVisible(ready && isHumanStyle());
    boolean analyzing = this.context.analyzing();
    analysisState.setActionEnabled(!analyzing);
    analysisState.showShortGame(
        stage == Stage.NEEDS_ANALYSIS
            && !analyzing
            && this.context.analyzedMoves() >= this.context.totalMoves());
    analysisState.showProgress(analysisText(), Tone.WORKING, this.context.analyzedMoves(), total());
    humanStyleState.setDownloadNoteVisible(!this.context.humanStyleInstalled());
    if (ready && analyzing) {
      analysisBanner.showText(analysisText(), false);
    } else if (ready && isPartlyAnalyzed()) {
      analysisBanner.showText(
          MessageFormat.format(
              bundle.getString("PlayerStrengthEstimate.partial"),
              this.context.analyzedMoves(),
              total()),
          true);
    } else {
      analysisBanner.showText(null, false);
    }
    cards.show(
        views,
        switch (stage) {
          case NEEDS_ANALYSIS -> ANALYSIS_CARD;
          case NEEDS_HUMAN_STYLE -> HUMAN_STYLE_CARD;
          default -> VIEWS[tabs.selectedIndex()];
        });
    revalidate();
    repaint();
  }

  /** The figures need enough analysed moves; the human-style model also its own data. */
  static Stage stageFor(PlayerStrengthEstimator.Report report, Context context) {
    if (report.hasEnoughData()) {
      return Stage.READY;
    }
    boolean humanStyle = report.model != null && report.model.isHumanSl40();
    return humanStyle && context.analysisReady() ? Stage.NEEDS_HUMAN_STYLE : Stage.NEEDS_ANALYSIS;
  }

  private boolean isHumanStyle() {
    return report.model != null && report.model.isHumanSl40();
  }

  private int total() {
    return Math.max(context.totalMoves(), context.analyzedMoves());
  }

  /** Less than nine in ten moves of the game have analysis, so the figures may be off. */
  private boolean isPartlyAnalyzed() {
    return context.analyzedMoves() * 10L < total() * 9L;
  }

  /** "Analyzing the whole game: 42 of 326 moves done", or null when no analysis is running. */
  private String analysisText() {
    if (!context.analyzing()) {
      return null;
    }
    return MessageFormat.format(
        bundle.getString("PlayerStrengthEstimate.empty.analysis.running"),
        context.analyzedMoves(),
        total());
  }

  /**
   * Shows how the HumanSL preparation is doing in the footer and on the human-style page, with its
   * progress when {@code total} moves are known.
   */
  void showStatus(String text, Tone tone, int done, int total) {
    String shown = text == null || text.isBlank() ? " " : text;
    status.setText(shown);
    statusDot.setTone(tone);
    status.repaint();
    humanStyleState.showProgress(text, tone, done, total);
  }

  private void showBackgroundEnabled(boolean enabled) {
    background.setSelected(enabled);
    humanStyleState.backgroundSwitch().setSelected(enabled);
  }

  PlayerStrengthEstimator.Report report() {
    return report;
  }

  Stage stage() {
    return stage;
  }

  int selectedView() {
    return tabs.selectedIndex();
  }

  boolean isPickerOpen() {
    return picker.isMenuOpen();
  }

  StrengthTabs tabs() {
    return tabs;
  }

  StrengthModelPicker picker() {
    return picker;
  }

  StrengthButton detailsButton() {
    return detailsButton;
  }

  /** The background switch in the footer, shown with the human-style figures. */
  StrengthSwitch backgroundSwitch() {
    return background;
  }

  JPanel footer() {
    return footer;
  }

  StrengthEmptyState analysisState() {
    return analysisState;
  }

  StrengthEmptyState humanStyleState() {
    return humanStyleState;
  }

  StrengthBanner analysisBanner() {
    return analysisBanner;
  }

  JLabel subtitle() {
    return subtitle;
  }

  JLabel status() {
    return status;
  }

  /** The view on screen. */
  JComponent currentView() {
    for (Component view : views.getComponents()) {
      if (view.isVisible()) {
        return (JComponent) view;
      }
    }
    return null;
  }

  private String subtitleText(int view) {
    return bundle.getString("PlayerStrengthEstimate.subtitle." + VIEWS[view]);
  }

  private void showView(int view) {
    subtitle.setText(subtitleText(view));
    if (stage == Stage.READY) {
      cards.show(views, VIEWS[view]);
    }
  }

  private void clearViews() {
    views.removeAll();
    views.add(analysisState, ANALYSIS_CARD);
    views.add(humanStyleState, HUMAN_STYLE_CARD);
  }

  private void buildViews() {
    clearViews();
    views.add(assessmentView(), VIEWS[ASSESSMENT]);
    views.add(phasesView(), VIEWS[PHASES]);
    views.add(matchView(), VIEWS[MATCH]);
    views.add(performanceView(), VIEWS[PERFORMANCE]);
  }

  private JComponent phasesView() {
    return new StrengthPhaseView(
        report,
        phaseBounds,
        lastMove(),
        blackName,
        whiteName,
        bundle,
        style,
        bounds -> {
          phaseBounds = bounds.clone();
          actions.savePhaseBounds(bounds.clone());
        });
  }

  /** The last move of the game: the main line's length, or the last analysed move if later. */
  private int lastMove() {
    int last = context.totalMoves();
    for (PlayerStrengthEstimator.SideReport side :
        new PlayerStrengthEstimator.SideReport[] {report.black, report.white}) {
      if (side != null) {
        for (PlayerStrengthEstimator.Sample sample : side.samples) {
          last = Math.max(last, sample.moveNumber);
        }
      }
    }
    return last;
  }

  /** Where the phases are split now. */
  int[] phaseBounds() {
    return phaseBounds.clone();
  }

  /** The phases of the game as the phases view shows them. */
  List<StrengthPhases.Phase> phases() {
    return StrengthPhases.split(report, phaseBounds, lastMove());
  }

  private static JPanel column(int gap) {
    JPanel panel = new JPanel(new BorderLayout(0, gap));
    panel.setOpaque(false);
    return panel;
  }

  private JPanel pair(JComponent black, JComponent white) {
    JPanel pair = new JPanel(new GridLayout(1, 2, style.px(16), 0));
    pair.setOpaque(false);
    pair.add(black);
    pair.add(white);
    return pair;
  }

  private JComponent assessmentView() {
    JPanel view = column(style.px(14));
    view.add(
        pair(
            new StrengthRankCard(blackName, true, report.black, bundle, style),
            new StrengthRankCard(whiteName, false, report.white, bundle, style)),
        BorderLayout.NORTH);
    view.add(new StrengthCompareCard(compareRows(), style), BorderLayout.CENTER);
    String note =
        report.model.isHumanSl40()
            ? "HumanSL40.reviewNote"
            : "PlayerStrengthEstimate.reviewOnlyNote";
    view.add(new StrengthNote(bundle.getString(note), false, style), BorderLayout.SOUTH);
    return view;
  }

  private String help(String key) {
    return bundle.getString("PlayerStrengthEstimate.help." + key);
  }

  private List<StrengthCompareCard.Row> compareRows() {
    PlayerStrengthEstimator.SideReport black = report.black;
    PlayerStrengthEstimator.SideReport white = report.white;
    boolean blackKnown = black != null && black.hasSamples();
    boolean whiteKnown = white != null && white.hasSamples();
    return List.of(
        new StrengthCompareCard.Row(
            bundle.getString("PlayerStrengthEstimate.firstChoice"),
            help("firstChoice"),
            blackKnown ? StrengthText.precisePercent(black.firstChoiceRate) : "-",
            whiteKnown ? StrengthText.precisePercent(white.firstChoiceRate) : "-",
            blackKnown ? black.firstChoiceRate : Double.NaN,
            whiteKnown ? white.firstChoiceRate : Double.NaN,
            1),
        new StrengthCompareCard.Row(
            bundle.getString("PlayerStrengthEstimate.goodMoveRate"),
            help("goodMoveRate"),
            blackKnown ? StrengthText.precisePercent(black.moveRankGoodMoveRate) : "-",
            whiteKnown ? StrengthText.precisePercent(white.moveRankGoodMoveRate) : "-",
            blackKnown ? black.moveRankGoodMoveRate : Double.NaN,
            whiteKnown ? white.moveRankGoodMoveRate : Double.NaN,
            1),
        new StrengthCompareCard.Row(
            bundle.getString("PlayerStrengthEstimate.avgScoreLoss"),
            help("avgScoreLoss"),
            blackKnown ? black.averageScoreLossText() : "-",
            whiteKnown ? white.averageScoreLossText() : "-",
            blackKnown && black.scoreSampleCount > 0 ? black.averageScoreLoss : Double.NaN,
            whiteKnown && white.scoreSampleCount > 0 ? white.averageScoreLoss : Double.NaN,
            -1),
        new StrengthCompareCard.Row(
            bundle.getString("PlayerStrengthEstimate.weightedScoreLoss"),
            help("weightedScoreLoss"),
            blackKnown ? black.weightedScoreLossText() : "-",
            whiteKnown ? white.weightedScoreLossText() : "-",
            blackKnown ? black.weightedScoreLoss : Double.NaN,
            whiteKnown ? white.weightedScoreLoss : Double.NaN,
            -1),
        new StrengthCompareCard.Row(
            bundle.getString("PlayerStrengthEstimate.moves"),
            help("moves"),
            String.valueOf(black == null ? 0 : black.sampleCount),
            String.valueOf(white == null ? 0 : white.sampleCount),
            black == null ? 0 : black.sampleCount,
            white == null ? 0 : white.sampleCount,
            0));
  }

  private JComponent matchView() {
    JPanel view = column(style.px(14));
    view.add(
        pair(
            new StrengthAccuracyCard(blackName, true, report.black, bundle, style),
            new StrengthAccuracyCard(whiteName, false, report.white, bundle, style)),
        BorderLayout.NORTH);
    view.add(
        new StrengthTimeline(report, blackName, whiteName, bundle, style), BorderLayout.CENTER);
    view.add(
        new StrengthNote(bundle.getString("PlayerStrengthEstimate.match.reviewNote"), false, style),
        BorderLayout.SOUTH);
    return view;
  }

  private JComponent performanceView() {
    JPanel view = column(0);
    view.add(
        new StrengthDistributionCard(report, blackName, whiteName, bundle, style),
        BorderLayout.CENTER);
    return view;
  }

  /**
   * The views, one at a time. They fill the window when it is tall enough for them and scroll
   * otherwise.
   */
  private final class ViewStack extends JPanel implements Scrollable {
    private static final long serialVersionUID = 1L;

    private ViewStack(CardLayout layout) {
      super(layout);
      setOpaque(false);
    }

    @Override
    public Dimension getPreferredScrollableViewportSize() {
      return getPreferredSize();
    }

    @Override
    public int getScrollableUnitIncrement(Rectangle visible, int orientation, int direction) {
      return style.px(16);
    }

    @Override
    public int getScrollableBlockIncrement(Rectangle visible, int orientation, int direction) {
      return Math.max(style.px(16), visible.height - style.px(32));
    }

    @Override
    public boolean getScrollableTracksViewportWidth() {
      return true;
    }

    @Override
    public boolean getScrollableTracksViewportHeight() {
      return getParent() instanceof JViewport viewport
          && viewport.getHeight() >= getMinimumSize().height;
    }
  }

  /**
   * Lays out the title and the subtitle, then the view switch on the left and the model controls on
   * the right of one row. When that row is too narrow, the controls go on a row of their own.
   */
  private final class HeaderLayout implements LayoutManager {
    private final Component title;
    private final Component subtitle;
    private final Component tabs;
    private final Component controls;

    private HeaderLayout(Component title, Component subtitle, Component tabs, Component controls) {
      this.title = title;
      this.subtitle = subtitle;
      this.tabs = tabs;
      this.controls = controls;
    }

    @Override
    public void addLayoutComponent(String name, Component component) {}

    @Override
    public void removeLayoutComponent(Component component) {}

    private boolean wraps(int width) {
      return tabs.getPreferredSize().width + style.px(24) + controls.getPreferredSize().width
          > width;
    }

    private int toolbarHeight(int width) {
      Dimension tabSize = tabs.getPreferredSize();
      Dimension controlSize = controls.getPreferredSize();
      return wraps(width)
          ? tabSize.height + style.px(10) + controlSize.height
          : Math.max(tabSize.height, controlSize.height);
    }

    @Override
    public Dimension preferredLayoutSize(Container parent) {
      Insets insets = parent.getInsets();
      int width =
          parent.getWidth() > 0
              ? parent.getWidth() - insets.left - insets.right
              : tabs.getPreferredSize().width + style.px(24) + controls.getPreferredSize().width;
      int height =
          title.getPreferredSize().height
              + style.px(2)
              + subtitle.getPreferredSize().height
              + style.px(18)
              + toolbarHeight(width);
      return new Dimension(
          Math.max(title.getPreferredSize().width, tabs.getPreferredSize().width)
              + insets.left
              + insets.right,
          height + insets.top + insets.bottom);
    }

    @Override
    public Dimension minimumLayoutSize(Container parent) {
      return preferredLayoutSize(parent);
    }

    @Override
    public void layoutContainer(Container parent) {
      Insets insets = parent.getInsets();
      int x = insets.left;
      int width = parent.getWidth() - insets.left - insets.right;
      int y = insets.top;
      int titleHeight = title.getPreferredSize().height;
      title.setBounds(x, y, width, titleHeight);
      y += titleHeight + style.px(2);
      int subtitleHeight = subtitle.getPreferredSize().height;
      subtitle.setBounds(x, y, width, subtitleHeight);
      y += subtitleHeight + style.px(18);
      Dimension tabSize = tabs.getPreferredSize();
      Dimension controlSize = controls.getPreferredSize();
      if (wraps(width)) {
        tabs.setBounds(x, y, Math.min(width, tabSize.width), tabSize.height);
        controls.setBounds(
            x,
            y + tabSize.height + style.px(10),
            Math.min(width, controlSize.width),
            controlSize.height);
        ((FlowLayout) ((JPanel) controls).getLayout()).setAlignment(FlowLayout.LEFT);
      } else {
        int row = Math.max(tabSize.height, controlSize.height);
        tabs.setBounds(x, y + (row - tabSize.height) / 2, tabSize.width, tabSize.height);
        controls.setBounds(
            x + width - controlSize.width,
            y + (row - controlSize.height) / 2,
            controlSize.width,
            controlSize.height);
        ((FlowLayout) ((JPanel) controls).getLayout()).setAlignment(FlowLayout.RIGHT);
      }
    }
  }
}
