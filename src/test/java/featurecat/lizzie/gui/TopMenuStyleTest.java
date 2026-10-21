package featurecat.lizzie.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JSeparator;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import org.junit.jupiter.api.Test;

class TopMenuStyleTest {
  private static JPopupMenu popup(JMenuItem... items) {
    JPopupMenu popup = new JPopupMenu();
    for (JMenuItem item : items) popup.add(item);
    return popup;
  }

  private static void open(JMenu menu) {
    JPopupMenu popup = menu.getPopupMenu();
    for (PopupMenuListener listener : popup.getPopupMenuListeners()) {
      listener.popupMenuWillBecomeVisible(new PopupMenuEvent(popup));
    }
  }

  @Test
  void itemsSeparatorsSubmenusAndThePopupGetTheTopMenuLook() {
    JMenuItem open = new JMenuItem("打开棋谱(O)");
    JCheckBoxMenuItem komi = new JCheckBoxMenuItem("加载SGF棋谱中的贴目", true);
    JMenu recent = new JMenu("最近打开");
    JMenuItem file = new JMenuItem("1: game.sgf");
    recent.add(file);
    JPopupMenu popup = popup(open, komi);
    popup.addSeparator();
    popup.add(recent);

    TopMenuStyle.install(popup);

    assertInstanceOf(TopMenuStyle.Surface.class, popup.getUI());
    assertInstanceOf(TopMenuItemUI.class, open.getUI());
    assertInstanceOf(TopMenuItemUI.class, komi.getUI());
    assertInstanceOf(TopMenuItemUI.Submenu.class, recent.getUI());
    assertInstanceOf(TopMenuStyle.Hairline.class, ((JSeparator) popup.getComponent(2)).getUI());
    // Submenus are styled as they open: several are rebuilt each time (最近打开).
    assertFalse(file.getUI() instanceof TopMenuItemUI);
    open(recent);
    assertInstanceOf(TopMenuItemUI.class, file.getUI());

    // A theme switch reinstalls the look and feel's delegates; the next opening restores ours,
    // without stacking listeners.
    SwingUtilities.updateComponentTreeUI(popup);
    assertFalse(open.getUI() instanceof TopMenuItemUI);
    TopMenuStyle.install(popup);
    open(recent);
    assertInstanceOf(TopMenuItemUI.class, open.getUI());
    assertInstanceOf(TopMenuItemUI.class, file.getUI());
    assertEquals(1, styleListeners(recent.getPopupMenu()));
  }

  private static long styleListeners(JPopupMenu popup) {
    return java.util.Arrays.stream(popup.getPopupMenuListeners())
        .filter(listener -> listener.getClass().getName().startsWith(TopMenuStyle.class.getName()))
        .count();
  }

  @Test
  void shortcutsMoveToTheHintColumnWhileItemsKeepTheirText() {
    JMenuItem save = new JMenuItem("保存(Ctrl+S)");
    JMenuItem floating = new JMenuItem("浮动主棋盘(保留原棋盘)");
    JMenuItem whole = new JMenuItem("整盘精析（推荐）");
    whole.setAccelerator(
        KeyStroke.getKeyStroke(
            KeyEvent.VK_B, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK));
    TopMenuStyle.install(popup(save, floating, whole));

    assertEquals(new TopMenuItemUI.Row("保存", "Ctrl+S", 0, false, 0), TopMenuItemUI.Row.of(save));
    assertEquals("浮动主棋盘(保留原棋盘)", TopMenuItemUI.Row.of(floating).label());
    assertNull(TopMenuItemUI.Row.of(floating).hint());
    assertEquals("Ctrl+Shift+B", TopMenuItemUI.Row.of(whole).hint());
    // Tooltips, accessibility and search still see the full text.
    assertEquals("保存(Ctrl+S)", save.getText());
  }

  @Test
  void theMarkColumnIsReservedOnlyWhenTheMenuNeedsIt() {
    JMenuItem plain = new JMenuItem("新建");
    JPopupMenu popup = popup(plain, new JMenuItem("退出"));
    TopMenuStyle.install(popup);
    assertEquals(TopMenuItemUI.PADDING, TopMenuItemUI.Row.of(plain).textX());

    popup.add(new JCheckBoxMenuItem("坐标(C)"));
    int withMark = TopMenuItemUI.PADDING + TopMenuItemUI.MARK + TopMenuItemUI.MARK_GAP;
    assertEquals(withMark, TopMenuItemUI.Row.of(plain).textX());

    JMenuItem icon = new JMenuItem("添加黑子", new ImageIcon(new BufferedImage(16, 16, 2)));
    JMenuItem other = new JMenuItem("清空棋盘");
    popup(icon, other);
    assertEquals(withMark, TopMenuItemUI.Row.of(other).textX());
  }

