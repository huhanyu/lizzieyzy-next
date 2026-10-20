package featurecat.lizzie.gui;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.MultipleGradientPaint;
import java.awt.RadialGradientPaint;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Point2D;

/** Small shaded Go stones for the sidebar, optionally with a move number on them. */
final class StoneGlyph {
  private static final Color[] BLACK =
      new Color[] {new Color(118, 118, 116), new Color(40, 40, 39), new Color(6, 6, 6)};
  private static final Color[] WHITE =
      new Color[] {new Color(255, 255, 255), new Color(236, 232, 223), new Color(196, 188, 172)};
  private static final float[] STOPS = new float[] {0f, 0.45f, 1f};

  private StoneGlyph() {}

  /** Paints a stone of diameter {@code size} with its top-left corner at ({@code x}, {@code y}). */
  static void paint(Graphics2D g, double x, double y, double size, boolean black) {
    Ellipse2D stone = new Ellipse2D.Double(x, y, size, size);
    g.setPaint(
        new RadialGradientPaint(
            new Point2D.Double(x + size * 0.36, y + size * 0.3),
            (float) (size * 0.78),
            STOPS,
            black ? BLACK : WHITE,
            MultipleGradientPaint.CycleMethod.NO_CYCLE));
    g.fill(stone);
    // A hairline keeps a black stone visible on dark rows and a white stone on light rows.
    g.setColor(black ? new Color(255, 255, 255, 70) : new Color(0, 0, 0, 90));
    g.draw(new Ellipse2D.Double(x + 0.5, y + 0.5, size - 1, size - 1));
  }

  /** Paints a stone with {@code number} centred on it, as move numbers appear in a game record. */
  static void paintNumbered(
      Graphics2D g, double x, double y, double size, boolean black, String number, Font font) {
    paint(g, x, y, size, black);
    Font kerned = FittedFont.withKerning(font);
    FontMetrics metrics = g.getFontMetrics(kerned);
    g.setFont(kerned);
    g.setColor(black ? new Color(250, 250, 248) : new Color(24, 24, 24));
    float textX = (float) (x + (size - metrics.stringWidth(number)) / 2);
    float baseline =
        (float) (y + (size - metrics.getAscent() - metrics.getDescent()) / 2) + metrics.getAscent();
    g.drawString(number, textX, baseline);
  }

  /** The diameter a numbered stone needs so that {@code number} fits with a little margin. */
  static int numberedSize(FontMetrics metrics, String number) {
    return Math.max(metrics.getHeight() + 4, metrics.stringWidth(number) + 8);
  }
}
