package featurecat.lizzie.gui;

import featurecat.lizzie.Lizzie;
import featurecat.lizzie.analysis.Leelaz;
import java.util.Objects;
import java.util.function.BooleanSupplier;

/** Temporarily releases foreground analysis resources while another engine is starting. */
final class ForegroundAnalysisPause {
  static final class PauseAttempt {
    final ForegroundAnalysisPause pause;
    final Throwable failure;

    private PauseAttempt(ForegroundAnalysisPause pause, Throwable failure) {
      this.pause = pause;
      this.failure = failure;
    }
  }

  static final class RestoreLease {
    private final BooleanSupplier currentTarget;
    private final BooleanSupplier pondering;
    private final Runnable resume;
    private boolean restorePending;

    private RestoreLease(
        BooleanSupplier currentTarget,
        BooleanSupplier pondering,
        Runnable resume,
        boolean restorePending) {
      this.currentTarget = currentTarget;
      this.pondering = pondering;
      this.resume = resume;
      this.restorePending = restorePending;
    }

    static RestoreLease inactive() {
      return new RestoreLease(() -> false, () -> false, () -> {}, false);
    }

    synchronized void restore() {
      if (!restorePending) {
        return;
      }
      if (currentTarget.getAsBoolean() && !pondering.getAsBoolean()) {
        resume.run();
      }
      // Consume the lease only after every potentially-failing observation and the resume itself
      // have completed. A transient engine failure must leave the lease retryable.
      restorePending = false;
    }

    synchronized boolean isRestorePending() {
      return restorePending;
    }

    synchronized Throwable restoreBestEffort(int maximumAttempts) {
      Throwable restoreFailure = null;
      int attempts = Math.max(1, maximumAttempts);
      for (int attempt = 0; attempt < attempts && restorePending; attempt++) {
        try {
          restore();
        } catch (RuntimeException | Error failure) {
          if (restoreFailure == null) {
            restoreFailure = failure;
          } else if (restoreFailure != failure) {
            restoreFailure.addSuppressed(failure);
          }
        }
      }
      return restorePending ? restoreFailure : null;
    }
  }

  private final RestoreLease restoreLease;
  private boolean ownsRestoreLease;

  private ForegroundAnalysisPause(RestoreLease restoreLease, boolean ownsRestoreLease) {
    this.restoreLease = restoreLease;
    this.ownsRestoreLease = ownsRestoreLease;
  }

  static ForegroundAnalysisPause inactive() {
    return new ForegroundAnalysisPause(RestoreLease.inactive(), false);
  }

  static ForegroundAnalysisPause adopt(RestoreLease restoreLease) {
    RestoreLease lease = restoreLease == null ? RestoreLease.inactive() : restoreLease;
    return new ForegroundAnalysisPause(lease, lease.isRestorePending());
  }

  static ForegroundAnalysisPause pauseCurrent() {
    PauseAttempt attempt = pauseCurrentAttempt();
    if (attempt.failure != null) {
      Throwable restoreFailure = attempt.pause.restoreBestEffort(2);
      if (restoreFailure != null && restoreFailure != attempt.failure) {
        attempt.failure.addSuppressed(restoreFailure);
      }
      throwUnchecked(attempt.failure);
    }
    return attempt.pause;
  }

  static PauseAttempt pauseCurrentAttempt() {
    Leelaz engine = Lizzie.leelaz;
    if (engine == null) {
      return new PauseAttempt(inactive(), null);
    }
    return acquireAttempt(
        () -> Lizzie.leelaz == engine && engine.isStarted(),
        engine::isPondering,
        () -> {
          engine.notPondering();
          engine.nameCmd();
        },
        engine::ponder);
  }

  static ForegroundAnalysisPause acquire(
      BooleanSupplier currentTarget,
      BooleanSupplier pondering,
      Runnable pause,
      Runnable resume) {
    PauseAttempt attempt = acquireAttempt(currentTarget, pondering, pause, resume);
    if (attempt.failure != null) {
      Throwable restoreFailure = attempt.pause.restoreBestEffort(2);
      if (restoreFailure != null && restoreFailure != attempt.failure) {
        attempt.failure.addSuppressed(restoreFailure);
      }
      throwUnchecked(attempt.failure);
    }
    return attempt.pause;
  }

  static PauseAttempt acquireAttempt(
      BooleanSupplier currentTarget,
      BooleanSupplier pondering,
      Runnable pause,
      Runnable resume) {
    Objects.requireNonNull(currentTarget, "currentTarget");
    Objects.requireNonNull(pondering, "pondering");
    Objects.requireNonNull(pause, "pause");
    Objects.requireNonNull(resume, "resume");
    if (!pondering.getAsBoolean()) {
      return new PauseAttempt(inactive(), null);
    }
    ForegroundAnalysisPause acquired =
        new ForegroundAnalysisPause(
            new RestoreLease(currentTarget, pondering, resume, true), true);
    try {
      pause.run();
      return new PauseAttempt(acquired, null);
    } catch (RuntimeException | Error failure) {
      // Return the already-created lease to the caller; a partial pause must remain recoverable.
      return new PauseAttempt(acquired, failure);
    }
  }

  private static void throwUnchecked(Throwable failure) {
    if (failure instanceof Error) {
      throw (Error) failure;
    }
    throw (RuntimeException) failure;
  }

  synchronized RestoreLease transferRestoreResponsibility() {
    if (!ownsRestoreLease) {
      return RestoreLease.inactive();
    }
    ownsRestoreLease = false;
    return restoreLease;
  }

  synchronized void restore() {
    if (!ownsRestoreLease) {
      return;
    }
    restoreLease.restore();
    ownsRestoreLease = false;
  }

  /**
   * Attempts restoration more than once without consuming a failed lease.
   *
   * <p>Dialog cleanup must continue even when an engine throws while resuming. Returning the last
   * failure lets the caller keep the UI recoverable, while {@link RestoreLease#restore()} keeps the
   * lease pending until a complete attempt succeeds.
   */
  synchronized Throwable restoreBestEffort(int maximumAttempts) {
    if (!ownsRestoreLease) {
      return null;
    }
    Throwable restoreFailure = restoreLease.restoreBestEffort(maximumAttempts);
    if (!restoreLease.isRestorePending()) {
      ownsRestoreLease = false;
    }
    return restoreFailure;
  }

  synchronized boolean isRestorePending() {
    return ownsRestoreLease && restoreLease.isRestorePending();
  }
}
