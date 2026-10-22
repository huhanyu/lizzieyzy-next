package featurecat.lizzie.training;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MoveVerdictTest {
  @Test
  void theWinChanceDropDecidesInAnOpenGame() {
    assertEquals(MoveVerdict.GOOD, MoveVerdict.of(0.50, 0.46, 1.0));
    assertEquals(MoveVerdict.SLOW, MoveVerdict.of(0.50, 0.44, 1.0));
    assertEquals(MoveVerdict.PROBLEM, MoveVerdict.of(0.50, 0.39, 3.0));
    assertEquals(MoveVerdict.BLUNDER, MoveVerdict.of(0.50, 0.29, 6.0));
  }

  @Test
  void aLossIsRatedByTheWholePercentageShown() {
    assertEquals(MoveVerdict.SLOW, MoveVerdict.of(0.50, 0.451, 1.0), "shown as -5%");
    assertEquals(MoveVerdict.GOOD, MoveVerdict.of(0.50, 0.4551, 1.0), "shown as -4%");
  }

  @Test
  void pointsDoNotMatterWhileTheGameIsOpen() {
    assertEquals(MoveVerdict.GOOD, MoveVerdict.of(0.50, 0.48, 9.0));
  }

  @Test
  void onceTheGameIsDecidedThePointsLostDecide() {
    // At 2% the win chance can hardly fall, but giving away 10 points is still a mistake.
    assertEquals(MoveVerdict.PROBLEM, MoveVerdict.of(0.02, 0.01, 10.0));
    assertEquals(MoveVerdict.SLOW, MoveVerdict.of(0.99, 0.98, 5.0));
    assertEquals(MoveVerdict.BLUNDER, MoveVerdict.of(0.99, 0.97, 16.0));
    assertEquals(MoveVerdict.GOOD, MoveVerdict.of(0.02, 0.02, 2.0));
  }

  @Test
  void pointsNeverSoftenAWinChanceVerdict() {
    assertEquals(MoveVerdict.BLUNDER, MoveVerdict.of(0.97, 0.70, 2.0));
  }

  @Test
  void onlySlowProblemAndBlunderAreMistakes() {
    assertFalse(MoveVerdict.BRILLIANT.isMistake());
    assertFalse(MoveVerdict.GOOD.isMistake());
    assertTrue(MoveVerdict.SLOW.isMistake());
    assertTrue(MoveVerdict.PROBLEM.isMistake());
    assertTrue(MoveVerdict.BLUNDER.isMistake());
  }
}
