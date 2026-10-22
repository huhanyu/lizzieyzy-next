package featurecat.lizzie.training;

import java.time.Duration;

/**
 * How long a player of the opponent's level would take over a move: quick when nearly everyone at
 * that level plays the same move, longer when the choice is open, and a little quicker in the
 * opening. It only paces the reply; the move itself is chosen before this is applied.
 */
public final class HumanThinkTime {
  static final long MIN_MILLIS = 400L;
  static final long MAX_MILLIS = 8_000L;
  private static final long OPENING_BASE_MILLIS = 1_000L;
  private static final long BASE_MILLIS = 1_600L;
  private static final double OPENING_SHARE_OF_BOARD = 0.1;
  private static final double UNKNOWN_TOP_PROBABILITY = 0.45;
  private static final long DELIVERY_RESERVE_MILLIS = 500L;

  private HumanThinkTime() {}

  /**
   * @param topHumanProbability share of the human policy on its most popular move, NaN if unknown
   * @param randomValue uniform in [0, 1), so equal positions do not always take equally long
   * @param moveLimit the move's time budget, or null when unlimited
   */
  public static long millis(
      double topHumanProbability,
      int moveNumber,
      int boardArea,
      double randomValue,
      Duration moveLimit) {
    double top =
        Double.isFinite(topHumanProbability)
            ? Math.max(0.0, Math.min(1.0, topHumanProbability))
            : UNKNOWN_TOP_PROBABILITY;
    double difficulty = 0.6 + 2.2 * Math.pow(1.0 - top, 1.5);
    double jitter = 0.75 + 0.6 * Math.max(0.0, Math.min(1.0, randomValue));
    long base = moveNumber < boardArea * OPENING_SHARE_OF_BOARD ? OPENING_BASE_MILLIS : BASE_MILLIS;
    long target = Math.round(base * difficulty * jitter);
    long ceiling = MAX_MILLIS;
    if (moveLimit != null) {
      ceiling =
          Math.min(ceiling, Math.max(MIN_MILLIS, moveLimit.toMillis() - DELIVERY_RESERVE_MILLIS));
    }
    return Math.max(MIN_MILLIS, Math.min(ceiling, target));
  }
}
