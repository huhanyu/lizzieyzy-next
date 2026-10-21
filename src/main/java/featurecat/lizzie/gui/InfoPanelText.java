package featurecat.lizzie.gui;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.math.BigDecimal;
import java.text.MessageFormat;
import java.util.Locale;
import java.util.ResourceBundle;

/** The labelled figures of the game panel above the win-rate bar: "吻合度 54.4", "贴目 7.5". */
final class InfoPanelText {
  static final Color SECONDARY_TEXT = new Color(0xB7, 0xBC, 0xC4);

  private InfoPanelText() {}

  static String captures(ResourceBundle strings, int stones) {
    return format(strings, "LizzieFrame.info.captures", String.valueOf(stones));
  }

  static String accuracy(ResourceBundle strings, double percent) {
    return format(
        strings, "LizzieFrame.info.accuracy", String.format(Locale.ENGLISH, "%.1f", percent));
  }

  /** Komi as set, without trailing zeros: "7.5", "7", "6.25". */
  static String komi(ResourceBundle strings, double komi) {
    String value = BigDecimal.valueOf(komi).stripTrailingZeros().toPlainString();
    return format(strings, "LizzieFrame.info.komi", value);
  }

  static String turn(ResourceBundle strings, boolean blackToPlay) {
    return strings.getString(
        blackToPlay ? "LizzieFrame.info.blackToPlay" : "LizzieFrame.info.whiteToPlay");
  }

  /**
   * Draws the engine status dot, green while it analyses and red otherwise, and after it the side
   * to move unless {@code turn} is null, together centred on ({@code centerX}, {@code centerY}).
   */
  static void drawStatusAndTurn(
      Graphics2D g, Font font, int centerX, int centerY, int dot, boolean analysing, String turn) {
    Font kerned = FittedFont.withKerning(font);
    FontMetrics metrics = g.getFontMetrics(kerned);
    int gap = turn == null ? 0 : Math.max(4, dot / 2 + 1);
    int textWidth = turn == null ? 0 : metrics.stringWidth(turn);
    int left = centerX - (dot + gap + textWidth) / 2;
    g.setColor(analysing ? Color.GREEN : Color.RED);
    g.fillOval(left, centerY - dot / 2, dot, dot);
    if (turn == null) return;
    g.setFont(kerned);
    g.setColor(SECONDARY_TEXT);
    int baseline = centerY + (metrics.getAscent() - metrics.getDescent()) / 2;
    g.drawString(turn, left + dot + gap, baseline);
  }

  private static String format(ResourceBundle strings, String key, String value) {
    return MessageFormat.format(strings.getString(key), value);
  }
}
