package featurecat.lizzie.gui;

import java.awt.Dimension;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsEnvironment;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.Window;

/** Shared high-DPI and small-screen sizing rules for the AI Coach dialogs. */
final class HumanSlDialogBounds {
  private static final int SCREEN_MARGIN = 40;

  private HumanSlDialogBounds() {}

  static Rectangle usableBounds(Window owner, Window dialog) {
    GraphicsConfiguration configuration =
        owner != null ? owner.getGraphicsConfiguration() : dialog.getGraphicsConfiguration();
    if (configuration == null) {
      return GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
    }
    Rectangle bounds = configuration.getBounds();
    Insets insets = Toolkit.getDefaultToolkit().getScreenInsets(configuration);
    return new Rectangle(
        bounds.x + insets.left,
        bounds.y + insets.top,
        Math.max(1, bounds.width - insets.left - insets.right),
        Math.max(1, bounds.height - insets.top - insets.bottom));
  }

  static Dimension fit(
      Dimension packed,
      Dimension current,
      Rectangle usableBounds,
      int preferredWidth,
      int preferredHeight) {
    int availableWidth = Math.max(1, usableBounds.width - SCREEN_MARGIN);
    int availableHeight = Math.max(1, usableBounds.height - SCREEN_MARGIN);
    int packedWidth = packed == null ? 0 : packed.width;
    int packedHeight = packed == null ? 0 : packed.height;
    int currentWidth = current == null ? 0 : current.width;
    int currentHeight = current == null ? 0 : current.height;
    return new Dimension(
        Math.min(availableWidth, Math.max(preferredWidth, Math.max(packedWidth, currentWidth))),
        Math.min(
            availableHeight, Math.max(preferredHeight, Math.max(packedHeight, currentHeight))));
  }

  static Dimension minimum(Dimension target, int preferredWidth, int preferredHeight) {
    return new Dimension(
        Math.min(preferredWidth, target.width), Math.min(preferredHeight, target.height));
  }

  static void keepOnScreen(Window window, Rectangle usableBounds) {
    int maxX = usableBounds.x + usableBounds.width - window.getWidth();
    int maxY = usableBounds.y + usableBounds.height - window.getHeight();
    window.setLocation(
        Math.max(usableBounds.x, Math.min(window.getX(), maxX)),
        Math.max(usableBounds.y, Math.min(window.getY(), maxY)));
  }
}
