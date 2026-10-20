package featurecat.lizzie.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.font.GlyphVector;
import java.awt.font.TextAttribute;
import java.awt.image.BufferedImage;
import org.junit.jupiter.api.Test;

class FittedFontTest {
  // The status line under the win-rate bar: Chinese mixed with digits and punctuation.
  private static final String STATUS = "黑领先:0.4 最后一手(#  ): +19.8% +1.3目";
  private static final Font DIALOG = new Font(Font.DIALOG, Font.PLAIN, 12);

  @Test
  void cappedFontKeepsKerning() {
    Graphics2D g = canvas().createGraphics();

    Font font = FittedFont.fit(g, DIALOG, Font.PLAIN, STATUS, 54, 857, 28);

    assertEquals(28, font.getSize());
    assertEquals(TextAttribute.KERNING_ON, font.getAttributes().get(TextAttribute.KERNING));
    assertTrue(font.hasLayoutAttributes());
  }

  @Test
  void uncappedFontFillsTheWidthInTheRequestedStyle() {
    Graphics2D g = canvas().createGraphics();

    Font font = FittedFont.fit(g, DIALOG, Font.BOLD, STATUS, 200, 300, Integer.MAX_VALUE);

    assertTrue(font.isBold());
    int width = g.getFontMetrics(font).stringWidth(STATUS);
    assertTrue(width > 270 && width < 320, "width " + width);
  }

  @Test
  void heightLimitWinsOverWidth() {
    Graphics2D g = canvas().createGraphics();

    Font font = FittedFont.fit(g, DIALOG, Font.PLAIN, "0.4", 10, 2000, Integer.MAX_VALUE);

    assertEquals(10f, font.getSize2D());
  }

  @Test
  void cappedMixedScriptLabelKeepsItsLatinGlyphsOnMacOs() {
    assumeTrue(System.getProperty("os.name", "").startsWith("Mac"));
    BufferedImage image = canvas();
    Graphics2D g = image.createGraphics();
    // An unusual size, so no earlier test in this JVM has cached Dialog glyphs at it.
    Font font = FittedFont.fit(g, DIALOG, Font.PLAIN, STATUS, 80, 2000, 37);
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    g.setColor(Color.WHITE);
    g.setFont(font);

    g.drawString(STATUS, 10, 80);

    GlyphVector glyphs =
        font.layoutGlyphVector(
            g.getFontRenderContext(),
            STATUS.toCharArray(),
            0,
            STATUS.length(),
            Font.LAYOUT_LEFT_TO_RIGHT);
    StringBuilder blank = new StringBuilder();
    for (int i = 0; i < glyphs.getNumGlyphs(); i++) {
      Rectangle box = glyphs.getGlyphOutline(i, 10, 80).getBounds();
      if (!box.isEmpty() && !hasInk(image, box)) {
        blank.append(STATUS.charAt(glyphs.getGlyphCharIndex(i)));
      }
    }
    assertEquals("", blank.toString(), "characters drawn blank");
  }

  private static BufferedImage canvas() {
    return new BufferedImage(1600, 120, BufferedImage.TYPE_INT_ARGB);
  }

  private static boolean hasInk(BufferedImage image, Rectangle box) {
    for (int y = Math.max(0, box.y); y < Math.min(image.getHeight(), box.y + box.height); y++) {
      for (int x = Math.max(0, box.x); x < Math.min(image.getWidth(), box.x + box.width); x++) {
        if ((image.getRGB(x, y) >>> 24) != 0) {
          return true;
        }
      }
    }
    return false;
  }
}
