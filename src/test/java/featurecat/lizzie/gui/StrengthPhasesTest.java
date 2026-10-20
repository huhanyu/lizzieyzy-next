package featurecat.lizzie.gui;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import featurecat.lizzie.analysis.PlayerStrengthEstimator;
import featurecat.lizzie.rules.Stone;
import java.util.List;
import org.junit.jupiter.api.Test;

class StrengthPhasesTest {
  /** A game of {@code lastMove} moves in which every move of both sides was analysed. */
  private static PlayerStrengthEstimator.Report game(int lastMove) throws Exception {
    return StrengthFixtures.report(
        StrengthFixtures.side(
            StrengthFixtures.moves(
                Stone.BLACK, 1, (lastMove + 1) / 2, PlayerStrengthEstimator.MoveCategory.GREAT),
            1.0,
            1.0),
        StrengthFixtures.side(
            StrengthFixtures.moves(
                Stone.WHITE, 2, lastMove / 2, PlayerStrengthEstimator.MoveCategory.GOOD),
            0.5,
            1.0),
        PlayerStrengthEstimator.StrengthModel.XGBOOST20TUN);
  }

  /** A phase whose sides are ranked {@code black} and {@code white}, or unranked for NaN. */
  private static StrengthPhases.Phase phase(int index, double black, double white)
      throws Exception {
    return new StrengthPhases.Phase(
        index, index * 50 + 1, index * 50 + 50, side(Stone.BLACK, black), side(Stone.WHITE, white));
  }

  private static PlayerStrengthEstimator.SideReport side(Stone color, double rank)
      throws Exception {
    return StrengthFixtures.side(
        StrengthFixtures.moves(
            color,
            color == Stone.BLACK ? 1 : 2,
            Double.isNaN(rank) ? 2 : 12,
            PlayerStrengthEstimator.MoveCategory.GREAT),
        PlayerStrengthEstimator.StrengthModel.XGBOOST20TUN,
        rank,
        0.5,
        0.8,
        "5d",
        PlayerStrengthEstimator.Confidence.MEDIUM);
  }

  @Test
  void oddBoundariesBecomeUsableOnes() {
    assertArrayEquals(new int[] {60, 150}, StrengthPhases.normalize(null));
    assertArrayEquals(new int[] {60, 150}, StrengthPhases.normalize(new int[] {1}));
    assertArrayEquals(new int[] {10, 20}, StrengthPhases.normalize(new int[] {2, 5}));
    assertArrayEquals(new int[] {80, 90}, StrengthPhases.normalize(new int[] {80, 85}));
    assertTrue(StrengthPhases.isDefault(new int[] {60, 150}));
    assertFalse(StrengthPhases.isDefault(new int[] {61, 150}));
  }

  @Test
  void eachSideIsEstimatedOverItsOwnMovesOfEachPhase() throws Exception {
    List<StrengthPhases.Phase> phases =
        StrengthPhases.split(game(200), StrengthPhases.DEFAULT_BOUNDS, 200);
    assertEquals(3, phases.size());
    assertEquals(1, phases.get(0).first());
    assertEquals(60, phases.get(0).last());
    assertEquals(30, phases.get(0).black().sampleCount, "Black's odd moves 1 to 59");
    assertEquals(30, phases.get(0).white().sampleCount, "White's even moves 2 to 60");
    assertEquals(61, phases.get(1).first());
    assertEquals(150, phases.get(1).last());
    assertEquals(45, phases.get(1).black().sampleCount);
    assertEquals(151, phases.get(2).first());
    assertEquals(200, phases.get(2).last());
    assertEquals(25, phases.get(2).white().sampleCount);
    for (StrengthPhases.Phase phase : phases) {
      assertTrue(phase.played());
      assertTrue(phase.ranked(true) && phase.ranked(false), phase.name());
    }
  }

  @Test
  void gameThatEndsEarlyNeverReachesTheEndgame() throws Exception {
    List<StrengthPhases.Phase> phases =
        StrengthPhases.split(game(120), StrengthPhases.DEFAULT_BOUNDS, 120);
    assertEquals(120, phases.get(1).last(), "the middle game ends with the game");
    assertFalse(phases.get(2).played());
    assertFalse(phases.get(2).ranked(true));
  }

  @Test
  void phaseWithFewMovesHasNoRank() throws Exception {
    StrengthPhases.Phase phase = phase(0, Double.NaN, 5.0);
    assertFalse(phase.ranked(true), "two moves are too few");
    assertTrue(phase.ranked(false));
    assertTrue(Double.isNaN(phase.lead()));
  }

  @Test
  void bestAndWeakestPhaseNeedARealDifference() throws Exception {
    List<StrengthPhases.Phase> phases =
        List.of(phase(0, 4.0, 6.0), phase(1, 7.0, 6.1), phase(2, 3.0, 6.2));
    assertEquals(1, StrengthPhases.extreme(phases, true, true));
    assertEquals(2, StrengthPhases.extreme(phases, true, false));
    assertTrue(StrengthPhases.worthReviewing(phases, true));
    assertEquals(-1, StrengthPhases.extreme(phases, false, true), "White stayed within 0.3");
    assertFalse(StrengthPhases.worthReviewing(phases, false));
    assertEquals(
        -1,
        StrengthPhases.extreme(List.of(phase(0, 4.0, 6.0)), true, true),
        "one ranked phase has nothing to compare with");
  }
}
