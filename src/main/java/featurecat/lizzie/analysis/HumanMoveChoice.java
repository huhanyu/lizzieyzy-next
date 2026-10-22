package featurecat.lizzie.analysis;

/**
 * The AI's move plus how clear-cut it was for human players of that rank, and the AI's own view of
 * the position when KataGo searched it.
 *
 * @param topHumanProbability share of the human policy on its most popular legal move, or NaN
 * @param aiWinrate the AI's winrate before its move, from its own side, or NaN when not searched
 * @param aiScoreLead the AI's score lead before its move, from its own side, or NaN
 */
public record HumanMoveChoice(
    String move, double topHumanProbability, double aiWinrate, double aiScoreLead) {
  public HumanMoveChoice(String move, double topHumanProbability) {
    this(move, topHumanProbability, Double.NaN, Double.NaN);
  }

  public static HumanMoveChoice of(String move) {
    return new HumanMoveChoice(move, Double.NaN);
  }
}
