package featurecat.lizzie.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.MultiResolutionImage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ToolbarIconCacheTest {
  @AfterEach
  void clearCache() {
    ToolbarIconCache.clearForTests();
  }

  @Test
  void reusesSameResourceSizeAndStyle() {
    javax.swing.ImageIcon first = ToolbarIconCache.get("/assets/open.png", 24, false);
    javax.swing.ImageIcon second = ToolbarIconCache.get("/assets/open.png", 24, false);

    assertSame(first, second);
    assertEquals(24, first.getIconWidth());
    assertEquals(24, first.getIconHeight());
  }

  @Test
  void sizeAndStyleHaveSeparateEntries() {
    javax.swing.ImageIcon base = ToolbarIconCache.get("/assets/open.png", 24, false);

    assertNotSame(base, ToolbarIconCache.get("/assets/open.png", 28, false));
    assertNotSame(base, ToolbarIconCache.get("/assets/open.png", 24, true));
  }

  @Test
  void picturesWithAGlyphAreDrawnAndOthersStillScaled() {
    javax.swing.ImageIcon drawn = ToolbarIconCache.get("/assets/open.png", 16, false);
    javax.swing.ImageIcon scaled = ToolbarIconCache.get("/assets/playing.png", 16, false);

    assertTrue(drawn.getImage() instanceof MultiResolutionImage, "open is drawn");
    assertFalse(scaled.getImage() instanceof MultiResolutionImage, "playing has no glyph");
    assertEquals(16, scaled.getIconWidth());
  }
}
