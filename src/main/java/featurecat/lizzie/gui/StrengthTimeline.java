package featurecat.lizzie.gui;

import featurecat.lizzie.analysis.PlayerStrengthEstimator;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import javax.swing.JPanel;
import javax.swing.JToolTip;

/**
 * The move hit map: along the game, the moves where each side played the AI's first choice (upper
 * lane) or a good move (lower lane). Hovering a mark highlights it and shows that move.
 */
final class StrengthTimeline extends JPanel {
  private static final long serialVersionUID = 1L;
  private final transient StrengthStyle style;
  private final transient ResourceBundle bundle;
  private final transient PlayerStrengthEstimator.Report report;
  private final String blackName;
  private final String whiteName;
  private final String title;
  private final String hint;
  private final String firstChoiceLabel;
  private final String goodMoveLabel;
  private final int maxMove;
  private transient Mark hovered;

  /** A mark of one move in one lane. */
  record Mark(PlayerStrengthEstimator.Sample sample, boolean black, boolean firstChoiceLane) {}

  private record Geometry(StrengthMoveAxis axis, int laneHeight, int[] laneTops, int axisTop) {
    int laneTop(boolean black, boolean firstChoiceLane) {
      return laneTops[(black ? 0 : 2) + (firstChoiceLane ? 0 : 1)];
    }
  }

  StrengthTimeline(
      PlayerStrengthEstimator.Report report,
      String blackName,
      String whiteName,
      ResourceBundle bundle,
      StrengthStyle style) {
    this.report = report;
    this.blackName = blackName;
    this.whiteName = whiteName;
    this.bundle = bundle;
    this.style = style;
    title = bundle.getString("PlayerStrengthEstimate.match.moveMap");
    hint = bundle.getString("PlayerStrengthEstimate.match.moveMapHint");
    firstChoiceLabel = bundle.getString("PlayerStrengthEstimate.match.firstChoice");
    goodMoveLabel = bundle.getString("PlayerStrengthEstimate.match.goodMove");
    maxMove = StrengthStats.maxMove(report);
    setOpaque(false);
    setToolTipText("");
    getAccessibleContext().setAccessibleName(title);
    getAccessibleContext().setAccessibleDescription(hint);
    MouseAdapter hover =
        new MouseAdapter() {
          @Override
          public void mouseMoved(MouseEvent event) {
            setHovered(markAt(event.getPoint()));
          }

          @Override
          public void mouseExited(MouseEvent event) {
            setHovered(null);
          }
        };
    addMouseListener(hover);
    addMouseMotionListener(hover);
  }

  private void setHovered(Mark mark) {
    if (mark == null ? hovered == null : mark.equals(hovered)) {
      return;
    }
    hovered = mark;
    repaint();
  }

  Mark hovered() {
    return hovered;
  }

  private boolean hasMoves() {
    return (report.black != null && report.black.hasSamples())
        || (report.white != null && report.white.hasSamples());
  }

  private int padding() {
    return style.px(22);
  }

  private Font titleFont() {
    return style.font(Font.BOLD, 3);
  }

  private Font smallFont() {
    return style.font(Font.PLAIN, -1);
  }

  private Font valueFont() {
    return style.font(Font.BOLD, -1);
  }

  private List<String> hintLines(int width) {
    return StrengthPaint.wrap(
        hint, getFontMetrics(smallFont()), Math.max(80, width - padding() * 2), 2);
  }

  private int chartTop(int width) {
    return padding()
        + getFontMetrics(titleFont()).getHeight()
        + style.px(4)
        + hintLines(width).size() * getFontMetrics(smallFont()).getHeight()
        + style.px(18);
  }

  private int axisHeight() {
    return getFontMetrics(smallFont()).getHeight() + style.px(8);
  }

  private int laneLabelWidth() {
    FontMetrics metrics = getFontMetrics(smallFont());
    return Math.max(metrics.stringWidth(firstChoiceLabel), metrics.stringWidth(goodMoveLabel))
        + style.px(12);
  }

