package featurecat.lizzie.gui;

import featurecat.lizzie.Lizzie;
import featurecat.lizzie.rules.Board;
import featurecat.lizzie.rules.BoardHistoryList;
import featurecat.lizzie.rules.BoardHistoryNode;
import featurecat.lizzie.rules.Stone;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.ResourceBundle;

/**
 * One line on the main board while no game is open, saying how to open or start one. It goes as
 * soon as a stone is on the board or a game is loaded.
 */
final class EmptyBoardHint {
  private static final Color PILL = new Color(20, 22, 26, 170);
  private static final Color TEXT = new Color(0xF1, 0xF2, 0xF4);

  private EmptyBoardHint() {}

  /** No move, no setup stone and no variation: a fresh board. */
  static boolean isEmptyGame(BoardHistoryList history) {
    if (history == null) return false;
    BoardHistoryNode start = history.getStart();
    if (start == null || history.getCurrentHistoryNode() != start) return false;
    if (start.numberOfChildren() > 0) return false;
    for (Stone stone : start.getData().stones) {
      if (!stone.isEmpty()) return false;
    }
    return true;
  }

  /** The main board's call: the hint shows while the open game is empty. */
  static void paintOnMainBoard(
      Graphics2D g, boolean mainBoard, int boardX, int boardY, int boardWidth, int boardHeight) {
    Board board = Lizzie.board;
    paint(
        g,
        mainBoard && board != null && isEmptyGame(board.getHistory()),
        Lizzie.resourceBundle,
        LizzieFrame.uiFont,
        boardX,
        boardY,
        boardWidth,
        boardHeight);
  }

  /** Paints the hint centred on the board when {@code show} is true. */
  static void paint(
      Graphics2D g,
      boolean show,
      ResourceBundle strings,
      Font base,
      int boardX,
      int boardY,
      int boardWidth,
      int boardHeight) {
    if (!show || boardWidth < 200) return;
    String text = strings.getString("BoardRenderer.emptyHint");
    Font font =
        FittedFont.withKerning(base.deriveFont(Font.PLAIN, Math.max(13f, boardWidth / 48f)));
    FontMetrics metrics = g.getFontMetrics(font);
    int padX = metrics.getHeight() * 3 / 4;
    int width = Math.min(boardWidth - 20, metrics.stringWidth(text) + 2 * padX);
    int height = metrics.getHeight() + metrics.getHeight() / 2;
    int left = boardX + (boardWidth - width) / 2;
    int top = boardY + (boardHeight - height) / 2;
    Graphics2D pill = (Graphics2D) g.create();
    try {
      pill.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      pill.setColor(PILL);
      pill.fillRoundRect(left, top, width, height, height, height);
      pill.setFont(font);
      pill.setColor(TEXT);
      int baseline = top + (height - metrics.getHeight()) / 2 + metrics.getAscent();
      pill.drawString(text, left + (width - metrics.stringWidth(text)) / 2, baseline);
    } finally {
      pill.dispose();
    }
  }
}
