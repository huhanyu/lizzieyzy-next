package featurecat.lizzie.fox;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class FoxProtocolStateTest {
  @Test void fragmentedAndCoalescedFramesAreAtomic() {
    FoxProtocol codec = new FoxProtocol();
    List<String> frames = new ArrayList<>();
    byte[] first = "$FASTATUS,0*0F\r\n".getBytes(StandardCharsets.US_ASCII);
    for (int i = 0; i < first.length - 1; i++) {
      codec.accept(new byte[] {first[i]}, 1, frames::add);
      assertTrue(frames.isEmpty());
    }
    codec.accept(new byte[] {first[first.length - 1]}, 1, frames::add);
    byte[] two = ("$FASTATUS,0*0F\r\n$FASTATUS,0*0F\r\n").getBytes(StandardCharsets.US_ASCII);
    codec.accept(two, two.length, frames::add);
    assertEquals(List.of("FASTATUS,0", "FASTATUS,0", "FASTATUS,0"), frames);
    byte[] bad = "$FASTATUS,0*00\r\n".getBytes(StandardCharsets.US_ASCII);
    assertThrows(IllegalArgumentException.class, () -> codec.accept(bad, bad.length, frames::add));
    assertEquals(3, frames.size());
  }

  @Test void waitsForAllInitializationAndPreservesUnnumberedPass() {
    FoxGameState state = new FoxGameState();
    state.connected();
    state.receive("FARULE,19,3600,30,3,650", 0);
    state.receive("FASTATUS,1,B", 0);
    state.receive("FAMOVE,1,B,0^0^0^N", 0);
    assertFalse(state.snapshot().searchable());
    state.receive("FATIMELEFT,3600,30,3", 0);
    assertTrue(state.snapshot().searchable());
    state.receive("FAMOVE,0,B,1^16^3^B", 1);
    state.receive("FAMOVE,1,B,2^3^15^W", 2);
    state.receive("FASKIP,0,B", 3);
    state.receive("FASKIP,0,B", 4);
    state.receive("FASTATUS,1,B,1^16^3^B,2^3^15^W", 5);
    state.receive("FAMOVE,0,B,2^3^15^W", 6);
    assertEquals(3, state.snapshot().actions().size());
    assertEquals(2, state.snapshot().placements().size());
    assertTrue(state.snapshot().actions().get(2).isPass());
    assertEquals('W', state.snapshot().toPlay());
    assertEquals("R16", state.snapshot().placements().get(0).vertex(19));
    state.receive("FAMOVE,1,B,3^15^15^W", 7);
    assertEquals(4, state.snapshot().actions().size());
    assertTrue(state.snapshot().searchable());
  }

  @Test void coldMidgameAndMissingActionsNeverBecomeCompleteHistory() {
    FoxGameState cold = new FoxGameState();
    cold.connected();
    cold.receive("FARULE,19,60,30,3,650", 0);
    cold.receive("FASTATUS,1,B,1^16^3^B", 0);
    cold.receive("FATIMELEFT,60,30,3", 0);
    cold.receive("FAMOVE,0,B,1^16^3^B", 0);
    assertFalse(cold.snapshot().searchable());
    assertTrue(cold.snapshot().actions().isEmpty());
    FoxGameState state = initialized();
    state.receive("FAMOVE,1,B,1^3^3^W", 0);
    assertFalse(state.snapshot().complete());
    assertThrows(IllegalArgumentException.class,
        () -> state.receive("FAMOVE,0,B,3^4^4^B", 0));
    assertEquals(1, state.snapshot().placements().size());
  }

  @Test void setupIsNotAnActionAndKomiUsesHundredths() {
    FoxGameState state = new FoxGameState();
    state.connected();
    state.receive("FARULE,9,60,10,2,50,0^2^2^B,0^6^6^B", 0);
    state.receive("FASTATUS,1,W", 0);
    state.receive("FATIMELEFT,60,10,2", 0);
    state.receive("FAMOVE,1,W,0^0^0^N", 0);
    assertTrue(state.snapshot().searchable());
    assertEquals(0.5, state.snapshot().rules().komi());
    assertEquals(2, state.snapshot().rules().setup().size());
    assertTrue(state.snapshot().actions().isEmpty());
    assertEquals('W', state.snapshot().toPlay());
  }

  @Test void disconnectRequiresNewGameRatherThanEmptyReconnectAssumption() {
    FoxGameState state = initialized();
    long connection = state.snapshot().connection();
    state.disconnected();
    assertFalse(state.snapshot().searchable());
    initialize(state);
    assertTrue(state.snapshot().connection() > connection);
    assertFalse(state.snapshot().searchable());
    state.receive("FASTATUS,0", 0);
    state.receive("FARULE,19,60,30,3,650", 0);
    state.receive("FASTATUS,1,B", 0);
    state.receive("FATIMELEFT,60,30,3", 0);
    state.receive("FAMOVE,1,B,0^0^0^N", 0);
    assertTrue(state.snapshot().searchable());
  }

  private static FoxGameState initialized() {
    FoxGameState state = new FoxGameState(); initialize(state); return state;
  }
  private static void initialize(FoxGameState state) {
    state.connected();
    state.receive("FARULE,19,60,30,3,650", 0);
    state.receive("FASTATUS,1,B", 0);
    state.receive("FATIMELEFT,60,30,3", 0);
    state.receive("FAMOVE,1,B,0^0^0^N", 0);
  }
}
