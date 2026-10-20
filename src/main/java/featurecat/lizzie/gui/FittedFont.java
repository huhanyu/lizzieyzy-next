package featurecat.lizzie.gui;

import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.font.TextAttribute;
import java.util.Map;

/**
 * Chooses the font for a label that must fit a box, such as the status line under the win-rate bar.
 *
 * <p>The fitted font always keeps {@link TextAttribute#KERNING}, also when its size is capped. This
 * matters for more than spacing: {@code Graphics2D.drawString} lays out a font with a layout
 * attribute through {@code TextLayout}. Without one it takes the JDK's fast path, which on macOS
 * draws the Latin glyphs of a string blank when the same string also needs glyphs from a fallback
 * font (Chinese, Japanese or Korean in the Dialog font), and caches the blank glyphs for that size,
 * so later labels lose them as well. Rebuilding the capped font with {@code new Font(name, style,
 * size)} dropped the attribute and turned "黑领先:0.4 最后一手(# ): +19.8%" into "黑领先 4 最后一手 19 8".
 */
final class FittedFont {
  private static final Map<TextAttribute, Object> KERNING =
      Map.of(TextAttribute.KERNING, TextAttribute.KERNING_ON);

  private FittedFont() {}

  /**
   * Returns the largest font based on {@code base} in which {@code text} is at most {@code
   * maxWidth} wide and at most {@code maxHeight} high, and never larger than {@code maxSize}.
   */
  static Font fit(
      Graphics2D g,
      Font base,
      int style,
      String text,
      double maxHeight,
      double maxWidth,
      int maxSize) {
    Font font = base.deriveFont(style, 100f).deriveFont(KERNING);
    FontMetrics metrics = g.getFontMetrics(font);
    font = font.deriveFont((float) (font.getSize2D() * maxWidth / metrics.stringWidth(text)));
    font = font.deriveFont((float) Math.min(maxHeight, font.getSize()));
    return font.getSize() > maxSize ? font.deriveFont((float) maxSize) : font;
  }
}
