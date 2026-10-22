package featurecat.lizzie.gui;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import org.junit.jupiter.api.Test;

class CoachBoardMarksTest {
  @Test
  void aNineteenBoardSplitsIntoCornersSidesAndCentre() {
    assertEquals(0, CoachBoardMarks.areaOf(3, 3, 19, 19), "D16 is the top left corner");
    assertEquals(2, CoachBoardMarks.areaOf(15, 3, 19, 19), "Q16");
    assertEquals(4, CoachBoardMarks.areaOf(9, 9, 19, 19), "tengen");
    assertEquals(5, CoachBoardMarks.areaOf(16, 9, 19, 19), "R10 is the right side");
    assertEquals(8, CoachBoardMarks.areaOf(18, 18, 19, 19));
    assertArrayEquals(new int[] {0, 0, 5, 5}, CoachBoardMarks.areaBounds(0, 19, 19));
    assertArrayEquals(new int[] {6, 6, 12, 12}, CoachBoardMarks.areaBounds(4, 19, 19));
    assertArrayEquals(new int[] {13, 6, 18, 12}, CoachBoardMarks.areaBounds(5, 19, 19));
  }

  @Test
  void smallBoardsSplitTheSameWay() {
    assertArrayEquals(new int[] {0, 0, 2, 2}, CoachBoardMarks.areaBounds(0, 9, 9));
    assertArrayEquals(new int[] {9, 9, 12, 12}, CoachBoardMarks.areaBounds(8, 13, 13));
    assertEquals(4, CoachBoardMarks.areaOf(4, 4, 9, 9));
  }

  @Test
  void everyChangeBumpsTheVersionAndClearingEmptiesIt() {
    CoachBoardMarks marks = new CoachBoardMarks();
    int start = marks.version();

    marks.showArea(new int[] {0, 0, 5, 5});
    marks.showPoint(3, 3);

    assertEquals(start + 2, marks.version());
    assertArrayEquals(new int[] {3, 3}, marks.point());
    marks.clear();
    assertTrue(marks.isEmpty());
    assertNull(marks.area());
    int cleared = marks.version();
    marks.clear();
    assertEquals(cleared, marks.version(), "clearing nothing changes nothing");
  }

  @Test
  void aPointIsDrawnWhereItsIntersectionIs() {
    BufferedImage image = new BufferedImage(200, 200, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = image.createGraphics();
    CoachBoardMarks marks = new CoachBoardMarks();
    marks.showPoint(2, 3);

    marks.paint(g, 20, 20, 20, 20);
    g.dispose();

    int ringLeftEdge = image.getRGB(20 + 2 * 20 - 9, 20 + 3 * 20);
    assertNotEquals(0, ringLeftEdge >>> 24, "the ring around (60, 80)");
    assertEquals(0, image.getRGB(190, 10) >>> 24, "nothing far away");
  }
}
