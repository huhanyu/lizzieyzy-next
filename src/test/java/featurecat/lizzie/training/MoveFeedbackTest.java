package featurecat.lizzie.training;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MoveFeedbackTest {
  /** A middle-game position at 3 dan: Q16 is best, D4 is what players usually play. */
  private static PositionReview before(double commonLead) {
    return new PositionReview(
        0.0,
        12.0,
        List.of(
            new PositionReview.Candidate("Q16", 300, 0.5),
            new PositionReview.Candidate("D4", 80, commonLead),
            new PositionReview.Candidate("K10", 40, -3.5)),
        Map.of("D4", 0.55, "Q16", 0.04, "K10", 0.08, "A1", 0.001));
  }

  private static PositionReview after(double opponentLead) {
    return new PositionReview(opponentLead, 11.0, List.of(), Map.of());
  }

  @Test
  void kataGosChoiceKeepsTheWinChance() {
    MoveFeedback feedback = MoveFeedback.judge(31, "Q16", before(0.2), null).orElseThrow();

    assertTrue(feedback.playedBest());
    assertEquals(0.0, feedback.winChange());
    assertEquals(0.0, feedback.pointsLost());
    assertEquals(MoveVerdict.GOOD, feedback.verdict(), "D4 is nearly as good, so not brilliant");
    assertEquals(31, feedback.moveNumber());
  }

  @Test
  void findingTheRareBestMoveWhereTheUsualMoveFailsIsBrilliant() {
    MoveFeedback feedback = MoveFeedback.judge(31, "Q16", before(-3.0), null).orElseThrow();

    assertEquals(MoveVerdict.BRILLIANT, feedback.verdict());
    assertEquals(0.04, feedback.humanShare());
  }

  @Test
  void aWorseMoveFromTheSameSearchLosesWinChance() {
    MoveFeedback feedback = MoveFeedback.judge(31, "K10", before(0.2), null).orElseThrow();

    assertEquals(MoveVerdict.PROBLEM, feedback.verdict());
    assertEquals(4.0, feedback.pointsLost(), 1e-9);
    assertEquals(
        RankAwareWinrate.of(-3.5, 12.0) - RankAwareWinrate.of(0.5, 12.0),
        feedback.winChange(),
        1e-12);
    assertFalse(feedback.playedBest());
  }

  @Test
  void aMoveTheSearchMissedIsJudgedFromThePositionAfterIt() {
    PositionReview before = before(0.2);
    assertTrue(MoveFeedback.needsPositionAfter(before, "A1"));
    assertFalse(MoveFeedback.needsPositionAfter(before, "D4"));

    MoveFeedback feedback = MoveFeedback.judge(31, "A1", before, after(15.5)).orElseThrow();

    assertEquals(16.0, feedback.pointsLost(), 1e-9);
    assertEquals(MoveVerdict.BLUNDER, feedback.verdict());
    assertTrue(MoveFeedback.judge(31, "A1", before, null).isEmpty(), "no review after it");
  }

  @Test
  void aPassIsJudgedLikeAnyMove() {
    MoveFeedback feedback = MoveFeedback.judge(200, "PASS", before(0.2), after(8.0)).orElseThrow();

    assertEquals("pass", feedback.move());
    assertEquals(8.5, feedback.pointsLost(), 1e-9);
  }

  @Test
  void theSpreadOfThePositionAfterIsUsedWhenTheFirstReviewHasNone() {
    PositionReview before =
        new PositionReview(
            0.0,
            Double.NaN,
            List.of(new PositionReview.Candidate("Q16", 100, 0.0)),
            Map.of("Q16", 0.3));

    MoveFeedback feedback = MoveFeedback.judge(9, "A1", before, after(4.0)).orElseThrow();

    assertEquals(RankAwareWinrate.of(-4.0, 11.0), feedback.winAfter(), 1e-12);
  }

  @Test
  void nothingIsSaidWithoutASearch() {
    assertTrue(
        MoveFeedback.judge(1, "Q16", new PositionReview(0, 1, List.of(), Map.of()), null)
            .isEmpty());
  }
}
