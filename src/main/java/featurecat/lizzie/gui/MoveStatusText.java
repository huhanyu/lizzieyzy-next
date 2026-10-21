package featurecat.lizzie.gui;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.ResourceBundle;

/**
 * The status line under the win-rate bar, on one line: who leads by how much ("白领先 7.3 目" rather
 * than "黑领先:-7.3"), then what the previous move changed ("上一手(#3) -0.3% -1.9目").
 */
final class MoveStatusText {
  private MoveStatusText() {}

  /** Names the side that leads; a lead that rounds to 0.0 reads as even. */
  static String lead(ResourceBundle strings, double blackLead) {
    String points = String.format(Locale.ENGLISH, "%.1f", Math.abs(blackLead));
    if ("0.0".equals(points)) {
      return strings.getString("LizzieFrame.status.even");
    }
    return MessageFormat.format(
        strings.getString(
            blackLead > 0 ? "LizzieFrame.status.blackLeads" : "LizzieFrame.status.whiteLeads"),
        points);
  }

  /**
   * What the previous move changed for the side that played it: its rank among the candidates when
   * it was one ({@code candidateRank > 0}), then the winrate and, when known, the score.
   */
  static String previousMove(
      ResourceBundle strings, int candidateRank, double winrateChange, Double scoreChange) {
    StringBuilder text = new StringBuilder(strings.getString("LizzieFrame.status.previousMove"));
    if (candidateRank > 0) {
      text.append("(#").append(candidateRank).append(')');
    }
    text.append(' ').append(signed(winrateChange)).append('%');
    if (scoreChange != null) {
      text.append(' ').append(signed(scoreChange)).append(strings.getString("LizzieFrame.pts"));
    }
    return text.toString();
  }

  private static String signed(double value) {
    String digits = String.format(Locale.ENGLISH, "%.1f", Math.abs(value));
    return ("0.0".equals(digits) ? "" : value > 0 ? "+" : "-") + digits;
  }
}
