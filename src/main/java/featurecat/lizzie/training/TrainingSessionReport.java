package featurecat.lizzie.training;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Final coaching report focused on the three most valuable review positions. */
public final class TrainingSessionReport {
  private final List<TrainingMoveAssessment> assessments;

  public TrainingSessionReport(List<HumanMoveDecision> decisions) {
    this(decisions, Collections.<Integer>emptySet());
  }

  public TrainingSessionReport(
      List<HumanMoveDecision> decisions, Set<Integer> deepenedMoveNumbers) {
    ArrayList<HumanMoveDecision> ranked = new ArrayList<HumanMoveDecision>();
    if (decisions != null) {
      ranked.addAll(decisions);
    }
    ranked.sort(
        Comparator.comparingDouble(HumanMoveDecision::severity)
            .reversed()
            .thenComparingInt(value -> value.moveNumber));
    ArrayList<TrainingMoveAssessment> rows = new ArrayList<TrainingMoveAssessment>();
    Set<Integer> deepened =
        deepenedMoveNumbers == null
            ? Collections.<Integer>emptySet()
            : new HashSet<Integer>(deepenedMoveNumbers);
    for (int index = 0; index < Math.min(3, ranked.size()); index++) {
      HumanMoveDecision decision = ranked.get(index);
      rows.add(new TrainingMoveAssessment(decision, deepened.contains(decision.moveNumber)));
    }
    assessments = Collections.unmodifiableList(rows);
  }

  public List<TrainingMoveAssessment> assessments() {
    return assessments;
  }

  public boolean isEmpty() {
    return assessments.isEmpty();
  }

  public double averageHumanProbability() {
    double total = 0.0;
    int count = 0;
    for (TrainingMoveAssessment assessment : assessments) {
      double probability = assessment.decision.humanPolicyProbability;
      if (Double.isFinite(probability)) {
        total += probability;
        count++;
      }
    }
    return count == 0 ? Double.NaN : total / count;
  }
}
