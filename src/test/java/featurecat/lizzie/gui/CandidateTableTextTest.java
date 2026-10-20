package featurecat.lizzie.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class CandidateTableTextTest {
  @Test
  void playedMoveShowsItsValueThenTheGapToTheBestMove() {
    assertEquals("99.2 ↓0.1", CandidateTableText.valueWithDifference(99.2, -0.1));
    assertEquals("7.3 ↑0.4", CandidateTableText.valueWithDifference(7.3, 0.42));
    assertEquals("7.3", CandidateTableText.valueWithDifference(7.3, -0.04));
  }
}
