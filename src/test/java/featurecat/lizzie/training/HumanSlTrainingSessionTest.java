package featurecat.lizzie.training;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class HumanSlTrainingSessionTest {
  @Test
  void listenersHearEachChangeOnce() {
    HumanSlTrainingSession session = new HumanSlTrainingSession();
    List<HumanSlTrainingSession.State> heard = new ArrayList<>();
    session.addListener(heard::add);

    session.setState(HumanSlTrainingSession.State.PREPARING);
    session.setState(HumanSlTrainingSession.State.PREPARING);
    session.setState(HumanSlTrainingSession.State.PLAYING);
    session.setState(null);

    assertEquals(
        List.of(HumanSlTrainingSession.State.PREPARING, HumanSlTrainingSession.State.PLAYING),
        heard);
    assertEquals(HumanSlTrainingSession.State.PLAYING, session.state());
  }

  @Test
  void aNewSessionIsIdle() {
    assertEquals(HumanSlTrainingSession.State.IDLE, new HumanSlTrainingSession().state());
  }
}
