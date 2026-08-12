package featurecat.lizzie.theme;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.awt.Color;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ThemeBlunderNodeRulesTest {
  private static final List<Double> DEFAULT_THRESHOLDS =
      List.of(-24.0, -12.0, -6.0, -3.0, -1.0, 3.0, 100.0);

  @Test
  void validThemeRulesWinAsAnAtomicPair() {
    Theme theme = themeWith(rules("[-8,-2,100]", "[[1,2,3],[4,5,6],[7,8,9]]"), globalRules());

    assertEquals(List.of(-8.0, -2.0, 100.0), theme.blunderWinrateThresholds().orElseThrow());
    assertEquals(new Color(4, 5, 6), theme.blunderNodeColors().orElseThrow().get(-2.0));
  }

  @Test
  void missingThemeColorsFallsBackToTheCompleteGlobalPair() {
    JSONObject incompleteTheme = new JSONObject();
    incompleteTheme.put("blunder-winrate-thresholds", new JSONArray("[-8,-2,100]"));

    assertUsesGlobalRules(themeWith(incompleteTheme, globalRules()));
  }

  @Test
  void emptyThemeRulesFallBackToTheCompleteGlobalPair() {
    assertUsesGlobalRules(themeWith(rules("[]", "[]"), globalRules()));
  }

  @Test
  void malformedThresholdFallsBackToTheCompleteGlobalPair() {
    assertUsesGlobalRules(
        themeWith(rules("[-8,\"bad\",100]", "[[1,2,3],[4,5,6],[7,8,9]]"), globalRules()));
  }

  @Test
  void mismatchedLengthsFallBackToTheCompleteGlobalPair() {
    assertUsesGlobalRules(themeWith(rules("[-8,100]", "[[1,2,3]]"), globalRules()));
  }

  @Test
  void invalidColorChannelFallsBackToTheCompleteGlobalPair() {
    assertUsesGlobalRules(
        themeWith(rules("[-8,100]", "[[1,2,3],[4,5,256]]"), globalRules()));
  }

  @Test
  void duplicateOrUnsortedThresholdsFallBackToTheCompleteGlobalPair() {
    assertUsesGlobalRules(
        themeWith(rules("[-8,-8,100]", "[[1,2,3],[4,5,6],[7,8,9]]"), globalRules()));
    assertUsesGlobalRules(
        themeWith(rules("[-2,-8,100]", "[[1,2,3],[4,5,6],[7,8,9]]"), globalRules()));
  }

  @Test
  void invalidThemeAndGlobalRulesUseBuiltInDefaults() {
    Theme theme = themeWith(rules("[-8,100]", "[[1,2,3]]"), new JSONObject());

    assertEquals(DEFAULT_THRESHOLDS, theme.blunderWinrateThresholds().orElseThrow());
    assertEquals(
        new Color(0, 210, 210), theme.blunderNodeColors().orElseThrow().get(100.0));
  }

  @Test
  void colorGetterWorksBeforeThresholdGetter() {
    Theme theme = themeWith(rules("[-8,100]", "[[1,2,3],[4,5,6,7]]"), globalRules());

    Map<Double, Color> colors = theme.blunderNodeColors().orElseThrow();

    assertEquals(new Color(1, 2, 3), colors.get(-8.0));
    assertEquals(new Color(4, 5, 6, 7), colors.get(100.0));
    assertEquals(List.of(-8.0, 100.0), theme.blunderWinrateThresholds().orElseThrow());
  }

  @Test
  void invalidLegacyThemeFallsBackWithoutRewritingIt(@TempDir Path tempDir) throws Exception {
    String previousPathPrefix = Theme.pathPrefix;
    Path themeDirectory = tempDir.resolve("legacy");
    Files.createDirectories(themeDirectory);
    Path themeFile = themeDirectory.resolve("theme.txt");
    String original =
        rules("[-8,100]", "[[1,2,3]]").toString(2) + System.lineSeparator();
    Files.writeString(themeFile, original, StandardCharsets.UTF_8);
    try {
      Theme.pathPrefix = tempDir.toString() + java.io.File.separator;
      JSONObject ui = globalRules();
      ui.put("theme", "legacy");
      Theme theme = new Theme();

      theme.getTheme(ui);

      assertUsesGlobalRules(theme);
      assertEquals(original, Files.readString(themeFile, StandardCharsets.UTF_8));
    } finally {
      Theme.pathPrefix = previousPathPrefix;
    }
  }

  private static Theme themeWith(JSONObject themeRules, JSONObject globalRules) {
    globalRules.put("theme", "missing-test-theme-" + UUID.randomUUID());
    Theme theme = new Theme();
    theme.getTheme(globalRules);
    theme.config = themeRules;
    return theme;
  }

  private static JSONObject rules(String thresholds, String colors) {
    JSONObject rules = new JSONObject();
    rules.put("blunder-winrate-thresholds", new JSONArray(thresholds));
    rules.put("blunder-node-colors", new JSONArray(colors));
    return rules;
  }

  private static JSONObject globalRules() {
    return rules("[-12,0,100]", "[[11,12,13],[21,22,23],[31,32,33]]");
  }

  private static void assertUsesGlobalRules(Theme theme) {
    assertEquals(List.of(-12.0, 0.0, 100.0), theme.blunderWinrateThresholds().orElseThrow());
    assertEquals(new Color(11, 12, 13), theme.blunderNodeColors().orElseThrow().get(-12.0));
    assertEquals(new Color(31, 32, 33), theme.blunderNodeColors().orElseThrow().get(100.0));
  }
}
