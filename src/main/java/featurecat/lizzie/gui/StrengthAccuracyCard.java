package featurecat.lizzie.gui;

import featurecat.lizzie.analysis.PlayerStrengthEstimator;
import java.awt.BasicStroke;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Stroke;
import java.awt.event.MouseEvent;
import java.awt.geom.Arc2D;
import java.util.ResourceBundle;
import javax.swing.JPanel;
import javax.swing.JToolTip;

/** One side's overall agreement with the AI as a ring, with the figures behind it. */
final class StrengthAccuracyCard extends JPanel {
  private static final long serialVersionUID = 1L;
  private final transient StrengthStyle style;
  private final transient PlayerStrengthEstimator.SideReport report;
  private final String sideName;
  private final boolean black;
  private final String caption;
  private final String[][] stats;
  private final String[] helps;
  private final String matchHelp;

  StrengthAccuracyCard(
      String sideName,
      boolean black,
      PlayerStrengthEstimator.SideReport report,
      ResourceBundle bundle,
      StrengthStyle style) {
    this.sideName = sideName;
    this.black = black;
    this.report = report;
    this.style = style;
    caption = bundle.getString("PlayerStrengthEstimate.match.overallMatch");
    matchHelp = bundle.getString("PlayerStrengthEstimate.help.overallMatch");
    helps =
        new String[] {
          bundle.getString("PlayerStrengthEstimate.help.firstChoice"),
          bundle.getString("PlayerStrengthEstimate.help.goodMoveRate"),
          bundle.getString("PlayerStrengthEstimate.help.mistakeRate"),
          bundle.getString("PlayerStrengthEstimate.help.moves")
        };
    boolean known = report != null && report.hasSamples();
    stats =
        new String[][] {
          {
            bundle.getString("PlayerStrengthEstimate.firstChoice"),
            known ? StrengthText.precisePercent(report.firstChoiceRate) : "-"
          },
          {
            bundle.getString("PlayerStrengthEstimate.goodMoveRate"),
            known ? StrengthText.precisePercent(report.moveRankGoodMoveRate) : "-"
          },
          {
            bundle.getString("PlayerStrengthEstimate.mistakeRate"),
            known ? StrengthText.precisePercent(report.mistakeRate) : "-"
          },
          {
            bundle.getString("PlayerStrengthEstimate.moves"),
            String.valueOf(report == null ? 0 : report.sampleCount)
          }
        };
    setOpaque(false);
    StringBuilder description = new StringBuilder(caption).append(": ").append(matchText());
    for (String[] stat : stats) {
      description.append("; ").append(stat[0]).append(": ").append(stat[1]);
    }
    getAccessibleContext().setAccessibleName(sideName + " " + caption);
    setToolTipText("");
    getAccessibleContext().setAccessibleDescription(description.toString());
  }

  private boolean known() {
    return report != null && report.hasSamples();
  }

  private String matchText() {
    return known() ? StrengthText.percent(report.matchRate) : "-";
  }

  private int padding() {
    return style.px(20);
  }

  private int ringSize() {
    return style.px(96);
  }

  private Font headlineFont() {
    return style.font(Font.BOLD, 3);
  }

  private Font smallFont() {
    return style.font(Font.PLAIN, -1);
  }

  private Font valueFont() {
    return style.font(Font.BOLD, 4);
  }

  private int contentHeight() {
    return getFontMetrics(headlineFont()).getHeight()
        + style.px(14)
        + getFontMetrics(smallFont()).getHeight()
        + style.px(2)
        + getFontMetrics(valueFont()).getHeight();
  }

  @Override
  public Dimension getPreferredSize() {
    return new Dimension(
        style.px(480), padding() * 2 + Math.max(ringSize(), contentHeight()) + style.px(4));
  }

  @Override
  public Dimension getMinimumSize() {
    return new Dimension(style.px(360), getPreferredSize().height);
  }

  /** The explanation of the ring or of the figure under the pointer. */
  @Override
  public String getToolTipText(MouseEvent event) {
    String help = helpAt(event.getX());
    return help == null
        ? null
        : StrengthText.wrappedTooltip(
            help, getFontMetrics(style.font(Font.PLAIN, 0)), style.px(300));
  }

  @Override
  public JToolTip createToolTip() {
    JToolTip tip = super.createToolTip();
    tip.setFont(style.font(Font.PLAIN, 0));
    return tip;
  }

