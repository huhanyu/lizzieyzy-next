package featurecat.lizzie.teacher.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TeacherPersonaTest {
  @Test
  void nullInputUsesSafeIntermediateDefaults() {
    String prompt = TeacherPersona.buildTeacherPersonaInstruction(null);

    assertTrue(prompt.contains("当前用户是级位/中级水平"));
    assertTrue(prompt.contains("用户年龄未指定"));
    assertTrue(prompt.contains("你的风格：平衡自然"));
    assertTrue(prompt.contains("术语密度：中"));
    assertTrue(prompt.contains("讲解节奏：标准"));
    assertTrue(prompt.contains("参考变化：适中"));
  }

  @Test
  void partiallyMigratedInputDoesNotFallThroughToDanLevel() {
    TeacherPersona.TeacherPersonaInput input = new TeacherPersona.TeacherPersonaInput();
    input.level = null;
    input.ageRange = null;
    input.style = null;
    input.terminologyDensity = null;
    input.explanationPace = null;
    input.variationDetail = null;

    String prompt = TeacherPersona.buildTeacherPersonaInstruction(input);

    assertTrue(prompt.contains("当前用户是级位/中级水平"));
    assertTrue(prompt.contains("用户年龄未指定"));
    assertTrue(prompt.contains("你的风格：平衡自然"));
  }

  @Test
  void explicitPersonaPreservesEveryUserChoice() {
    TeacherPersona.TeacherPersonaInput input = new TeacherPersona.TeacherPersonaInput();
    input.level = TeacherPersona.Level.DAN;
    input.rank = TeacherPersona.Rank.D5;
    input.exactAge = 15;
    input.ageRange = TeacherPersona.AgeRange.TEEN;
    input.style = TeacherPersona.Style.RIGOROUS;
    input.terminologyDensity = TeacherPersona.TerminologyDensity.HIGH;
    input.explanationPace = TeacherPersona.ExplanationPace.DETAILED;
    input.variationDetail = TeacherPersona.VariationDetail.MANY;

    String prompt = TeacherPersona.buildTeacherPersonaInstruction(input);

    assertTrue(prompt.contains("当前用户是段位水平"));
    assertTrue(prompt.contains("当前用户段位：5d"));
    assertTrue(prompt.contains("用户年龄：15 岁"));
    assertTrue(prompt.contains("用户是青少年"));
    assertTrue(prompt.contains("你的风格：严谨细致"));
    assertTrue(prompt.contains("术语密度：多"));
    assertTrue(prompt.contains("讲解节奏：细讲"));
    assertTrue(prompt.contains("参考变化：详细"));
  }

  @Test
  void normalizationIsBoundedAndDeterministic() {
    assertEquals(TeacherPersona.Level.INTERMEDIATE, TeacherPersona.normalizeCoachLevel(null));
    assertEquals(TeacherPersona.Rank.D9, TeacherPersona.normalizeStudentRank("9d"));
    assertEquals(TeacherPersona.Rank.SUB1D, TeacherPersona.normalizeStudentRank("3k"));
    assertEquals(0, TeacherPersona.normalizeExactStudentAge(-2));
    assertEquals(120, TeacherPersona.normalizeExactStudentAge(155));
    assertEquals(16, TeacherPersona.normalizeExactStudentAge(15.6));
    assertEquals(0, TeacherPersona.normalizeExactStudentAge(Double.NaN));
  }
}
