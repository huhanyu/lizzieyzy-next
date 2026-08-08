package featurecat.lizzie.teacher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import featurecat.lizzie.analysis.MoveData;
import featurecat.lizzie.rules.Board;
import featurecat.lizzie.rules.BoardData;
import featurecat.lizzie.rules.BoardHistoryNode;
import featurecat.lizzie.rules.Stone;
import featurecat.lizzie.rules.Zobrist;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class TeacherEvidenceTest {
  @Test
  void copiesTopThreeCandidatesActualMoveAndBoundedPv() {
    BoardHistoryNode parent = new BoardHistoryNode(BoardData.empty(19, 19));
    String actual = Board.convertCoordinatesToName(3, 3).toUpperCase();
    parent.getData().bestMoves =
        List.of(
            move("Q16", 61.5, 3.2, 1200, 20),
            move(actual, 55.0, 1.1, 900, 4),
            move("D4", 52.0, 0.4, 600, 3),
            move("Q4", 51.0, 0.1, 400, 2));
    append(parent, moveData(1, 3, 3, false));

    TeacherEvidence.Position evidence = TeacherEvidence.current(parent).orElseThrow();

    assertEquals(actual, evidence.actualMove);
    assertEquals(3, evidence.candidates.size());
    assertEquals(TeacherEvidence.MAX_PV_MOVES, evidence.candidates.get(0).variation.size());
    assertTrue(evidence.actualWinrateLoss.isPresent());
    assertEquals(6.5, evidence.actualWinrateLoss.getAsDouble(), 0.0001);
  }

  @Test
  void wholeGameSelectsAtMostFortyKeyPositionsInChronologicalOrder() {
    BoardHistoryNode root = new BoardHistoryNode(BoardData.empty(19, 19));
    BoardHistoryNode parent = root;
    for (int moveNumber = 1; moveNumber <= 45; moveNumber++) {
      int x = moveNumber % 19;
      int y = (moveNumber * 3) % 19;
      String actual = Board.convertCoordinatesToName(x, y).toUpperCase();
      parent.getData().bestMoves =
          List.of(
              move("Q16", 70.0, 5.0, 1000 + moveNumber, 3),
              move(actual, 70.0 - moveNumber / 2.0, 3.0, 800, 3));
      parent = append(parent, moveData(moveNumber, x, y, moveNumber % 2 == 0));
    }

    TeacherEvidence.Range range = TeacherEvidence.wholeGame(root);

    assertEquals(45, range.analyzedPositions);
    assertEquals(40, range.positions.size());
    assertEquals(5, range.omittedPositions);
    assertEquals(0, range.positions.get(0).moveNumber);
    assertEquals(44, range.positions.get(range.positions.size() - 1).moveNumber);
    for (int index = 1; index < range.positions.size(); index++) {
      assertTrue(range.positions.get(index - 1).moveNumber < range.positions.get(index).moveNumber);
    }
  }

  private static MoveData move(
      String coordinate, double winrate, double scoreMean, int visits, int variationLength) {
    MoveData move = new MoveData();
    move.coordinate = coordinate;
    move.winrate = winrate;
    move.scoreMean = scoreMean;
    move.playouts = visits;
    move.variation = new ArrayList<>();
    for (int index = 0; index < variationLength; index++) {
      move.variation.add(index % 2 == 0 ? "D4" : "Q16");
    }
    return move;
  }

  private static BoardData moveData(int moveNumber, int x, int y, boolean blackToPlayAfterMove) {
    Stone[] stones = new Stone[19 * 19];
    Arrays.fill(stones, Stone.EMPTY);
    return BoardData.move(
        stones,
        new int[] {x, y},
        blackToPlayAfterMove ? Stone.WHITE : Stone.BLACK,
        blackToPlayAfterMove,
        new Zobrist(),
        moveNumber,
        new int[19 * 19],
        0,
        0,
        50.0,
        0);
  }

  private static BoardHistoryNode append(BoardHistoryNode parent, BoardData data) {
    BoardHistoryNode child = new BoardHistoryNode(data);
    parent.variations.add(child);
    parent.setPreviousForChild(child);
    return child;
  }
}
