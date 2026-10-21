package featurecat.lizzie.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.List;
import org.junit.jupiter.api.Test;

class WinrateGraphFrameTest {
  private static final Font FONT = new Font(Font.SANS_SERIF, Font.PLAIN, 11);

  @Test
  void wideGraphsGiveTheLegendABandOfFixedHeight() {
    int band = WinrateGraphFrame.LEGEND_BAND;

    assertEquals(band, WinrateGraphFrame.legendBand(360, true, false), "same on every platform");
    assertTrue(band >= metrics().getHeight(), "the legend's text fits in the band");
    assertEquals(0, WinrateGraphFrame.legendBand(200, true, false), "narrow graphs keep theirs");
    assertEquals(0, WinrateGraphFrame.legendBand(360, false, false), "no legend, no band");
  }

  @Test
  void theLegendStaysInItsBandAboveThePlot() {
    BufferedImage image = new BufferedImage(360, 120, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = image.createGraphics();
    int band = WinrateGraphFrame.LEGEND_BAND;
    WinrateGraphFrame.drawLegend(
        g, FONT, 0, 0, 360, List.of("黑方胜率", "目差"), List.of(Color.CYAN, Color.MAGENTA));
    g.dispose();

    assertTrue(paintedRows(image, 0, band) > 0, "the legend is drawn");
    assertEquals(0, paintedRows(image, band, image.getHeight()), "nothing covers the plot");
  }

  @Test
  void theScaleSitsJustInsideThePlotsLeftEdge() {
    BufferedImage image = new BufferedImage(360, 200, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = image.createGraphics();
    int plotX = 30;
    WinrateGraphFrame.drawScale(g, FONT, plotX, 20, 160, false);
    g.dispose();

    int labels = metrics().stringWidth("100") + 6;
    assertEquals(0, paintedColumns(image, 0, plotX), "nothing outside the plot");
    assertTrue(paintedColumns(image, plotX, plotX + labels) > 0, "100 / 50% / 0 are labelled");
    assertEquals(0, paintedColumns(image, plotX + labels + 4, 360), "only at the left edge");
  }

  private static FontMetrics metrics() {
    Graphics2D g = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).createGraphics();
    FontMetrics metrics = g.getFontMetrics(FONT);
    g.dispose();
    return metrics;
  }

  private static int paintedRows(BufferedImage image, int from, int to) {
    int count = 0;
    for (int y = from; y < to; y++) {
      for (int x = 0; x < image.getWidth(); x++) {
        if ((image.getRGB(x, y) >>> 24) > 0) {
          count++;
          break;
        }
      }
    }
    return count;
  }

  private static int paintedColumns(BufferedImage image, int from, int to) {
    int count = 0;
    for (int x = from; x < to; x++) {
      for (int y = 0; y < image.getHeight(); y++) {
        if ((image.getRGB(x, y) >>> 24) > 0) {
          count++;
          break;
        }
      }
    }
    return count;
  }
}
