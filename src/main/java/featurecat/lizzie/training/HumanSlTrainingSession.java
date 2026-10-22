package featurecat.lizzie.training;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Small observable state machine shared by the toolbar, setup dialog and coach panel. */
public final class HumanSlTrainingSession {
  public enum State {
    IDLE,
    PREPARING,
    PLAYING,
    FINISHED
  }

  public interface Listener {
    void onTrainingStateChanged(State state);
  }

  private final List<Listener> listeners = new CopyOnWriteArrayList<Listener>();
  private volatile State state = State.IDLE;

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
}
