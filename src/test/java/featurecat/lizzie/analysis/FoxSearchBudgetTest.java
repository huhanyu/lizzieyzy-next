package featurecat.lizzie.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class FoxSearchBudgetTest {
  @Test
  void reservesBatchAndDeliveryTimeForShortTurns() {
    assertEquals(2.5, FoxEngineSession.ownTurnSearchLimit(3, 60), 1e-9);
    assertEquals(2.3, FoxEngineSession.ownTurnSearchLimit(3, 2.8), 1e-9);
  }

  @Test
  void keepsProportionalMarginForLongTurnsAndHonorsBothCaps() {
    assertEquals(28.5, FoxEngineSession.ownTurnSearchLimit(30, 60), 1e-9);
    assertEquals(19, FoxEngineSession.ownTurnSearchLimit(30, 20), 1e-9);
  }

  @Test
  void tinyRemainingBudgetsStillSearchWithoutExtendingTheDeadline() {
    for (double remaining : new double[] {0.001, 0.1, 0.5, 1}) {
      double limit = FoxEngineSession.ownTurnSearchLimit(3, remaining);
      assertEquals(remaining * 0.5, limit, 1e-9);
      assertTrue(limit > 0 && limit < remaining);
    }
  }
}
