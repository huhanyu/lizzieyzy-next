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
import java.awt.Shape;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import javax.swing.JPanel;
import javax.swing.JToolTip;

/**
 * The detailed match chart: for each side the first-choice and good-move lanes, the stretches of
 * the game played at 7 dan or above with their rank, and the stretches that resemble AI play.
 */
final class StrengthSegmentChart extends JPanel {
  private static final long serialVersionUID = 1L;
  private final transient StrengthStyle style;
  private final transient ResourceBundle bundle;
  private final transient PlayerStrengthEstimator.Report report;
  private final String[] sideNames;
  private final String[] rowLabels;
  private final String title;
  private final int maxMove;
  private transient Stretch hovered;

  /** A labelled stretch of one side's moves, where it is drawn. */
  record Stretch(boolean black, StrengthStats.Segment segment, String label, Rectangle bounds) {}

  StrengthSegmentChart(
      PlayerStrengthEstimator.Report report,
      String[] sideNames,
      ResourceBundle bundle,
      StrengthStyle style) {
    this.report = report;
    this.sideNames = sideNames.clone();
    this.bundle = bundle;
    this.style = style;
    rowLabels =
        new String[] {
          bundle.getString("PlayerStrengthEstimate.match.firstChoice"),
          bundle.getString("PlayerStrengthEstimate.match.goodMove"),
          bundle.getString("PlayerStrengthEstimate.match.strength"),
          bundle.getString("PlayerStrengthEstimate.match.aiLike")
        };
    title = bundle.getString("PlayerStrengthEstimate.match.trend");
    maxMove = StrengthStats.maxMove(report);
    setOpaque(false);
    setToolTipText("");
    getAccessibleContext().setAccessibleName(title);
    MouseAdapter hover =
        new MouseAdapter() {
          @Override
          public void mouseMoved(MouseEvent event) {
            Stretch window = stretchAt(event.getPoint());
            if (window == null ? hovered != null : !window.equals(hovered)) {
              hovered = window;
              repaint();
            }
          }

          @Override
          public void mouseExited(MouseEvent event) {
            if (hovered != null) {
              hovered = null;
              repaint();
            }
          }
        };
    addMouseListener(hover);
    addMouseMotionListener(hover);
  }

  private int padding() {
    return style.px(22);
  }

  private Font smallFont() {
    return style.font(Font.PLAIN, -1);
  }

  private int titleHeight() {
    return getFontMetrics(style.font(Font.BOLD, 3)).getHeight() + style.px(16);
  }

  private int headerHeight() {
    return Math.max(style.px(26), getFontMetrics(style.font(Font.BOLD, 3)).getHeight());
  }

  private int[] rowHeights() {
    return new int[] {style.px(10), style.px(10), style.px(20), style.px(16)};
  }

  private int rowGap() {
    return style.px(7);
  }

  private int sideHeight() {
    int height = headerHeight() + style.px(10);
    for (int row : rowHeights()) {
      height += row + rowGap();
    }
    return height - rowGap();
  }

  private int sideGap() {
    return style.px(26);
  }

  private int axisHeight() {
    return getFontMetrics(smallFont()).getHeight() + style.px(8);
  }

  @Override
  public Dimension getPreferredSize() {
    return new Dimension(
        style.px(820), padding() * 2 + titleHeight() + sideHeight() * 2 + sideGap() + axisHeight());
  }

  private StrengthMoveAxis axis() {
    FontMetrics metrics = getFontMetrics(smallFont());
    int labelWidth = 0;
    for (String label : rowLabels) {
      labelWidth = Math.max(labelWidth, metrics.stringWidth(label));
    }
    int left = padding() + labelWidth + style.px(14);
    return new StrengthMoveAxis(left, getWidth() - padding() - left, maxMove, style.px(6));
  }

  private int sideTop(boolean black) {
    int available = getHeight() - padding() * 2 - titleHeight() - axisHeight();
    int block = sideHeight() * 2 + sideGap();
    int top = padding() + titleHeight() + Math.max(0, (available - block) / 2);
    return black ? top : top + sideHeight() + sideGap();
  }

  private int rowTop(boolean black, int row) {
    int top = sideTop(black) + headerHeight() + style.px(10);
    int[] heights = rowHeights();
    for (int i = 0; i < row; i++) {
      top += heights[i] + rowGap();
    }
    return top;
  }