  @Test
  void hintsShareOneRightEdgeThatClearsTheChevrons() {
    JMenuItem play = new JMenuItem("自动播放(Ctrl+A)");
    JMenu rank = new JMenu("落子评价标记(Alt+M)");
    TopMenuStyle.install(popup(play, rank));
    int width = 300;
    int edge = width - TopMenuItemUI.PADDING - TopMenuItemUI.CHEVRON;
    assertEquals(edge, TopMenuItemUI.Row.of(play).hintRight(width));
    assertEquals(edge, TopMenuItemUI.Row.of(rank).hintRight(width));
    assertEquals("Alt+M", TopMenuItemUI.Row.of(rank).hint());
  }

  @Test
  void aRowPaintsItsLabelLeftItsHintRightAndAnInsetHighlight() {
    JMenuItem save = new JMenuItem("Save (Ctrl+S)");
    TopMenuStyle.install(popup(save));
    Dimension size = save.getPreferredSize();
    save.setSize(size.width + 120, size.height);
    int width = save.getWidth(), height = save.getHeight();
    int labelEnd = TopMenuItemUI.PADDING + TopMenuItemUI.textWidth(save, "Save");
    int hintStart = width - TopMenuItemUI.PADDING - TopMenuItemUI.textWidth(save, "Ctrl+S");

    BufferedImage plain = paint(save);
    int surface = AppleStyleSupport.popupSurfaceColor().getRGB();
    assertTrue(inked(plain, TopMenuItemUI.PADDING, labelEnd, surface), "label at the left");
    assertTrue(
        inked(plain, hintStart, width - TopMenuItemUI.PADDING, surface), "hint at the right");
    assertFalse(inked(plain, labelEnd + 4, hintStart - 4, surface), "nothing in between");
    assertEquals(surface, plain.getRGB(8, 3));

    save.setArmed(true);
    BufferedImage armed = paint(save);
    assertEquals(opaque(AppleStyleSupport.popupSelectionColor(), surface), armed.getRGB(8, 3));
    assertEquals(surface, armed.getRGB(1, height / 2), "the highlight stays inset");
  }

  @Test
  void aLabelAndButtonRowLinesUpWithTheOtherRows() {
    JMenuItem custom = new JMenuItem();
    custom.setLayout(null);
    JLabel label = new JLabel("自定义布局1(Alt+8)");
    JButton set = new JButton("设置");
    custom.add(label);
    custom.add(set);
    custom.setPreferredSize(new Dimension(153, 20));
    JPopupMenu popup = popup(new JCheckBoxMenuItem("坐标(C)", true), custom);

    TopMenuStyle.install(popup);
    TopMenuStyle.install(popup);

    assertEquals("自定义布局1", label.getText());
    assertEquals("Alt+8", TopMenuItemUI.Row.of(custom).hint());
    Dimension size = custom.getPreferredSize();
    assertEquals(TopMenuItemUI.rowHeight(custom), size.height);
    custom.setSize(size.width + 40, size.height);
    custom.doLayout();
    TopMenuItemUI.Row row = TopMenuItemUI.Row.of(custom);
    assertEquals(row.textX(), label.getX());
    assertEquals(custom.getWidth() - TopMenuItemUI.PADDING, set.getX() + set.getWidth());
    assertTrue(row.hintRight(custom.getWidth()) < set.getX(), "the hint stops before the button");
  }

  private static BufferedImage paint(JMenuItem item) {
    BufferedImage image =
        new BufferedImage(item.getWidth(), item.getHeight(), BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = image.createGraphics();
    try {
      item.paint(g);
    } finally {
      g.dispose();
    }
    return image;
  }

  private static boolean inked(BufferedImage image, int fromX, int toX, int surface) {
    for (int x = Math.max(0, fromX); x < Math.min(image.getWidth(), toX); x++) {
      for (int y = 0; y < image.getHeight(); y++) {
        if (image.getRGB(x, y) != surface) return true;
      }
    }
    return false;
  }

  /** A translucent colour as it shows on the surface. */
  private static int opaque(java.awt.Color color, int surface) {
    BufferedImage pixel = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = pixel.createGraphics();
    g.setColor(new java.awt.Color(surface, true));
    g.fillRect(0, 0, 1, 1);
    g.setColor(color);
    g.fillRect(0, 0, 1, 1);
    g.dispose();
    return pixel.getRGB(0, 0);
  }
}