  private int valueWidth() {
    return getFontMetrics(valueFont()).stringWidth("100.0%") + style.px(16);
  }

  @Override
  public Dimension getPreferredSize() {
    int width = style.px(720);
    return new Dimension(
        width,
        chartTop(width)
            + style.px(12) * 4
            + style.px(8) * 2
            + style.px(26)
            + axisHeight()
            + padding());
  }

  @Override
  public Dimension getMinimumSize() {
    return new Dimension(style.px(480), getPreferredSize().height);
  }

  private Geometry geometry() {
    int width = getWidth();
    int pad = padding();
    int chartX = pad + style.px(28) + laneLabelWidth();
    StrengthMoveAxis axis =
        new StrengthMoveAxis(chartX, width - pad - valueWidth() - chartX, maxMove, style.px(6));
    int top = chartTop(width);
    int available = getHeight() - top - axisHeight() - pad;
    int laneGap = style.px(8);
    int sideGap = style.px(26);
    int lane =
        Math.max(style.px(10), Math.min(style.px(26), (available - sideGap - laneGap * 2) / 4));
    int block = lane * 4 + laneGap * 2 + sideGap;
    int y = top + Math.max(0, (available - block) / 2);
    int[] lanes = {
      y, y + lane + laneGap, y + lane * 2 + laneGap + sideGap, y + lane * 3 + laneGap * 2 + sideGap
    };
    return new Geometry(axis, lane, lanes, lanes[3] + lane + style.px(8));
  }

  /** The centre of the mark of {@code move} at the current size. */
  int moveX(int move) {
    return geometry().axis().center(move);
  }

  /** The bounds of one lane at the current size. */
  Rectangle laneBounds(boolean black, boolean firstChoiceLane) {
    Geometry geometry = geometry();
    return new Rectangle(
        geometry.axis().left,
        geometry.laneTop(black, firstChoiceLane),
        geometry.axis().width,
        geometry.laneHeight());
  }

  private static boolean inLane(PlayerStrengthEstimator.Sample sample, boolean firstChoiceLane) {
    return firstChoiceLane
        ? sample.firstChoice
        : sample.moveRankCategory != null && sample.moveRankCategory.isGoodMove();
  }

  /** The mark under {@code point}, or null. */
  Mark markAt(Point point) {
    if (point == null || !hasMoves()) {
      return null;
    }
    Geometry geometry = geometry();
    StrengthMoveAxis axis = geometry.axis();
    int reach = axis.reach();
    for (int side = 0; side < 2; side++) {
      boolean black = side == 0;
      for (int lane = 0; lane < 2; lane++) {
        boolean firstChoiceLane = lane == 0;
        Rectangle bounds =
            new Rectangle(
                axis.left - reach,
                geometry.laneTop(black, firstChoiceLane) - style.px(3),
                axis.width + reach * 2,
                geometry.laneHeight() + style.px(6));
        if (!bounds.contains(point)) {
          continue;
        }
        PlayerStrengthEstimator.Sample best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (PlayerStrengthEstimator.Sample sample :
            StrengthStats.movesInOrder(black ? report.black : report.white)) {
          int distance = Math.abs(point.x - axis.center(sample.moveNumber));
          if (inLane(sample, firstChoiceLane) && distance <= reach && distance < bestDistance) {
            best = sample;
            bestDistance = distance;
          }
        }
        return best == null ? null : new Mark(best, black, firstChoiceLane);
      }
    }
    return null;
  }

  @Override
  public String getToolTipText(MouseEvent event) {
    Mark mark = markAt(event.getPoint());
    return mark == null ? null : tooltip(mark);
  }

  @Override
  public JToolTip createToolTip() {
    JToolTip tip = super.createToolTip();
    tip.setFont(style.font(Font.PLAIN, 0));
    return tip;
  }