  /** The labelled stretches of both sides at the current size. */
  List<Stretch> stretches() {
    List<Stretch> stretches = new ArrayList<>();
    StrengthMoveAxis axis = axis();
    for (int side = 0; side < 2; side++) {
      boolean black = side == 0;
      PlayerStrengthEstimator.SideReport sideReport = black ? report.black : report.white;
      int top = rowTop(black, 2);
      for (StrengthStats.Segment segment : StrengthStats.segments(sideReport)) {
        if (!StrengthStats.labelsRank(segment, sideReport)) {
          continue;
        }
        int left = axis.x(segment.firstMove());
        int right = axis.x(segment.lastMove()) + axis.tick;
        stretches.add(
            new Stretch(
                black,
                segment,
                StrengthText.band(segment.report().strengthBand, bundle),
                new Rectangle(left, top, Math.max(style.px(20), right - left), rowHeights()[2])));
      }
    }
    return stretches;
  }

  private Stretch stretchAt(Point point) {
    for (Stretch window : stretches()) {
      Rectangle bounds = new Rectangle(window.bounds());
      bounds.grow(style.px(3), style.px(4));
      if (bounds.contains(point)) {
        return window;
      }
    }
    return null;
  }

  @Override
  public String getToolTipText(MouseEvent event) {
    Stretch window = stretchAt(event.getPoint());
    if (window == null) {
      return null;
    }
    return String.format(
        Locale.US,
        bundle.getString("PlayerStrengthEstimate.match.rankTooltip"),
        sideNames[window.black() ? 0 : 1],
        window.segment().firstMove(),
        window.segment().lastMove(),
        window.label());
  }

  @Override
  public JToolTip createToolTip() {
    JToolTip tip = super.createToolTip();
    tip.setFont(style.font(Font.PLAIN, 0));
    return tip;
  }

  @Override
  protected void paintComponent(Graphics graphics) {
    Graphics2D g = StrengthStyle.smooth(graphics);
    try {
      style.paintCard(g, getWidth(), getHeight());
      Font titleFont = style.font(Font.BOLD, 3);
      StrengthPaint.text(
          g,
          title,
          titleFont,
          titleFont.getSize() - 3,
          style.text,
          padding(),
          padding() + g.getFontMetrics(titleFont).getAscent(),
          getWidth() - padding() * 2,
          StrengthPaint.LEFT);
      StrengthMoveAxis axis = axis();
      axis.paint(
          g,
          style,
          smallFont(),
          rowTop(true, 0) - style.px(4),
          rowTop(false, 3) + rowHeights()[3] + style.px(4),
          rowTop(false, 3) + rowHeights()[3] + style.px(10));
      List<Stretch> stretches = stretches();
      paintSide(g, axis, true, report.black, stretches);
      paintSide(g, axis, false, report.white, stretches);
    } finally {
      g.dispose();
    }
  }

  private void paintSide(
      Graphics2D g,
      StrengthMoveAxis axis,
      boolean black,
      PlayerStrengthEstimator.SideReport side,
      List<Stretch> stretches) {
    int pad = padding();
    int top = sideTop(black);
    int header = headerHeight();
    double stone = style.px(18);
    StoneGlyph.paint(g, pad, top + (header - stone) / 2.0, stone, black);
    Font headline = style.font(Font.BOLD, 3);
    FontMetrics headlineMetrics = g.getFontMetrics(headline);
    int baseline =
        top
            + (header - headlineMetrics.getAscent() - headlineMetrics.getDescent()) / 2
            + headlineMetrics.getAscent();
    boolean known = side != null && side.hasSamples();
    int statsWidth = paintStats(g, side, known, getWidth() - pad, baseline);
    StrengthPaint.text(
        g,
        sideNames[black ? 0 : 1],
        headline,
        headline.getSize() - 3,
        style.text,
        pad + (float) stone + style.px(8),
        baseline,
        getWidth() - pad * 2 - (int) stone - style.px(8) - statsWidth - style.px(16),
        StrengthPaint.LEFT);

    Font small = smallFont();
    FontMetrics smallMetrics = g.getFontMetrics(small);
    int[] heights = rowHeights();
    for (int row = 0; row < heights.length; row++) {
      int rowTop = rowTop(black, row);
      g.setFont(small);
      g.setColor(style.secondaryText);
      g.drawString(
          rowLabels[row],
          axis.left - style.px(10) - smallMetrics.stringWidth(rowLabels[row]),
          rowTop
              + (heights[row] - smallMetrics.getAscent() - smallMetrics.getDescent()) / 2
              + smallMetrics.getAscent());
      if (row < 2) {
        g.setColor(style.sunken);
        g.fill(
            new RoundRectangle2D.Double(
                axis.left, rowTop, axis.width, heights[row], style.px(4), style.px(4)));
      }
    }
    if (!known) {
      return;
    }
    Color good = StrengthStyle.mix(style.accent, style.surface, 0.5);
    for (PlayerStrengthEstimator.Sample sample : StrengthStats.movesInOrder(side)) {
      int x = axis.x(sample.moveNumber);
      if (sample.firstChoice) {
        g.setColor(style.accent);
        g.fillRect(x, rowTop(black, 0), axis.tick, heights[0]);
      }
      if (sample.moveRankCategory != null && sample.moveRankCategory.isGoodMove()) {
        g.setColor(good);
        g.fillRect(x, rowTop(black, 1), axis.tick, heights[1]);
      }
    }
    for (Stretch window : stretches) {
      if (window.black() == black) {
        paintStretch(g, window);
      }
    }
    paintAiRow(g, axis, black, side, rowTop(black, 3), heights[3]);
  }

