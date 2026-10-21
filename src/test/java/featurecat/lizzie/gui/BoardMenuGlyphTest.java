package featurecat.lizzie.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import javax.swing.Icon;
import javax.swing.JMenuItem;
import org.junit.jupiter.api.Test;

class BoardMenuGlyphTest {
  private static final Color LIGHT = new Color(0xFB, 0xF8, 0xF2);
  private static final Color DARK = new Color(38, 41, 47);

  @Test
  void everyGlyphIsMenuSizedAndStaysInsideItsBox() {
    for (BoardMenuGlyph glyph : BoardMenuGlyph.values()) {
      Icon icon = glyph.icon();
      assertEquals(16, icon.getIconWidth(), glyph.name());
      assertEquals(16, icon.getIconHeight(), glyph.name());
      for (Color surface : new Color[] {LIGHT, DARK}) {
        BufferedImage image = render(icon, item(surface, ink(surface), true));
        int inside = 0;
        int outside = 0;
        for (int y = 0; y < image.getHeight(); y++) {
          for (int x = 0; x < image.getWidth(); x++) {
            if (image.getRGB(x, y) == surface.getRGB()) continue;
            boolean box = x >= MARGIN && x < MARGIN + 32 && y >= MARGIN && y < MARGIN + 32;
            if (box) inside++;
            else outside++;
          }
        }
        assertTrue(inside > 40, glyph + " draws something");
        assertEquals(0, outside, glyph + " stays inside its 16 px box");
      }
    }
  }

  @Test
  void linesTakeTheItemTextColourAndFadeWhenDisabled() {
    Color text = new Color(0x33, 0x3A, 0x36);
    Icon icon = BoardMenuGlyph.REMOVE.icon();
    int enabled = darkest(render(icon, item(LIGHT, text, true)));
    int disabled = darkest(render(icon, item(LIGHT, text, false)));
    assertTrue(Math.abs(enabled - luminance(text)) < 12, "ink is the item's text colour");
    assertTrue(disabled > enabled + 60, "a disabled item's glyph fades: " + disabled);
    BufferedImage onDark = render(icon, item(DARK, new Color(244, 247, 251), true));
    assertTrue(brightest(onDark) > 220, "a dark menu gets light lines");
  }

  @Test
  void keepGlyphsShowTheToggleState() {
    JMenuItem item = item(LIGHT, ink(LIGHT), true);
    assertTrue(bluePixels(render(BoardMenuGlyph.KEEP_ON.icon(), item)) > 200);
    assertEquals(0, bluePixels(render(BoardMenuGlyph.KEEP_OFF.icon(), item)));
  }

  private static final int MARGIN = 4;

  /** The icon at a 2x display scale, inside a margin. */
  static BufferedImage render(Icon icon, JMenuItem item) {
    BufferedImage image =
        new BufferedImage(32 + 2 * MARGIN, 32 + 2 * MARGIN, BufferedImage.TYPE_INT_RGB);
    Graphics2D g = image.createGraphics();
    g.setColor(item.getBackground());
    g.fillRect(0, 0, image.getWidth(), image.getHeight());
    g.translate(MARGIN, MARGIN);
    g.scale(2, 2);
    icon.paintIcon(item, g, 0, 0);
    g.dispose();
    return image;
  }

  static int bluePixels(BufferedImage image) {
    int count = 0;
    for (int y = 0; y < image.getHeight(); y++) {
      for (int x = 0; x < image.getWidth(); x++) {
        Color c = new Color(image.getRGB(x, y));
        if (c.getBlue() > 150 && c.getBlue() > c.getRed() + 60) count++;
      }
    }
    return count;
  }

  private static JMenuItem item(Color surface, Color text, boolean enabled) {
    JMenuItem item = new JMenuItem();
    item.setBackground(surface);
    item.setForeground(text);
    item.setEnabled(enabled);
    return item;
  }

  private static Color ink(Color surface) {
    return surface == DARK ? new Color(244, 247, 251) : new Color(0x33, 0x3A, 0x36);
  }

  private static int darkest(BufferedImage image) {
    int min = 255;
    for (int y = 0; y < image.getHeight(); y++) {
      for (int x = 0; x < image.getWidth(); x++) {
        min = Math.min(min, luminance(new Color(image.getRGB(x, y))));
      }
    }
    return min;
  }

  private static int brightest(BufferedImage image) {
    int max = 0;
    for (int y = 0; y < image.getHeight(); y++) {
      for (int x = 0; x < image.getWidth(); x++) {
        max = Math.max(max, luminance(new Color(image.getRGB(x, y))));
      }
    }
    return max;
  }

  private static int luminance(Color c) {
    return (int) Math.round(0.2126 * c.getRed() + 0.7152 * c.getGreen() + 0.0722 * c.getBlue());
  }
}