  /** What the ring or the figure at {@code x} means. */
  String helpAt(int x) {
    int pad = padding();
    int ring = ringSize();
    if (x < pad + ring + style.px(12)) {
      return matchHelp;
    }
    int left = pad + ring + style.px(24);
    int column = Math.max(1, (getWidth() - pad - left) / stats.length);
    int index = (x - left) / column;
    return x >= left && index < stats.length ? helps[index] : null;
  }

  @Override
  protected void paintComponent(Graphics graphics) {
    Graphics2D g = StrengthStyle.smooth(graphics);
    try {
      int width = getWidth();
      int height = getHeight();
      style.paintCard(g, width, height);
      int pad = padding();
      int ring = ringSize();
      paintRing(g, pad, (height - ring) / 2, ring);

      int x = pad + ring + style.px(24);
      int contentWidth = width - pad - x;
      int top = (height - contentHeight()) / 2;
      Font headline = headlineFont();
      FontMetrics headlineMetrics = g.getFontMetrics(headline);
      double stone = style.px(18);
      StoneGlyph.paint(g, x, top + (headlineMetrics.getHeight() - stone) / 2.0, stone, black);
      StrengthPaint.text(
          g,
          sideName,
          headline,
          headline.getSize() - 3,
          style.text,
          x + (float) stone + style.px(8),
          top + headlineMetrics.getAscent(),
          contentWidth - (int) stone - style.px(8),
          StrengthPaint.LEFT);

      Font small = smallFont();
      FontMetrics smallMetrics = g.getFontMetrics(small);
      Font value = valueFont();
      FontMetrics valueMetrics = g.getFontMetrics(value);
      int statTop = top + headlineMetrics.getHeight() + style.px(14);
      int column = contentWidth / stats.length;
      for (int i = 0; i < stats.length; i++) {
        int statX = x + column * i;
        StrengthPaint.text(
            g,
            stats[i][0],
            small,
            small.getSize() - 2,
            style.secondaryText,
            statX,
            statTop + smallMetrics.getAscent(),
            column - style.px(8),
            StrengthPaint.LEFT);
        StrengthPaint.text(
            g,
            stats[i][1],
            value,
            value.getSize() - 4,
            known() || i == stats.length - 1 ? style.text : style.tertiaryText,
            statX,
            statTop + smallMetrics.getHeight() + style.px(2) + valueMetrics.getAscent(),
            column - style.px(8),
            StrengthPaint.LEFT);
      }
    } finally {
      g.dispose();
    }
  }

  /** The ring of the overall match, with the percentage and its caption inside. */
  private void paintRing(Graphics2D g, int x, int y, int size) {
    double stroke = style.px(8);
    double inset = stroke / 2;
    double diameter = size - stroke;
    Stroke previous = g.getStroke();
    g.setStroke(new BasicStroke((float) stroke, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
    g.setColor(style.sunken);
    g.draw(new Arc2D.Double(x + inset, y + inset, diameter, diameter, 0, 360, Arc2D.OPEN));
    if (known() && report.matchRate > 0) {
      g.setColor(style.accent);
      g.draw(
          new Arc2D.Double(
              x + inset,
              y + inset,
              diameter,
              diameter,
              90,
              -360 * StrengthStats.clamp(report.matchRate),
              Arc2D.OPEN));
    }
    g.setStroke(previous);

    Font number = style.font(Font.BOLD, 9);
    FontMetrics numberMetrics = g.getFontMetrics(number);
    Font small = style.font(Font.PLAIN, -2);
    FontMetrics smallMetrics = g.getFontMetrics(small);
    int inner = (int) (size - stroke * 2 - style.px(10));
    int block = numberMetrics.getAscent() + numberMetrics.getDescent() + smallMetrics.getHeight();
    int top = y + (size - block) / 2;
    float center = x + size / 2f;
    StrengthPaint.text(
        g,
        matchText(),
        number,
        number.getSize() - 6,
        known() ? style.text : style.tertiaryText,
        center,
        top + numberMetrics.getAscent(),
        inner,
        StrengthPaint.CENTER);
    StrengthPaint.text(
        g,
        caption,
        small,
        Math.max(9, small.getSize() - 2),
        style.secondaryText,
        center,
        top + numberMetrics.getAscent() + numberMetrics.getDescent() + smallMetrics.getAscent(),
        inner,
        StrengthPaint.CENTER);
  }
}
