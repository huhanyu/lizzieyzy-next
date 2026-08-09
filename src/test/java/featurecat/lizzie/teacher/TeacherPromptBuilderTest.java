package featurecat.lizzie.teacher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Locale;
import java.util.OptionalDouble;
import org.junit.jupiter.api.Test;

class TeacherPromptBuilderTest {
  @Test
  void promptContainsEvidenceAndExplicitAntiHallucinationContract() {
    TeacherEvidence.Position position =
        new TeacherEvidence.Position(
            42,
            "B",
            1600,
            "D4",
            OptionalDouble.of(2.5),
            List.of(
                new TeacherEvidence.Candidate(1, "Q16", 62.4, 3.1, 1000, List.of("Q16", "D4")),
                new TeacherEvidence.Candidate(2, "D4", 59.9, 1.4, 600, List.of("D4", "Q16"))));

    List<TeacherLlmClient.Message> messages =
        TeacherPromptBuilder.forPosition(position, Locale.SIMPLIFIED_CHINESE, null);
    String system = messages.get(0).content;
    String evidence = messages.get(1).content;

    assertTrue(system.contains("Simplified Chinese"));
    assertTrue(system.contains("Never invent"));
    assertTrue(evidence.contains("D4"));
    assertTrue(evidence.contains("Q16"));
    assertTrue(evidence.contains("pv=Q16"));
    assertFalse(evidence.contains("pv=Q16(B) Q16(W)"));
    assertTrue(evidence.contains("2.5"));
    assertFalse(evidence.contains("user comment"));
  }

  @Test
  void playedMoveWarningUsesARealLineBreak() {
    TeacherEvidence.Position position =
        new TeacherEvidence.Position(
            9,
            "W",
            800,
            "C3",
            OptionalDouble.empty(),
            List.of(new TeacherEvidence.Candidate(1, "Q16", 60, 2, 800, List.of("Q16"))));

    String evidence =
        TeacherPromptBuilder.forPosition(position, Locale.ENGLISH, null).get(1).content;

    assertTrue(evidence.contains("C3"));
    assertTrue(evidence.contains("\n"));
    assertFalse(evidence.contains("list.\\\\n"));
  }

  @Test
  void followUpKeepsTheOriginalRangeEvidenceInsteadOfOnlyTheLastPosition() {
    List<TeacherLlmClient.Message> evidenceContext =
        List.of(
            new TeacherLlmClient.Message("system", "evidence-only"),
            new TeacherLlmClient.Message("user", "move 12 evidence\nmove 38 evidence"));

    List<TeacherLlmClient.Message> followUp =
        TeacherPromptBuilder.forFollowUp(
            evidenceContext, "previous answer", "Why was move 12 important?", Locale.ENGLISH, null);

    assertEquals(4, followUp.size());
    assertEquals("move 12 evidence\nmove 38 evidence", followUp.get(1).content);
    assertEquals("previous answer", followUp.get(2).content);
    assertTrue(followUp.get(3).content.contains("Why was move 12 important?"));
  }
}
