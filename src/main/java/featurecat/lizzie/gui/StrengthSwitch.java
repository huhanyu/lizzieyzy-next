package featurecat.lizzie.gui;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import javax.swing.JCheckBox;

/** An on/off setting drawn as a switch: a rounded track with a sliding knob, then the label. */
final class StrengthSwitch extends JCheckBox {
  private static final long serialVersionUID = 1L;
  private final transient StrengthStyle style;

  StrengthSwitch(String text, boolean selected, StrengthStyle style) {
    super(text, selected);
    this.style = style;
    setFont(style.font(Font.PLAIN, 0));
    setForeground(style.text);
    setOpaque(false);
    setContentAreaFilled(false);
    setBorderPainted(false);
    setFocusPainted(false);
    setRolloverEnabled(true);
    setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    StrengthFocus.install(this);
  }

  private int trackWidth() {
    return style.px(32);
  }

  private int trackHeight() {
    return style.px(18);
  }

  @Override
  public Dimension getPreferredSize() {
    FontMetrics metrics = getFontMetrics(getFont());
    return new Dimension(
        trackWidth() + style.px(8) + metrics.stringWidth(getText()) + 2,
        Math.max(trackHeight() + 4, metrics.getHeight()));
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
      double width = trackWidth();
      double height = trackHeight();
      double top = (getHeight() - height) / 2.0;
      RoundRectangle2D track = new RoundRectangle2D.Double(1, top, width, height, height, height);
      g.setColor(
          isSelected() ? style.accent : getModel().isRollover() ? style.pressed : style.sunken);
      g.fill(track);
      if (!isSelected()) {
        g.setColor(style.border);
        g.draw(track);
      }
      double knob = height - 4;
      double knobX = isSelected() ? 1 + width - knob - 2 : 3;
      if (!style.dark) {
        g.setColor(new Color(0, 0, 0, 30));
        g.fill(new Ellipse2D.Double(knobX, top + 2.6, knob, knob));
      }
      g.setColor(Color.WHITE);
      g.fill(new Ellipse2D.Double(knobX, top + 2, knob, knob));
      if (StrengthFocus.visible(this)) {
        StrengthPaint.focusRing(
            g,
            style,
            new RoundRectangle2D.Double(0, top - 1, width + 2, height + 2, height + 2, height + 2));
      }
      FontMetrics metrics = g.getFontMetrics(getFont());
      g.setFont(getFont());
      g.setColor(isEnabled() ? getForeground() : style.tertiaryText);
      int baseline =
          (getHeight() - metrics.getAscent() - metrics.getDescent()) / 2 + metrics.getAscent();
      g.drawString(getText(), (float) (width + style.px(8) + 1), baseline);
    } finally {
      g.dispose();
    }
  }
}
