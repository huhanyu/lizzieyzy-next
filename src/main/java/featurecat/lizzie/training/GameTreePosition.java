package featurecat.lizzie.training;

import featurecat.lizzie.rules.BoardHistoryNode;

/** Where a coaching game stands in the board's history tree. */
public final class GameTreePosition {
  private GameTreePosition() {}

  /**
   * True while {@code node} is still reachable from {@code root}: neither it nor an ancestor was
   * deleted.
   */
  public static boolean isAttached(BoardHistoryNode node, BoardHistoryNode root) {
    if (node == null || root == null) {
      return false;
    }
    BoardHistoryNode current = node;
    while (current.previous().isPresent()) {
      BoardHistoryNode parent = current.previous().get();
      if (parent.indexOfNode(current) < 0) {
        return false;
      }
      current = parent;
    }
    return current == root;
  }

  /** Moves from {@code start} down to {@code node}, or -1 when {@code node} is not below it. */
  public static int movesSince(BoardHistoryNode start, BoardHistoryNode node) {
    int moves = 0;
    for (BoardHistoryNode current = node; current != null; moves++) {
      if (current == start) {
        return moves;
      }
      current = current.previous().orElse(null);
    }
    return -1;
  }

  /**
   * How many moves a take-back removes: the player's last move and the AI's reply when it is the
   * player's turn, only the player's move while the AI has not answered yet, and never a move from
   * before the game started.
   */
  public static int takeBackMoves(boolean humanToPlay, int movesSinceStart) {
    if (humanToPlay) {
      return movesSinceStart >= 2 ? 2 : 0;
    }
    return movesSinceStart >= 1 ? 1 : 0;
  }
}
