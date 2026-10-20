package featurecat.lizzie.gui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.List;

/** Text and shape helpers shared by the custom-painted parts of the player-strength dialog. */
final class StrengthPaint {
  static final int LEFT = 0;
  static final int CENTER = 1;
  static final int RIGHT = 2;
  private static final String ELLIPSIS = "\u2026";

  private StrengthPaint() {}

  /**
   * The largest size of {@code font}, down to {@code minSize}, in which {@code text} is at most
   * {@code maxWidth} wide.
   */
  static Font fit(Graphics2D g, String text, Font font, int minSize, int maxWidth) {
    Font fitted = font;
    while (fitted.getSize() > minSize && g.getFontMetrics(fitted).stringWidth(text) > maxWidth) {
      fitted = fitted.deriveFont((float) fitted.getSize() - 1);
    }
    return fitted;
  }

  /**
   * Draws {@code text} at {@code baseline}, aligned on {@code x} as {@code align} says, shrinking
   * the font down to {@code minSize} and then shortening the text so it stays within {@code
   * maxWidth}. Returns the width drawn.
   */
  static int text(
      Graphics2D g,
      String text,
      Font font,
      int minSize,
      Color color,
      float x,
      float baseline,
      int maxWidth,
      int align) {
    String value = text == null ? "" : text;
    Font fitted = fit(g, value, font, minSize, maxWidth);
    FontMetrics metrics = g.getFontMetrics(fitted);
    value = ellipsize(value, metrics, maxWidth);
    int width = metrics.stringWidth(value);
    float left = align == CENTER ? x - width / 2f : align == RIGHT ? x - width : x;
    g.setFont(fitted);
    g.setColor(color);
    g.drawString(value, left, baseline);
    return width;
  }

  static String ellipsize(String text, FontMetrics metrics, int maxWidth) {
    if (text == null || text.isEmpty() || metrics.stringWidth(text) <= maxWidth) {
      return text == null ? "" : text;
    }
    int end = text.length();
    while (end > 0 && metrics.stringWidth(text.substring(0, end) + ELLIPSIS) > maxWidth) {
      end--;
    }
    return text.substring(0, end).stripTrailing() + ELLIPSIS;
  }

  /**
   * Breaks {@code text} into at most {@code maxLines} lines of at most {@code width}, at the places
   * the language allows; the last line is shortened when the text does not fit.
   */
  static List<String> wrap(String text, FontMetrics metrics, int width, int maxLines) {
    List<String> lines = new ArrayList<>();
    String value = text == null ? "" : text.strip();
    BreakIterator breaks = BreakIterator.getLineInstance();
    breaks.setText(value);
    int start = 0;
    while (start < value.length() && lines.size() < maxLines) {
      if (lines.size() == maxLines - 1) {
        lines.add(ellipsize(value.substring(start), metrics, width));
        break;
      }
      int end = -1;
      for (int next = breaks.following(start);
          next != BreakIterator.DONE
              && metrics.stringWidth(value.substring(start, next).stripTrailing()) <= width;
          next = breaks.next()) {
        end = next;
      }
      if (end < 0) {
        end = start + 1;
        while (end < value.length()
            && metrics.stringWidth(value.substring(start, end + 1)) <= width) {
          end++;
        }
      }
      lines.add(value.substring(start, end).strip());
      start = end;
      while (start < value.length() && Character.isWhitespace(value.charAt(start))) {
        start++;
      }
    }
    return lines;
  }

  static void dot(Graphics2D g, Color color, double centerX, double centerY, double diameter) {
    g.setColor(color);
    g.fill(
        new Ellipse2D.Double(centerX - diameter / 2, centerY - diameter / 2, diameter, diameter));
  }

  /** A small outlined circle with an "i", the mark of something that has an explanation. */
  static void infoMark(Graphics2D g, Color color, double x, double y, double size) {
    Stroke stroke = g.getStroke();
    g.setColor(color);
    g.setStroke(new BasicStroke((float) Math.max(1.0, size / 12.0)));
    g.draw(new Ellipse2D.Double(x + 0.5, y + 0.5, size - 1, size - 1));
    double centerX = x + size / 2.0;
    double dot = Math.max(1.6, size * 0.14);
    g.fill(new Ellipse2D.Double(centerX - dot / 2, y + size * 0.22, dot, dot));
    g.setStroke(
        new BasicStroke(
            (float) Math.max(1.3, size * 0.12), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
    g.draw(new Line2D.Double(centerX, y + size * 0.44, centerX, y + size * 0.74));
    g.setStroke(stroke);
  }

  /** Outlines {@code shape} in the accent colour to show keyboard focus. */
  static void focusRing(Graphics2D g, StrengthStyle style, Shape shape) {
    focusRing(g, style.accent, shape);
  }

  /** Outlines {@code shape} in {@code color} to show keyboard focus. */
  static void focusRing(Graphics2D g, Color color, Shape shape) {
    Stroke stroke = g.getStroke();
    g.setStroke(new BasicStroke(2f));
    g.setColor(StrengthStyle.alpha(color, 220));
    g.draw(shape);
    g.setStroke(stroke);
  }
}