  private String tooltip(Mark mark) {
    PlayerStrengthEstimator.Sample sample = mark.sample();
    String unknown = bundle.getString("PlayerStrengthEstimate.match.tooltipUnknown");
    String category =
        sample.firstChoice || mark.firstChoiceLane() ? firstChoiceLabel : goodMoveLabel;
    String aiChoice =
        sample.aiRank < 0
            ? unknown
            : sample.firstChoice
                ? firstChoiceLabel
                : String.format(
                    Locale.US,
                    bundle.getString("PlayerStrengthEstimate.match.tooltipAiRank"),
                    sample.aiRank + 1);
    String scoreLoss =
        sample.scoreLoss.isPresent()
            ? String.format(Locale.US, "%.1f", sample.scoreLoss.get())
            : unknown;
    return "<html><b>"
        + StrengthText.escape(mark.black() ? blackName : whiteName)
        + " "
        + StrengthText.escape(
            String.format(
                Locale.US,
                bundle.getString("PlayerStrengthEstimate.match.tooltipMove"),
                sample.moveNumber))
        + "</b><br>"
        + StrengthText.escape(category)
        + "<br>"
        + line("PlayerStrengthEstimate.match.tooltipAiChoice", aiChoice)
        + line(
            "PlayerStrengthEstimate.match.tooltipWinrateLoss",
            String.format(Locale.US, "%.1f%%", sample.winrateLoss))
        + line("PlayerStrengthEstimate.match.tooltipScoreLoss", scoreLoss)
        + StrengthText.escape(bundle.getString("PlayerStrengthEstimate.match.tooltipComplexity"))
        + ": "
        + String.format(Locale.US, "%.0f", StrengthStats.clamp(sample.complexity) * 100.0)
        + "</html>";
  }

  private String line(String key, String value) {
    return StrengthText.escape(bundle.getString(key)) + ": " + StrengthText.escape(value) + "<br>";
  }

  private Color goodMoveColor() {
    return StrengthStyle.mix(style.accent, style.surface, 0.5);
  }

  @Override
  protected void paintComponent(Graphics graphics) {
    Graphics2D g = StrengthStyle.smooth(graphics);
    try {
      int width = getWidth();
      int pad = padding();
      style.paintCard(g, width, getHeight());

      Font titleFont = titleFont();
      FontMetrics titleMetrics = g.getFontMetrics(titleFont);
      int legendWidth = paintLegend(g, width - pad, pad, titleMetrics.getHeight());
      StrengthPaint.text(
          g,
          title,
          titleFont,
          titleFont.getSize() - 3,
          style.text,
          pad,
          pad + titleMetrics.getAscent(),
          width - pad * 2 - legendWidth - style.px(16),
          StrengthPaint.LEFT);
      Font small = smallFont();
      FontMetrics smallMetrics = g.getFontMetrics(small);
      int hintBaseline = pad + titleMetrics.getHeight() + style.px(4) + smallMetrics.getAscent();
      g.setFont(small);
      g.setColor(style.secondaryText);
      for (String line : hintLines(width)) {
        g.drawString(line, pad, hintBaseline);
        hintBaseline += smallMetrics.getHeight();
      }

      if (!hasMoves()) {
        int top = chartTop(width);
        StrengthPaint.text(
            g,
            bundle.getString("PlayerStrengthEstimate.match.noMoveSamples"),
            style.font(Font.PLAIN, 1),
            style.font(Font.PLAIN, -2).getSize(),
            style.tertiaryText,
            width / 2f,
            top + (getHeight() - top - pad) / 2f,
            width - pad * 2,
            StrengthPaint.CENTER);
        return;
      }
      Geometry geometry = geometry();
      geometry
          .axis()
          .paint(
              g,
              style,
              small,
              geometry.laneTop(true, true) - style.px(4),
              geometry.laneTop(false, false) + geometry.laneHeight() + style.px(4),
              geometry.axisTop());
      paintSide(g, geometry, true, report.black);
      paintSide(g, geometry, false, report.white);
    } finally {
      g.dispose();
    }
  }

