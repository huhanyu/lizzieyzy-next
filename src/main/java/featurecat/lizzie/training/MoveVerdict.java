package featurecat.lizzie.training;

/** How 边下边提醒 rates one of the player's moves. */
public enum MoveVerdict {
  /** The best move, which few players of the rank find, where their usual move loses ground. */
  BRILLIANT,
  GOOD,
  /** 缓手: the rank-aware win chance dropped by 5% or more. */
  SLOW,
  /** 问题手: dropped by 10% or more. */
  PROBLEM,
  /** 恶手: dropped by 20% or more. */
  BLUNDER;

  static final double SLOW_LOSS = 0.05;
  static final double PROBLEM_LOSS = 0.10;
  static final double BLUNDER_LOSS = 0.20;

  /**
   * Below 5% or above 95% the game is decided for players of the rank, and the win chance hardly
   * moves even for a big mistake. There the points a move gives away decide instead.
   */
  static final double DECIDED = 0.05;

  static final double SLOW_POINTS = 4.0;
  static final double PROBLEM_POINTS = 8.0;
  static final double BLUNDER_POINTS = 15.0;

  /**
   * @param winBefore the player's rank-aware win chance with the best move, 0 to 1
   * @param winAfter the chance after the move played
   * @param pointsLost points the move gives away against the best move
   */
  static MoveVerdict of(double winBefore, double winAfter, double pointsLost) {
    // Rated by the whole percentage the panel shows, so "−5%" is never called a good move.
    double loss = Math.round(Math.max(0.0, winBefore - winAfter) * 100.0) / 100.0;
    MoveVerdict verdict =
        loss >= BLUNDER_LOSS
            ? BLUNDER
            : loss >= PROBLEM_LOSS ? PROBLEM : loss >= SLOW_LOSS ? SLOW : GOOD;
    if (winBefore < DECIDED || winBefore > 1.0 - DECIDED) {
      MoveVerdict byPoints =
          pointsLost >= BLUNDER_POINTS
              ? BLUNDER
              : pointsLost >= PROBLEM_POINTS ? PROBLEM : pointsLost >= SLOW_POINTS ? SLOW : GOOD;
      if (byPoints.compareTo(verdict) > 0) {
        verdict = byPoints;
      }
    }
    return verdict;
  }

  public boolean isMistake() {
    return this == SLOW || this == PROBLEM || this == BLUNDER;
  }
}
