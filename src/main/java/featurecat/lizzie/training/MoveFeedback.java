package featurecat.lizzie.training;

import java.util.Optional;

/**
 * 边下边提醒's judgement of one of the player's moves.
 *
 * @param moveNumber the move's number in the game
 * @param move the move, such as "Q16" or "pass"
 * @param winBefore the player's rank-aware win chance with KataGo's best move, 0 to 1
 * @param winAfter the chance after the move played
 * @param pointsLost points given away against the best move, never negative
 * @param humanShare how often players of the rank play this move, 0 to 1, NaN when unknown
 * @param playedBest whether the move is KataGo's choice
 */
public record MoveFeedback(
    int moveNumber,
    String move,
    MoveVerdict verdict,
    double winBefore,
    double winAfter,
    double pointsLost,
    double humanShare,
    boolean playedBest) {

  /** Below this share of players, finding the best move counts as rare. */
  static final double RARE_SHARE = 0.10;

  /** The change of the win chance, negative for a loss. */
  public double winChange() {
    return winAfter - winBefore;
  }

  /**
   * Whether the position after the move must be searched to judge it, because the search before the
   * move did not look at it closely.
   */
  public static boolean needsPositionAfter(PositionReview before, String move) {
    return Double.isNaN(before.leadAfter(move));
  }

  /**
   * Judges {@code move}, played where {@code before} was reviewed.
   *
   * @param after the review of the position after the move, from the opponent's side; needed only
   *     when {@link #needsPositionAfter} says so, otherwise it may be null
   */
  public static Optional<MoveFeedback> judge(
      int moveNumber, String move, PositionReview before, PositionReview after) {
    Optional<PositionReview.Candidate> best = before.best();
    if (best.isEmpty()) {
      return Optional.empty();
    }
    double bestLead = best.get().scoreLead();
    double lead = before.leadAfter(move);
    if (Double.isNaN(lead) && after != null) {
      lead = -after.rootLead();
    }
    if (!Double.isFinite(lead) || !Double.isFinite(bestLead)) {
      return Optional.empty();
    }
    double stdev =
        Double.isFinite(before.scoreStdev()) || after == null
            ? before.scoreStdev()
            : after.scoreStdev();
    double winBefore = RankAwareWinrate.of(bestLead, stdev);
    double winAfter = RankAwareWinrate.of(lead, stdev);
    double pointsLost = Math.max(0.0, bestLead - lead);
    boolean playedBest =
        PositionReview.normalize(best.get().move()).equals(PositionReview.normalize(move));
    double share = before.humanShare(move);
    MoveVerdict verdict = MoveVerdict.of(winBefore, winAfter, pointsLost);
    if (verdict == MoveVerdict.GOOD && playedBest && isRareFind(before, move, share, winBefore)) {
      verdict = MoveVerdict.BRILLIANT;
    }
    return Optional.of(
        new MoveFeedback(
            moveNumber,
            PositionReview.normalize(move).equals("pass") ? "pass" : move.trim(),
            verdict,
            winBefore,
            winAfter,
            pointsLost,
            share,
            playedBest));
  }

  /**
   * Few players of the rank play the move, and the one they usually play loses at least a 缓手's
   * worth of win chance, so finding it mattered.
   */
  private static boolean isRareFind(
      PositionReview before, String move, double share, double winBefore) {
    if (!(share < RARE_SHARE)) {
      return false;
    }
    String common = before.commonMove();
    if (common == null || common.equals(PositionReview.normalize(move))) {
      return false;
    }
    double commonLead = before.leadAfter(common);
    if (Double.isNaN(commonLead)) {
      return false;
    }
    double commonWin = RankAwareWinrate.of(commonLead, before.scoreStdev());
    return winBefore - commonWin >= MoveVerdict.SLOW_LOSS;
  }
}
