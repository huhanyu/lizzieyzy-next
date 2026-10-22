package featurecat.lizzie.training;

/**
 * The one scale the setup dialog picks an opponent on: 20 kyu up to 1 kyu, then 1 dan up to 9 dan,
 * 29 stops in all, weakest first.
 */
public final class RankScale {
  public static final int STOPS = 29;
  private static final int KYU_STOPS = 20;

  private RankScale() {}

  public static int index(int rank, boolean dan) {
    return dan ? KYU_STOPS - 1 + clamp(rank, 1, 9) : KYU_STOPS - clamp(rank, 1, 20);
  }

  public static int rank(int index) {
    int stop = clamp(index, 0, STOPS - 1);
    return stop < KYU_STOPS ? KYU_STOPS - stop : stop - KYU_STOPS + 1;
  }

  public static boolean dan(int index) {
    return clamp(index, 0, STOPS - 1) >= KYU_STOPS;
  }

  private static int clamp(int value, int low, int high) {
    return Math.max(low, Math.min(high, value));
  }
}
