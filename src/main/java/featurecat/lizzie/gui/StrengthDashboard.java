package featurecat.lizzie.gui;

import featurecat.lizzie.analysis.PlayerStrengthEstimator;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.awt.Rectangle;
import java.util.List;
import java.util.ResourceBundle;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JViewport;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;

/**
 * The content of the player-strength dialog: the title, a toolbar with the view switch and the
 * model controls, the assessment, match and performance views, and a footer for the HumanSL
 * background preparation.
 */
final class StrengthDashboard extends JPanel {
  private static final long serialVersionUID = 1L;
  static final int ASSESSMENT = 0;
  static final int MATCH = 1;
  static final int PERFORMANCE = 2;
  private static final String[] VIEWS = {"assessment", "match", "performance"};

  /** What the dashboard asks of the dialog around it. */
  interface Actions {
    void chooseModel(PlayerStrengthEstimator.StrengthModel model);

    void importModel();

    void showDetails(int view);

    /** Turns the background preparation on or off; returns whether it is on afterwards. */
    boolean setBackgroundEnabled(boolean enabled);

    void prepareNow();
  }

  /** How the HumanSL preparation is doing, which colours the dot before its status. */
  enum Tone {
    IDLE,
    WORKING,
    READY,
    PROBLEM
  }

  private final transient StrengthStyle style;
  private final transient ResourceBundle bundle;
  private final transient Actions actions;
  private final String blackName;
  private final String whiteName;
  private final JLabel subtitle;
  private final StrengthTabs tabs;
  private final StrengthModelPicker picker;
  private final StrengthButton importButton;
  private final StrengthButton detailsButton;
  private final CardLayout cards = new CardLayout();
  private final ViewStack views;
  private final StrengthSwitch background;
  private final StrengthButton prepare;
  private final JLabel status;
  private final DotIcon statusDot;
  private transient PlayerStrengthEstimator.Report report;
  private boolean updatingPicker;

