package featurecat.lizzie;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import featurecat.lizzie.analysis.Leelaz;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class LizzieEngineAuthorityMutationTest {
  @Test
  void primaryMutationWaitsForPresentationLeaseAndPreservesExactGeneration() throws Exception {
    Leelaz previousPrimary = Lizzie.leelaz;
    Leelaz engine = new Leelaz("");
    Lizzie.EngineAuthorityPresentationLease presentationLease = null;
    Thread mutationWorker = null;
    try {
      Lizzie.setPrimaryEngine(engine);
      long expectedGeneration = Lizzie.capturePrimaryEngineGeneration(engine);
      presentationLease =
          Lizzie.claimEngineAuthorityPresentation(
              Lizzie.board, Lizzie.engineManager, engine, expectedGeneration);
      assertNotNull(presentationLease);

      CountDownLatch mutationStarted = new CountDownLatch(1);
      CountDownLatch mutationCompleted = new CountDownLatch(1);
      AtomicInteger actionRuns = new AtomicInteger();
      AtomicReference<Boolean> mutationResult = new AtomicReference<>();
      AtomicReference<Throwable> mutationFailure = new AtomicReference<>();
      mutationWorker =
          new Thread(
              () -> {
                mutationStarted.countDown();
                try {
                  mutationResult.set(
                      Lizzie.runIfPrimaryEngineWithMutation(
                          engine,
                          expectedGeneration,
                          mutation -> {
                            actionRuns.incrementAndGet();
                            mutation.replaceWith(engine);
                          }));
                } catch (Throwable failure) {
                  mutationFailure.set(failure);
                } finally {
                  mutationCompleted.countDown();
                }
              },
              "primary-authority-mutation-writer");
      mutationWorker.setDaemon(true);
      mutationWorker.start();

      assertTrue(mutationStarted.await(2, TimeUnit.SECONDS));
      assertFalse(
          mutationCompleted.await(100, TimeUnit.MILLISECONDS),
          "PRIMARY mutation must wait while the presentation lease is active");
      assertEquals(expectedGeneration, Lizzie.capturePrimaryEngineGeneration(engine));
      assertEquals(0, actionRuns.get());

      presentationLease.close();
      presentationLease = null;
      assertTrue(mutationCompleted.await(2, TimeUnit.SECONDS));
      mutationWorker.join(2_000L);
      assertFalse(mutationWorker.isAlive());
      assertNull(mutationFailure.get());
      assertEquals(Boolean.TRUE, mutationResult.get());
      assertEquals(1, actionRuns.get());

      long committedGeneration = Lizzie.capturePrimaryEngineGeneration(engine);
      assertEquals(expectedGeneration + 1L, committedGeneration);
      assertFalse(
          Lizzie.runIfPrimaryEngineWithMutation(
              engine,
              expectedGeneration,
              mutation -> {
                actionRuns.incrementAndGet();
                mutation.replaceWith(engine);
              }),
          "the stale generation must not regain mutation authority");
      assertEquals(1, actionRuns.get());
      assertEquals(committedGeneration, Lizzie.capturePrimaryEngineGeneration(engine));
    } finally {
      if (presentationLease != null) {
        presentationLease.close();
      }
      if (mutationWorker != null) {
        mutationWorker.join(2_000L);
      }
      Lizzie.setPrimaryEngine(previousPrimary);
    }
  }

  @Test
  void presentationOwnerCannotUpgradeItsLeaseThroughPrimaryMutationCapability() throws Exception {
    Leelaz previousPrimary = Lizzie.leelaz;
    Leelaz engine = new Leelaz("");
    Lizzie.EngineAuthorityPresentationLease presentationLease = null;
    try {
      Lizzie.setPrimaryEngine(engine);
      long expectedGeneration = Lizzie.capturePrimaryEngineGeneration(engine);
      presentationLease =
          Lizzie.claimEngineAuthorityPresentation(
              Lizzie.board, Lizzie.engineManager, engine, expectedGeneration);
      assertNotNull(presentationLease);
      AtomicInteger actionRuns = new AtomicInteger();

      assertThrows(
          IllegalStateException.class,
          () ->
              Lizzie.runIfPrimaryEngineWithMutation(
                  engine,
                  expectedGeneration,
                  mutation -> {
                    actionRuns.incrementAndGet();
                    mutation.replaceWith(engine);
                  }));
      assertEquals(0, actionRuns.get());
      assertEquals(expectedGeneration, Lizzie.capturePrimaryEngineGeneration(engine));
    } finally {
      if (presentationLease != null) {
        presentationLease.close();
      }
      Lizzie.setPrimaryEngine(previousPrimary);
    }
  }
}
