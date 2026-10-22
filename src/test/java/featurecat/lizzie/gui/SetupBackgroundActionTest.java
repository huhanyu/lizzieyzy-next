package featurecat.lizzie.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class SetupBackgroundActionTest {
  @Test
  void profileMutationLeavesEventThreadResponsiveAndPublishesBeforeUnlocking() throws Exception {
    CountDownLatch started = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    CountDownLatch finished = new CountDownLatch(1);
    List<String> callbacks = new ArrayList<>();
    AtomicReference<Thread> worker = new AtomicReference<>();
    SwingUtilities.invokeAndWait(
        () ->
            worker.set(
                SetupBackgroundAction.start(
                    "test-profile-apply",
                    () -> {
                      assertFalse(SwingUtilities.isEventDispatchThread());
                      started.countDown();
                      assertTrue(release.await(5, TimeUnit.SECONDS));
                      return "applied";
                    },
                    result -> {
                      assertTrue(SwingUtilities.isEventDispatchThread());
                      callbacks.add(result);
                    },
                    error -> {
                      throw new AssertionError(error);
                    },
                    completed -> {
                      assertTrue(SwingUtilities.isEventDispatchThread());
                      assertSame(worker.get(), completed);
                      callbacks.add("unlocked");
                      finished.countDown();
                    })));
    assertTrue(started.await(5, TimeUnit.SECONDS));
    try {
      SwingUtilities.invokeAndWait(() -> assertTrue(callbacks.isEmpty()));
    } finally {
      release.countDown();
    }
    assertTrue(finished.await(5, TimeUnit.SECONDS));
    assertEquals(List.of("applied", "unlocked"), callbacks);
  }

  @Test
  void failedMutationPublishesOnlyFailureAndStillUnlocks() throws Exception {
    IOException expected = new IOException("Profile write failed");
    List<String> callbacks = new ArrayList<>();
    CountDownLatch finished = new CountDownLatch(1);
    SwingUtilities.invokeAndWait(
        () ->
            SetupBackgroundAction.start(
                "test-profile-failure",
                () -> {
                  throw expected;
                },
                result -> callbacks.add("success"),
                error -> {
                  assertTrue(SwingUtilities.isEventDispatchThread());
                  assertSame(expected, error);
                  callbacks.add("failure");
                },
                completed -> {
                  callbacks.add("unlocked");
                  finished.countDown();
                }));
    assertTrue(finished.await(5, TimeUnit.SECONDS));
    assertEquals(List.of("failure", "unlocked"), callbacks);
  }
}
