package featurecat.lizzie.teacher.knowledge;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class JosekiKnowledgeReloadTest {
  @Test
  void invalidationReloadsAllSourcesAndKeepsPublishedListsImmutable() {
    List<JosekiRecognizer.JosekiPatternCard> initial = JosekiRecognizer.loadJosekiCards();
    assertFalse(initial.isEmpty());
    assertThrows(UnsupportedOperationException.class, initial::clear);

    JosekiRecognizer.invalidateCache();
    List<JosekiRecognizer.JosekiPatternCard> reloaded = JosekiRecognizer.loadJosekiCards();

    assertFalse(reloaded.isEmpty());
    assertNotSame(initial, reloaded);
    assertThrows(UnsupportedOperationException.class, reloaded::clear);
  }
}
