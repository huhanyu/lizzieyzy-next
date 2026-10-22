package featurecat.lizzie.training;

import java.util.List;

/** Controls how foreground analysis is presented during a HumanSL game. */
public enum TrainingMode {
  /**
   * 边下边提醒: analysis stays hidden, and each of the player's moves is rated right after it is played,
   * by the win chance of players of the opponent's rank.
   */
  LIVE_FEEDBACK,
  POST_GAME_REVIEW,
  LIVE_ANALYSIS,
  /** Compatibility value for integrations created before live correction became live analysis. */
  @Deprecated
  LIVE_CORRECTION;

  /** The modes the setup dialog offers, in its order; the first is the default. */
  public static final List<TrainingMode> CHOICES =
      List.of(LIVE_FEEDBACK, POST_GAME_REVIEW, LIVE_ANALYSIS);

  public boolean isLiveAnalysis() {
    return this == LIVE_ANALYSIS || this == LIVE_CORRECTION;
  }

  /** The mode as the setup dialog offers it. */
  public TrainingMode choice() {
    return isLiveAnalysis() ? LIVE_ANALYSIS : this;
  }

  /** Key of the mode's name in the display strings, with an English fallback. */
  public String[] label() {
    switch (choice()) {
      case LIVE_FEEDBACK:
        return new String[] {"HumanSlTraining.mode.feedback", "Live feedback"};
      case LIVE_ANALYSIS:
        return new String[] {"HumanSlTraining.mode.live", "Live analysis"};
      default:
        return new String[] {"HumanSlTraining.mode.review", "Post-game review"};
    }
  }
}
