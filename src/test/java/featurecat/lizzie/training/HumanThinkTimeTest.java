package featurecat.lizzie.training;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class HumanThinkTimeTest {
  private static final int AREA = 19 * 19;
  private static final Duration TEN_SECONDS = Duration.ofSeconds(10);

  @Test
  void obviousOpeningRepliesAreQuick() {
    long millis = HumanThinkTime.millis(0.9, 5, AREA, 0.5, TEN_SECONDS);

    assertTrue(millis >= HumanThinkTime.MIN_MILLIS && millis < 1_000L, "was " + millis);
  }

  @Test
  void openMiddleGameChoicesTakeLonger() {
    long millis = HumanThinkTime.millis(0.15, 120, AREA, 0.5, TEN_SECONDS);

    assertTrue(millis > 3_000L, "was " + millis);
  }

  @Test
  void harderChoicesNeverTakeLessTime() {
    long previous = 0L;
    for (double top = 1.0; top >= 0.0; top -= 0.1) {
      long millis = HumanThinkTime.millis(top, 120, AREA, 0.5, TEN_SECONDS);
      assertTrue(millis >= previous, "top " + top + " took " + millis + " < " + previous);
      previous = millis;
    }
  }

  @Test
  void theOpeningIsQuickerThanTheMiddleGame() {
    assertTrue(
        HumanThinkTime.millis(0.5, 10, AREA, 0.5, TEN_SECONDS)
            < HumanThinkTime.millis(0.5, 100, AREA, 0.5, TEN_SECONDS));
  }

  @Test
  void theMoveTimeLimitCapsTheDelay() {
    // The hardest middle-game move with the slowest jitter: 1.6 s x 2.8 x 1.35.
    assertEquals(6_048L, HumanThinkTime.millis(0.0, 120, AREA, 1.0, Duration.ofHours(24)));
    assertEquals(6_048L, HumanThinkTime.millis(0.0, 120, AREA, 1.0, (Duration) null));
    assertEquals(1_500L, HumanThinkTime.millis(0.0, 120, AREA, 1.0, Duration.ofSeconds(2)));
  }

  @Test
  void noMoveWaitsLongerThanEightSeconds() {
    for (double top = 0.0; top <= 1.0; top += 0.05) {
      assertTrue(
          HumanThinkTime.millis(top, 400, 9 * 9, 1.0, (Duration) null) <= HumanThinkTime.MAX_MILLIS,
          "top " + top);
    }
  }

  @Test
  void unknownDifficultyUsesAMiddlingPace() {
    long unknown = HumanThinkTime.millis(Double.NaN, 120, AREA, 0.5, TEN_SECONDS);

    assertTrue(unknown > HumanThinkTime.millis(0.9, 120, AREA, 0.5, TEN_SECONDS));
    assertTrue(unknown < HumanThinkTime.millis(0.05, 120, AREA, 0.5, TEN_SECONDS));
  }

  @Test
  void thePaceOnlyScalesTheWaitWithinItsBounds() {
    long natural = HumanThinkTime.millis(0.5, 120, AREA, 0.5, HumanThinkTime.Pace.NATURAL);
    long fast = HumanThinkTime.millis(0.5, 120, AREA, 0.5, HumanThinkTime.Pace.FAST);
    long slow = HumanThinkTime.millis(0.5, 120, AREA, 0.5, HumanThinkTime.Pace.SLOW);

    assertEquals(HumanThinkTime.millis(0.5, 120, AREA, 0.5, (Duration) null), natural);
    assertTrue(fast < natural && natural < slow, fast + " " + natural + " " + slow);
    assertTrue(fast <= 1_800L, "fast is about a second, was " + fast);
    assertTrue(
        HumanThinkTime.millis(0.0, 120, AREA, 1.0, HumanThinkTime.Pace.SLOW) > 10_000L,
        "slow is not capped by a move time limit");
  }

  @Test
  void neverQuickerThanTheFloor() {
    assertEquals(
        HumanThinkTime.MIN_MILLIS, HumanThinkTime.millis(1.0, 0, AREA, 0.0, Duration.ofMillis(1)));
  }
}
