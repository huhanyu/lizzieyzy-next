package featurecat.lizzie.teacher.knowledge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class JsonKnowledgeLoaderTest {
  @Test
  void parsesEscapesNumbersBooleansAndNullWithoutLosingTypes() {
    Object parsed =
        JsonKnowledgeLoader.parse(
            "{\"name\":\"\\u5b9a\\u5f0f\\nA\",\"score\":12.5,\"enabled\":true,\"none\":null}");

    Map<?, ?> object = assertInstanceOf(Map.class, parsed);
    assertEquals("定式\nA", object.get("name"));
    assertEquals(12.5, ((Number) object.get("score")).doubleValue(), 0.0001);
    assertEquals(Boolean.TRUE, object.get("enabled"));
    assertEquals(null, object.get("none"));
  }

  @Test
  void rejectsMalformedOrTrailingJson() {
    assertThrows(RuntimeException.class, () -> JsonKnowledgeLoader.parse("{\"a\":}"));
    assertThrows(
        IllegalArgumentException.class, () -> JsonKnowledgeLoader.parse("{\"a\":1} trailing"));
  }

  @Test
  void bundledKnowledgeCatalogsLoadWithExpectedCoreContent() {
    assertFalse(JsonKnowledgeLoader.loadJosekiPatternCards().isEmpty());
    assertFalse(JsonKnowledgeLoader.loadEliteCards().isEmpty());
    assertFalse(JsonKnowledgeLoader.loadTrainingProblems().isEmpty());
    assertFalse(JsonKnowledgeLoader.loadJosekiLines().isEmpty());
    List<LocalPatternMatcher.ShapePatternCard> shapes = JsonKnowledgeLoader.loadShapePatternCards();
    assertFalse(shapes.isEmpty());
    assertFalse(shapes.stream().allMatch(card -> card.minScore == null));
  }

  @Test
  void bundledCatalogsAreParsedOnceAndPublishedAsReadOnlyLists() {
    List<LocalShapeGeometryMatcher.ProblemEntry> problems =
        JsonKnowledgeLoader.loadTrainingProblems();

    assertSame(problems, JsonKnowledgeLoader.loadTrainingProblems());
    assertSame(JsonKnowledgeLoader.loadJosekiLines(), JsonKnowledgeLoader.loadJosekiLines());
    assertThrows(UnsupportedOperationException.class, problems::clear);
  }

  @Test
  void bundledJosekiSourcesIncludeDistributionMetadata() {
    Object parsed =
        JsonKnowledgeLoader.parse(
            JsonKnowledgeLoader.readResource("knowledge/joseki-source-manifest.json"));
    List<?> sources = assertInstanceOf(List.class, parsed);

    assertFalse(
        sources.stream()
            .map(Map.class::cast)
            .filter(source -> Boolean.TRUE.equals(source.get("bundled")))
            .anyMatch(
                source ->
                    source.get("license") == null
                        || source.get("license").toString().isBlank()
                        || source.get("usePolicy") == null));
  }
}
