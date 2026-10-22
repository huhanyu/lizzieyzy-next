package featurecat.lizzie.training;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import featurecat.lizzie.rules.BoardData;
import featurecat.lizzie.rules.BoardHistoryNode;
import org.junit.jupiter.api.Test;

class GameTreePositionTest {
  private static BoardHistoryNode node() {
    return new BoardHistoryNode(BoardData.empty(3, 3));
  }

  @Test
  void nodesInTheTreeAreAttached() {
    BoardHistoryNode root = node();
    BoardHistoryNode first = root.add(node());
    BoardHistoryNode second = first.add(node());

    assertTrue(GameTreePosition.isAttached(second, root));
    assertTrue(GameTreePosition.isAttached(root, root));
  }

  @Test
  void deletingAnAncestorDetachesEveryNodeBelowIt() {
    BoardHistoryNode root = node();
    BoardHistoryNode first = root.add(node());
    BoardHistoryNode second = first.add(node());

    root.deleteChild(0);

    assertFalse(GameTreePosition.isAttached(first, root));
    assertFalse(GameTreePosition.isAttached(second, root));
  }

  @Test
  void nodesOfAnotherTreeAreNotAttached() {
    BoardHistoryNode root = node();
    BoardHistoryNode other = node();
    BoardHistoryNode move = other.add(node());

    assertFalse(GameTreePosition.isAttached(move, root));
    assertFalse(GameTreePosition.isAttached(null, root));
    assertFalse(GameTreePosition.isAttached(move, null));
  }

  @Test
  void movesSinceCountsDownTheBranch() {
    BoardHistoryNode root = node();
    BoardHistoryNode first = root.add(node());
    BoardHistoryNode second = first.add(node());

    assertEquals(2, GameTreePosition.movesSince(root, second));
    assertEquals(1, GameTreePosition.movesSince(first, second));
    assertEquals(0, GameTreePosition.movesSince(second, second));
    assertEquals(-1, GameTreePosition.movesSince(second, first));
  }

  @Test
  void aTakeBackRemovesTheReplyAndThePlayersMoveButNothingBeforeTheGame() {
    assertEquals(2, GameTreePosition.takeBackMoves(true, 4));
    assertEquals(2, GameTreePosition.takeBackMoves(true, 2));
    // The AI opened as Black and the player has not moved yet.
    assertEquals(0, GameTreePosition.takeBackMoves(true, 1));
    assertEquals(0, GameTreePosition.takeBackMoves(true, 0));
    assertEquals(0, GameTreePosition.takeBackMoves(true, -1));
    // The AI is still answering: only the player's move goes.
    assertEquals(1, GameTreePosition.takeBackMoves(false, 3));
    assertEquals(1, GameTreePosition.takeBackMoves(false, 1));
    assertEquals(0, GameTreePosition.takeBackMoves(false, 0));
  }
}
