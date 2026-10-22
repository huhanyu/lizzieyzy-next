package featurecat.lizzie.training;

import java.util.Locale;

/**
 * How a coaching game ended: a counted margin, a resignation, a draw, or a count that failed.
 *
 * @param blackWins true or false for a winner, null for a draw or an uncounted game
 * @param margin points the winner won by, 0 for a draw, NaN for a resignation or no count
 */
public record GameResult(Boolean blackWins, double margin, boolean byResignation) {
  public static GameResult resignation(boolean blackWins) {
    return new GameResult(blackWins, Double.NaN, true);
  }

  public static GameResult uncounted() {
    return new GameResult(null, Double.NaN, false);
  }

  /**
   * From KataGo's final score lead for Black, rounded to what the komi allows: with a half-point
   * komi every result is a half point, with a whole-point komi a draw is possible.
   */
  public static GameResult byScore(double blackLead, double komi) {
    if (!Double.isFinite(blackLead) || !Double.isFinite(komi)) {
      return uncounted();
    }
    double fraction = komi - Math.floor(komi);
    double rounded = Math.round(blackLead - fraction) + fraction;
    if (Math.abs(rounded) < 1.0e-9) {
      return new GameResult(null, 0.0, false);
    }
    return new GameResult(rounded > 0, Math.abs(rounded), false);
  }

  public boolean isDraw() {
    return blackWins == null && margin == 0.0;
  }

  public boolean isCounted() {
    return blackWins != null || isDraw();
  }

  /** The SGF RE value: B+3.5, W+R, 0 for a draw, empty when the game was not counted. */
  public String sgf() {
    if (isDraw()) {
      return "0";
    }
    if (blackWins == null) {
      return "";
    }
    return (blackWins ? "B+" : "W+") + (byResignation ? "R" : marginText());
  }

  /** The margin without a trailing ".0": 3.5 or 4. */
  public String marginText() {
    if (!Double.isFinite(margin)) {
      return "";
    }
    return margin == Math.rint(margin)
        ? String.valueOf((long) margin)
        : String.format(Locale.ROOT, "%.1f", margin);
  }
}
