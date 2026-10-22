package featurecat.lizzie.gui;

import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.util.List;
import java.util.function.Supplier;
import javax.swing.JComponent;

/**
 * 边下边提醒's record of the game so far: how many moves got each rating, and the latest rated moves, as
 * many as fit above the buttons.
 */
final class CoachHistoryView extends JComponent {
  static final int TALLY_HEIGHT = 52;
  static final int HEADING_HEIGHT = 26;
  static final int ROW_HEIGHT = 34;
  private static final int GAP = 12;

  private final Supplier<CoachPanelState> state;

  /**
   * @param onPick receives the move number of a clicked row once the game is over
   */
  CoachHistoryView(Supplier<CoachPanelState> state, java.util.function.IntConsumer onPick) {
    this.state = state;
    setOpaque(false);
    java.awt.event.MouseAdapter mouse =
        new java.awt.event.MouseAdapter() {
          @Override
          public void mouseClicked(java.awt.event.MouseEvent e) {
            CoachPanelState.Row row = rowAt(e.getY());
            if (row != null) {
              onPick.accept(row.moveNumber());
            }
          }

          @Override
          public void mouseMoved(java.awt.event.MouseEvent e) {
            setCursor(
                java.awt.Cursor.getPredefinedCursor(
                    rowAt(e.getY()) == null
                        ? java.awt.Cursor.DEFAULT_CURSOR
                        : java.awt.Cursor.HAND_CURSOR));
          }
        };
    addMouseListener(mouse);
    addMouseMotionListener(mouse);
  }

  /** The clickable row at {@code y}, or null. */
  CoachPanelState.Row rowAt(int y) {
    CoachPanelState.History history = state.get().history();
    if (history == null || !history.clickable()) {
      return null;
    }
    int top = TALLY_HEIGHT + GAP + HEADING_HEIGHT;
    int index = y < top ? -1 : (y - top) / ROW_HEIGHT;
    int rows = rowsThatFit(getHeight(), history.recent().size());
    return index >= 0 && index < rows ? history.recent().get(index) : null;
  }

  /** How many of the latest moves fit in {@code height}. */
  static int rowsThatFit(int height, int available) {
    int rows = (height - TALLY_HEIGHT - GAP - HEADING_HEIGHT) / ROW_HEIGHT;
    return Math.max(0, Math.min(available, rows));
  }

  @Override
  protected void paintComponent(Graphics graphics) {
    CoachPanelState.History history = state.get().history();
    if (history == null || getHeight() < TALLY_HEIGHT) {
      return;
    }
    Graphics2D g = (Graphics2D) graphics.create();
    try {
      CoachPaint.smooth(g);
      tallies(g, history.tallies(), getWidth());
      int rows = rowsThatFit(getHeight(), history.recent().size());
      if (rows > 0) {
        int y = TALLY_HEIGHT + GAP;
        g.setColor(CoachPanel.MUTED);
        g.setFont(CoachPaint.font(history.heading(), Font.BOLD, 13f));
        g.drawString(history.heading(), 2, y + 15);
        y += HEADING_HEIGHT;
        for (int i = 0; i < rows; i++) {
          row(
              g,
              history.recent().get(i),
              y + i * ROW_HEIGHT,
              getWidth(),
              i == 0 && !history.clickable());
        }
      }
    } finally {
      g.dispose();
    }
  }

  private void tallies(Graphics2D g, List<CoachPanelState.Tally> tallies, int width) {
    int gap = 6;
    int cell = (width - gap * (tallies.size() - 1)) / Math.max(1, tallies.size());
    for (int i = 0; i < tallies.size(); i++) {
      CoachPanelState.Tally tally = tallies.get(i);
      int x = i * (cell + gap);
      g.setColor(CoachPanel.SURFACE);
      g.fillRoundRect(x, 0, cell, TALLY_HEIGHT, 20, 20);
      String count = String.valueOf(tally.count());
      g.setFont(CoachPaint.font(count, Font.BOLD, 17f));
      FontMetrics countMetrics = g.getFontMetrics();
      g.setColor(tally.count() == 0 ? CoachPaint.FAINT : CoachPaint.verdictColor(tally.verdict()));
      g.drawString(count, x + (cell - countMetrics.stringWidth(count)) / 2, 24);
      g.setFont(CoachPaint.font(tally.label(), Font.PLAIN, 12f));
      FontMetrics labelMetrics = g.getFontMetrics();
      String label = CoachPaint.fit(labelMetrics, tally.label(), cell - 6);
      g.setColor(CoachPanel.MUTED);
      g.drawString(label, x + (cell - labelMetrics.stringWidth(label)) / 2, 42);
    }
  }

  private void row(Graphics2D g, CoachPanelState.Row row, int y, int width, boolean latest) {
    if (latest) {
      g.setColor(CoachPanel.SURFACE);
      g.fillRoundRect(0, y, width, ROW_HEIGHT, 16, 16);
    }
    int baseline = y + 22;
    g.setFont(CoachPaint.font(row.number(), Font.PLAIN, 13.5f));
    g.setColor(CoachPaint.FAINT);
    g.drawString(row.number(), 8, baseline);
    g.setFont(CoachPaint.font(row.move(), Font.BOLD, 13.5f));
    g.setColor(CoachPanel.TEXT);
    g.drawString(CoachPaint.fit(g.getFontMetrics(), row.move(), 52), 56, baseline);
    g.setFont(CoachPaint.font(row.label(), Font.BOLD, 12f));
    CoachPaint.tag(
        g,
        112,
        y + 7,
        20,
        row.label(),
        CoachPaint.verdictTint(row.verdict()),
        CoachPaint.verdictColor(row.verdict()));
    g.setFont(CoachPaint.font(row.change(), Font.PLAIN, 13.5f));
    g.setColor(CoachPaint.changeColor(row.verdict(), row.change()));
    g.drawString(row.change(), width - 8 - g.getFontMetrics().stringWidth(row.change()), baseline);
  }
}
