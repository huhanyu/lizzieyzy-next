package featurecat.lizzie.logging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class DeadlineTest {
  @Test
  void runAllReturnsByOneSharedDeadlineWhenActionsIgnoreInterrupts() throws Exception {
    CountDownLatch entered = new CountDownLatch(2);
    CountDownLatch release = new CountDownLatch(1);
    CountDownLatch finished = new CountDownLatch(2);
    Runnable blocked =
        () -> {
          entered.countDown();
          try {
            while (release.getCount() > 0) {
              try {
                release.await();
              } catch (InterruptedException ignored) {
              }
            }
          } finally {
            finished.countDown();
          }
        };

    long started = System.nanoTime();
    try {
      Deadline.runAll(
          started + TimeUnit.MILLISECONDS.toNanos(300), List.of(blocked, blocked));
      long elapsed = System.nanoTime() - started;
      assertEquals(0L, entered.getCount(), "both cleanup actions must start in parallel");
      assertTrue(elapsed <= TimeUnit.MILLISECONDS.toNanos(500), "elapsedNanos=" + elapsed);
    } finally {
      release.countDown();
    }
    assertTrue(finished.await(3, TimeUnit.SECONDS));
  }
}
