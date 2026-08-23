package featurecat.lizzie.logging;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class ObservationTextTest {
  @Test
  void utf8LimitDoesNotSplitMultibyteOrSupplementaryCharacters() {
    String bounded = ObservationText.boundedUtf8("棋😀".repeat(40), 73, 20);

    assertTrue(bounded.getBytes(StandardCharsets.UTF_8).length <= 73, bounded);
    assertTrue(bounded.endsWith(" [truncated]"), bounded);
    assertFalse(hasUnpairedSurrogate(bounded), bounded);
  }

  @Test
  void lineLimitTreatsCrLfAsOneBreak() {
    String bounded = ObservationText.boundedUtf8("一\r\n二\r\n三\r\n四", 200, 3);

    assertTrue(bounded.lines().count() <= 3, bounded);
    assertTrue(bounded.endsWith(" [truncated]"), bounded);
    assertFalse(bounded.contains("四"), bounded);
  }

  private static boolean hasUnpairedSurrogate(String value) {
    for (int index = 0; index < value.length(); index++) {
      char current = value.charAt(index);
      if (Character.isHighSurrogate(current)) {
        if (index + 1 >= value.length() || !Character.isLowSurrogate(value.charAt(index + 1))) {
          return true;
        }
        index++;
      } else if (Character.isLowSurrogate(current)) {
        return true;
      }
    }
    return false;
  }
}
