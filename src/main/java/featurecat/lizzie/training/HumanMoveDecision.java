package featurecat.lizzie.training;

import featurecat.lizzie.rules.Board;
import featurecat.lizzie.rules.BoardHistoryNode;

/** Analysis of one move made by the human player during a coaching game. */
public final class HumanMoveDecision {
  public final int moveNumber;
  public final int boardWidth;
  public final int boardHeight;
  public final BoardHistoryNode positionBeforeMove;
  public final String actualMove;
  public final String commonHumanMove;
  public final String kataGoBestMove;
  public final double humanPolicyProbability;
  public final double scoreLoss;
  public final double winrateLoss;

  public HumanMoveDecision(
      int moveNumber,
      BoardHistoryNode positionBeforeMove,
      String actualMove,
      String commonHumanMove,
      String kataGoBestMove,
      double humanPolicyProbability,
      double scoreLoss,
      double winrateLoss) {
    this.moveNumber = moveNumber;
    boardWidth = Math.max(1, Board.boardWidth);
    boardHeight = Math.max(1, Board.boardHeight);
    this.positionBeforeMove = positionBeforeMove;
    this.actualMove = valueOrPass(actualMove);
    this.commonHumanMove = valueOrPass(commonHumanMove);
    this.kataGoBestMove = valueOrPass(kataGoBestMove);
    this.humanPolicyProbability = finiteOrNaN(humanPolicyProbability);
    this.scoreLoss = nonNegativeOrNaN(scoreLoss);
    this.winrateLoss = nonNegativeOrNaN(winrateLoss);
  }

  public boolean isProblemMove() {
    return (!Double.isNaN(scoreLoss) && scoreLoss >= 1.5)
        || (!Double.isNaN(winrateLoss) && winrateLoss >= 0.05);
  }

  public double severity() {
    double scorePart = Double.isNaN(scoreLoss) ? 0.0 : scoreLoss / 2.0;
    double winratePart = Double.isNaN(winrateLoss) ? 0.0 : winrateLoss * 10.0;
    return Math.max(scorePart, winratePart);
  }

  private static String valueOrPass(String move) {
    return move == null || move.trim().isEmpty() ? "pass" : move.trim();
  }

  private static double finiteOrNaN(double value) {
    return Double.isFinite(value) ? value : Double.NaN;
  }

  private static double nonNegativeOrNaN(double value) {
    return Double.isFinite(value) ? Math.max(0.0, value) : Double.NaN;
  }
}
