package featurecat.lizzie.gui;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.ResourceBundle;

/**
 * The engine status at the bottom left of the main window: one small line such as "KataGo · 分析中 ·
 * 空格 暂停 · 按住 X 看快捷键", on a translucent band no larger than the line. It replaced three lines whose
 * font grew with the window and covered the panel above.
 */
final class StatusOverlay {
  private static final Color BAND = new Color(0, 0, 0, 140);
  private static final int PADDING = 6;

  private StatusOverlay() {}

  /** Font size of the status line: two steps above the frame font, at least 13. */
  static int fontSize(int frameFontSize) {
    return Math.max(13, frameFontSize + 2);
  }

  /** Height of one status line, band included. */
  static int lineHeight(int frameFontSize) {
    return Math.round(fontSize(frameFontSize) * 1.8f);
  }

  /**
   * Height to reserve at the bottom of the panel: one line, or two while the engine loads, when a
   * cause and its remedy must both stay readable.
   */
  static int reservedHeight(int frameFontSize, boolean engineLoaded) {
    return (engineLoaded ? 1 : 2) * lineHeight(frameFontSize) + 4;
  }

  static String state(ResourceBundle strings, boolean autoAnalyzing, boolean pondering) {
    return strings.getString(
        autoAnalyzing
            ? "LizzieFrame.status.autoAnalyzing"
            : pondering ? "LizzieFrame.status.analyzing" : "LizzieFrame.status.paused");
  }

  /**
   * The keys to press: Space pauses or resumes the analysis, and holding X lists the shortcuts
   * until the user has found it. While a whole game is analysed automatically, Space is left out.
   */
  static String hint(
      ResourceBundle strings, boolean autoAnalyzing, boolean pondering, boolean userKnowsX) {
    String space =
        autoAnalyzing
            ? ""
            : strings.getString(
                pondering ? "LizzieFrame.status.spaceToPause" : "LizzieFrame.status.spaceToResume");
    return MainWindowTitle.join(
        " · ", space, userKnowsX ? "" : strings.getString("LizzieFrame.status.holdX"));
  }

  static String line(String engine, String state, String hint) {
    return MainWindowTitle.join(" · ", engine, state, hint);
  }

  /** Draws {@code text} on one line at {@code (x, top)}, shortened to fit {@code maxWidth}. */
  static void draw(Graphics2D g, Font font, String text, int x, int top, int height, int maxWidth) {
    if (text == null || text.isEmpty() || height <= 0 || maxWidth <= 2 * PADDING) {
      return;
    }
    // Kerning sends the mixed Chinese and Latin line through TextLayout, which macOS draws whole.
    Font kerned = FittedFont.withKerning(font);
    FontMetrics metrics = g.getFontMetrics(kerned);
    String shown = LizzieFrame.truncateStatusText(text, metrics, maxWidth - 2 * PADDING);
    if (shown.isEmpty()) {
      return;
    }
    int width = metrics.stringWidth(shown) + 2 * PADDING;
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    g.setColor(BAND);
    g.fillRoundRect(x, top, width, height, 8, 8);
    g.setColor(Color.WHITE);
    g.setFont(kerned);
    g.drawString(
        shown, x + PADDING, top + (height - metrics.getHeight()) / 2 + metrics.getAscent());
  }
}
