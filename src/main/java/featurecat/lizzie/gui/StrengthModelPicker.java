package featurecat.lizzie.gui;

import featurecat.lizzie.analysis.PlayerStrengthEstimator;
import java.awt.BasicStroke;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.plaf.basic.BasicComboBoxUI;
import javax.swing.plaf.basic.BasicComboPopup;
import javax.swing.plaf.basic.ComboPopup;

/** The rounded field that shows the player-strength model and opens the list of models. */
final class StrengthModelPicker extends JComboBox<PlayerStrengthEstimator.StrengthModel> {
  private static final long serialVersionUID = 1L;
  private final transient StrengthStyle style;
  private final String caption;
  private boolean rollover;

  StrengthModelPicker(
      PlayerStrengthEstimator.StrengthModel[] models, String caption, StrengthStyle style) {
    super(models);
    this.style = style;
    this.caption = caption;
    setUI(new PickerUi());
    setRenderer(new ModelRenderer());
    setOpaque(false);
    setBorder(BorderFactory.createEmptyBorder());
    setFont(style.font(Font.BOLD, 0));
    setMaximumRowCount(8);
    setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    StrengthFocus.install(this);
    addMouseListener(
        new MouseAdapter() {
          @Override
          public void mouseEntered(MouseEvent event) {
            rollover = true;
            repaint();
          }

          @Override
          public void mouseExited(MouseEvent event) {
            rollover = false;
            repaint();
          }
        });
  }

  @Override
  public void updateUI() {
    super.updateUI();
    if (style != null) {
      setUI(new PickerUi());
    }
  }

  private String selectedName() {
    Object item = getSelectedItem();
    return item instanceof PlayerStrengthEstimator.StrengthModel model
        ? StrengthText.modelName(model)
        : "-";
  }

  @Override
  public Dimension getPreferredSize() {
    FontMetrics captionMetrics = getFontMetrics(style.font(Font.PLAIN, 0));
    FontMetrics valueMetrics = getFontMetrics(getFont());
    int widest = valueMetrics.stringWidth("-");
    for (int i = 0; i < getItemCount(); i++) {
      widest = Math.max(widest, valueMetrics.stringWidth(StrengthText.modelName(getItemAt(i))));
    }
    int width =
        style.px(12)
            + captionMetrics.stringWidth(caption)
            + style.px(10)
            + widest
            + style.px(12)
            + style.px(10)
            + style.px(12);
    return new Dimension(width, Math.max(style.px(34), valueMetrics.getHeight() + style.px(12)));
  }

  @Override
  public Dimension getMinimumSize() {
    return getPreferredSize();
  }

  @Override
  public Dimension getMaximumSize() {
    return getPreferredSize();
  }

  @Override
  protected void paintComponent(Graphics graphics) {
    Graphics2D g = StrengthStyle.smooth(graphics);
    try {
      int width = getWidth();
      int height = getHeight();
      double arc = style.px(10);
      RoundRectangle2D shape =
          new RoundRectangle2D.Double(0.5, 0.5, width - 1, height - 1, arc, arc);
      g.setColor(rollover || isPopupVisible() ? style.sunken : style.surface);
      g.fill(shape);
      g.setColor(isPopupVisible() ? style.accent : style.border);
      g.draw(shape);
      if (StrengthFocus.visible(this) && !isPopupVisible()) {
        StrengthPaint.focusRing(
            g, style, new RoundRectangle2D.Double(1.5, 1.5, width - 3, height - 3, arc, arc));
      }

      Font captionFont = style.font(Font.PLAIN, 0);
      FontMetrics captionMetrics = g.getFontMetrics(captionFont);
      FontMetrics valueMetrics = g.getFontMetrics(getFont());
      int baseline =
          (height - valueMetrics.getAscent() - valueMetrics.getDescent()) / 2
              + valueMetrics.getAscent();
      int x = style.px(12);
      g.setFont(captionFont);
      g.setColor(style.secondaryText);
      g.drawString(caption, x, baseline);
      x += captionMetrics.stringWidth(caption) + style.px(10);
      int chevron = style.px(10);
      StrengthPaint.text(
          g,
          selectedName(),
          getFont(),
          getFont().getSize(),
          isEnabled() ? style.text : style.tertiaryText,
          x,
          baseline,
          width - x - chevron - style.px(24),
          StrengthPaint.LEFT);

      double chevronX = width - style.px(12) - chevron;
      double chevronY = height / 2.0 - chevron * 0.25;
      Path2D path = new Path2D.Double();
      path.moveTo(chevronX, chevronY);
      path.lineTo(chevronX + chevron / 2.0, chevronY + chevron / 2.0);
      path.lineTo(chevronX + chevron, chevronY);
      g.setColor(style.secondaryText);
      g.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
      g.draw(path);
    } finally {
      g.dispose();
    }
  }

  private final class PickerUi extends BasicComboBoxUI {
    @Override
    protected JButton createArrowButton() {
      JButton button = new JButton();
      button.setBorder(BorderFactory.createEmptyBorder());
      button.setContentAreaFilled(false);
      button.setFocusable(false);
      button.setOpaque(false);
      button.setPreferredSize(new Dimension(0, 0));
      return button;
    }

    @Override
    protected ComboPopup createPopup() {
      BasicComboPopup popup = new BasicComboPopup(comboBox);
      popup.setBorder(BorderFactory.createLineBorder(style.border));
      popup.getList().setBackground(style.surface);
      popup.getList().setSelectionBackground(style.accentSoft);
      return popup;
    }

    @Override
    public void paint(Graphics graphics, JComponent component) {}
  }

  private final class ModelRenderer extends DefaultListCellRenderer {
    private static final long serialVersionUID = 1L;

    @Override
    public Component getListCellRendererComponent(
        JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
      JLabel label =
          (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
      boolean current = value != null && value.equals(getSelectedItem());
      label.setText(
          value instanceof PlayerStrengthEstimator.StrengthModel model
              ? StrengthText.modelName(model)
              : "-");
      label.setFont(style.font(current ? Font.BOLD : Font.PLAIN, 0));
      label.setIcon(new CheckIcon(current));
      label.setIconTextGap(style.px(8));
      label.setBorder(
          BorderFactory.createEmptyBorder(style.px(7), style.px(10), style.px(7), style.px(16)));
      label.setOpaque(true);
      label.setBackground(isSelected ? style.accentSoft : style.surface);
      label.setForeground(style.text);
      return label;
    }
  }

  private final class CheckIcon implements Icon {
    private final boolean visible;

    private CheckIcon(boolean visible) {
      this.visible = visible;
    }

    @Override
    public void paintIcon(Component component, Graphics graphics, int x, int y) {
      if (!visible) {
        return;
      }
      Graphics2D g = StrengthStyle.smooth(graphics);
      try {
        double size = getIconWidth();
        Path2D check = new Path2D.Double();
        check.moveTo(x + size * 0.12, y + size * 0.55);
        check.lineTo(x + size * 0.4, y + size * 0.8);
        check.lineTo(x + size * 0.9, y + size * 0.22);
        g.setColor(style.accent);
        g.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(check);
      } finally {
        g.dispose();
      }
    }

    @Override
    public int getIconWidth() {
      return style.px(12);
    }

    @Override
    public int getIconHeight() {
      return style.px(12);
    }
  }
}
