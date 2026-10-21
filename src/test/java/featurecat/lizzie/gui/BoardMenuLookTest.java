package featurecat.lizzie.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import featurecat.lizzie.Lizzie;
import java.awt.Component;
import java.util.List;
import java.util.ResourceBundle;
import javax.swing.ImageIcon;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BoardMenuLookTest {
  private ResourceBundle previousBundle;
  private boolean previousKeepForcing;

  @BeforeEach
  void useBundle() {
    previousBundle = Lizzie.resourceBundle;
    previousKeepForcing = LizzieFrame.isKeepForcing;
    Lizzie.resourceBundle = ResourceBundle.getBundle("l10n.DisplayStrings");
  }

  @AfterEach
  void restore() {
    Lizzie.resourceBundle = previousBundle;
    LizzieFrame.isKeepForcing = previousKeepForcing;
  }

  @Test
  void bothBoardMenusShowOneGlyphColumnBetweenHairlines() {
    for (JPopupMenu menu : List.of(new RightClickMenu(), new RightClickMenu2())) {
      for (Component component : menu.getComponents()) {
        if (component instanceof JMenuItem item) {
          String name = item.getText();
          // A check box item would add a second column in front of every icon.
          assertFalse(item instanceof JCheckBoxMenuItem, name);
          assertNotNull(item.getIcon(), name + " has an icon");
          assertFalse(item.getIcon() instanceof ImageIcon, name + " uses a glyph, not a picture");
          assertEquals(BoardMenuGlyph.SIZE, item.getIcon().getIconWidth(), name);
          assertSame(item.getIcon(), item.getDisabledIcon(), name + " fades its own glyph");
          assertEquals(BoardMenuStyle.ICON_TEXT_GAP, item.getIconTextGap(), name);
        } else {
          // Not the toolbar separator the stone menu used, which drew boxes in a menu.
          assertInstanceOf(BoardMenuStyle.Hairline.class, component);
        }
      }
    }
  }

  @Test
  void keepForcingItemShowsTheCurrentState() {
    RightClickMenu menu = new RightClickMenu();
    JMenuItem keep = find(menu, Lizzie.resourceBundle.getString("RightClickMenu.avoid2"));
    LizzieFrame.isKeepForcing = true;
    menu.refreshLook();
    assertEquals(BoardMenuGlyph.KEEP_ON.icon(), keep.getIcon());
    LizzieFrame.isKeepForcing = false;
    menu.refreshLook();
    assertEquals(BoardMenuGlyph.KEEP_OFF.icon(), keep.getIcon());
  }

  private static JMenuItem find(JPopupMenu menu, String text) {
    for (Component component : menu.getComponents()) {
      if (component instanceof JMenuItem item && text.equals(item.getText())) return item;
    }
    throw new AssertionError("no item " + text);
  }
}