  private int paintStats(
      Graphics2D g,
      PlayerStrengthEstimator.SideReport side,
      boolean known,
      int right,
      int baseline) {
    String[][] stats = {
      {rowLabels[0], known ? StrengthText.precisePercent(side.firstChoiceRate) : "-"},
      {rowLabels[1], known ? StrengthText.precisePercent(side.moveRankGoodMoveRate) : "-"},
      {rowLabels[3], known ? StrengthText.precisePercent(StrengthStats.aiLikelihood(side)) : "-"}
    };
    Font label = smallFont();
    Font value = style.font(Font.BOLD, 0);
    FontMetrics labelMetrics = g.getFontMetrics(label);
    FontMetrics valueMetrics = g.getFontMetrics(value);
    int x = right;
    for (int i = stats.length - 1; i >= 0; i--) {
      int valueWidth = valueMetrics.stringWidth(stats[i][1]);
      x -= valueWidth;
      g.setFont(value);
      g.setColor(style.text);
      g.drawString(stats[i][1], x, baseline);
      x -= style.px(6) + labelMetrics.stringWidth(stats[i][0]);
      g.setFont(label);
      g.setColor(style.secondaryText);
      g.drawString(stats[i][0], x, baseline);
      if (i > 0) {
        x -= style.px(18);
      }
    }
    return right - x;
  }

  private void paintStretch(Graphics2D g, Stretch window) {
    Rectangle bounds = window.bounds();
    double arc = bounds.height;
    RoundRectangle2D pill =
        new RoundRectangle2D.Double(bounds.x, bounds.y, bounds.width, bounds.height, arc, arc);
    g.setColor(
        window.equals(hovered)
            ? StrengthStyle.mix(style.accentSoft, style.accent, 0.25)
            : style.accentSoft);
    g.fill(pill);
    if (bounds.width > style.px(24)) {
      Shape clip = g.getClip();
      g.clip(pill);
      Font font = style.font(Font.BOLD, -2);
      FontMetrics metrics = g.getFontMetrics(font);
      StrengthPaint.text(
          g,
          window.label(),
          font,
          style.font(Font.PLAIN, -4).getSize(),
          style.accent,
          bounds.x + bounds.width / 2f,
          bounds.y
              + (bounds.height - metrics.getAscent() - metrics.getDescent()) / 2f
              + metrics.getAscent(),
          bounds.width - style.px(10),
          StrengthPaint.CENTER);
      g.setClip(clip);
    }
  }

  private void paintAiRow(
      Graphics2D g,
      StrengthMoveAxis axis,
      boolean black,
      PlayerStrengthEstimator.SideReport side,
      int top,
      int height) {
    RoundRectangle2D track =
        new RoundRectangle2D.Double(
            axis.left + 0.5, top + 0.5, axis.width - 1, height - 1, height, height);
    g.setColor(style.border);
    g.draw(track);
    boolean any = false;
    Shape clip = g.getClip();
    g.clip(track);
    List<StrengthStats.Segment> segments = StrengthStats.segments(side);
    for (int i = 0; i < segments.size(); i++) {
      StrengthStats.Segment segment = segments.get(i);
      if (!StrengthStats.resemblesAi(segment, side)) {
        continue;
      }
      any = true;
      // Reach the next stretch, so that neighbouring AI-like stretches read as one run.
      int left = axis.x(segment.firstMove());
      int right =
          i + 1 < segments.size()
              ? axis.x(segments.get(i + 1).firstMove())
              : axis.x(segment.lastMove()) + axis.tick;
      g.setColor(style.attention);
      g.fill(new Rectangle2D.Double(left, top, Math.max(style.px(4), right - left), height));
    }
    g.setClip(clip);
    if (!any) {
      Font font = smallFont();
      FontMetrics metrics = g.getFontMetrics(font);
      StrengthPaint.text(
          g,
          bundle.getString("PlayerStrengthEstimate.match.noAiInterval"),
          font,
          font.getSize() - 2,
          style.tertiaryText,
          axis.left + axis.width / 2f,
          top + (height - metrics.getAscent() - metrics.getDescent()) / 2f + metrics.getAscent(),
          axis.width - style.px(16),
          StrengthPaint.CENTER);
    }
  }
}
