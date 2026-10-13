package featurecat.lizzie.update;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Application-owned discovery. All state and callbacks are confined to the EDT. */
final class UpdateCheckCoordinator {
  enum Origin { AUTOMATIC, MANUAL }

  record State(boolean checking, Origin origin, UpdateCheckSelection selection,
      UpdateCheckResult result) {
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
  private boolean inFlight;
  private boolean automaticInvalidated;
  private boolean presentingOffer;

  UpdateCheckCoordinator(Discovery discovery, OfferHandoff handoff, Runner runner,
      Consumer<State> reminder) {
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

  void applicationReady(boolean enabled, UpdateCheckSelection selection) {
    if (startupConsumed) return;
    startupConsumed = true;
    if (enabled) start(Origin.AUTOMATIC, selection);
  }

  boolean checkManually(UpdateCheckSelection selection) {
    return start(Origin.MANUAL, selection);
  }

  void invalidateAutomatic() {
    if (state.origin() != Origin.AUTOMATIC) return;
    automaticInvalidated = true;
    publish(new State(inFlight, Origin.AUTOMATIC, null, null));
  }

  private boolean start(Origin origin, UpdateCheckSelection selection) {
    if (inFlight || presentingOffer) return false;
    inFlight = true;
    automaticInvalidated = false;
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
    if (origin == Origin.AUTOMATIC) reminder.accept(state);
    else openOffer();
  }

  /** Explicit user action; inspecting an automatic result never changes its origin. */
  void openOffer() {
    if (!state.hasOffer() || presentingOffer) return;
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

  private void publish(State next) {
    state = next;
    for (Consumer<State> observer : List.copyOf(observers)) observer.accept(next);
  }
}
