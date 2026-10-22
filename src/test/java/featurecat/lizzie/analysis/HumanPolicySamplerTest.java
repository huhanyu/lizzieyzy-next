package featurecat.lizzie.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class HumanPolicySamplerTest {
  private static HumanLikeMoveSelector.Candidate move(String name, double probability) {
    return new HumanLikeMoveSelector.Candidate(name, probability);
  }

  @Test
  void kyuAndUpToFiveDanPlayTheRawPolicy() {
    assertTrue(HumanPolicySampler.playsRawPolicy("rank_20k"));
    assertTrue(HumanPolicySampler.playsRawPolicy("rank_1k"));
    assertTrue(HumanPolicySampler.playsRawPolicy("rank_1d"));
    assertTrue(HumanPolicySampler.playsRawPolicy("rank_5d"));
    assertTrue(HumanPolicySampler.playsRawPolicy(" RANK_3D "));
  }

  @Test
  void strongerAndUnknownProfilesKeepTheSearch() {
    assertFalse(HumanPolicySampler.playsRawPolicy("rank_6d"));
    assertFalse(HumanPolicySampler.playsRawPolicy("rank_9d"));
    assertFalse(HumanPolicySampler.playsRawPolicy("proyear_2023"));
    assertFalse(HumanPolicySampler.playsRawPolicy("preaz_3d"));
    assertFalse(HumanPolicySampler.playsRawPolicy("rank_xd"));
    assertFalse(HumanPolicySampler.playsRawPolicy("rank_"));
    assertFalse(HumanPolicySampler.playsRawPolicy(null));
  }

  @Test
  void temperatureFollowsKataGosHalflifeFromEarlyToLate() {
    assertEquals(0.85, HumanPolicySampler.temperature(0, 19, 19), 1e-12);
    assertEquals(0.775, HumanPolicySampler.temperature(80, 19, 19), 1e-12);
    assertEquals(0.70, HumanPolicySampler.temperature(2000, 19, 19), 1e-6);
    // Smaller boards age faster: one halflife on 19x19 is 19/9 halflives on 9x9.
    assertEquals(
        0.70 + 0.15 * Math.pow(0.5, 19.0 / 9.0), HumanPolicySampler.temperature(80, 9, 9), 1e-12);
  }

  @Test
  void movesAboveOnePercentKeepTheirHumanOdds() {
    double[] weights =
        HumanPolicySampler.weights(
            List.of(move("D4", 0.6), move("Q16", 0.39), move("A1", 0.01)), 0.7);

    assertEquals(1.0, weights[0], 1e-12);
    assertEquals(0.65, weights[1], 1e-12);
    assertEquals(0.01 / 0.6, weights[2], 1e-12);
  }

  @Test
  void rareMovesAreDampedByTheTemperature() {
    double[] weights =
        HumanPolicySampler.weights(
            List.of(move("D4", 0.6), move("Q16", 0.39), move("A1", 0.001)), 0.7);
    double threshold = Math.log(0.01) + Math.log(0.991 / 0.6);
    double expected = Math.exp((Math.log(0.001 / 0.6) - threshold) / 0.7 + threshold);

    assertEquals(expected, weights[2], 1e-12);
    assertTrue(weights[2] < 0.001 / 0.6);
  }

  @Test
  void samplingFollowsTheWeights() {
    List<HumanLikeMoveSelector.Candidate> moves = List.of(move("D4", 0.5), move("Q16", 0.5));

    assertEquals("D4", HumanPolicySampler.sample(moves, 0, 19, 19, 0.0));
    assertEquals("D4", HumanPolicySampler.sample(moves, 0, 19, 19, 0.49));
    assertEquals("Q16", HumanPolicySampler.sample(moves, 0, 19, 19, 0.51));
    assertEquals("Q16", HumanPolicySampler.sample(moves, 0, 19, 19, 1.0));
  }

  @Test
  void samplingNeverPicksAMoveWithoutPolicy() {
    List<HumanLikeMoveSelector.Candidate> moves =
        List.of(move("D4", 0.0), move("Q16", 1.0), move("C3", Double.NaN));

    assertEquals("Q16", HumanPolicySampler.sample(moves, 0, 19, 19, 0.0));
    assertEquals("Q16", HumanPolicySampler.sample(moves, 0, 19, 19, 0.999));
    assertNull(HumanPolicySampler.sample(List.of(move("D4", 0.0)), 0, 19, 19, 0.5));
    assertNull(HumanPolicySampler.sample(List.of(), 0, 19, 19, 0.5));
  }

  @Test
  void topShareIsTheMostPopularMovesShareOfTheLegalPolicy() {
    assertEquals(
        0.75, HumanPolicySampler.topShare(List.of(move("D4", 0.3), move("Q16", 0.1))), 1e-12);
    assertTrue(Double.isNaN(HumanPolicySampler.topShare(List.of())));
  }
}
