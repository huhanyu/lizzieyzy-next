package featurecat.lizzie.training;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RankScaleTest {
  @Test
  void twentyKyuToNineDanIsOneScaleWeakestFirst() {
    assertEquals(0, RankScale.index(20, false));
    assertEquals(19, RankScale.index(1, false));
    assertEquals(20, RankScale.index(1, true));
    assertEquals(22, RankScale.index(3, true));
    assertEquals(28, RankScale.index(9, true));
    assertEquals(29, RankScale.STOPS);
  }

  @Test
  void everyStopMapsBackToItsRank() {
    for (int stop = 0; stop < RankScale.STOPS; stop++) {
      assertEquals(
          stop, RankScale.index(RankScale.rank(stop), RankScale.dan(stop)), "stop " + stop);
    }
    assertEquals(20, RankScale.rank(0));
    assertFalse(RankScale.dan(19));
    assertTrue(RankScale.dan(20));
    assertEquals(9, RankScale.rank(28));
  }

  @Test
  void outOfRangeValuesStayOnTheScale() {
    assertEquals(0, RankScale.index(99, false));
    assertEquals(28, RankScale.index(99, true));
    assertEquals(9, RankScale.rank(99));
    assertEquals(20, RankScale.rank(-5));
  }
}
