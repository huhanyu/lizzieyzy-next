package featurecat.lizzie.teacher.analysis;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class QualityGateTest {
  @Test
  void ordinaryMarkdownDoesNotWarnThatStructuredJsonIsMissing() {
    QualityGate.TeacherQualityGateResult result =
        QualityGate.runTeacherQualityGate("这手应先看D4附近的变化。", null, false);

    assertTrue(result.structuredWarnings.isEmpty());
    assertTrue(result.structuredViolations.isEmpty());
  }

  @Test
  void malformedStructuredGroundingJsonIsRejected() {
    QualityGate.TeacherQualityGateResult result =
        QualityGate.runTeacherQualityGate("```json\n{\"claims\": [}\n```", null, false);

    assertFalse(result.structuredViolations.isEmpty());
  }
}
