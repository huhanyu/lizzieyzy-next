package featurecat.lizzie.training;

/**
 * The chance that a player of the opponent's rank goes on to win, which 边下边提醒 judges moves by.
 *
 * <p>KataGo's own winrate assumes two superhuman players carry on. For amateurs it sits near 0% or
 * 100% as soon as someone leads by a few points, so it hides big mistakes made after that, and it
 * calls a two-point slip in a close endgame a disaster at every level. Here KataGo's score lead is
 * measured against how much games between players of that rank still swing from the position: the
 * HumanSL model's score standard deviation, which is around 18 points in the opening and a few
 * points in the late endgame.
 */
public final class RankAwareWinrate {
  /** Used when the engine reports no spread: about the middle game of amateur dan players. */
  static final double FALLBACK_SCORE_STDEV = 12.0;

  /** Below this the spread is noise; a finished game would otherwise swing on half a point. */
  static final double MIN_SCORE_STDEV = 1.0;

  private RankAwareWinrate() {}

  /**
   * @param scoreLead points the player leads by
   * @param scoreStdev the HumanSL score standard deviation of the position, NaN when unknown
   * @return the player's chance to win, from 0 to 1
   */
  public static double of(double scoreLead, double scoreStdev) {
    if (!Double.isFinite(scoreLead)) {
      return Double.NaN;
    }
    return normalCdf(scoreLead / spread(scoreStdev));
  }

  static double spread(double scoreStdev) {
    return Double.isFinite(scoreStdev) && scoreStdev > 0.0
        ? Math.max(MIN_SCORE_STDEV, scoreStdev)
        : FALLBACK_SCORE_STDEV;
  }

  /** The standard normal distribution function, accurate to about 1e-7 (Abramowitz–Stegun). */
  static double normalCdf(double x) {
    double z = Math.abs(x) / Math.sqrt(2.0);
    double t = 1.0 / (1.0 + 0.3275911 * z);
    double erf =
        1.0
            - ((((1.061405429 * t - 1.453152027) * t + 1.421413741) * t - 0.284496736) * t
                    + 0.254829592)
                * t
                * Math.exp(-z * z);
    return x >= 0.0 ? 0.5 * (1.0 + erf) : 0.5 * (1.0 - erf);
  }
}
