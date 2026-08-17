package featurecat.lizzie.training;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class HumanSlTrainingSessionTest {

  @Test
  void reportKeepsOnlyThreeMostSevereMovesInStableOrder() {
    TrainingSessionReport report =
        new TrainingSessionReport(
            List.of(
                decision(8, 0.5, 0.01),
                decision(20, 4.0, 0.02),
                decision(12, 1.0, 0.20),
                decision(4, 2.0, 0.03)));

    assertEquals(List.of(12, 20, 4), moveNumbers(report));
  }

  @Test
  void deepResultReplacesQuickResultWithoutDuplicatingMove() {
    HumanSlTrainingSession session = new HumanSlTrainingSession();
    session.addDecision(decision(33, 1.0, 0.01));
    session.upsertDecision(decision(33, 3.0, 0.08), true);

    TrainingSessionReport report = session.buildReport();

    assertEquals(1, session.decisions().size());
    assertEquals(3.0, report.assessments().get(0).decision.scoreLoss, 0.0001);
    assertTrue(report.assessments().get(0).deepened);
  }

  @Test
  void quickResultsRemainUsableWhenDeepAnalysisIsUnavailable() {
    HumanSlTrainingSession session = new HumanSlTrainingSession();
    session.addDecision(decision(18, 2.0, 0.04));

    TrainingSessionReport report = session.buildReport();

    assertFalse(report.isEmpty());
    assertFalse(report.assessments().get(0).deepened);
  }

  @Test
  void removingReplayedMoveAlsoClearsItsDeepResult() {
    HumanSlTrainingSession session = new HumanSlTrainingSession();
    session.upsertDecision(decision(22, 4.0, 0.10), true);

    session.removeDecision(22);
    session.addDecision(decision(22, 0.2, 0.01));

    TrainingSessionReport report = session.buildReport();
    assertEquals(1, session.decisions().size());
    assertEquals(0.2, report.assessments().get(0).decision.scoreLoss, 0.0001);
    assertFalse(report.assessments().get(0).deepened);
  }

  private static List<Integer> moveNumbers(TrainingSessionReport report) {
    return report.assessments().stream()
        .map(assessment -> assessment.decision.moveNumber)
        .toList();
  }

  private static HumanMoveDecision decision(
      int moveNumber, double scoreLoss, double winrateLoss) {
    return new HumanMoveDecision(
        moveNumber,
        null,
        "D4",
        "Q16",
        "C3",
        0.25,
        scoreLoss,
        winrateLoss);
  }
}
