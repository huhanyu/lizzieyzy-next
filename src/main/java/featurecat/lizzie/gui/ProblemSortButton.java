package featurecat.lizzie.gui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.geom.Path2D;
import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.AbstractAction;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.KeyStroke;
import javax.swing.MenuElement;
import javax.swing.MenuSelectionManager;

/**
 * The sort control above the problem list: a rounded chip with the current order that opens a menu
 * of the three orders. On a very narrow sidebar the label wraps instead of being cut off.
 */
final class ProblemSortButton extends JButton {
  private static final int PAD_X = 10;
  private static final int PAD_Y = 5;
  private static final int GAP = 6;
  private static final int CHEVRON = 8;
  private final Consumer<ProblemListSort> onChange;
  private ProblemListSort order;

  ProblemSortButton(ProblemListSort initial, Consumer<ProblemListSort> onChange) {
    this.order = initial == null ? ProblemListSort.LOSS_DESC : initial;
    this.onChange = onChange;
    setContentAreaFilled(false);
    setBorderPainted(false);
    setFocusPainted(false);
    setOpaque(false);
    setRolloverEnabled(true);
    addActionListener(event -> showMenu());
    getInputMap(JComponent.WHEN_FOCUSED)
        .put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0), "open-sort-menu");
    getActionMap()
        .put(
            "open-sort-menu",
            new AbstractAction() {
              @Override
              public void actionPerformed(ActionEvent event) {
                showMenu();
              }
            });
    refreshText();
  }

  ProblemListSort order() {
    return order;
  }

  /** Changes the order as the menu does, and tells the list. */
  void setOrder(ProblemListSort next) {
    if (next == null || next == order) return;
    order = next;
    refreshText();
    onChange.accept(next);
  }

  /** Refreshes the label after a language change. */
  void refreshText() {
    setText(order.toString());
    getAccessibleContext().setAccessibleName(BlunderListPanel.text("BlunderListPanel.sort"));
    getAccessibleContext().setAccessibleDescription(order.toString());
    setToolTipText(BlunderListPanel.text("BlunderListPanel.sort"));
    revalidate();
    repaint();
  }

  JPopupMenu createMenu() {
    JPopupMenu menu = new JPopupMenu();
    menu.getAccessibleContext().setAccessibleName(BlunderListPanel.text("BlunderListPanel.sort"));
    ButtonGroup group = new ButtonGroup();
    for (ProblemListSort choice : ProblemListSort.values()) {
      JRadioButtonMenuItem item = new JRadioButtonMenuItem(choice.toString(), choice == order);
      AppleStyleSupport.styleMenuItem(item);
      item.addActionListener(event -> setOrder(choice));
      group.add(item);
      menu.add(item);
    }
    return menu;
  }

  private void showMenu() {
    if (!isShowing()) return;
    JPopupMenu menu = createMenu();
    menu.show(this, 0, getHeight() + 4);
    // Put the keyboard on the current order, so Up/Down and Enter work at once.
    for (MenuElement element : menu.getSubElements()) {
      if (element instanceof JRadioButtonMenuItem
          && ((JRadioButtonMenuItem) element).isSelected()) {
        MenuSelectionManager.defaultManager().setSelectedPath(new MenuElement[] {menu, element});
      }
    }
  }

  private Font labelFont() {
    return FittedFont.withKerning(getFont());
  }

  private int iconSize(FontMetrics metrics) {
    return Math.max(10, metrics.getAscent() - 3);
  }

  /** The chip's size within {@code width}; the label wraps when one line does not fit. */
  Dimension sizeFor(int width) {
    FontMetrics metrics = getFontMetrics(labelFont());
    int chrome = PAD_X + iconSize(metrics) + GAP + GAP + CHEVRON + PAD_X;
    int oneLine = metrics.stringWidth(getText());
    int textWidth = Math.max(1, Math.min(oneLine, width - chrome));
    List<String> lines = lines(metrics, textWidth);
    int widest = 0;
    for (String line : lines) widest = Math.max(widest, metrics.stringWidth(line));
    int height = Math.max(iconSize(metrics), metrics.getHeight() * lines.size()) + PAD_Y * 2;
    return new Dimension(chrome + widest, height);
  }

  @Override
  public Dimension getPreferredSize() {
    return sizeFor(Integer.MAX_VALUE);
  }

  private List<String> lines(FontMetrics metrics, int width) {
    String text = getText();
    List<String> lines = new ArrayList<>();
    if (metrics.stringWidth(text) <= width) {
      lines.add(text);
      return lines;
    }
    BreakIterator breaks = BreakIterator.getLineInstance(BlunderListPanel.bundle().getLocale());
    breaks.setText(text);
    int start = 0;
    int end = breaks.next();
    int lastFit = -1;
    while (end != BreakIterator.DONE) {
      if (metrics.stringWidth(text.substring(start, end).stripTrailing()) <= width) {
        lastFit = end;
        end = breaks.next();
        continue;
      }
      if (lastFit > start) {
        lines.add(text.substring(start, lastFit).stripTrailing());
        start = lastFit;
        lastFit = -1;
        continue;
      }
      // One word is wider than the chip: break it between characters.
      int cut = start + 1;
      while (cut < end && metrics.stringWidth(text.substring(start, cut + 1)) <= width) cut++;
      lines.add(text.substring(start, cut));
      start = cut;
    }
    if (start < text.length()) lines.add(text.substring(start).stripTrailing());
    return lines;
  }

  @Override
  protected void paintComponent(Graphics graphics) {
    Graphics2D g = (Graphics2D) graphics.create();
    try {
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      Font font = labelFont();
      FontMetrics metrics = g.getFontMetrics(font);
      int icon = iconSize(metrics);
      List<String> lines = lines(metrics, textWidth(metrics));
      int arc = lines.size() > 1 ? 14 : getHeight();
      boolean active = getModel().isRollover() || getModel().isPressed();
      g.setColor(SidebarPanel.rowHighlightColor(active));
      g.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, arc, arc);
      Color secondary = SidebarPanel.secondaryTextColor();
      g.setColor(new Color(secondary.getRed(), secondary.getGreen(), secondary.getBlue(), 110));
      g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, arc, arc);

      int middle = getHeight() / 2;
      paintSortIcon(g, PAD_X, middle - icon / 2, icon, secondary);
      int textX = PAD_X + icon + GAP;
      int blockHeight = metrics.getHeight() * lines.size();
      int baseline = (getHeight() - blockHeight) / 2 + metrics.getAscent();
      g.setFont(font);
      g.setColor(SidebarPanel.primaryTextColor());
      for (String line : lines) {
        g.drawString(line, textX, baseline);
        baseline += metrics.getHeight();
      }
      paintChevron(g, getWidth() - PAD_X - CHEVRON, middle, secondary);
      if (isFocusOwner()) {
        g.setColor(SidebarPanel.accentTextColor());
        g.setStroke(new BasicStroke(2f));
        g.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, arc - 2, arc - 2);
      }
    } finally {
      g.dispose();
    }
  }

  private int textWidth(FontMetrics metrics) {
    return Math.max(1, getWidth() - (PAD_X + iconSize(metrics) + GAP + GAP + CHEVRON + PAD_X));
  }

  /** Three bars getting shorter: the usual "sorted" mark. */
  private static void paintSortIcon(Graphics2D g, int x, int y, int size, Color color) {
    g.setColor(color);
    g.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
    float step = size / 3f;
    for (int i = 0; i < 3; i++) {
      float lineY = y + step * i + step / 2;
      g.draw(new java.awt.geom.Line2D.Float(x, lineY, x + size * (1f - i * 0.28f), lineY));
    }
  }

  private static void paintChevron(Graphics2D g, int x, int middle, Color color) {
    Path2D.Float chevron = new Path2D.Float();
    chevron.moveTo(x, middle - CHEVRON / 4f);
    chevron.lineTo(x + CHEVRON / 2f, middle + CHEVRON / 4f);
    chevron.lineTo(x + CHEVRON, middle - CHEVRON / 4f);
    g.setColor(color);
    g.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
    g.draw(chevron);
  }
}
