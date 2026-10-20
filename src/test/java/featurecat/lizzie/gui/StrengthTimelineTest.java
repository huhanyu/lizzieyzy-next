package featurecat.lizzie.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import featurecat.lizzie.AppLocale;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ResourceBundle;
import org.junit.jupiter.api.Test;

class StrengthTimelineTest {
  private static final ResourceBundle BUNDLE = AppLocale.ENGLISH.loadBundle();
  private static final StrengthStyle STYLE = new StrengthStyle(false, false, "Dialog", 12);

  private static StrengthTimeline timeline() throws Exception {
    StrengthTimeline timeline =
        new StrengthTimeline(StrengthFixtures.twoMoveReport(), "Black", "White", BUNDLE, STYLE);
    timeline.setSize(900, 320);
    paint(timeline);
    return timeline;
  }

  private static void paint(StrengthTimeline timeline) {
    BufferedImage image = new BufferedImage(900, 320, BufferedImage.TYPE_INT_ARGB);
    timeline.paint(image.createGraphics());
  }

  private static Point inLane(
      StrengthTimeline timeline, boolean black, boolean firstChoice, int move) {
    Rectangle lane = timeline.laneBounds(black, firstChoice);
    return new Point(timeline.moveX(move), lane.y + lane.height / 2);
  }

  private static String tooltipAt(StrengthTimeline timeline, Point point) {
    return timeline.getToolTipText(
        new MouseEvent(timeline, MouseEvent.MOUSE_MOVED, 0L, 0, point.x, point.y, 0, false));
  }

  @Test
  void firstChoiceMarkShowsTheMove() throws Exception {
    StrengthTimeline timeline = timeline();
    String tooltip = tooltipAt(timeline, inLane(timeline, true, true, 1));
    assertNotNull(tooltip);
    assertTrue(tooltip.contains("Black"));
    assertTrue(tooltip.contains("move 1"));
    assertTrue(tooltip.contains("1.2%"), "winrate loss");
    assertTrue(tooltip.contains("0.4"), "score loss");
  }

  @Test
  void firstChoiceAlsoCountsAsAGoodMove() throws Exception {
    StrengthTimeline timeline = timeline();
    String tooltip = tooltipAt(timeline, inLane(timeline, true, false, 1));
    assertNotNull(tooltip, "the first choice is marked in the good-move lane too");
    assertTrue(tooltip.contains(BUNDLE.getString("PlayerStrengthEstimate.match.firstChoice")));
  }

  @Test
  void goodMoveMarkShowsItsAiRankLossAndComplexity() throws Exception {
    StrengthTimeline timeline = timeline();
    String tooltip = tooltipAt(timeline, inLane(timeline, false, false, 2));
    assertNotNull(tooltip);
    assertTrue(tooltip.contains("White"));
    assertTrue(tooltip.contains("AI #2"));
    assertTrue(tooltip.contains("3.4%"));
    assertTrue(tooltip.contains("1.1"));
    assertTrue(tooltip.contains("35"), "complexity on the 0-100 scale");
  }

  @Test
  void unmarkedPlacesHaveNoTooltip() throws Exception {
    StrengthTimeline timeline = timeline();
    assertNull(tooltipAt(timeline, inLane(timeline, false, true, 2)), "not a first choice");
    Rectangle black = timeline.laneBounds(true, true);
    assertNull(tooltipAt(timeline, new Point(black.x + black.width / 2, black.y - 30)));
    assertNull(tooltipAt(timeline, new Point(5, 5)));
  }

  @Test
  void hoveringAMarkHighlightsItUntilThePointerLeaves() throws Exception {
    StrengthTimeline timeline = timeline();
    Point point = inLane(timeline, true, true, 1);
    timeline.dispatchEvent(
        new MouseEvent(timeline, MouseEvent.MOUSE_MOVED, 0L, 0, point.x, point.y, 0, false));
    assertNotNull(timeline.hovered());
    assertEquals(1, timeline.hovered().sample().moveNumber);
    paint(timeline);
    timeline.dispatchEvent(
        new MouseEvent(timeline, MouseEvent.MOUSE_EXITED, 0L, 0, -1, -1, 0, false));
    assertNull(timeline.hovered());
  }

  @Test
  void gameWithoutAnalysedMovesShowsNoMarks() throws Exception {
    StrengthTimeline timeline =
        new StrengthTimeline(StrengthFixtures.emptyReport(), "Black", "White", BUNDLE, STYLE);
    timeline.setSize(900, 320);
    paint(timeline);
    assertNull(timeline.markAt(new Point(450, 200)));
  }
}
