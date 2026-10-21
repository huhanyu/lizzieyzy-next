package featurecat.lizzie.gui;

import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import javax.imageio.ImageIO;
import javax.swing.ImageIcon;

/**
 * Loads toolbar icons once per size and visual style: drawn {@link ToolbarGlyph}s for the pictures
 * they replace, scaled resources for the rest.
 */
final class ToolbarIconCache {
  private static final Map<IconKey, ImageIcon> CACHE = new HashMap<>();

  private ToolbarIconCache() {}

  static synchronized ImageIcon get(String resourcePath, int size, boolean brighten) {
    IconKey key = new IconKey(resourcePath, size, brighten);
    ImageIcon cached = CACHE.get(key);
    if (cached != null) {
      return cached;
    }

    ImageIcon loaded = load(resourcePath, size, brighten);
    CACHE.put(key, loaded);
    return loaded;
  }

  private static ImageIcon load(String resourcePath, int size, boolean brighten) {
    ToolbarGlyph.Variant glyph = ToolbarGlyph.BY_ASSET.get(resourcePath);
    if (glyph != null) {
      // Drawn rather than scaled, in the toolbar's own colours; brighten means a dark toolbar.
      return glyph.glyph().icon(size, brighten, glyph.selected());
    }
    try (InputStream stream = ToolbarIconCache.class.getResourceAsStream(resourcePath)) {
      if (stream == null) {
        return new ImageIcon();
      }
      BufferedImage source = ImageIO.read(stream);
      if (source == null) {
        return new ImageIcon();
      }
      Image image = source.getScaledInstance(size, size, Image.SCALE_SMOOTH);
      if (brighten) {
        image = AppleStyleSupport.brightenIcon(image, size);
      }
      return new ImageIcon(image);
    } catch (IOException | RuntimeException e) {
      System.err.println("Unable to load toolbar icon " + resourcePath + ": " + e.getMessage());
      return new ImageIcon();
    }
  }

  static synchronized void clearForTests() {
    CACHE.clear();
  }

  private record IconKey(String resourcePath, int size, boolean brighten) {}
}
