package featurecat.lizzie.gui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.font.TextLayout;
import java.awt.geom.AffineTransform;

/**
 * The players' names under the board: a light plate behind both names and the two small stones,
 * with the names in dark type. They used to be black and white-outlined text straight on the wood
 * grain, which made the white player's name hard to read.
 */
final class BoardNamePlate {
  static final Color PLATE = new Color(250, 248, 242, 230);
  static final Color TEXT = new Color(0x1F, 0x23, 0x28);
  private static final Color STONE_EDGE = new Color(0, 0, 0, 120);

  private BoardNamePlate() {}

  static void paintPlate(Graphics2D g, int left, int top, int right, int bottom) {
    int height = Math.max(1, bottom - top);
    g.setColor(PLATE);
    g.fillRoundRect(left, top, Math.max(1, right - left), height, height, height);
  }

  /** The two stones between the names; the side to move is drawn on top. */
  static void paintStones(
      Graphics2D g, int blackX, int whiteX, int top, int size, boolean blackOnTop) {
    if (blackOnTop) {
      white(g, whiteX, top, size);
      black(g, blackX, top, size);
    } else {
      black(g, blackX, top, size);
      white(g, whiteX, top, size);
    }
  }

  /** A name as glyph outlines, which keeps every glyph of mixed-script names on macOS. */
  static void paintName(Graphics2D g, Font font, String name, double x, double baseline) {
    if (name.isEmpty()) return;
    TextLayout layout = new TextLayout(name, font, g.getFontRenderContext());
    g.setColor(TEXT);
    g.fill(layout.getOutline(AffineTransform.getTranslateInstance(x, baseline)));
  }

  private static void black(Graphics2D g, int x, int top, int size) {
    g.setColor(Color.BLACK);
    g.fillOval(x, top, size, size);
  }

  private static void white(Graphics2D g, int x, int top, int size) {
    g.setColor(Color.WHITE);
    g.fillOval(x, top, size, size);
    g.setColor(STONE_EDGE);
    g.setStroke(new BasicStroke(1f));
    g.drawOval(x, top, size, size);
  }
}
