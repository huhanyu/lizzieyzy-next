package featurecat.lizzie.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Component;
import java.awt.Graphics2D;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.image.BufferedImage;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import org.junit.jupiter.api.Test;

class WindowMenuStripTest {
  @Test
  void mirrorsProminentToolbarActionsAndDelegatesClicks() {
    JMenuBar menuBar = new JMenuBar();
    menuBar.add(new JMenu("分析"));
    menuBar.add(Box.createHorizontalStrut(6));
    JButton sourceButton = new JButton("AI 解说");
    sourceButton.setName("aiCommentaryToolbarButton");
    sourceButton.getAccessibleContext().setAccessibleName("AI 解说");
    sourceButton.getAccessibleContext().setAccessibleDescription("生成当前棋局解说");
    AtomicInteger invocationCount = new AtomicInteger();
    sourceButton.addActionListener(event -> invocationCount.incrementAndGet());
    menuBar.add(sourceButton);

    WindowMenuStrip strip = new WindowMenuStrip(menuBar);

    assertEquals(2, strip.getComponentCount());
    assertTrue(strip.getComponent(1) instanceof JButton);
    JButton mirroredButton = (JButton) strip.getComponent(1);
    assertEquals("AI 解说", mirroredButton.getText());
    assertEquals("aiCommentaryToolbarButton", mirroredButton.getName());
    assertEquals("AI 解说", mirroredButton.getAccessibleContext().getAccessibleName());
    assertEquals("生成当前棋局解说", mirroredButton.getAccessibleContext().getAccessibleDescription());

    mirroredButton.doClick(0);

    assertEquals(1, invocationCount.get());
  }

  @Test
  void engineMenuEndsWithAChevronInRoomOfItsOwn() {
    JMenuBar menuBar = new JMenuBar();
    JMenu plain = new JMenu("[1]: KataGo");
    JMenu engine = new JMenu("[1]: KataGo");
    engine.putClientProperty("lizzie.engineMenu", Boolean.TRUE);
    menuBar.add(plain);
    menuBar.add(engine);

    WindowMenuStrip strip = new WindowMenuStrip(menuBar);

    JButton plainButton = (JButton) strip.getComponent(0);
    JButton engineButton = (JButton) strip.getComponent(1);
    assertEquals(
        plainButton.getPreferredSize().width + WindowMenuStrip.CHEVRON_SPACE,
        engineButton.getPreferredSize().width);
    engineButton.setSize(engineButton.getPreferredSize());
    BufferedImage image =
        new BufferedImage(
            engineButton.getWidth(), engineButton.getHeight(), BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = image.createGraphics();
    engineButton.paint(g);
    g.dispose();
    boolean painted = false;
    int chevronLeft = engineButton.getWidth() - 10 - WindowMenuStrip.CHEVRON_SPACE + 2;
    for (int x = chevronLeft; x < engineButton.getWidth() - 8; x++) {
      for (int y = 0; y < engineButton.getHeight(); y++) {
        painted |= (image.getRGB(x, y) >>> 24) > 0;
      }
    }
    assertTrue(painted, "the chevron is painted after the engine name");
  }

  @Test
  void mirrorsAiCoachVisualRoleAndDynamicSelectionState() {
    JMenuBar menuBar = new JMenuBar();
    JButton sourceButton = new JButton("AI 陪练");
    HumanSlTrainingStyle.stylePrimary(sourceButton);
    AppleStyleSupport.preserveCustomButtonStyle(sourceButton);
    menuBar.add(sourceButton);

    WindowMenuStrip strip = new WindowMenuStrip(menuBar);
    JButton mirroredButton = (JButton) strip.getComponent(0);

    assertEquals(HumanSlTrainingStyle.ACCENT, mirroredButton.getBackground());
    assertEquals(java.awt.Color.WHITE, mirroredButton.getForeground());
    assertNotNull(mirroredButton.getUI());
    assertTrue(mirroredButton.getUI().getClass().getName().contains("RoundedButtonUI"));

    sourceButton.setText("陪练中");
    sourceButton.setSelected(true);

    assertEquals("陪练中", mirroredButton.getText());
    assertTrue(mirroredButton.isSelected());
  }

  @Test
  void mirrorsSourceMenuTextAndEnabledStateWithoutRebuild() {
    JMenuBar menuBar = new JMenuBar();
    JMenu engine = new JMenu("[1] KataGo");
    menuBar.add(engine);

    WindowMenuStrip strip = new WindowMenuStrip(menuBar);
    JButton button = (JButton) strip.getComponent(0);

    engine.setText("[2] ZenGTPX");
    engine.setEnabled(false);

    assertEquals("[2] ZenGTPX", button.getText());
    assertEquals("[2] ZenGTPX", button.getAccessibleContext().getAccessibleName());
    assertFalse(button.isEnabled());
  }

  @Test
  void openingATopMenuGivesItAndItsSubmenusTheTopMenuLook() {
    JMenuBar menuBar = new JMenuBar();
    JMenu file = new JMenu("文件");
    JMenuItem save = new JMenuItem("保存(Ctrl+S)");
    JMenu recent = new JMenu("最近打开");
    file.add(save);
    file.add(recent);
    menuBar.add(file);
    WindowMenuStrip strip = new WindowMenuStrip(menuBar);

    JPopupMenu popup = file.getPopupMenu();
    popup.setInvoker(strip.getComponent(0));
    popup.setVisible(true);
    try {
      assertTrue(popup.getUI() instanceof TopMenuStyle.Surface);
      assertTrue(save.getUI() instanceof TopMenuItemUI);
      assertTrue(recent.getUI() instanceof TopMenuItemUI.Submenu);
    } finally {
      popup.setVisible(false);
    }
  }

  @Test
  void secondPressOnOpenTopMenuClosesInsteadOfReopening() {
    JMenuBar menuBar = new JMenuBar();
    JMenu view = new JMenu("显示");
    view.add(new JMenuItem("面板"));
    menuBar.add(view);

    WindowMenuStrip strip = new WindowMenuStrip(menuBar);
    Component component = strip.getComponent(0);
    assertTrue(component instanceof JButton);

    JButton button = (JButton) component;
    JPopupMenu popup = view.getPopupMenu();
    popup.setInvoker(button);
    popup.setVisible(true);
    assertTrue(popup.isVisible());

    MouseEvent press =
        new MouseEvent(
            button,
            MouseEvent.MOUSE_PRESSED,
            System.currentTimeMillis(),
            0,
            4,
            4,
            1,
            false,
            MouseEvent.BUTTON1);
    for (MouseListener listener : button.getMouseListeners()) {
      listener.mousePressed(press);
    }
    button.doClick(0);

    assertFalse(popup.isVisible());
  }
}
