package featurecat.lizzie.gui;

import featurecat.lizzie.training.MoveVerdict;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.util.ArrayList;
import java.util.List;

/** Colours and drawing helpers shared by the parts of the AI Coach panel. */
final class CoachPaint {
  static final Color BODY_TEXT = new Color(0xd5, 0xdb, 0xd9);
  static final Color FAINT = new Color(0x8b, 0x95, 0x91);
  static final Color TRACK = new Color(0x2c, 0x32, 0x35);

  private CoachPaint() {}

  static void smooth(Graphics2D g) {
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    g.setRenderingHint(
        RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
    g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
  }

  static Font font(String text, int style, float size) {
    return HumanSlTrainingStyle.fontForText(text, style, Math.round(size));
  }

  /** Text colour of a rating, as in the history list. */
  static Color verdictColor(MoveVerdict verdict) {
    switch (verdict) {
      case BRILLIANT:
        return new Color(0x8c, 0xc3, 0xff);
      case SLOW:
        return new Color(0xf2, 0xc1, 0x4e);
      case PROBLEM:
        return new Color(0xf7, 0xa8, 0x6b);
      case BLUNDER:
        return new Color(0xff, 0x8a, 0x7f);
      case GOOD:
      default:
        return new Color(0x6f, 0xdd, 0xb0);
    }
  }

  /**
   * Colour of a win chance change: the rating's colour, except that a small drop on a move still
   * rated good stays neutral, so green always means the chance held or rose.
   */
  static Color changeColor(MoveVerdict verdict, String change) {
    boolean drop = change != null && change.startsWith("\u2212");
    return drop && !verdict.isMistake() ? CoachPanel.MUTED : verdictColor(verdict);
  }

  /** The translucent background of a rating's tag in the history list. */
  static Color verdictTint(MoveVerdict verdict) {
    Color solid = verdictSolid(verdict);
    return new Color(solid.getRed(), solid.getGreen(), solid.getBlue(), 41);
  }

  /** The filled tag on the last move's card. */
  static Color verdictSolid(MoveVerdict verdict) {
    switch (verdict) {
      case BRILLIANT:
        return new Color(0x79, 0xb8, 0xff);
      case SLOW:
        return new Color(0xf2, 0xc1, 0x4e);
      case PROBLEM:
        return new Color(0xf5, 0x9e, 0x5b);
      case BLUNDER:
        return new Color(0xff, 0x7a, 0x6e);
      case GOOD:
      default:
        return new Color(0x5f, 0xd3, 0xa5);
    }
  }

  /** Dark text that reads on {@link #verdictSolid}. */
  static Color verdictSolidText(MoveVerdict verdict) {
    switch (verdict) {
      case BRILLIANT:
        return new Color(0x0b, 0x1f, 0x33);
      case SLOW:
        return new Color(0x2e, 0x23, 0x06);
      case PROBLEM:
        return new Color(0x2f, 0x1a, 0x08);
      case BLUNDER:
        return new Color(0x33, 0x11, 0x0d);
      case GOOD:
      default:
        return new Color(0x0e, 0x2a, 0x1f);
    }
  }

  /** Draws a rounded tag with its label and returns its width. */
  static int tag(Graphics2D g, int x, int y, int height, String label, Color fill, Color text) {
    FontMetrics metrics = g.getFontMetrics();
    int width = metrics.stringWidth(label) + 14;
    g.setColor(fill);
    g.fillRoundRect(x, y, width, height, 12, 12);
    g.setColor(text);
    g.drawString(label, x + 7, y + (height - metrics.getHeight()) / 2 + metrics.getAscent());
    return width;
  }

  static void stone(Graphics2D g, int cx, int cy, int radius, boolean black) {
    Color[] colors =
        black
            ? new Color[] {new Color(0x6c, 0x6c, 0x6c), new Color(0x11, 0x11, 0x11)}
            : new Color[] {Color.WHITE, new Color(0xcf, 0xcb, 0xc2)};
    g.setPaint(
        new RadialGradientPaint(
            cx - radius * 0.3f, cy - radius * 0.4f, radius * 1.4f, new float[] {0f, 1f}, colors));
    g.fillOval(cx - radius, cy - radius, radius * 2, radius * 2);
    if (black) {
      // A light rim keeps a black stone visible on the dark panel.
      g.setColor(new Color(0x6a, 0x74, 0x78));
      g.setStroke(new BasicStroke(1.2f));
      g.drawOval(cx - radius, cy - radius, radius * 2, radius * 2);
      g.setStroke(new BasicStroke(1f));
    }
  }

  /** {@code value} cut to {@code width} with an ellipsis. */
  static String fit(FontMetrics metrics, String value, int width) {
    if (value == null) {
      return "";
    }
    if (metrics.stringWidth(value) <= width) {
      return value;
    }
    String ellipsis = "…";
    for (int end = value.length() - 1; end > 0; end--) {
      String candidate = value.substring(0, end) + ellipsis;
      if (metrics.stringWidth(candidate) <= width) {
        return candidate;
      }
    }
    return ellipsis;
  }

  /** Splits {@code value} into lines; CJK text may break between any two characters. */
  static List<String> wrap(String value, FontMetrics metrics, int width) {
    List<String> lines = new ArrayList<>();
    if (value == null || value.isEmpty()) {
      return lines;
    }
    StringBuilder line = new StringBuilder();
    int lastSpace = -1;
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      line.append(c);
      if (c == ' ') {
        lastSpace = line.length() - 1;
      }
      if (metrics.stringWidth(line.toString()) > width && line.length() > 1) {
        if (lastSpace > 0) {
          lines.add(line.substring(0, lastSpace));
          line.delete(0, lastSpace + 1);
        } else {
          lines.add(line.substring(0, line.length() - 1));
          line.delete(0, line.length() - 1);
        }
        lastSpace = line.lastIndexOf(" ");
      }
    }
    if (line.length() > 0) {
      lines.add(line.toString());
    }
    return lines;
  }
}
