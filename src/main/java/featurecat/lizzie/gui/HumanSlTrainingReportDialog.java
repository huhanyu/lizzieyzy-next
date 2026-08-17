package featurecat.lizzie.gui;

import featurecat.lizzie.Lizzie;
import featurecat.lizzie.rules.Board;
import featurecat.lizzie.rules.Stone;
import featurecat.lizzie.training.HumanMoveDecision;
import featurecat.lizzie.training.TrainingMoveAssessment;
import featurecat.lizzie.training.TrainingSessionReport;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.Window;
import java.text.MessageFormat;
import java.util.ResourceBundle;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingConstants;
import javax.swing.JTextArea;
import javax.swing.WindowConstants;

/** Review report focused on the three highest-value learning positions. */
public final class HumanSlTrainingReportDialog extends JDialog {
  private static final long serialVersionUID = 1L;
  private final ResourceBundle resources = Lizzie.resourceBundle;
  private final HumanSlGameController controller;
  private final TrainingSessionReport report;

  public HumanSlTrainingReportDialog(
      Window owner, HumanSlGameController controller, TrainingSessionReport report) {
    super(
        owner,
        Lizzie.resourceBundle.getString("HumanSlTraining.report.title"),
        java.awt.Dialog.ModalityType.MODELESS);
    this.controller = controller;
    this.report = report;
    setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE);
    setContentPane(buildContent());
    pack();
    setMinimumSize(new Dimension(900, 520));
    setSize(Math.max(1040, getWidth()), Math.max(590, getHeight()));
    setLocationRelativeTo(owner);
  }

  public void showReport() {
    if (!isVisible()) {
      setVisible(true);
    }
    toFront();
  }

  private JComponent buildContent() {
    JPanel root = new JPanel(new BorderLayout(0, 14));
    root.setName("humanSlTrainingReport");
    root.setBackground(HumanSlTrainingStyle.BACKGROUND);
    root.setBorder(BorderFactory.createEmptyBorder(18, 20, 18, 20));
    root.add(buildHeader(), BorderLayout.NORTH);
    root.add(buildRows(), BorderLayout.CENTER);
    root.add(buildSummary(), BorderLayout.SOUTH);
    AccessibilitySupport.applyToTree(root);
    return root;
  }

  private JComponent buildHeader() {
    JPanel header = new JPanel(new BorderLayout(16, 0));
    header.setOpaque(false);
    JPanel copy = new JPanel();
    copy.setOpaque(false);
    copy.setLayout(new BoxLayout(copy, BoxLayout.Y_AXIS));
    String titleText = text("HumanSlTraining.report.title", "Training report");
    JLabel title = new JLabel(titleText);
    title.setForeground(HumanSlTrainingStyle.TEXT);
    title.setFont(font(titleText, Font.BOLD, 24));
    String subtitleText =
        text(
            "HumanSlTraining.report.subtitle",
            "Focus on three key positions: your move, a common human move, and KataGo's best move.");
    JTextArea subtitle = new JTextArea(subtitleText);
    subtitle.setEditable(false);
    subtitle.setFocusable(false);
    subtitle.setOpaque(false);
    subtitle.setBorder(null);
    subtitle.setLineWrap(true);
    subtitle.setWrapStyleWord(true);
    subtitle.setRows(2);
    subtitle.setColumns(48);
    subtitle.setForeground(HumanSlTrainingStyle.MUTED);
    subtitle.setFont(font(subtitleText, Font.PLAIN, 13));
    title.setAlignmentX(LEFT_ALIGNMENT);
    subtitle.setAlignmentX(LEFT_ALIGNMENT);
    copy.add(title);
    copy.add(Box.createVerticalStrut(4));
    copy.add(subtitle);
    header.add(copy, BorderLayout.CENTER);

    JPanel actions = new JPanel();
    actions.setOpaque(false);
    JFontButton save = new JFontButton(text("HumanSlTraining.report.save", "Save report"));
    JFontButton newTraining =
        new JFontButton(text("HumanSlTraining.report.new", "New training"));
    HumanSlTrainingStyle.styleSecondary(save);
    HumanSlTrainingStyle.stylePrimary(newTraining);
    save.addActionListener(event -> controller.saveTrainingReport());
    newTraining.addActionListener(
        event -> {
          setVisible(false);
          Lizzie.frame.startHumanSlGameDialog();
        });
    actions.add(save);
    actions.add(newTraining);
    header.add(actions, BorderLayout.EAST);
    return header;
  }

  private JComponent buildRows() {
    HumanSlTrainingStyle.RoundedPanel table =
        new HumanSlTrainingStyle.RoundedPanel(
            HumanSlTrainingStyle.CARD, HumanSlTrainingStyle.BORDER, 14);
    table.setLayout(new GridBagLayout());
    table.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
    if (report == null || report.isEmpty()) {
      String emptyText =
          text(
              "HumanSlTraining.report.empty",
              "There are not enough analyzed human moves for a detailed report.");
      JLabel empty = new JLabel(emptyText);
      empty.setForeground(HumanSlTrainingStyle.MUTED);
      empty.setFont(font(emptyText, Font.PLAIN, 13));
      table.add(empty);
      return table;
    }

    String[] headings = {
      text("HumanSlTraining.report.position", "Position"),
      text("HumanSlTraining.report.actual", "Your move"),
      text("HumanSlTraining.report.common", "Common human"),
      text("HumanSlTraining.report.best", "KataGo best"),
      text("HumanSlTraining.report.loss", "Point loss"),
      text("HumanSlTraining.report.winrate", "Winrate"),
      text("HumanSlTraining.report.human", "Human choice"),
      text("HumanSlTraining.report.actions", "Actions")
    };
    GridBagConstraints c = new GridBagConstraints();
    c.gridy = 0;
    c.fill = GridBagConstraints.BOTH;
    c.insets = new Insets(0, 2, 5, 2);
    for (int column = 0; column < headings.length; column++) {
      c.gridx = column;
      c.weightx = column >= 1 && column <= 3 ? 1.0 : 0.0;
      JLabel heading = new JLabel(headings[column]);
      heading.setHorizontalAlignment(SwingConstants.CENTER);
      heading.setForeground(HumanSlTrainingStyle.MUTED);
      heading.setFont(font(headings[column], Font.BOLD, 11));
      table.add(heading, c);
    }

    int row = 1;
    for (TrainingMoveAssessment assessment : report.assessments()) {
      addRow(table, row++, assessment.decision);
    }
    JScrollPane scroll = new JScrollPane(table);
    scroll.setBorder(null);
    scroll.getViewport().setOpaque(false);
    scroll.setOpaque(false);
    scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
    return scroll;
  }

  private void addRow(JPanel table, int row, HumanMoveDecision decision) {
    GridBagConstraints c = new GridBagConstraints();
    c.gridy = row;
    c.fill = GridBagConstraints.BOTH;
    c.insets = new Insets(3, 2, 3, 2);
    c.gridx = 0;
    c.weightx = 0.0;
    String moveNumberText =
        MessageFormat.format(
            text("HumanSlTraining.report.moveNumber", "Move {0}"), decision.moveNumber);
    JLabel moveNumber = new JLabel(moveNumberText);
    moveNumber.setHorizontalAlignment(SwingConstants.CENTER);
    moveNumber.setForeground(HumanSlTrainingStyle.TEXT);
    moveNumber.setFont(font(moveNumberText, Font.BOLD, 13));
    table.add(cell(moveNumber), c);

    c.gridx = 1;
    c.weightx = 1.0;
    table.add(cell(new PositionPreview(decision, decision.actualMove, "A")), c);
    c.gridx = 2;
    table.add(cell(new PositionPreview(decision, decision.commonHumanMove, "H")), c);
    c.gridx = 3;
    table.add(cell(new PositionPreview(decision, decision.kataGoBestMove, "K")), c);

    c.gridx = 4;
    c.weightx = 0.0;
    table.add(metricCell(formatScore(decision.scoreLoss), true, 66), c);
    c.gridx = 5;
    table.add(metricCell(formatPercent(decision.winrateLoss), true, 80), c);
    c.gridx = 6;
    table.add(metricCell(formatPercent(decision.humanPolicyProbability), false, 104), c);

    c.gridx = 7;
    JPanel actions = new JPanel(new GridBagLayout());
    actions.setOpaque(false);
    GridBagConstraints action = new GridBagConstraints();
    action.gridx = 0;
    action.gridy = 0;
    action.fill = GridBagConstraints.HORIZONTAL;
    JFontButton retry =
        new JFontButton(text("HumanSlTraining.report.retry", "Replay this position"));
    JFontButton view = new JFontButton(text("HumanSlTraining.report.view", "View position"));
    HumanSlTrainingStyle.stylePrimary(retry);
    HumanSlTrainingStyle.styleSecondary(view);
    retry.addActionListener(
        event -> {
          setVisible(false);
          controller.retryReportPosition(decision);
        });
    view.addActionListener(
        event -> {
          Lizzie.board.navigateToNode(decision.positionBeforeMove);
          Lizzie.frame.refresh();
          Lizzie.frame.setMainPanelFocus();
        });
    actions.add(retry, action);
    action.gridy = 1;
    action.insets = new Insets(5, 0, 0, 0);
    actions.add(view, action);
    table.add(cell(actions), c);
  }

  private JPanel cell(JComponent child) {
    JPanel panel = new JPanel(new BorderLayout());
    panel.setBackground(HumanSlTrainingStyle.CARD);
    panel.setBorder(BorderFactory.createCompoundBorder(new HumanSlTrainingStyle.RoundedBorder(new Color(228, 224, 214), 8), BorderFactory.createEmptyBorder(5, 6, 5, 6)));
    panel.add(child, BorderLayout.CENTER);
    return panel;
  }

  private JComponent metricCell(String value, boolean warning, int width) {
    JPanel panel = cell(metric(value, warning));
    Dimension preferred = panel.getPreferredSize();
    panel.setMinimumSize(new Dimension(width, preferred.height));
    panel.setPreferredSize(new Dimension(width, preferred.height));
    return panel;
  }

  private JLabel metric(String value, boolean warning) {
    JLabel label = new JLabel(value);
    label.setHorizontalAlignment(SwingConstants.CENTER);
    label.setForeground(warning ? HumanSlTrainingStyle.WARNING : HumanSlTrainingStyle.TEXT);
    label.setFont(font(value, Font.BOLD, 13));
    return label;
  }

  private JComponent buildSummary() {
    HumanSlTrainingStyle.RoundedPanel panel =
        new HumanSlTrainingStyle.RoundedPanel(
            HumanSlTrainingStyle.CARD_ALT, HumanSlTrainingStyle.BORDER, 12);
    panel.setLayout(new BorderLayout());
    panel.setBorder(BorderFactory.createEmptyBorder(10, 14, 10, 14));
    double probability = report == null ? Double.NaN : report.averageHumanProbability();
    String summaryText =
        Double.isFinite(probability)
            ? MessageFormat.format(
                text(
                    "HumanSlTraining.report.summary",
                    "Average human-style choice probability in key positions: {0}"),
                formatPercent(probability))
            : text(
                "HumanSlTraining.report.summaryUnavailable",
                "Review the highlighted positions to build more reliable habits.");
    JLabel summary = new JLabel(summaryText);
    summary.setForeground(HumanSlTrainingStyle.TEXT);
    summary.setFont(font(summaryText, Font.BOLD, 12));
    panel.add(summary, BorderLayout.WEST);
    return panel;
  }

  private Font font(String value, int style, int size) {
    return HumanSlTrainingStyle.fontForText(value, style, size);
  }

  private String text(String key, String fallback) {
    try {
      return resources.getString(key);
    } catch (Exception ignored) {
      return fallback;
    }
  }

  private static String formatScore(double value) {
    return Double.isFinite(value)
        ? String.format(java.util.Locale.US, "%.1f", value)
        : "-";
  }

  private static String formatPercent(double value) {
    return Double.isFinite(value)
        ? String.format(java.util.Locale.US, "%.1f%%", value * 100.0)
        : "-";
  }

  private static final class PositionPreview extends JComponent {
    private static final long serialVersionUID = 1L;
    private final HumanMoveDecision decision;
    private final String move;
    private final String badge;

    private PositionPreview(HumanMoveDecision decision, String move, String badge) {
      this.decision = decision;
      this.move = move;
      this.badge = badge;
      setPreferredSize(new Dimension(128, 92));
      setMinimumSize(new Dimension(110, 80));
    }

    @Override
    protected void paintComponent(Graphics graphics) {
      Graphics2D g2 = (Graphics2D) graphics.create();
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      int padding = 7;
      int size = Math.min(getWidth() - padding * 2, getHeight() - padding * 2);
      int left = (getWidth() - size) / 2;
      int top = (getHeight() - size) / 2;
      g2.setColor(new Color(222, 184, 111));
      g2.fillRoundRect(left, top, size, size, 8, 8);
      int width = decision.boardWidth;
      int height = decision.boardHeight;
      int grid = Math.max(1, size - 12);
      double dx = grid / (double) Math.max(1, width - 1);
      double dy = grid / (double) Math.max(1, height - 1);
      int ox = left + 6;
      int oy = top + 6;
      g2.setColor(new Color(83, 63, 35, 175));
      g2.setStroke(new BasicStroke(0.7f));
      for (int x = 0; x < width; x++) {
        int px = ox + (int) Math.round(x * dx);
        g2.drawLine(px, oy, px, oy + grid);
      }
      for (int y = 0; y < height; y++) {
        int py = oy + (int) Math.round(y * dy);
        g2.drawLine(ox, py, ox + grid, py);
      }
      Stone[] stones = decision.positionBeforeMove.getData().stones;
      int stoneSize = Math.max(3, (int) Math.floor(Math.min(dx, dy) * 0.88));
      for (int x = 0; x < width; x++) {
        for (int y = 0; y < height; y++) {
          Stone stone = stones[x * height + y];
          if (stone != Stone.BLACK && stone != Stone.WHITE) {
            continue;
          }
          int px = ox + (int) Math.round(x * dx) - stoneSize / 2;
          int py = oy + (int) Math.round(y * dy) - stoneSize / 2;
          g2.setColor(stone == Stone.BLACK ? new Color(34, 35, 34) : new Color(249, 247, 239));
          g2.fillOval(px, py, stoneSize, stoneSize);
          g2.setColor(new Color(60, 60, 55, 120));
          g2.drawOval(px, py, stoneSize, stoneSize);
        }
      }
      int[] coords = Board.asCoordinates(move, height).orElse(null);
      if (coords != null
          && coords[0] >= 0
          && coords[0] < width
          && coords[1] >= 0
          && coords[1] < height) {
        int marker = Math.max(12, stoneSize + 4);
        int px = ox + (int) Math.round(coords[0] * dx) - marker / 2;
        int py = oy + (int) Math.round(coords[1] * dy) - marker / 2;
        g2.setColor(HumanSlTrainingStyle.ACCENT);
        g2.fillOval(px, py, marker, marker);
        g2.setColor(Color.WHITE);
        g2.setFont(HumanSlTrainingStyle.fontForText(badge, Font.BOLD, Math.max(8, marker - 4)));
        java.awt.FontMetrics metrics = g2.getFontMetrics();
        g2.drawString(
            badge,
            px + (marker - metrics.stringWidth(badge)) / 2,
            py + (marker - metrics.getHeight()) / 2 + metrics.getAscent());
      }
      g2.dispose();
    }
  }
}
