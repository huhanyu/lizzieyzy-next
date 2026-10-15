package featurecat.lizzie.update;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Application-owned update lifecycle. State and callbacks are confined to the EDT. */
final class UpdateCheckCoordinator {
  enum Origin { AUTOMATIC, MANUAL }
  enum DownloadPhase { NONE, DOWNLOADING, PAUSED, CANCELLED, FAILED, READY }

  record Download(DownloadPhase phase, ResumableDownloader.Progress progress, String error) {}

  interface DownloadService {
    Path prepare(UpdateCheckResult result, ResumableDownloader.Control control,
        ResumableDownloader.ProgressListener progress) throws Exception;
    void apply(UpdateCheckResult result, Path prepared) throws Exception;
  }

  record State(boolean checking, Origin origin, UpdateCheckSelection selection,
      UpdateCheckResult result, Download download) {
    State(boolean checking, Origin origin, UpdateCheckSelection selection, UpdateCheckResult result) {
      this(checking, origin, selection, result, new Download(DownloadPhase.NONE, null, null));
    }
    boolean downloading() {
      return download.phase() == DownloadPhase.DOWNLOADING || paused();
    }
    boolean paused() { return download.phase() == DownloadPhase.PAUSED; }
    boolean ready() { return download.phase() == DownloadPhase.READY; }
    boolean hasOffer() {
      return result != null && result.reason == UpdateCheckResult.Reason.OFFER;
    }
  }

  interface OfferHandoff {
    void openWindows(UpdateCheckSelection selection, UpdateCheckResult result);
    void openPackage(UpdateCheckSelection selection, UpdateCheckResult result);
  }

  interface Discovery {
    UpdateCheckResult check(UpdateCheckSelection selection);
  }

  interface Runner {
    void runBackground(Runnable work);
    void runOnEdt(Runnable work);
  }

  private final Discovery discovery;
  private final OfferHandoff handoff;
  private final Runner runner;
  private final Consumer<State> reminder;
  private final List<Consumer<State>> observers = new ArrayList<>();
  private State state = new State(false, null, null, null);
  private boolean startupConsumed;
  private boolean startupCaptured;
  private UpdateCheckSelection startupSelection;
  private boolean automaticCheck;
  private boolean inFlight;
  private boolean automaticInvalidated;
  private boolean presentingOffer;
  private final DownloadService downloads;
  private boolean automaticDownload;
  private boolean manualDownload;
  private boolean reminded;
  private ResumableDownloader.Control control;
  private Path prepared;

  UpdateCheckCoordinator(Discovery discovery, OfferHandoff handoff, Runner runner,
      Consumer<State> reminder) {
    this(discovery, handoff, runner, reminder, new UpdateDownloadService());
  }

  UpdateCheckCoordinator(Discovery discovery, OfferHandoff handoff, Runner runner,
      Consumer<State> reminder, DownloadService downloads) {
    this.downloads = downloads;
    this.discovery = discovery;
    this.handoff = handoff;
    this.runner = runner;
    this.reminder = reminder;
  }

  State state() { return state; }

  void subscribe(Consumer<State> observer) {
    observers.add(observer);
    observer.accept(state);
  }

  void unsubscribe(Consumer<State> observer) { observers.remove(observer); }

  /** Called once before exposing preference controls; subsequent policy changes only revoke. */
  void captureStartupPolicy(boolean enabled, boolean download, UpdateCheckSelection selection) {
    if (startupCaptured) return;
    startupCaptured = true;
    automaticCheck = enabled;
    startupSelection = enabled ? selection : null;
    automaticDownload = enabled && download;
  }

  /** This run's startup authority; re-enabling a revoked or new option only applies next launch. */
  boolean automaticCheckActive() { return automaticCheck; }

  boolean automaticDownloadActive() { return automaticDownload; }

  void applicationReady() {
    if (startupConsumed) return;
    startupConsumed = true;
    if (startupSelection != null) start(Origin.AUTOMATIC, startupSelection);
    startupSelection = null;
  }

  boolean checkManually(UpdateCheckSelection selection) {
    return start(Origin.MANUAL, selection);
  }

  void invalidateAutomatic() {
    startupSelection = null;
    if (state.origin() != Origin.AUTOMATIC || manualDownload) return;
    if (control != null) control.cancel();
    prepared = null;
    automaticInvalidated = true;
    publish(new State(inFlight, Origin.AUTOMATIC, null, null));
  }

  void disableAutomaticCheck() {
    automaticCheck = false;
    automaticDownload = false;
    invalidateAutomatic();
  }

  void disableAutomaticDownload() {
    automaticDownload = false;
    if (state.origin() == Origin.AUTOMATIC && !manualDownload && state.downloading()) {
      cancelDownload();
    }
  }

