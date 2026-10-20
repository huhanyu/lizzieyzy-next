package featurecat.lizzie.gui;

import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.geom.Line2D;

/** Places the moves of a game along a horizontal chart and labels them with round move numbers. */
final class StrengthMoveAxis {
  final int left;
  final int width;
  final int maxMove;

  /** The width of the mark of one move. */
  final int tick;

  StrengthMoveAxis(int left, int width, int maxMove, int maxTick) {
    this.left = left;
    this.width = Math.max(1, width);
    this.maxMove = Math.max(1, maxMove);
    tick = Math.max(2, Math.min(maxTick, (int) (2.0 * this.width / this.maxMove) - 1));
  }

  /** The left edge of the mark of {@code move}. */
  int x(int move) {
    if (maxMove <= 1) {
      return left;
    }
    double position = StrengthStats.clamp((move - 1.0) / (maxMove - 1.0));
    return left + (int) Math.round(position * (width - tick));
  }

  int center(int move) {
    return x(move) + tick / 2;
  }

  /** How far a pointer may be from a mark's centre and still point at it. */
  int reach() {
    return Math.max(tick, (int) Math.ceil(width / (double) maxMove));
  }

  /** A round step between labelled moves, giving about one label per {@code spacing} points. */
  int step(int spacing) {
    int targetTicks = Math.max(3, width / Math.max(1, spacing));
    double rough = Math.max(1.0, (maxMove - 1.0) / targetTicks);
    int magnitude = 1;
    while (magnitude * 10 < rough) {
      magnitude *= 10;
    }
    for (int factor : new int[] {1, 2, 5, 10}) {
      if (factor * magnitude >= rough) {
        return factor * magnitude;
      }
    }
    return magnitude * 10;
  }

  /**
   * Draws a grid line from {@code gridTop} to {@code gridBottom} at move 1 and at each round move,
   * and labels them, and the last move, under {@code labelTop}.
   */
  void paint(
      Graphics2D g, StrengthStyle style, Font font, int gridTop, int gridBottom, int labelTop) {
    FontMetrics metrics = g.getFontMetrics(font);
    int step = step(style.px(80));
    int baseline = labelTop + metrics.getAscent();
    int end = center(maxMove);
    String endLabel = String.valueOf(maxMove);
    int endLeft = end - metrics.stringWidth(endLabel) / 2;
    int lastRight = Integer.MIN_VALUE;
    g.setFont(font);
    for (int move = 1; move < maxMove; move = move == 1 && step > 1 ? step : move + step) {
      int x = center(move);
      g.setColor(style.divider);
      g.draw(new Line2D.Double(x + 0.5, gridTop, x + 0.5, gridBottom));
      String label = String.valueOf(move);
      int labelWidth = metrics.stringWidth(label);
      if (x - labelWidth / 2 > lastRight + style.px(8)
          && x + labelWidth / 2 < endLeft - style.px(8)) {
        g.setColor(style.tertiaryText);
        g.drawString(label, x - labelWidth / 2f, baseline);
        lastRight = x + labelWidth / 2;
      }
    }
    g.setColor(style.divider);
    g.draw(new Line2D.Double(end + 0.5, gridTop, end + 0.5, gridBottom));
    g.setColor(style.tertiaryText);
    g.drawString(endLabel, endLeft, baseline);
  }
}
