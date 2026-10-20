package featurecat.lizzie.gui;

import java.util.Locale;

/** Text of the candidate tables' winrate and score cells for the move actually played. */
final class CandidateTableText {
  private CandidateTableText() {}

  /**
   * "99.2 ↓0.1": the value, then how far the played move is from the best one. The arrow carries
   * the sign, so the cell no longer reads "↓-0.1(99.2)" and is cut to "↓-0.1(99..." in narrow
   * columns. A difference that rounds to 0.0 is left out.
   */
  static String valueWithDifference(double value, double difference) {
    String shown = String.format(Locale.ENGLISH, "%.1f", value);
    String gap = String.format(Locale.ENGLISH, "%.1f", Math.abs(difference));
    return "0.0".equals(gap) ? shown : shown + " " + (difference > 0 ? "↑" : "↓") + gap;
  }
}
