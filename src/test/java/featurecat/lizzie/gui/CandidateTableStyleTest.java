package featurecat.lizzie.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import featurecat.lizzie.Config;
import featurecat.lizzie.ConfigTestHelper;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;

class CandidateTableStyleTest {
  @Test
  void theTableIsDarkWithAlternatingRows() {
    assertTrue(brightness(CandidateTableStyle.SURFACE) < 150);
    assertNotEquals(CandidateTableStyle.rowBackground(0), CandidateTableStyle.rowBackground(1));
    assertTrue(brightness(CandidateTableStyle.TEXT) > 690, "light text on the dark table");
  }

  @Test
  void playedMoveIsColouredByHowFarItFallsShort() throws Exception {
    Config config = ConfigTestHelper.createForTests(Files.createTempDirectory("candidate-style"));
    config.useMorandiColors = false;

    Color best = CandidateTableStyle.playedMoveBackground(config, 0, 0, false);
    Color blunder = CandidateTableStyle.playedMoveBackground(config, -40, -20, false);
    Color blunderSelected = CandidateTableStyle.playedMoveBackground(config, -40, -20, true);

    assertTrue(best.getGreen() > best.getRed(), "a played best move is green: " + best);
    assertTrue(blunder.getRed() > blunder.getGreen(), "a blunder is not green: " + blunder);
    assertTrue(blunderSelected.getAlpha() > blunder.getAlpha(), "selection makes it stronger");
  }

  @Test
  void rankCellShowsTheTagInAPill() {
    Font font = new Font(Font.SANS_SERIF, Font.PLAIN, 13);
    BufferedImage ranked = paintRank("1(实战)", font);
    BufferedImage tagOnly = paintRank("\n实战\n", font);

    assertTrue(hasColour(ranked, new Color(0x2F, 0x6B, 0x5E)), "the tag pill is drawn");
    assertTrue(hasColour(tagOnly, new Color(0x2F, 0x6B, 0x5E)), "a played move off the list");
  }

  @Test
  void shareBarIsAsLongAsTheShare() {
    assertEquals(0, filledLength(0));
    assertTrue(Math.abs(filledLength(50) - 46) <= 1, "half of the 92-pixel track");
    assertEquals(92, filledLength(100));
  }

  private static BufferedImage paintRank(String cell, Font font) {
    BufferedImage image = new BufferedImage(90, 24, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = image.createGraphics();
    CandidateTableStyle.paintRankWithTag(g, cell, font, Color.WHITE, 90, 24);
    g.dispose();
    return image;
  }

  private static int filledLength(double percent) {
    BufferedImage image = new BufferedImage(100, 24, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = image.createGraphics();
    CandidateTableStyle.paintShareBar(g, percent, 100, 24);
    g.dispose();
    int fill = new Color(0x7F, 0xB5, 0xA8).getRGB();
    int length = 0;
    for (int x = 0; x < 100; x++) if (image.getRGB(x, 20) == fill) length++;
    return length;
  }

  private static boolean hasColour(BufferedImage image, Color colour) {
    for (int y = 0; y < image.getHeight(); y++) {
      for (int x = 0; x < image.getWidth(); x++) {
        if (image.getRGB(x, y) == colour.getRGB()) return true;
      }
    }
    return false;
  }

  private static int brightness(Color color) {
    return color.getRed() + color.getGreen() + color.getBlue();
  }
}
