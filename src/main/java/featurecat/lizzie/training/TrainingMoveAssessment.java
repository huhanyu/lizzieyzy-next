package featurecat.lizzie.training;

/** Immutable report row derived from a HumanSL coaching decision. */
public final class TrainingMoveAssessment {
  public final HumanMoveDecision decision;
  public final boolean deepened;

  public TrainingMoveAssessment(HumanMoveDecision decision, boolean deepened) {
    this.decision = decision;
    this.deepened = deepened;
  }
}
