package featurecat.lizzie.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import featurecat.lizzie.analysis.MoveRankDefinition;
import featurecat.lizzie.analysis.PlayerStrengthEstimator;
import featurecat.lizzie.rules.Stone;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class StrengthStatsTest {
  @Test
  void eachMoveCountsInExactlyOneTier() throws Exception {
    PlayerStrengthEstimator.SideReport side =
        StrengthFixtures.side(
            List.of(
                StrengthFixtures.sample(
                    Stone.BLACK,
                    1,
                    0.0,
                    Optional.of(0.0),
                    true,
                    0,
                    PlayerStrengthEstimator.MoveCategory.EXCELLENT),
                StrengthFixtures.sample(
                    Stone.BLACK,
                    3,
                    0.2,
                    Optional.of(0.1),
                    false,
                    1,
                    PlayerStrengthEstimator.MoveCategory.EXCELLENT),
                StrengthFixtures.sample(
                    Stone.BLACK,
                    5,
                    1.0,
                    Optional.of(0.4),
                    false,
                    1,
                    PlayerStrengthEstimator.MoveCategory.GREAT),
                StrengthFixtures.sample(
                    Stone.BLACK,
                    7,
                    3.0,
                    Optional.of(1.0),
                    false,
                    2,
                    PlayerStrengthEstimator.MoveCategory.GOOD)),
            0.25,
            0.75);

    int[] counts = StrengthStats.tierCounts(side);

    assertEquals(1, counts[MoveRankDefinition.Rank.BEST.ordinal()], "only the first choice");
    assertEquals(
        2,
        counts[MoveRankDefinition.Rank.GOOD.ordinal()],
        "a Best move that is not the first choice joins the Good moves");
    assertEquals(1, counts[MoveRankDefinition.Rank.NORMAL.ordinal()]);
    int total = 0;
    for (int count : counts) {
      total += count;
    }
    assertEquals(side.sampleCount, total);
  }

  @Test
  void stretchesCoverEveryMoveInOrder() throws Exception {
    List<PlayerStrengthEstimator.Sample> samples = new ArrayList<>();
    samples.addAll(
        StrengthFixtures.moves(Stone.BLACK, 1, 40, PlayerStrengthEstimator.MoveCategory.EXCELLENT));
    PlayerStrengthEstimator.SideReport side = StrengthFixtures.side(samples, 1.0, 1.0);

    List<StrengthStats.Segment> segments = StrengthStats.segments(side);

    assertTrue(segments.size() > 1, "a long game is split into stretches");
    assertEquals(1, segments.get(0).firstMove());
    assertEquals(79, segments.get(segments.size() - 1).lastMove());
    int moves = 0;
    for (int i = 0; i < segments.size(); i++) {
      StrengthStats.Segment segment = segments.get(i);
      moves += segment.report().sampleCount;
      assertTrue(segment.report().sampleCount >= StrengthStats.minimumSegmentMoves(side));
      if (i > 0) {
        assertTrue(segment.firstMove() > segments.get(i - 1).lastMove());
      }
    }
    assertEquals(40, moves);
  }

  @Test
  void humanSlStretchesNeedSixMoves() throws Exception {
    PlayerStrengthEstimator.SideReport side =
        StrengthFixtures.side(
            StrengthFixtures.moves(Stone.WHITE, 2, 10, PlayerStrengthEstimator.MoveCategory.GREAT),
            PlayerStrengthEstimator.StrengthModel.HUMANSL40,
            5.0,
            0.0,
            1.0,
            "5-6d",
            PlayerStrengthEstimator.Confidence.MEDIUM);
    assertEquals(6, StrengthStats.minimumSegmentMoves(side));
  }

  @Test
  void onlyLongStrongStretchesAreLabelledWithARank() throws Exception {
    PlayerStrengthEstimator.SideReport side =
        StrengthFixtures.side(
            StrengthFixtures.moves(Stone.BLACK, 1, 10, PlayerStrengthEstimator.MoveCategory.GREAT),
            0.5,
            1.0);
    assertTrue(StrengthStats.labelsRank(stretch(side, 6, "8d"), side));
    assertTrue(StrengthStats.labelsRank(stretch(side, 6, "11d\u4e00\u7ebf\u804c\u4e1a"), side));
    assertFalse(StrengthStats.labelsRank(stretch(side, 6, "3-4d"), side));
    assertFalse(StrengthStats.labelsRank(stretch(side, 6, "1-2k"), side));
    assertFalse(StrengthStats.labelsRank(stretch(side, 2, "9d"), side), "too short");
  }

  @Test
  void aiLikelihoodIsZeroWithoutMoves() throws Exception {
    assertEquals(0.0, StrengthStats.aiLikelihood(StrengthFixtures.emptyReport().black));
    assertEquals(0.0, StrengthStats.aiLikelihood(null));
  }

  private static StrengthStats.Segment stretch(
      PlayerStrengthEstimator.SideReport side, int moves, String band) throws Exception {
    PlayerStrengthEstimator.SideReport part =
        StrengthFixtures.side(
            side.samples.subList(0, moves),
            side.model,
            7.0,
            0.5,
            1.0,
            band,
            PlayerStrengthEstimator.Confidence.MEDIUM);
    return new StrengthStats.Segment(1, moves * 2 - 1, part);
  }
}
