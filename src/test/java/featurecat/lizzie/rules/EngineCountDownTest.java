package featurecat.lizzie.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import featurecat.lizzie.analysis.Leelaz;
import org.junit.jupiter.api.Test;

class EngineCountDownTest {
  @Test
  void rawAdvancedModesProduceDeterministicPerMoveCommands() throws Exception {
    Leelaz engine = new Leelaz("");

    EngineCountDown canadian = clock("time_settings 0 30 3", engine, true);
    assertEquals("time_left B 30 2", canadian.claimTimeLeftCommand());
    assertEquals("time_left B 30 1", canadian.claimTimeLeftCommand());
    assertEquals("time_left B 30 3", canadian.claimTimeLeftCommand());

    EngineCountDown kataCanadian =
        clock("kata-time_settings canadian 0 12.5 2", engine, false);
    assertEquals("time_left W 12.50 1", kataCanadian.claimTimeLeftCommand());
    assertEquals("time_left W 12.50 2", kataCanadian.claimTimeLeftCommand());

    EngineCountDown byoyomi = clock("kata-time_settings byoyomi 0 0.01 2", engine, true);
    byoyomi.countDownCentiseconds();
    byoyomi.countDownCentiseconds();
    assertEquals("time_left B 0.01 1", byoyomi.claimTimeLeftCommand());

    EngineCountDown fischer = clock("kata-time_settings fischer 10 2.5", engine, false);
    assertEquals("time_left W 12.50 0", fischer.claimTimeLeftCommand());
    assertEquals("time_left W 15.00 0", fischer.claimTimeLeftCommand());

    EngineCountDown capped =
        clock("kata-time_settings fischer-capped 10 5 12 11", engine, true);
    assertEquals("time_left B 11.00 0", capped.claimTimeLeftCommand());
    assertEquals("time_left B 11.00 0", capped.claimTimeLeftCommand());

    EngineCountDown absolute =
        clock("kata-time_settings absolute 9.75", engine, false);
    assertEquals("time_left W 9.75 0", absolute.claimTimeLeftCommand());
  }

  @Test
  void byoyomiTicksNeverWriteToTheEngine() throws Exception {
    RecordingLeelaz engine = new RecordingLeelaz();
    EngineCountDown byoyomi = clock("kata-time_settings byoyomi 0 0.01 2", engine, true);

    for (int tick = 0; tick < 8; tick++) {
      byoyomi.countDownCentiseconds();
    }

    assertEquals(0, engine.commands);
    assertEquals("time_left B 0.01 0", byoyomi.claimTimeLeftCommand());
    assertEquals(0, engine.commands);
  }

  private static EngineCountDown clock(String settings, Leelaz engine, boolean black) {
    EngineCountDown clock = new EngineCountDown();
    assertTrue(clock.setEngineCountDown(settings, engine));
    clock.initialize(black);
    return clock;
  }

  private static final class RecordingLeelaz extends Leelaz {
    private int commands;

    private RecordingLeelaz() throws Exception {
      super("");
    }

    @Override
    public void sendCommand(String command) {
      commands++;
    }
  }
}