  StrengthDashboard(
      PlayerStrengthEstimator.Report report,
      PlayerStrengthEstimator.StrengthModel[] models,
      boolean backgroundEnabled,
      StrengthStyle style,
      ResourceBundle bundle,
      Actions actions) {
    super(new BorderLayout());
    this.report = report;
    this.style = style;
    this.bundle = bundle;
    this.actions = actions;
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
                bundle.getString("PlayerStrengthEstimate.tab.match"),
                bundle.getString("PlayerStrengthEstimate.tab.performance")),
            ASSESSMENT,
            this::showView,
            style);

    picker =
        new StrengthModelPicker(models, bundle.getString("PlayerStrengthEstimate.model"), style);
    picker.setName("strength40-selector");
    picker.setToolTipText(bundle.getString("PlayerStrengthEstimate.model.tooltip"));
    picker
        .getAccessibleContext()
        .setAccessibleName(bundle.getString("PlayerStrengthEstimate.model"));
    picker.setSelectedItem(report.model);
    picker.addActionListener(
        event -> {
          if (!updatingPicker
              && picker.getSelectedItem() instanceof PlayerStrengthEstimator.StrengthModel model) {
            actions.chooseModel(model);
          }
        });
    importButton =
        new StrengthButton(
            bundle.getString("PlayerStrengthEstimate.importModel"),
            StrengthButton.Glyph.IMPORT,
            style);
    importButton.setToolTipText(bundle.getString("PlayerStrengthEstimate.importModel.tooltip"));
    importButton.addActionListener(event -> actions.importModel());
    detailsButton =
        new StrengthButton(
            bundle.getString("PlayerStrengthEstimate.detailData"),
            StrengthButton.Glyph.DETAILS,
            style);
    detailsButton.addActionListener(event -> actions.showDetails(tabs.selectedIndex()));

    JPanel controls = new JPanel(new FlowLayout(FlowLayout.RIGHT, style.px(8), 0));
    controls.setOpaque(false);
    controls.add(picker);
    controls.add(importButton);
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
    JPanel body = new JPanel(new BorderLayout());
    body.setOpaque(false);
    body.setBorder(BorderFactory.createEmptyBorder(0, style.px(28), style.px(18), style.px(28)));
    body.add(scroll, BorderLayout.CENTER);
    add(body, BorderLayout.CENTER);

    background =
        new StrengthSwitch(
            bundle.getString("HumanSL40.backgroundToggle"), backgroundEnabled, style);
    background.setName("strength40-background");
    background.addActionListener(
        event -> background.setSelected(actions.setBackgroundEnabled(background.isSelected())));
    statusDot = new DotIcon(style.tertiaryText, style.px(7));
    status = new JLabel(" ", statusDot, SwingConstants.LEADING);
    status.setName("strength40-status");
    status.setIconTextGap(style.px(7));
    status.setFont(style.font(Font.PLAIN, 0));
    status.setForeground(style.secondaryText);
    prepare =
        new StrengthButton(
            bundle.getString("HumanSL40.manualRun"), StrengthButton.Glyph.NONE, style);
    prepare.setName("strength40-run");
    prepare.addActionListener(event -> actions.prepareNow());
    JPanel footer =
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
    footer.add(background);
    footer.add(Box.createHorizontalStrut(style.px(20)));
    footer.add(status);
    footer.add(Box.createHorizontalGlue());
    footer.add(prepare);
    footer.add(Box.createVerticalStrut(prepare.getPreferredSize().height));
    add(footer, BorderLayout.SOUTH);

    buildViews();
  }

  /** Shows a new estimate in place, keeping the selected view. */
  void showReport(
      PlayerStrengthEstimator.Report report, PlayerStrengthEstimator.StrengthModel[] models) {
    this.report = report;
    updatingPicker = true;
    try {
      picker.setModel(new DefaultComboBoxModel<>(models));
      picker.setSelectedItem(report.model);
    } finally {
      updatingPicker = false;
    }
    buildViews();
    revalidate();
    repaint();
  }

  void showStatus(String text, Tone tone) {
    status.setText(text == null || text.isBlank() ? " " : text);
    statusDot.color =
        switch (tone) {
          case WORKING -> style.caution;
          case READY -> style.accent;
          case PROBLEM -> style.negative;
          default -> style.tertiaryText;
        };
    status.repaint();
  }

  PlayerStrengthEstimator.Report report() {
    return report;
  }

  int selectedView() {
    return tabs.selectedIndex();
  }

  boolean isPickerOpen() {
    return picker.isPopupVisible();
  }

  StrengthTabs tabs() {
    return tabs;
  }

  StrengthModelPicker picker() {
    return picker;
  }

  StrengthButton importButton() {
    return importButton;
  }

  StrengthButton detailsButton() {
    return detailsButton;
  }

  StrengthSwitch backgroundSwitch() {
    return background;
  }

  StrengthButton prepareButton() {
    return prepare;
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
    cards.show(views, VIEWS[view]);
  }

  private void buildViews() {
    views.removeAll();
    views.add(assessmentView(), VIEWS[ASSESSMENT]);
    views.add(matchView(), VIEWS[MATCH]);
    views.add(performanceView(), VIEWS[PERFORMANCE]);
    cards.show(views, VIEWS[tabs.selectedIndex()]);
    prepare.setVisible(report.model != null && report.model.isHumanSl40());
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
    boolean enough = report.hasEnoughData();
    String note =
        report.model.isHumanSl40()
            ? enough ? "HumanSL40.reviewNote" : "HumanSL40.unavailable"
            : enough
                ? "PlayerStrengthEstimate.reviewOnlyNote"
                : "PlayerStrengthEstimate.needMoreData";
    view.add(new StrengthNote(bundle.getString(note), !enough, style), BorderLayout.SOUTH);
    return view;
  }

  private List<StrengthCompareCard.Row> compareRows() {
    PlayerStrengthEstimator.SideReport black = report.black;
    PlayerStrengthEstimator.SideReport white = report.white;
    boolean blackKnown = black != null && black.hasSamples();
    boolean whiteKnown = white != null && white.hasSamples();
    return List.of(
        new StrengthCompareCard.Row(
            bundle.getString("PlayerStrengthEstimate.firstChoice"),
            blackKnown ? StrengthText.precisePercent(black.firstChoiceRate) : "-",
            whiteKnown ? StrengthText.precisePercent(white.firstChoiceRate) : "-",
            blackKnown ? black.firstChoiceRate : Double.NaN,
            whiteKnown ? white.firstChoiceRate : Double.NaN,
            1),
        new StrengthCompareCard.Row(
            bundle.getString("PlayerStrengthEstimate.goodMoveRate"),
            blackKnown ? StrengthText.precisePercent(black.moveRankGoodMoveRate) : "-",
            whiteKnown ? StrengthText.precisePercent(white.moveRankGoodMoveRate) : "-",
            blackKnown ? black.moveRankGoodMoveRate : Double.NaN,
            whiteKnown ? white.moveRankGoodMoveRate : Double.NaN,
            1),
        new StrengthCompareCard.Row(
            bundle.getString("PlayerStrengthEstimate.avgScoreLoss"),
            blackKnown ? black.averageScoreLossText() : "-",
            whiteKnown ? white.averageScoreLossText() : "-",
            blackKnown && black.scoreSampleCount > 0 ? black.averageScoreLoss : Double.NaN,
            whiteKnown && white.scoreSampleCount > 0 ? white.averageScoreLoss : Double.NaN,
            -1),
        new StrengthCompareCard.Row(
            bundle.getString("PlayerStrengthEstimate.weightedScoreLoss"),
            blackKnown ? black.weightedScoreLossText() : "-",
            whiteKnown ? white.weightedScoreLossText() : "-",
            blackKnown ? black.weightedScoreLoss : Double.NaN,
            whiteKnown ? white.weightedScoreLoss : Double.NaN,
            -1),
        new StrengthCompareCard.Row(
            bundle.getString("PlayerStrengthEstimate.moves"),
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

  /** The coloured dot before the HumanSL status. */
  private static final class DotIcon implements Icon {
    private Color color;
    private final int size;

    private DotIcon(Color color, int size) {
      this.color = color;
      this.size = size;
    }

    @Override
    public void paintIcon(Component component, Graphics graphics, int x, int y) {
      Graphics2D g = StrengthStyle.smooth(graphics);
      try {
        StrengthPaint.dot(g, color, x + size / 2.0, y + size / 2.0, size);
      } finally {
        g.dispose();
      }
    }

    @Override
    public int getIconWidth() {
      return size;
    }

    @Override
    public int getIconHeight() {
      return size;
    }
  }
}
