package featurecat.lizzie.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class HumanSlModelCheckTest {
  @Test
  void theModelIsHashedOffTheEventThreadAndReportedOnIt() throws Exception {
    AtomicBoolean checkedOnEventThread = new AtomicBoolean(true);
    AtomicBoolean reportedOnEventThread = new AtomicBoolean(false);
    AtomicReference<Path> reported = new AtomicReference<>();
    CountDownLatch done = new CountDownLatch(1);

    SwingUtilities.invokeAndWait(
        () ->
            HumanSlModelCheck.start(
                () -> {
                  checkedOnEventThread.set(SwingUtilities.isEventDispatchThread());
                  return Path.of("human.bin.gz");
                },
                model -> {
                  reportedOnEventThread.set(SwingUtilities.isEventDispatchThread());
                  reported.set(model);
                  done.countDown();
                }));

    assertTrue(done.await(5, TimeUnit.SECONDS));
    assertFalse(checkedOnEventThread.get());
    assertTrue(reportedOnEventThread.get());
    assertEquals(Path.of("human.bin.gz"), reported.get());
  }

  @Test
  void aFailingCheckStillReportsSoTheDialogNeverWaitsForever() throws Exception {
    AtomicReference<Path> reported = new AtomicReference<>(Path.of("unset"));
    CountDownLatch done = new CountDownLatch(1);
    HumanSlModelCheck.start(
        () -> {
          throw new IllegalStateException("disk gone");
        },
        model -> {
          reported.set(model);
          done.countDown();
        });

    assertTrue(done.await(5, TimeUnit.SECONDS));
    assertNull(reported.get(), "a failed check reads as a missing model");
  }
}
