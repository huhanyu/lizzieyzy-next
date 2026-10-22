package featurecat.lizzie.gui;

import static org.junit.jupiter.api.Assertions.assertTrue;

import featurecat.lizzie.analysis.HumanMoveChoice;
import featurecat.lizzie.training.HumanThinkTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class HumanSlGameControllerThinkTimeTest {
  private static final int AREA = 19 * 19;
  private static final HumanThinkTime.Pace NATURAL = HumanThinkTime.Pace.NATURAL;

  @Test
  void anObviousOpeningReplyComesQuickly() {
    long millis =
        HumanSlGameController.thinkTimeMillis(
            Optional.of(new HumanMoveChoice("Q4", 0.95)), 3, AREA, 0.0, NATURAL);

    assertTrue(millis < 800L, "was " + millis);
  }

  @Test
  void anOpenMiddleGameChoiceTakesSeveralSeconds() {
    long millis =
        HumanSlGameController.thinkTimeMillis(
            Optional.of(new HumanMoveChoice("K10", 0.1)), 120, AREA, 0.5, NATURAL);

    assertTrue(millis > 3_000L, "was " + millis);
  }

  @Test
  void theFastPaceAnswersInAboutASecond() {
    long millis =
        HumanSlGameController.thinkTimeMillis(
            Optional.of(new HumanMoveChoice("K10", 0.1)), 120, AREA, 0.5, HumanThinkTime.Pace.FAST);

    assertTrue(millis <= 1_800L, "was " + millis);
  }

  @Test
  void aFailedRequestStillUsesAPlausiblePace() {
    long millis = HumanSlGameController.thinkTimeMillis(Optional.empty(), 120, AREA, 0.5, NATURAL);

    assertTrue(millis > 1_000L && millis < 4_000L, "was " + millis);
  }
}
