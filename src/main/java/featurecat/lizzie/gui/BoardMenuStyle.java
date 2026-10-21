package featurecat.lizzie.gui;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;

/**
 * The frame of the board right-click menus: the app's popup style, faint one-pixel separators and
 * one icon column. It is applied again each time a menu opens, so a theme switch carries over.
 */
final class BoardMenuStyle {
  static final int ICON_TEXT_GAP = 7;

  private BoardMenuStyle() {}

  static void install(JPopupMenu menu) {
    AppleStyleSupport.installPopupStyle(menu);
    for (Component component : menu.getComponents()) {
      if (component instanceof JMenuItem item) {
        item.setIconTextGap(ICON_TEXT_GAP);
        // Swing only greys out picture icons; a glyph fades itself when its item is disabled.
        item.setDisabledIcon(item.getIcon());
      }
    }
  }

  static JPopupMenu.Separator separator() {
    return new Hairline();
  }

  /** A one-pixel rule with room above and below, in place of the look-and-feel's double line. */
  static final class Hairline extends JPopupMenu.Separator {
    private static final int HEIGHT = 9;
    private static final int INSET = 8;

    @Override
    public Dimension getPreferredSize() {
      return new Dimension(1, HEIGHT);
    }

    @Override
    public Dimension getMinimumSize() {
      return getPreferredSize();
    }

    @Override
    public Dimension getMaximumSize() {
      return new Dimension(Integer.MAX_VALUE, HEIGHT);
    }

    @Override
    protected void paintComponent(Graphics g) {
      g.setColor(AppleStyleSupport.popupRuleColor());
      g.fillRect(INSET, HEIGHT / 2, getWidth() - 2 * INSET, 1);
    }
  }
}
