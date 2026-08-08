package featurecat.lizzie.teacher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TeacherCommentCodecTest {
  @Test
  void preservesUserCommentWhenAddingAndReplacingCommentary() {
    String first = TeacherCommentCodec.upsert("User note\nSecond line", "First answer", "m1");
    String second = TeacherCommentCodec.upsert(first, "Replacement answer", "m2");

    assertTrue(second.startsWith("User note\nSecond line\n\n"));
    assertFalse(second.contains("First answer"));
    assertEquals("Replacement answer", TeacherCommentCodec.extract(second).orElseThrow());
    assertEquals(1, occurrences(second, TeacherCommentCodec.BEGIN));
    assertEquals(1, occurrences(second, TeacherCommentCodec.END));
  }

  @Test
  void incompleteMarkerInUserCommentIsNotDeleted() {
    String original = "Keep this " + TeacherCommentCodec.BEGIN + " fragment";
    assertEquals(original, TeacherCommentCodec.removeBlocks(original));
  }

  @Test
  void incompleteUserMarkerDoesNotPreventReplacingTheRealCommentaryBlock() {
    String userText = "Keep this " + TeacherCommentCodec.BEGIN + " fragment";
    String first = TeacherCommentCodec.upsert(userText, "First answer", "m1");
    String second = TeacherCommentCodec.upsert(first, "Second answer", "m2");

    assertTrue(second.startsWith(userText));
    assertFalse(second.contains("First answer"));
    assertEquals("Second answer", TeacherCommentCodec.extract(second).orElseThrow());
  }

  @Test
  void generatedMarkerTextIsEscapedBeforeWritingToSgf() {
    String generated = "Explanation " + TeacherCommentCodec.END + " still continues";
    String stored = TeacherCommentCodec.upsert("User note", generated, "model");

    assertEquals(
        "Explanation [LizzieYzy AI Commentary END escaped] still continues",
        TeacherCommentCodec.extract(stored).orElseThrow());
    assertEquals(1, occurrences(stored, TeacherCommentCodec.END));
  }

  @Test
  void emptyCommentaryIsRejected() {
    assertThrows(
        IllegalArgumentException.class, () -> TeacherCommentCodec.upsert("user", "  ", "model"));
  }

  private static int occurrences(String text, String needle) {
    int count = 0;
    int offset = 0;
    while ((offset = text.indexOf(needle, offset)) >= 0) {
      count++;
      offset += needle.length();
    }
    return count;
  }
}
