package featurecat.lizzie.gui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.plaf.basic.BasicScrollBarUI;

/** A slim scroll bar for the sidebar lists: a rounded thumb, no track and no arrow buttons. */
final class SlimScrollBarUI extends BasicScrollBarUI {
  private static final int THICKNESS = 10;

  @Override
  protected void configureScrollBarColors() {}

  @Override
  public Dimension getPreferredSize(JComponent component) {
    return new Dimension(THICKNESS, THICKNESS);
  }

  @Override
  protected JButton createDecreaseButton(int orientation) {
    return hiddenButton();
  }

  @Override
  protected JButton createIncreaseButton(int orientation) {
    return hiddenButton();
  }

  private static JButton hiddenButton() {
    JButton button = new JButton();
    Dimension none = new Dimension(0, 0);
    button.setPreferredSize(none);
    button.setMinimumSize(none);
    button.setMaximumSize(none);
    button.setFocusable(false);
    button.setBorder(null);
    return button;
  }

  @Override
  protected void paintTrack(Graphics graphics, JComponent component, Rectangle bounds) {}

  @Override
  protected void paintThumb(Graphics graphics, JComponent component, Rectangle thumb) {
    if (thumb.isEmpty() || !scrollbar.isEnabled()) return;
    Graphics2D g = (Graphics2D) graphics.create();
    try {
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      Color base = SidebarPanel.secondaryTextColor();
      int alpha = isDragging || isThumbRollover() ? 170 : 110;
      g.setColor(new Color(base.getRed(), base.getGreen(), base.getBlue(), alpha));
      boolean vertical = thumb.height >= thumb.width;
      int inset = 2;
      int w = Math.max(1, thumb.width - inset * 2);
      int h = Math.max(1, thumb.height - inset * 2);
      int arc = vertical ? w : h;
      g.fillRoundRect(thumb.x + inset, thumb.y + inset, w, h, arc, arc);
    } finally {
      g.dispose();
    }
  }
}
