package featurecat.lizzie.training;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class GameResultTest {
  @Test
  void aHalfPointKomiGivesHalfPointResults() {
    assertEquals("B+3.5", GameResult.byScore(3.4, 7.5).sgf());
    assertEquals("B+3.5", GameResult.byScore(3.9, 7.5).sgf());
    assertEquals("W+0.5", GameResult.byScore(-0.2, 7.5).sgf());
    assertEquals("B+0.5", GameResult.byScore(0.2, 6.5).sgf());
    assertEquals("W+12.5", GameResult.byScore(-12.6, 0.5).sgf());
  }

  @Test
  void aWholePointKomiAllowsWholePointResultsAndDraws() {
    assertEquals("B+3", GameResult.byScore(3.2, 6.0).sgf());
    assertEquals("W+1", GameResult.byScore(-0.6, 0.0).sgf());
    GameResult draw = GameResult.byScore(0.3, 6.0);
    assertTrue(draw.isDraw());
    assertTrue(draw.isCounted());
    assertEquals("0", draw.sgf());
  }

  @Test
  void resignationsRecordTheWinner() {
    assertEquals("B+R", GameResult.resignation(true).sgf());
    assertEquals("W+R", GameResult.resignation(false).sgf());
    assertTrue(GameResult.resignation(false).byResignation());
  }

  @Test
  void aFailedCountRecordsNothing() {
    GameResult result = GameResult.byScore(Double.NaN, 7.5);
    assertFalse(result.isCounted());
    assertFalse(result.isDraw());
    assertNull(result.blackWins());
    assertEquals("", result.sgf());
  }

  @Test
  void marginsReadWithoutATrailingZero() {
    assertEquals("4", GameResult.byScore(4.2, 6.0).marginText());
    assertEquals("3.5", GameResult.byScore(3.4, 7.5).marginText());
    assertEquals("", GameResult.resignation(true).marginText());
  }
}
