package featurecat.lizzie.training;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RankAwareWinrateTest {
  @Test
  void theNormalDistributionFunctionMatchesKnownValues() {
    assertEquals(0.5, RankAwareWinrate.normalCdf(0.0), 1e-7);
    assertEquals(0.8413447, RankAwareWinrate.normalCdf(1.0), 1e-6);
    assertEquals(0.0249979, RankAwareWinrate.normalCdf(-1.96), 1e-6);
  }

  @Test
  void theSameLeadCountsForMoreWhenGamesSwingLess() {
    double opening = RankAwareWinrate.of(5.0, 18.0);
    double endgame = RankAwareWinrate.of(5.0, 4.0);

    assertEquals(0.609, opening, 0.001);
    assertEquals(0.894, endgame, 0.001);
  }

  @Test
  void aBigMistakeStillShowsWhenThePlayerIsAlreadyBehind() {
    // KataGo's own winrate hardly moves here: it is near 0% already.
    double before = RankAwareWinrate.of(-10.0, 15.0);
    double after = RankAwareWinrate.of(-23.5, 15.0);

    assertTrue(before - after > 0.19, () -> "drop " + (before - after));
  }

  @Test
  void aMissingSpreadFallsBackAndATinyOneIsClamped() {
    assertEquals(
        RankAwareWinrate.normalCdf(6.0 / RankAwareWinrate.FALLBACK_SCORE_STDEV),
        RankAwareWinrate.of(6.0, Double.NaN),
        1e-12);
    assertEquals(RankAwareWinrate.normalCdf(0.5), RankAwareWinrate.of(0.5, 0.01), 1e-12);
    assertTrue(Double.isNaN(RankAwareWinrate.of(Double.NaN, 5.0)));
  }
}
