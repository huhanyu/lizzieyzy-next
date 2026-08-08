package featurecat.lizzie.teacher;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.OptionalDouble;
import org.junit.jupiter.api.Test;

class TeacherVerifierTest {
  @Test
  void acceptsCoordinatesFromEveryPositionInRangeEvidence() {
    TeacherEvidence.Position first = position("D4", "Q16", List.of("Q16", "D16"));
    TeacherEvidence.Position second = position("C3", "R17", List.of("R17", "R16"));

    TeacherVerifier.Result result =
        TeacherVerifier.verify("D4之后可比较Q16，另一处C3可考虑R17。", List.of(first, second));

    assertTrue(result.violations.isEmpty());
  }

  @Test
  void acceptsPlayedContinuationButRejectsUnsupportedCoordinates() {
    TeacherEvidence.Position position =
        new TeacherEvidence.Position(
            12,
            "B",
            1000,
            "D4",
            OptionalDouble.empty(),
            List.of(new TeacherEvidence.Candidate(1, "Q16", 55, 1.2, 800, List.of("Q16"))),
            List.of("C3"));

    assertTrue(TeacherVerifier.verify("实战随后走C3。", position).violations.isEmpty());
    assertFalse(TeacherVerifier.verify("还可以走T19。", position).violations.isEmpty());
  }

  @Test
  void missingPositionEvidenceDoesNotTurnEveryCoordinateIntoAFailure() {
    TeacherVerifier.Result result = TeacherVerifier.verify("棋盘示例坐标D4，胜率101%。", List.of());

    assertTrue(
        result.violations.stream()
            .noneMatch(message -> message.contains("Unsupported coordinate")));
    assertTrue(result.violations.stream().anyMatch(message -> message.contains("101.0%")));
  }

  private static TeacherEvidence.Position position(
      String actualMove, String candidate, List<String> variation) {
    return new TeacherEvidence.Position(
        10,
        "B",
        1000,
        actualMove,
        OptionalDouble.empty(),
        List.of(new TeacherEvidence.Candidate(1, candidate, 55, 1.2, 800, variation)));
  }
}
