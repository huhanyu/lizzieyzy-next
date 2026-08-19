package featurecat.lizzie.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Dimension;
import java.awt.Rectangle;
import org.junit.jupiter.api.Test;

class HumanSlDialogBoundsTest {
  @Test
  void setupDialogFitsA1366By768DisplayAt150PercentScaling() {
    Rectangle logicalUsableBounds = new Rectangle(0, 0, 911, 512);

    Dimension target =
        HumanSlDialogBounds.fit(new Dimension(920, 500), null, logicalUsableBounds, 920, 500);
    Dimension minimum = HumanSlDialogBounds.minimum(target, 860, 390);

    assertEquals(new Dimension(871, 472), target);
    assertTrue(minimum.width <= target.width);
    assertTrue(minimum.height <= target.height);
  }

  @Test
  void reportDialogFitsA1080pDisplayAt200PercentScaling() {
    Rectangle logicalUsableBounds = new Rectangle(0, 0, 960, 520);

    Dimension target =
        HumanSlDialogBounds.fit(new Dimension(1040, 590), null, logicalUsableBounds, 1040, 590);
    Dimension minimum = HumanSlDialogBounds.minimum(target, 900, 520);

    assertEquals(new Dimension(920, 480), target);
    assertEquals(new Dimension(900, 480), minimum);
  }

  @Test
  void expandingTheSetupDialogNeverEscapesTheCurrentMonitor() {
    Rectangle logicalUsableBounds = new Rectangle(1920, 0, 800, 450);

    Dimension target =
        HumanSlDialogBounds.fit(
            new Dimension(940, 540), new Dimension(920, 410), logicalUsableBounds, 920, 500);

    assertEquals(new Dimension(760, 410), target);
    assertTrue(target.width <= logicalUsableBounds.width);
    assertTrue(target.height <= logicalUsableBounds.height);
  }
}
