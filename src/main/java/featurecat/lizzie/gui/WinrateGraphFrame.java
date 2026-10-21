package featurecat.lizzie.gui;

import featurecat.lizzie.util.Utils;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Stroke;
import java.util.List;

/**
 * What surrounds the win-rate graph's curves: the legend in a band above the plot, where it no
 * longer covers the curves, and small 100 / 50% / 0 labels at the plot's left edge, behind the
 * curves. The plot keeps its width and left edge, which the move hit-testing depends on; only the
 * band moves its top down. The current move keeps its red marker without a guide line (933bfda1). A
 * graph narrower than {@link #MIN_WIDTH} keeps its old look.
 */
final class WinrateGraphFrame {
  static final int MIN_WIDTH = 240;
  static final Color TEXT = new Color(200, 200, 200);
  private static final Color SCALE = new Color(200, 200, 200, 150);
  private static final int SCALE_INSET = 3;

  private WinrateGraphFrame() {}

  /** Height of the legend band at the graph's normal size. */
  static final int LEGEND_BAND = 17;

  /**
   * The band taken from the top of the graph for the legend; none when the graph is narrow. Its
   * height does not follow the platform's font metrics, so the plot sits at the same place on every
   * platform (hit-testing and the overview's occlusion depend on it).
   */
  static int legendBand(int graphWidth, boolean legendShown, boolean large) {
    if (graphWidth < MIN_WIDTH || !legendShown) return 0;
    return large ? Utils.zoomOut(LEGEND_BAND) : LEGEND_BAND;
  }

  /** Labels the 100, 50% and 0 lines just inside the plot's left edge, above each line. */
  static void drawScale(
      Graphics2D g, Font font, int plotX, int plotY, int plotHeight, boolean middleLabelled) {
    Font previousFont = g.getFont();
    Color previousColor = g.getColor();
    g.setFont(font);
    g.setColor(SCALE);
    FontMetrics metrics = g.getFontMetrics();
    int x = plotX + SCALE_INSET;
    g.drawString("100", x, plotY + metrics.getAscent());
    if (!middleLabelled) g.drawString("50%", x, plotY + plotHeight / 2 - 2);
    g.drawString("0", x, plotY + plotHeight - 2);
    g.setFont(previousFont);
    g.setColor(previousColor);
  }

  /** The legend's colour swatches and names, right-aligned in the band above the plot. */
  static void drawLegend(
      Graphics2D g,
      Font font,
      int graphX,
      int graphY,
      int graphWidth,
      List<String> labels,
      List<Color> colors) {
    g.setFont(font);
    FontMetrics metrics = g.getFontMetrics();
    int swatch = 14;
    int width = 0;
    for (String label : labels) width += swatch + 4 + metrics.stringWidth(label) + 12;
    int x = graphX + graphWidth - width - 2;
    int baseline = graphY + 2 + metrics.getAscent();
    int lineY = baseline - (metrics.getAscent() - metrics.getDescent()) / 2;
    Stroke previous = g.getStroke();
    g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
    for (int i = 0; i < labels.size(); i++) {
      g.setColor(colors.get(i));
      g.drawLine(x, lineY, x + swatch, lineY);
      x += swatch + 4;
      g.setColor(TEXT);
      g.drawString(labels.get(i), x, baseline);
      x += metrics.stringWidth(labels.get(i)) + 12;
    }
    g.setStroke(previous);
  }
}
