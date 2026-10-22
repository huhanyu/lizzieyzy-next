package featurecat.lizzie.gui;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import featurecat.lizzie.rules.Board;
import featurecat.lizzie.training.MoveFeedback;
import featurecat.lizzie.training.MoveVerdict;
import featurecat.lizzie.training.PositionReview;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CoachAssistTest {
  private int previousWidth;
  private int previousHeight;
  private final CoachAssist assist = new CoachAssist();

  @BeforeEach
  void nineteen() {
    previousWidth = Board.boardWidth;
    previousHeight = Board.boardHeight;
    Board.boardWidth = 19;
    Board.boardHeight = 19;
  }

  @AfterEach
  void restore() {
    Board.boardWidth = previousWidth;
    Board.boardHeight = previousHeight;
  }

  private static PositionReview review(String best) {
    return new PositionReview(
        0.0, 12.0, List.of(new PositionReview.Candidate(best, 300, 0.5)), Map.of(best, 0.38));
  }

  @Test
  void aHintShowsTheAreaFirstAndThenThePoint() {
    assist.hint(review("R10"));

    assertEquals(CoachAssist.Hint.AREA, assist.hintStep());
    assertEquals(5, assist.hintArea(), "the right side");
    assertArrayEquals(new int[] {13, 6, 18, 12}, assist.marks().area());
    assertNull(assist.marks().point());

    assist.hint(review("R10"));

    assertEquals(CoachAssist.Hint.POINT, assist.hintStep());
    assertArrayEquals(new int[] {16, 9}, assist.marks().point());
    assertNull(assist.marks().area());
    assertEquals("R10", assist.hintMove());
    assertEquals(0.38, assist.hintShare());
    assertEquals(1, assist.hintsUsed(), "one position, one hint");
  }

  @Test
  void aHintAskedForBeforeTheReviewArrivesWaitsForIt() {
    assist.hint(null);
    assertEquals(CoachAssist.Hint.WAITING, assist.hintStep());
    assertTrue(assist.isHintOpen());
    assertTrue(assist.marks().isEmpty());

    assist.reviewArrived(review("D4"));

    assertEquals(CoachAssist.Hint.AREA, assist.hintStep());
    assertEquals(6, assist.hintArea(), "the bottom left corner");
  }

  @Test
  void closingTheCardKeepsTheMarkUntilThePlayerMoves() {
    assist.hint(review("D4"));
    assist.closeHint();

    assertFalse(assist.isHintOpen());
    assertFalse(assist.marks().isEmpty());

    assist.clearTurn();

    assertTrue(assist.marks().isEmpty());
    assertEquals(CoachAssist.Hint.NONE, assist.hintStep());
    assertEquals(1, assist.hintsUsed(), "the count stays for the game");
  }

  @Test
  void whenTheAiWouldPassTheHintSaysSoWithoutAnArea() {
    assist.hint(review("pass"));

    assertEquals(CoachAssist.Hint.POINT, assist.hintStep());
    assertEquals("pass", assist.hintMove());
    assertTrue(assist.marks().isEmpty());
  }

  @Test
  void afterABlunderSeeingTheAiMoveMarksIt() {
    MoveFeedback blunder =
        new MoveFeedback(5, "A1", MoveVerdict.BLUNDER, 0.48, 0.25, 13.2, 0.0, false, "Q4", 0.3);

    assist.decideAbout(blunder);
    assertEquals(blunder, assist.decision().orElseThrow());
    assertFalse(assist.isBestShown());

    assist.showBest();

    assertTrue(assist.isBestShown());
    assertArrayEquals(new int[] {15, 15}, assist.marks().point());
    assist.clearTurn();
    assertTrue(assist.decision().isEmpty());
  }
}
