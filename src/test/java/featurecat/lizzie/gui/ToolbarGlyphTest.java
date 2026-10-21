package featurecat.lizzie.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.awt.image.MultiResolutionImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.ImageIcon;
import org.junit.jupiter.api.Test;

class ToolbarGlyphTest {
  @Test
  void everyGlyphDrawsAtTheRequestedSizeOnLightAndDarkToolbars() {
    for (ToolbarGlyph glyph : ToolbarGlyph.values()) {
      for (boolean dark : new boolean[] {false, true}) {
        ImageIcon icon = glyph.icon(16, dark, false);
        assertEquals(16, icon.getIconWidth(), glyph.name());
        assertEquals(16, icon.getIconHeight(), glyph.name());
        int painted = countPainted(variant(icon, 16));
        assertTrue(painted > 12, () -> glyph + " draws only " + painted + " pixels");
      }
    }
  }

  @Test
  void iconsCarryHighDensityVariants() {
    ImageIcon icon = ToolbarGlyph.OPEN.icon(16, false, false);

    Image doubled = ((MultiResolutionImage) icon.getImage()).getResolutionVariant(32, 32);

    assertEquals(32, doubled.getWidth(null));
    assertEquals(32, doubled.getHeight(null));
  }

  @Test
  void darkToolbarsGetLightInkAndLightToolbarsDarkInk() {
    assertTrue(meanPaintedBrightness(variant(ToolbarGlyph.SAVE.icon(24, false, false), 24)) < 90);
    assertTrue(meanPaintedBrightness(variant(ToolbarGlyph.SAVE.icon(24, true, false), 24)) > 190);
  }

  @Test
  void selectedToolsGetABackingInsteadOfAnotherPicture() {
    BufferedImage plain = variant(ToolbarGlyph.MARK_SQUARE.icon(24, false, false), 24);
    BufferedImage selected = variant(ToolbarGlyph.MARK_SQUARE.icon(24, false, true), 24);

    assertEquals(0, alpha(plain, 2, 12), "outside the square stays clear");
    assertTrue(alpha(selected, 2, 12) > 0, "the selected tool is backed");
    assertEquals(24, selected.getWidth(), "selection does not change the icon's size");
  }

  @Test
  void stonesAndAreaMarksKeepTheirMeaningfulColours() {
    for (boolean dark : new boolean[] {false, true}) {
      Color black =
          new Color(variant(ToolbarGlyph.BLACK_STONE.icon(24, dark, false), 24).getRGB(12, 12));
      Color white =
          new Color(variant(ToolbarGlyph.WHITE_STONE.icon(24, dark, false), 24).getRGB(12, 12));
      assertTrue(
          black.getRed() + black.getGreen() + black.getBlue() < 300,
          "black stone is dark (lightened a little on a dark toolbar)");
      assertTrue(white.getRed() + white.getGreen() + white.getBlue() > 690, "white stone is white");
    }
    assertTrue(
        hasHue(variant(ToolbarGlyph.ALLOW_AREA.icon(24, true, false), 24), ToolbarGlyph.ALLOW));
    assertTrue(
        hasHue(variant(ToolbarGlyph.AVOID_AREA.icon(24, true, false), 24), ToolbarGlyph.AVOID));
  }

  @Test
  void everyPictureTheToolbarLoadsIsDrawn() throws Exception {
    String menu = Files.readString(Path.of("src/main/java/featurecat/lizzie/gui/Menu.java"));
    Matcher matcher = Pattern.compile("ToolbarIconCache\\.get\\(\\s*\"([^\"]+)\"").matcher(menu);
    List<String> paths = new ArrayList<>();
    while (matcher.find()) paths.add(matcher.group(1));

    assertFalse(paths.isEmpty());
    for (String path : paths) {
      assertTrue(ToolbarGlyph.BY_ASSET.containsKey(path), () -> path + " has no glyph");
    }
  }

  private static BufferedImage variant(ImageIcon icon, int pixels) {
    Image image = ((MultiResolutionImage) icon.getImage()).getResolutionVariant(pixels, pixels);
    BufferedImage copy = new BufferedImage(pixels, pixels, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = copy.createGraphics();
    g.drawImage(image, 0, 0, null);
    g.dispose();
    return copy;
  }

  private static int alpha(BufferedImage image, int x, int y) {
    return image.getRGB(x, y) >>> 24;
  }

  private static int countPainted(BufferedImage image) {
    int count = 0;
    for (int y = 0; y < image.getHeight(); y++) {
      for (int x = 0; x < image.getWidth(); x++) {
        if (alpha(image, x, y) > 128) count++;
      }
    }
    return count;
  }

  private static double meanPaintedBrightness(BufferedImage image) {
    long sum = 0;
    int count = 0;
    for (int y = 0; y < image.getHeight(); y++) {
      for (int x = 0; x < image.getWidth(); x++) {
        if (alpha(image, x, y) < 200) continue;
        Color c = new Color(image.getRGB(x, y));
        sum += (c.getRed() + c.getGreen() + c.getBlue()) / 3;
        count++;
      }
    }
    return count == 0 ? -1 : (double) sum / count;
  }

  private static boolean hasHue(BufferedImage image, Color expected) {
    for (int y = 0; y < image.getHeight(); y++) {
      for (int x = 0; x < image.getWidth(); x++) {
        if (alpha(image, x, y) < 230) continue;
        Color c = new Color(image.getRGB(x, y));
        if (Math.abs(c.getRed() - expected.getRed()) < 24
            && Math.abs(c.getGreen() - expected.getGreen()) < 24
            && Math.abs(c.getBlue() - expected.getBlue()) < 24) return true;
      }
    }
    return false;
  }
}
