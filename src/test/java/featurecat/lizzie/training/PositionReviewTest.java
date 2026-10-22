package featurecat.lizzie.training;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PositionReviewTest {
  private static PositionReview review() {
    return new PositionReview(
        1.2,
        10.0,
        List.of(
            new PositionReview.Candidate("Q16", 300, 1.5),
            new PositionReview.Candidate("D4", 60, 0.5),
            new PositionReview.Candidate("C3", 3, -9.0)),
        Map.of("D4", 0.5, "Q16", 0.2, "C3", 0.01, "pass", 0.0));
  }

  @Test
  void kataGosChoiceIsTheFirstCandidate() {
    assertEquals("Q16", review().best().orElseThrow().move());
    assertTrue(new PositionReview(0, 1, List.of(), Map.of()).best().isEmpty(), "nothing searched");
  }

  @Test
  void aMoveTheSearchLookedAtHasItsOwnLead() {
    assertEquals(1.5, review().leadAfter("Q16"));
    assertEquals(0.5, review().leadAfter("d4"));
  }

  @Test
  void aMoveTheSearchHardlySawNeedsThePositionAfterIt() {
    assertTrue(Double.isNaN(review().leadAfter("C3")), "3 visits is noise");
    assertTrue(Double.isNaN(review().leadAfter("A1")), "not searched at all");
  }

  @Test
  void sharesComeFromTheHumanPolicy() {
    assertEquals(0.5, review().humanShare("D4"));
    assertEquals(0.0, review().humanShare("A1"), "a move the policy left out");
    assertEquals(0.0, review().humanShare("PASS"));
    assertTrue(Double.isNaN(new PositionReview(0, 1, List.of(), Map.of()).humanShare("D4")));
  }

  @Test
  void theCommonMoveIsTheOnePlayersChooseMost() {
    assertEquals("D4", review().commonMove());
    assertEquals(
        "D4",
        new PositionReview(0, 1, List.of(), Map.of("Q16", 0.4, "D4", 0.4)).commonMove(),
        "ties break by name, not by map order");
    assertNull(new PositionReview(0, 1, List.of(), Map.of()).commonMove());
  }
}
