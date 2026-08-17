package featurecat.lizzie.training;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/** Small observable state machine shared by the toolbar, setup dialog, game bar and report. */
public final class HumanSlTrainingSession {
  public enum State {
    IDLE,
    PREPARING,
    PLAYING,
    REVIEWING,
    REPORT_READY,
    FINISHED
  }

  public interface Listener {
    void onTrainingStateChanged(State state);
  }

  private final List<Listener> listeners = new CopyOnWriteArrayList<Listener>();
  private final List<HumanMoveDecision> decisions = new ArrayList<HumanMoveDecision>();
  private final Set<Integer> deepenedMoveNumbers = new HashSet<Integer>();
  private volatile State state = State.IDLE;
  private volatile TrainingSessionReport report;

  public State state() {
    return state;
  }

  public void setState(State value) {
    if (value == null || value == state) {
      return;
    }
    state = value;
    for (Listener listener : listeners) {
      listener.onTrainingStateChanged(value);
    }
  }

  public void addListener(Listener listener) {
    if (listener != null) {
      listeners.add(listener);
    }
  }

  public synchronized void addDecision(HumanMoveDecision decision) {
    upsertDecision(decision, false);
  }

  /** Replaces an earlier quick result for the same move with a more accurate result. */
  public synchronized void upsertDecision(HumanMoveDecision decision, boolean deepened) {
    if (decision == null) {
      return;
    }
    for (int index = 0; index < decisions.size(); index++) {
      if (decisions.get(index).moveNumber == decision.moveNumber) {
        decisions.set(index, decision);
        if (deepened) {
          deepenedMoveNumbers.add(decision.moveNumber);
        }
        return;
      }
    }
    decisions.add(decision);
    if (deepened) {
      deepenedMoveNumbers.add(decision.moveNumber);
    }
  }

  public synchronized List<HumanMoveDecision> decisions() {
    return new ArrayList<HumanMoveDecision>(decisions);
  }

  public synchronized void removeDecision(int moveNumber) {
    decisions.removeIf(decision -> decision.moveNumber == moveNumber);
    deepenedMoveNumbers.remove(moveNumber);
  }

  public synchronized TrainingSessionReport buildReport() {
    report = new TrainingSessionReport(decisions, deepenedMoveNumbers);
    return report;
  }

  public TrainingSessionReport report() {
    return report;
  }
}
