package featurecat.lizzie.analysis;

/**
 * The AI's move plus how clear-cut it was for human players of that rank: the share of the human
 * policy on its most popular legal move, or NaN when unknown.
 */
public record HumanMoveChoice(String move, double topHumanProbability) {
  public static HumanMoveChoice of(String move) {
    return new HumanMoveChoice(move, Double.NaN);
  }
}
