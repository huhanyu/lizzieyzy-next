package featurecat.lizzie.gui;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.util.List;
import java.util.function.Supplier;
import javax.swing.JComponent;

/**
 * The card under the win chance: the result once the game is over, otherwise the player's last
 * move, with its rating in 边下边提醒.
 */
final class CoachMoveCard extends JComponent {
  private static final int PADDING = 14;
  private static final int LINE = 19;

  private final Supplier<CoachPanelState> state;

  CoachMoveCard(Supplier<CoachPanelState> state) {
    this.state = state;
    setOpaque(false);
  }

  /** 0 when there is nothing to show. */
  int heightFor() {
    return layout(null, getWidth());
  }

  @Override
  protected void paintComponent(Graphics graphics) {
    Graphics2D g = (Graphics2D) graphics.create();
    try {
      CoachPaint.smooth(g);
      layout(g, getWidth());
    } finally {
      g.dispose();
    }
  }

  private int layout(Graphics2D g, int width) {
    CoachPanelState s = state.get();
    if (s.result() != null) {
      return result(g, s, width);
    }
    CoachPanelState.MoveCard card = s.moveCard();
    if (card == null) {
      return 0;
    }
    return card.verdict() == null && card.change() == null && card.explanation() == null
        ? plainMove(g, card, width)
        : ratedMove(g, card, width);
  }

  /** The modes that do not rate moves: the label, then the move in bold. */
  private int plainMove(Graphics2D g, CoachPanelState.MoveCard card, int width) {
    int height = 62;
    if (g != null) {
      g.setColor(CoachPanel.SURFACE);
      g.fillRoundRect(0, 0, width, height, 24, 24);
      g.setColor(CoachPanel.MUTED);
      g.setFont(CoachPaint.font(card.label(), Font.PLAIN, 12.5f));
      g.drawString(card.label(), PADDING, 23);
      g.setColor(CoachPanel.TEXT);
      g.setFont(CoachPaint.font(card.move(), Font.BOLD, 15f));
      g.drawString(CoachPaint.fit(g.getFontMetrics(), card.move(), width - 28), PADDING, 47);
    }
    return height;
  }

  /** 边下边提醒: "你的上一手 · 第 71 手 L15 … 胜率 +1%", then the rating and why. */
  private int ratedMove(Graphics2D g, CoachPanelState.MoveCard card, int width) {
    Font tagFont = CoachPaint.font(card.verdictLabel(), Font.BOLD, 13f);
    int tagWidth =
        card.verdictLabel() == null
            ? 0
            : getFontMetrics(tagFont).stringWidth(card.verdictLabel()) + 14 + 10;
    Font bodyFont = CoachPaint.font(card.explanation(), Font.PLAIN, 13.5f);
    List<String> lines =
        CoachPaint.wrap(
            card.explanation(), getFontMetrics(bodyFont), width - 2 * PADDING - tagWidth);
    int bodyHeight =
        card.verdict() == null && lines.isEmpty() ? 0 : Math.max(24, lines.size() * LINE);
    int height = PADDING + 18 + (bodyHeight == 0 ? 0 : 10 + bodyHeight) + PADDING;
    if (g == null) {
      return height;
    }
    g.setColor(CoachPanel.SURFACE);
    g.fillRoundRect(0, 0, width, height, 24, 24);
    String change = card.change() == null ? "" : card.change();
    Font changeFont = CoachPaint.font(change, Font.BOLD, 13f);
    int changeWidth = getFontMetrics(changeFont).stringWidth(change);
    g.setFont(changeFont);
    g.setColor(
        card.verdict() == null
            ? CoachPanel.MUTED
            : CoachPaint.changeColor(card.verdict(), card.change()));
    g.drawString(change, width - PADDING - changeWidth, PADDING + 13);
    String title = card.label() + " · " + card.move();
    g.setFont(CoachPaint.font(title, Font.PLAIN, 13f));
    g.setColor(CoachPanel.MUTED);
    g.drawString(
        CoachPaint.fit(g.getFontMetrics(), title, width - 2 * PADDING - changeWidth - 8),
        PADDING,
        PADDING + 13);
    int top = PADDING + 18 + 10;
    if (card.verdict() != null) {
      g.setFont(tagFont);
      CoachPaint.tag(
          g,
          PADDING,
          top,
          24,
          card.verdictLabel(),
          CoachPaint.verdictSolid(card.verdict()),
          CoachPaint.verdictSolidText(card.verdict()));
    }
    g.setFont(bodyFont);
    g.setColor(CoachPaint.BODY_TEXT);
    FontMetrics body = g.getFontMetrics();
    int lineTop = lines.size() == 1 ? top + (24 - LINE) / 2 : top;
    for (int i = 0; i < lines.size(); i++) {
      g.drawString(
          lines.get(i),
          PADDING + tagWidth,
          lineTop + i * LINE + (LINE - body.getHeight()) / 2 + body.getAscent());
    }
    return height;
  }

  private int result(Graphics2D g, CoachPanelState s, int width) {
    Font hintFont = CoachPaint.font(s.resultHint(), Font.PLAIN, 12.5f);
    List<String> hint = CoachPaint.wrap(s.resultHint(), getFontMetrics(hintFont), width - 32);
    int height = 92 + hint.size() * LINE;
    if (g != null) {
      g.setColor(CoachPanel.RAISED);
      g.fillRoundRect(0, 0, width, height, 28, 28);
      g.setColor(new Color(61, 190, 170, 90));
      g.drawRoundRect(0, 0, width - 1, height - 1, 28, 28);
      String label = CoachPanel.text("CoachPanel.gameOver", "Game over");
      g.setColor(CoachPanel.MUTED);
      g.setFont(CoachPaint.font(label, Font.PLAIN, 12.5f));
      g.drawString(label, 16, 26);
      g.setColor(CoachPanel.TEXT);
      g.setFont(CoachPaint.font(s.result(), Font.BOLD, 24f));
      g.drawString(CoachPaint.fit(g.getFontMetrics(), s.result(), width - 32), 16, 60);
      g.setColor(CoachPanel.MUTED);
      g.setFont(hintFont);
      for (int i = 0; i < hint.size(); i++) {
        g.drawString(hint.get(i), 16, 86 + i * LINE);
      }
    }
    return height;
  }
}