  private boolean start(Origin origin, UpdateCheckSelection selection) {
    if (inFlight || presentingOffer) return false;
    inFlight = true;
    automaticInvalidated = false;
    prepared = null;
    control = null;
    manualDownload = false;
    publish(new State(true, origin, selection, null));
    runner.runBackground(() -> {
      UpdateCheckResult result;
      try {
        result = discovery.check(selection);
      } catch (RuntimeException e) {
        result = UpdateCheckResult.failure(UpdateCheckResult.FailureKind.UNEXPECTED);
      }
      UpdateCheckResult completed = result;
      runner.runOnEdt(() -> complete(origin, selection, completed));
    });
    return true;
  }

  private void complete(Origin origin, UpdateCheckSelection selection, UpdateCheckResult result) {
    inFlight = false;
    if (origin == Origin.AUTOMATIC && automaticInvalidated) {
      publish(new State(false, null, null, null));
      return;
    }
    if (result == null) result = UpdateCheckResult.failure(UpdateCheckResult.FailureKind.UNEXPECTED);
    if (result.reason == UpdateCheckResult.Reason.OFFER
        && result.windowsPlan == null && result.packagePlan == null) {
      result = UpdateCheckResult.failure(UpdateCheckResult.FailureKind.ADAPTER);
    }
    publish(new State(false, origin, selection, result));
    if (!state.hasOffer()) return;
    if (origin == Origin.AUTOMATIC) {
      if (automaticDownload) startDownload(result, false);
      else remind();
    } else openOffer();
  }

  /** Viewing an offer never grants download or installation authority. */
  void openOffer() {
    if (!state.hasOffer() || presentingOffer || inFlight) return;
    State offered = state;
    presentingOffer = true;
    try {
      if (offered.result().windowsPlan != null) {
        handoff.openWindows(offered.selection(), offered.result());
      } else {
        handoff.openPackage(offered.selection(), offered.result());
      }
    } finally {
      presentingOffer = false;
    }
  }

  /** A manual confirmation grants completion authority; merely viewing never calls this. */
  void startDownload(UpdateCheckResult expected, boolean completeWhenReady) {
    if (expected != state.result() || inFlight || !state.hasOffer()) return;
    if (state.ready()) {
      if (completeWhenReady) {
        manualDownload = true;
        applyPrepared();
      }
      return;
    }
    State offered = state;
    manualDownload = completeWhenReady;
    automaticInvalidated = false;
    prepared = null;
    ResumableDownloader.Control task = new ResumableDownloader.Control();
    control = task;
    inFlight = true;
    publishDownload(DownloadPhase.DOWNLOADING, null, null);
    runner.runBackground(() -> {
      Path path = null;
      Exception failure = null;
      try {
        task.checkCancelled();
        path = downloads.prepare(offered.result(), task, progress -> runner.runOnEdt(() -> {
          if (control == task && !task.isCancelled() && !automaticInvalidated && inFlight) {
            publishDownload(task.isPaused() ? DownloadPhase.PAUSED : DownloadPhase.DOWNLOADING,
                progress, null);
          }
        }));
      } catch (Exception e) { failure = e; }
      Path finished = path;
      Exception error = failure;
      runner.runOnEdt(() -> {
        if (control != task) return;
        inFlight = false;
        if (automaticInvalidated) {
          publish(new State(false, null, null, null));
          return;
        }
        if (task.isCancelled()) {
          publishDownload(DownloadPhase.CANCELLED, state.download().progress(), null);
        } else if (error != null || finished == null) {
          publishDownload(DownloadPhase.FAILED, state.download().progress(),
              error == null ? "" : UpdateText.userFacingError(error));
        } else {
          prepared = finished;
          publishDownload(DownloadPhase.READY, state.download().progress(), null);
          if (manualDownload) applyPrepared();
          else remind();
        }
      });
    });
  }

  void retryDownload() {
    startDownload(state.result(), manualDownload);
  }

  void cancelDownload() {
    if (!state.downloading() || control == null) return;
    control.cancel();
    prepared = null;
    publishDownload(DownloadPhase.CANCELLED, state.download().progress(), null);
  }

  void togglePause() {
    if (!state.downloading() || control == null) return;
    if (control.isPaused()) control.resume(); else control.pause();
    publishDownload(control.isPaused() ? DownloadPhase.PAUSED : DownloadPhase.DOWNLOADING,
        state.download().progress(), null);
  }

  boolean busy() { return inFlight; }

  private void applyPrepared() {
    if (!state.ready() || prepared == null || automaticInvalidated || inFlight) return;
    try {
      downloads.apply(state.result(), prepared);
    } catch (Exception e) {
      publishDownload(DownloadPhase.READY, state.download().progress(), UpdateText.userFacingError(e));
    }
  }

  private void remind() {
    if (reminded) return;
    reminded = true;
    reminder.accept(state);
  }

  private void publishDownload(DownloadPhase phase, ResumableDownloader.Progress progress, String error) {
    publish(new State(false, state.origin(), state.selection(), state.result(),
        new Download(phase, progress, error)));
  }

  private void publish(State next) {
    state = next;
    for (Consumer<State> observer : List.copyOf(observers)) observer.accept(next);
  }
}