  private int paintLegend(Graphics2D g, int right, int top, int rowHeight) {
    Font small = smallFont();
    FontMetrics metrics = g.getFontMetrics(small);
    int swatchWidth = style.px(4);
    int swatchHeight = style.px(11);
    int gap = style.px(6);
    int spacing = style.px(16);
    String[] labels = {firstChoiceLabel, goodMoveLabel};
    Color[] colors = {style.accent, goodMoveColor()};
    int width = spacing * (labels.length - 1);
    for (String label : labels) {
      width += swatchWidth + gap + metrics.stringWidth(label);
    }
    int x = right - width;
    int baseline =
        top + (rowHeight - metrics.getAscent() - metrics.getDescent()) / 2 + metrics.getAscent();
    g.setFont(small);
    for (int i = 0; i < labels.length; i++) {
      g.setColor(colors[i]);
      g.fill(
          new RoundRectangle2D.Double(
              x, top + (rowHeight - swatchHeight) / 2.0, swatchWidth, swatchHeight, 2, 2));
      x += swatchWidth + gap;
      g.setColor(style.secondaryText);
      g.drawString(labels[i], x, baseline);
      x += metrics.stringWidth(labels[i]) + spacing;
    }
    return width;
  }

  private void paintSide(
      Graphics2D g, Geometry geometry, boolean black, PlayerStrengthEstimator.SideReport side) {
    StrengthMoveAxis axis = geometry.axis();
    int pad = padding();
    int lane = geometry.laneHeight();
    int firstTop = geometry.laneTop(black, true);
    int goodTop = geometry.laneTop(black, false);
    double stone = style.px(18);
    StoneGlyph.paint(g, pad, (firstTop + goodTop + lane) / 2.0 - stone / 2.0, stone, black);

    Font small = smallFont();
    FontMetrics smallMetrics = g.getFontMetrics(small);
    Font value = valueFont();
    boolean known = side != null && side.hasSamples();
    for (int i = 0; i < 2; i++) {
      boolean firstChoiceLane = i == 0;
      int top = firstChoiceLane ? firstTop : goodTop;
      int baseline =
          top
              + (lane - smallMetrics.getAscent() - smallMetrics.getDescent()) / 2
              + smallMetrics.getAscent();
      String label = firstChoiceLane ? firstChoiceLabel : goodMoveLabel;
      g.setFont(small);
      g.setColor(style.secondaryText);
      g.drawString(label, axis.left - style.px(10) - smallMetrics.stringWidth(label), baseline);
      double radius = Math.min(style.px(4), lane);
      g.setColor(style.sunken);
      g.fill(new RoundRectangle2D.Double(axis.left, top, axis.width, lane, radius, radius));
      StrengthPaint.text(
          g,
          known
              ? StrengthText.precisePercent(
                  firstChoiceLane ? side.firstChoiceRate : side.moveRankGoodMoveRate)
              : "-",
          value,
          value.getSize(),
          known ? style.text : style.tertiaryText,
          getWidth() - pad,
          baseline,
          valueWidth(),
          StrengthPaint.RIGHT);
    }
    if (!known) {
      return;
    }
    Color good = goodMoveColor();
    for (PlayerStrengthEstimator.Sample sample : StrengthStats.movesInOrder(side)) {
      int x = axis.x(sample.moveNumber);
      if (inLane(sample, true)) {
        g.setColor(style.accent);
        g.fillRect(x, firstTop, axis.tick, lane);
      }
      if (inLane(sample, false)) {
        g.setColor(good);
        g.fillRect(x, goodTop, axis.tick, lane);
      }
    }
    if (hovered != null && hovered.black() == black) {
      int x = axis.x(hovered.sample().moveNumber);
      int top = hovered.firstChoiceLane() ? firstTop : goodTop;
      g.setColor(StrengthStyle.alpha(style.text, 80));
      g.fillRect(
          x + axis.tick / 2, firstTop - style.px(4), 1, goodTop + lane - firstTop + style.px(8));
      g.setColor(style.text);
      g.fillRect(x - 1, top - style.px(2), axis.tick + 2, lane + style.px(4));
    }
  }
}
