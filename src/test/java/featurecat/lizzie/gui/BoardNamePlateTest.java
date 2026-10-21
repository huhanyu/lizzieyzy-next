package featurecat.lizzie.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import org.junit.jupiter.api.Test;

class BoardNamePlateTest {
  @Test
  void namesAreDarkTypeOnALightPlate() {
    BufferedImage image = new BufferedImage(300, 60, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = image.createGraphics();
    BoardNamePlate.paintPlate(g, 10, 10, 290, 50);
    BoardNamePlate.paintName(g, new Font(Font.SANS_SERIF, Font.BOLD, 24), "Shusaku", 40, 40);
    g.dispose();

    Color plate = new Color(image.getRGB(20, 30), true);
    assertTrue(brightness(plate) > 600, "the plate is light: " + plate);
    int darkest = 765;
    for (int x = 40; x < 140; x++) {
      for (int y = 18; y < 42; y++)
        darkest = Math.min(darkest, brightness(new Color(image.getRGB(x, y))));
    }
    assertTrue(darkest < 150, "the name is dark type");
  }

  @Test
  void theSideToMoveHasItsStoneOnTop() {
    assertEquals(Color.BLACK.getRGB(), overlapPixel(true), "black to move: black stone on top");
    assertEquals(Color.WHITE.getRGB(), overlapPixel(false), "white to move: white stone on top");
  }

  private static int overlapPixel(boolean blackOnTop) {
    BufferedImage image = new BufferedImage(60, 30, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = image.createGraphics();
    BoardNamePlate.paintStones(g, 10, 22, 5, 20, blackOnTop);
    g.dispose();
    return image.getRGB(26, 15);
  }

  private static int brightness(Color color) {
    return color.getRed() + color.getGreen() + color.getBlue();
  }
}
