package featurecat.lizzie.gui;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.ActionEvent;
import java.awt.event.FocusEvent;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;
import javax.swing.AbstractAction;
import javax.swing.ButtonGroup;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import javax.swing.KeyStroke;

/**
 * The segmented control that switches between the views of the player-strength dialog. Each segment
 * is a toggle button, so Tab, Space and screen readers work as usual; the arrow keys move to the
 * neighbouring segment.
 */
final class StrengthTabs extends JPanel {
  private static final long serialVersionUID = 1L;
  private final transient StrengthStyle style;
  private final List<JToggleButton> segments = new ArrayList<>();

  StrengthTabs(List<String> labels, int selected, IntConsumer onSelect, StrengthStyle style) {
    super(null);
    this.style = style;
    setOpaque(false);
    ButtonGroup group = new ButtonGroup();
    for (int i = 0; i < labels.size(); i++) {
      int index = i;
      JToggleButton segment = new Segment(labels.get(i));
      segment.setSelected(i == selected);
      segment.addActionListener(event -> onSelect.accept(index));
      AccessibilitySupport.buttonWithoutTooltip(segment, labels.get(i), labels.get(i));
      group.add(segment);
      segments.add(segment);
      add(segment);
    }
    for (int i = 0; i < segments.size(); i++) {
      bindArrow(segments.get(i), "LEFT", i - 1);
      bindArrow(segments.get(i), "RIGHT", i + 1);
    }
  }

  int selectedIndex() {
    for (int i = 0; i < segments.size(); i++) {
      if (segments.get(i).isSelected()) {
        return i;
      }
    }
    return 0;
  }

  JToggleButton segment(int index) {
    return segments.get(index);
  }

  private void bindArrow(JToggleButton segment, String key, int target) {
    if (target < 0 || target >= segments.size()) {
      return;
    }
    String name = "strength-tabs-" + key;
    segment.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(key), name);
    segment
        .getActionMap()
        .put(
            name,
            new AbstractAction() {
              private static final long serialVersionUID = 1L;

              @Override
              public void actionPerformed(ActionEvent event) {
                JToggleButton next = segments.get(target);
                next.requestFocusInWindow(FocusEvent.Cause.TRAVERSAL_FORWARD);
                next.doClick(0);
              }
            });
  }

  private int segmentWidth() {
    int widest = 0;
    for (JToggleButton segment : segments) {
      FontMetrics metrics = segment.getFontMetrics(segment.getFont());
      widest = Math.max(widest, metrics.stringWidth(segment.getText()));
    }
    return Math.max(style.px(88), widest + style.px(36));
  }

  @Override
  public Dimension getPreferredSize() {
    int padding = style.px(3);
    FontMetrics metrics = getFontMetrics(style.font(Font.BOLD, 0));
    int height = Math.max(style.px(34), metrics.getHeight() + style.px(14));
    return new Dimension(padding * 2 + segmentWidth() * segments.size(), height);
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
  public void doLayout() {
    int padding = style.px(3);
    int width = segments.isEmpty() ? 0 : (getWidth() - padding * 2) / segments.size();
    for (int i = 0; i < segments.size(); i++) {
      segments.get(i).setBounds(padding + i * width, padding, width, getHeight() - padding * 2);
    }
  }

  @Override
  protected void paintComponent(Graphics graphics) {
    Graphics2D g = StrengthStyle.smooth(graphics);
    try {
      double arc = style.px(10);
      g.setColor(style.sunken);
      g.fill(new RoundRectangle2D.Double(0, 0, getWidth(), getHeight(), arc, arc));
    } finally {
      g.dispose();
    }
  }

  private final class Segment extends JToggleButton {
    private static final long serialVersionUID = 1L;

    private Segment(String text) {
      super(text);
      setFont(style.font(Font.BOLD, 0));
      setContentAreaFilled(false);
      setBorderPainted(false);
      setFocusPainted(false);
      setOpaque(false);
      setRolloverEnabled(true);
      setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
      AppleStyleSupport.preserveCustomButtonStyle(this);
      StrengthFocus.install(this);
    }

    @Override
    protected void paintComponent(Graphics graphics) {
      Graphics2D g = StrengthStyle.smooth(graphics);
      try {
        double arc = style.px(8);
        RoundRectangle2D shape =
            new RoundRectangle2D.Double(0.5, 0.5, getWidth() - 1, getHeight() - 1.5, arc, arc);
        if (isSelected()) {
          if (!style.dark) {
            g.setColor(new Color(0, 0, 0, 18));
            g.fill(
                new RoundRectangle2D.Double(0.5, 1.5, getWidth() - 1, getHeight() - 1.5, arc, arc));
          }
          g.setColor(style.raised);
          g.fill(shape);
          if (!style.dark) {
            g.setColor(style.border);
            g.draw(shape);
          }
        }
        if (StrengthFocus.visible(this)) {
          StrengthPaint.focusRing(
              g,
              style,
              new RoundRectangle2D.Double(1.5, 1.5, getWidth() - 3, getHeight() - 3.5, arc, arc));
        }
        FontMetrics metrics = g.getFontMetrics(getFont());
        g.setFont(getFont());
        g.setColor(isSelected() || getModel().isRollover() ? style.text : style.secondaryText);
        String text = StrengthPaint.ellipsize(getText(), metrics, getWidth() - style.px(12));
        int baseline =
            (getHeight() - metrics.getAscent() - metrics.getDescent()) / 2 + metrics.getAscent();
        g.drawString(text, (getWidth() - metrics.stringWidth(text)) / 2f, baseline);
      } finally {
        g.dispose();
      }
    }
  }
}
