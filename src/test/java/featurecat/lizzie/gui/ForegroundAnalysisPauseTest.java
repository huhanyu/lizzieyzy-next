package featurecat.lizzie.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ForegroundAnalysisPauseTest {
  @Test
  void inactiveAnalysisIsNotPausedOrResumed() {
    AtomicInteger pauses = new AtomicInteger();
    AtomicInteger resumes = new AtomicInteger();

    ForegroundAnalysisPause lease =
        ForegroundAnalysisPause.acquire(
            () -> true, () -> false, pauses::incrementAndGet, resumes::incrementAndGet);

    assertFalse(lease.isRestorePending());
    lease.restore();
    ForegroundAnalysisPause.RestoreLease transferred = lease.transferRestoreResponsibility();
    assertFalse(transferred.isRestorePending());
    transferred.restore();
    assertEquals(0, pauses.get());
    assertEquals(0, resumes.get());
  }

  @Test
  void failedPreparationRestoresTheSameForegroundAnalysis() {
    AtomicBoolean pondering = new AtomicBoolean(true);
    AtomicInteger pauses = new AtomicInteger();
    AtomicInteger resumes = new AtomicInteger();

    ForegroundAnalysisPause lease =
        ForegroundAnalysisPause.acquire(
            () -> true,
            pondering::get,
            () -> {
              pauses.incrementAndGet();
              pondering.set(false);
            },
            () -> {
              resumes.incrementAndGet();
              pondering.set(true);
            });

    assertTrue(lease.isRestorePending());
    assertEquals(1, pauses.get());
    assertFalse(pondering.get());
    lease.restore();
    lease.restore();
    assertTrue(pondering.get());
    assertEquals(1, resumes.get());
  }

  @Test
  void successfulPreparationTransfersRestoreResponsibilityToTheGame() {
    AtomicBoolean pondering = new AtomicBoolean(true);
    AtomicInteger resumes = new AtomicInteger();

    ForegroundAnalysisPause lease =
        ForegroundAnalysisPause.acquire(
            () -> true,
            pondering::get,
            () -> pondering.set(false),
            () -> {
              resumes.incrementAndGet();
              pondering.set(true);
            });

    ForegroundAnalysisPause.RestoreLease transferred =
        lease.transferRestoreResponsibility();
    assertTrue(transferred.isRestorePending());
    assertFalse(lease.transferRestoreResponsibility().isRestorePending());
    lease.restore();
    transferred.restore();
    transferred.restore();
    assertTrue(pondering.get());
    assertEquals(1, resumes.get());
  }

  @Test
  void staleEngineIsNeverRestarted() {
    AtomicBoolean pondering = new AtomicBoolean(true);
    AtomicInteger resumes = new AtomicInteger();

    ForegroundAnalysisPause lease =
        ForegroundAnalysisPause.acquire(
            () -> false,
            pondering::get,
            () -> pondering.set(false),
            resumes::incrementAndGet);

    lease.restore();
    assertFalse(pondering.get());
    assertEquals(0, resumes.get());
  }

  @Test
  void transientResumeFailureLeavesLeasePendingForRetry() {
    AtomicBoolean pondering = new AtomicBoolean(true);
    AtomicInteger resumes = new AtomicInteger();
    ForegroundAnalysisPause pause =
        ForegroundAnalysisPause.acquire(
            () -> true,
            pondering::get,
            () -> pondering.set(false),
            () -> {
              if (resumes.incrementAndGet() == 1) {
                throw new IllegalStateException("transient resume failure");
              }
              pondering.set(true);
            });

    assertThrows(IllegalStateException.class, pause::restore);
    assertTrue(pause.isRestorePending());
    assertFalse(pondering.get());

    pause.restore();

    assertFalse(pause.isRestorePending());
    assertTrue(pondering.get());
    assertEquals(2, resumes.get());
  }

  @Test
  void bestEffortRestoreRetriesATransientFailureAndConsumesTheLeaseOnce() {
    AtomicBoolean pondering = new AtomicBoolean(true);
    AtomicInteger resumes = new AtomicInteger();
    ForegroundAnalysisPause pause =
        ForegroundAnalysisPause.acquire(
            () -> true,
            pondering::get,
            () -> pondering.set(false),
            () -> {
              if (resumes.incrementAndGet() == 1) {
                throw new IllegalStateException("transient resume failure");
              }
              pondering.set(true);
            });

    assertNull(pause.restoreBestEffort(2));
    assertFalse(pause.isRestorePending());
    assertTrue(pondering.get());
    assertEquals(2, resumes.get());

    assertNull(pause.restoreBestEffort(2));
    assertEquals(2, resumes.get(), "a successful lease must never resume twice");
  }

  @Test
  void persistentBestEffortFailureKeepsTheSameLeaseRetryable() {
    AtomicBoolean pondering = new AtomicBoolean(true);
    AtomicBoolean allowResume = new AtomicBoolean();
    AtomicInteger resumes = new AtomicInteger();
    ForegroundAnalysisPause pause =
        ForegroundAnalysisPause.acquire(
            () -> true,
            pondering::get,
            () -> pondering.set(false),
            () -> {
              resumes.incrementAndGet();
              if (!allowResume.get()) {
                throw new IllegalStateException("persistent resume failure");
              }
              pondering.set(true);
            });

    assertNotNull(pause.restoreBestEffort(2));
    assertTrue(pause.isRestorePending());
    assertEquals(2, resumes.get());

    allowResume.set(true);
    assertNull(pause.restoreBestEffort(2));
    assertFalse(pause.isRestorePending());
    assertTrue(pondering.get());
    assertEquals(3, resumes.get());
  }

  @Test
  void partialPauseFailureReturnsTheSameRetryableRestoreLease() {
    AtomicBoolean pondering = new AtomicBoolean(true);
    AtomicInteger resumes = new AtomicInteger();

    ForegroundAnalysisPause.PauseAttempt attempt =
        ForegroundAnalysisPause.acquireAttempt(
            () -> true,
            pondering::get,
            () -> {
              pondering.set(false);
              throw new AssertionError("pause failed after stopping ponder");
            },
            () -> {
              resumes.incrementAndGet();
              pondering.set(true);
            });

    assertNotNull(attempt.failure);
    assertTrue(attempt.pause.isRestorePending());
    assertFalse(pondering.get());

    assertNull(attempt.pause.restoreBestEffort(2));
    assertFalse(attempt.pause.isRestorePending());
    assertTrue(pondering.get());
    assertEquals(1, resumes.get());
  }
}
