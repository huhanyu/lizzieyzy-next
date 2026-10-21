package featurecat.lizzie.gui;

import featurecat.lizzie.Config;
import featurecat.lizzie.theme.MorandiPalette;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/**
 * The main window's candidate table in the dark of the sidebar beside it: its colours, the played
 * move's row colour by how far it falls short of the best move, the "实战" tag after its rank and the
 * bar under each move's share of the visits.
 */
final class CandidateTableStyle {
  static final Color SURFACE = new Color(0x23, 0x25, 0x28);
  static final Color SURFACE_ALT = new Color(0x26, 0x29, 0x2C);
  static final Color HEADER = new Color(0x2F, 0x32, 0x36);
  static final Color HEADER_TEXT = new Color(0xB7, 0xBC, 0xC4);
  static final Color TEXT = new Color(0xF1, 0xF2, 0xF4);
  static final Color BORDER = new Color(0x3A, 0x3E, 0x44);
  static final Color SELECTED = new Color(255, 255, 255, 30);
  static final Color SELECTED_TEXT = new Color(0x6F, 0xD3, 0xC0);
  static final Color CHANGED_TEXT = new Color(0xFF, 0x8A, 0x80);
  private static final Color TAG = new Color(0x2F, 0x6B, 0x5E);
  private static final Color TAG_TEXT = new Color(0xD9, 0xF5, 0xEE);
  private static final Color SHARE_TRACK = new Color(0x3A, 0x3E, 0x44);
  private static final Color SHARE_FILL = new Color(0x7F, 0xB5, 0xA8);

  private CandidateTableStyle() {}

  static Color rowBackground(int row) {
    return row % 2 == 0 ? SURFACE : SURFACE_ALT;
  }

  /**
   * The played move's row: from best to blunder by the win-rate and score gaps (negative when worse
   * than the best move), stronger while the row is selected.
   */
  static Color playedMoveBackground(
      Config config, double winrateGap, double scoreGap, boolean selected) {
    boolean morandi = config.useMorandiColors;
    if (winrateGap <= config.winLossThreshold5 || scoreGap <= config.scoreLossThreshold5)
      return morandi
          ? (selected
              ? MorandiPalette.SUGGESTION_BLUNDER_ALPHA
              : MorandiPalette.SUGGESTION_BLUNDER_LIGHT)
          : new Color(85, 25, 80, selected ? 120 : 70);
    if (winrateGap <= config.winLossThreshold4 || scoreGap <= config.scoreLossThreshold4)
      return morandi
          ? (selected
              ? MorandiPalette.SUGGESTION_MISTAKE_ALPHA
              : MorandiPalette.SUGGESTION_MISTAKE_LIGHT)
          : new Color(208, 16, 19, selected ? 100 : 50);
    if (winrateGap <= config.winLossThreshold3 || scoreGap <= config.scoreLossThreshold3)
      return morandi
          ? (selected ? MorandiPalette.SUGGESTION_SLOW_ALPHA : MorandiPalette.SUGGESTION_SLOW_LIGHT)
          : new Color(200, 140, 50, selected ? 100 : 50);
    if (winrateGap <= config.winLossThreshold2 || scoreGap <= config.scoreLossThreshold2)
      return morandi
          ? (selected
              ? MorandiPalette.SUGGESTION_CAUTION_ALPHA
              : MorandiPalette.SUGGESTION_CAUTION_LIGHT)
          : new Color(180, 180, 0, selected ? 100 : 50);
    if (winrateGap <= config.winLossThreshold1 || scoreGap <= config.scoreLossThreshold1)
      return morandi
          ? (selected ? MorandiPalette.SUGGESTION_GOOD_ALPHA : MorandiPalette.SUGGESTION_GOOD_LIGHT)
          : new Color(140, 202, 34, selected ? 100 : 50);
    return morandi
        ? (selected ? MorandiPalette.SUGGESTION_BEST_ALPHA : MorandiPalette.SUGGESTION_BEST_LIGHT)
        : new Color(0, 180, 0, selected ? 100 : 60);
  }

  /**
   * The rank cell of the played move: its rank, then the tag in a pill. The model writes "1(实战)",
   * or just the tag when the move is not among the candidates.
   */
  static void paintRankWithTag(
      Graphics2D g, String cell, Font font, Color text, int width, int height) {
    String value = cell == null ? "" : cell.trim();
    int open = value.indexOf('(');
    String rank = open > 0 ? value.substring(0, open) : "";
    String tag = open > 0 ? value.substring(open + 1, value.lastIndexOf(')')) : value;
    Font tagFont = font.deriveFont(Math.max(9f, font.getSize2D() - 3f));
    FontMetrics rankMetrics = g.getFontMetrics(font);
    FontMetrics tagMetrics = g.getFontMetrics(tagFont);
    int gap = rank.isEmpty() ? 0 : 4;
    int tagWidth = tagMetrics.stringWidth(tag) + 8;
    int left = (width - rankMetrics.stringWidth(rank) - gap - tagWidth) / 2;
    int baseline = (height - rankMetrics.getHeight()) / 2 + rankMetrics.getAscent();
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    g.setFont(font);
    g.setColor(text);
    g.drawString(rank, left, baseline);
    int tagX = left + rankMetrics.stringWidth(rank) + gap;
    int tagHeight = tagMetrics.getHeight() + 2;
    int tagY = (height - tagHeight) / 2;
    g.setColor(TAG);
    g.fillRoundRect(tagX, tagY, tagWidth, tagHeight, 6, 6);
    g.setFont(tagFont);
    g.setColor(TAG_TEXT);
    g.drawString(tag, tagX + 4, tagY + 1 + tagMetrics.getAscent());
  }

  /** A thin bar along the bottom of the share cell, as long as the share of all visits. */
  static void paintShareBar(Graphics2D g, double percent, int width, int height) {
    int left = 4;
    int track = Math.max(1, width - 2 * left);
    int y = height - 4;
    g.setColor(SHARE_TRACK);
    g.fillRect(left, y, track, 2);
    g.setColor(SHARE_FILL);
    g.fillRect(left, y, (int) Math.round(track * Math.max(0, Math.min(100, percent)) / 100), 2);
  }
}
