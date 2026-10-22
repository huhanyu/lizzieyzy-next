package featurecat.lizzie.analysis;

import java.util.List;
import java.util.Locale;

/**
 * Picks a move the way KataGo's human-imitation config does (gtp_human5k_example.cfg): straight
 * from the HumanSL policy, damping only moves rarer than 1% so that model noise is not played.
 * KataGo's analysis docs say one visit at full temperature is the closest match to players of the
 * profile's rank up to mid-high dan, so these ranks need no search.
 */
final class HumanPolicySampler {
  static final double RARE_MOVE_PROBABILITY = 0.01;
  static final double EARLY_TEMPERATURE = 0.85;
  static final double LATE_TEMPERATURE = 0.70;
  static final double TEMPERATURE_HALFLIFE_MOVES = 80.0;
  static final int STRONGEST_RAW_POLICY_DAN = 5;

  private HumanPolicySampler() {}

  /** True for kyu ranks and up to 5 dan; stronger profiles need search to reach their rank. */
  static boolean playsRawPolicy(String profile) {
    if (profile == null) {
      return false;
    }
    String normalized = profile.trim().toLowerCase(Locale.ROOT);
    if (!normalized.startsWith("rank_") || normalized.length() < 7) {
      return false;
    }
    char suffix = normalized.charAt(normalized.length() - 1);
    int rank;
    try {
      rank = Integer.parseInt(normalized.substring(5, normalized.length() - 1));
    } catch (NumberFormatException e) {
      return false;
    }
    if (suffix == 'k') {
      return rank >= 1;
    }
    return suffix == 'd' && rank >= 1 && rank <= STRONGEST_RAW_POLICY_DAN;
  }

  /**
   * KataGo's interpolateEarly: the temperature halves its distance to the late value each halflife.
   */
  static double temperature(int moveNumber, int boardWidth, int boardHeight) {
    double area = Math.max(1, boardWidth) * (double) Math.max(1, boardHeight);
    double halflives =
        Math.max(0, moveNumber) / TEMPERATURE_HALFLIFE_MOVES * 19.0 / Math.sqrt(area);
    return LATE_TEMPERATURE + (EARLY_TEMPERATURE - LATE_TEMPERATURE) * Math.pow(0.5, halflives);
  }

  /**
   * Sampling weights relative to the most popular move, as KataGo's chooseIndexWithTemperature
   * computes them with chosenMoveTemperatureOnlyBelowProb: moves with at least 1% of the policy
   * keep their human odds, rarer ones are sharpened away by the temperature.
   */
  static double[] weights(List<HumanLikeMoveSelector.Candidate> moves, double temperature) {
    double[] weights = new double[moves == null ? 0 : moves.size()];
    double max = 0.0;
    double sum = 0.0;
    for (int i = 0; i < weights.length; i++) {
      double probability = moves.get(i).probability;
      if (probability > 0.0 && Double.isFinite(probability)) {
        max = Math.max(max, probability);
        sum += probability;
      }
    }
    if (!(max > 0.0) || !Double.isFinite(sum)) {
      return weights;
    }
    double threshold = Math.min(0.0, Math.log(RARE_MOVE_PROBABILITY) + Math.log(sum / max));
    double safeTemperature = Math.max(1.0e-4, temperature);
    for (int i = 0; i < weights.length; i++) {
      double probability = moves.get(i).probability;
      if (!(probability > 0.0) || !Double.isFinite(probability)) {
        continue;
      }
      double logRelative = Math.log(probability / max);
      double damped =
          logRelative > threshold
              ? logRelative
              : (logRelative - threshold) / safeTemperature + threshold;
      weights[i] = Math.exp(damped);
    }
    return weights;
  }

  /** Samples one move; {@code randomValue} is uniform in [0, 1). Null when nothing is playable. */
  static String sample(
      List<HumanLikeMoveSelector.Candidate> moves,
      int moveNumber,
      int boardWidth,
      int boardHeight,
      double randomValue) {
    double[] weights = weights(moves, temperature(moveNumber, boardWidth, boardHeight));
    double total = 0.0;
    for (double weight : weights) {
      total += weight;
    }
    if (!(total > 0.0) || !Double.isFinite(total)) {
      return null;
    }
    double target = Math.max(0.0, Math.min(0.999999999999, randomValue)) * total;
    double cumulative = 0.0;
    String last = null;
    for (int i = 0; i < weights.length; i++) {
      if (weights[i] <= 0.0) {
        continue;
      }
      last = moves.get(i).move;
      cumulative += weights[i];
      if (target < cumulative) {
        return last;
      }
    }
    return last;
  }

  /** Share of the policy on passing, 0 when the moves do not include a pass. */
  static double passShare(List<HumanLikeMoveSelector.Candidate> moves) {
    double pass = 0.0;
    double sum = 0.0;
    if (moves != null) {
      for (HumanLikeMoveSelector.Candidate move : moves) {
        if (move.probability > 0.0 && Double.isFinite(move.probability)) {
          sum += move.probability;
          if ("pass".equalsIgnoreCase(move.move)) {
            pass += move.probability;
          }
        }
      }
    }
    return sum > 0.0 ? pass / sum : 0.0;
  }

  /** Share of the policy on the most popular move, NaN when the policy is empty. */
  static double topShare(List<HumanLikeMoveSelector.Candidate> moves) {
    double max = 0.0;
    double sum = 0.0;
    if (moves != null) {
      for (HumanLikeMoveSelector.Candidate move : moves) {
        if (move.probability > 0.0 && Double.isFinite(move.probability)) {
          max = Math.max(max, move.probability);
          sum += move.probability;
        }
      }
    }
    return sum > 0.0 && Double.isFinite(sum) ? max / sum : Double.NaN;
  }
}
