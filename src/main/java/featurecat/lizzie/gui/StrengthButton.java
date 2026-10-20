package featurecat.lizzie.gui;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import javax.swing.ButtonModel;
import javax.swing.JButton;

/**
 * A rounded button of the player-strength dialog: a quiet secondary button with an optional line
 * icon, or the filled primary button of a step the user is invited to take.
 */
final class StrengthButton extends JButton {
  private static final long serialVersionUID = 1L;

  enum Glyph {
    NONE,
    IMPORT,
    DETAILS
  }

  private final transient StrengthStyle style;
  private final Glyph glyph;
  private final boolean primary;

  StrengthButton(String text, Glyph glyph, StrengthStyle style) {
    this(text, glyph, false, style);
  }

  private StrengthButton(String text, Glyph glyph, boolean primary, StrengthStyle style) {
    super(text);
    this.style = style;
    this.glyph = glyph;
    this.primary = primary;
    setFont(style.font(Font.BOLD, primary ? 1 : 0));
    setForeground(primary ? style.onAccent : style.text);
    setContentAreaFilled(false);
    setBorderPainted(false);
    setFocusPainted(false);
    setOpaque(false);
    setRolloverEnabled(true);
    setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    AppleStyleSupport.preserveCustomButtonStyle(this);
    StrengthFocus.install(this);
  }

  /** The filled button of the main step on a page, such as starting a calculation. */
  static StrengthButton primary(String text, StrengthStyle style) {
    return new StrengthButton(text, Glyph.NONE, true, style);
  }

  boolean isPrimary() {
    return primary;
  }

  @Override
  public Dimension getPreferredSize() {
    FontMetrics metrics = getFontMetrics(getFont());
    int icon = glyph == Glyph.NONE ? 0 : iconSize() + style.px(7);
    int padding = style.px(primary ? 22 : 14);
    return new Dimension(
        padding * 2 + icon + metrics.stringWidth(getText()),
        Math.max(style.px(primary ? 40 : 34), metrics.getHeight() + style.px(12)));
  }

  @Override
  public Dimension getMinimumSize() {
    return getPreferredSize();
  }

  @Override
  public Dimension getMaximumSize() {
    return getPreferredSize();
  }

  private int iconSize() {
    return style.px(14);
  }

  /** Fades a secondary button while it cannot be used. */
  @Override
  public void paint(Graphics graphics) {
    if (isEnabled() || primary) {
      super.paint(graphics);
      return;
    }
    Graphics2D g = (Graphics2D) graphics.create();
    try {
      g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.5f));
      super.paint(g);
    } finally {
      g.dispose();
    }
  }

  @Override
  protected void paintComponent(Graphics graphics) {
    Graphics2D g = StrengthStyle.smooth(graphics);
    try {
      ButtonModel model = getModel();
      double arc = style.px(10);
      RoundRectangle2D shape =
          new RoundRectangle2D.Double(0.5, 0.5, getWidth() - 1, getHeight() - 1, arc, arc);
      if (primary) {
        g.setColor(
            !isEnabled()
                ? style.sunken
                : model.isPressed() && model.isArmed()
                    ? StrengthStyle.mix(style.accent, style.text, 0.2)
                    : model.isRollover()
                        ? StrengthStyle.mix(style.accent, style.surface, 0.12)
                        : style.accent);
        g.fill(shape);
      } else {
        g.setColor(
            model.isPressed() && model.isArmed()
                ? style.pressed
                : model.isRollover() ? style.sunken : style.surface);
        g.fill(shape);
        g.setColor(style.border);
        g.draw(shape);
      }
      if (StrengthFocus.visible(this)) {
        StrengthPaint.focusRing(
            g,
            primary ? style.onAccent : style.accent,
            new RoundRectangle2D.Double(1.5, 1.5, getWidth() - 3, getHeight() - 3, arc, arc));
      }

      FontMetrics metrics = g.getFontMetrics(getFont());
      int icon = glyph == Glyph.NONE ? 0 : iconSize() + style.px(7);
      int contentWidth = icon + metrics.stringWidth(getText());
      int x = (getWidth() - contentWidth) / 2;
      if (glyph != Glyph.NONE) {
        paintGlyph(g, x, (getHeight() - iconSize()) / 2.0, iconSize());
      }
      g.setFont(getFont());
      g.setColor(isEnabled() ? getForeground() : style.tertiaryText);
      int baseline =
          (getHeight() - metrics.getAscent() - metrics.getDescent()) / 2 + metrics.getAscent();
      g.drawString(getText(), x + icon, baseline);
    } finally {
      g.dispose();
    }
  }

  private void paintGlyph(Graphics2D g, double x, double y, double size) {
    g.setColor(isEnabled() ? style.secondaryText : style.tertiaryText);
    g.setStroke(
        new BasicStroke(
            (float) Math.max(1.4, size / 9.0), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
    if (glyph == Glyph.IMPORT) {
      Path2D tray = new Path2D.Double();
      tray.moveTo(x + size * 0.08, y + size * 0.6);
      tray.lineTo(x + size * 0.08, y + size * 0.92);
      tray.lineTo(x + size * 0.92, y + size * 0.92);
      tray.lineTo(x + size * 0.92, y + size * 0.6);
      g.draw(tray);
      g.draw(new Line2D.Double(x + size / 2, y + size * 0.06, x + size / 2, y + size * 0.68));
      Path2D head = new Path2D.Double();
      head.moveTo(x + size * 0.27, y + size * 0.45);
      head.lineTo(x + size / 2, y + size * 0.68);
      head.lineTo(x + size * 0.73, y + size * 0.45);
      g.draw(head);
    } else {
      for (int row = 0; row < 3; row++) {
        double lineY = y + size * (0.2 + row * 0.3);
        g.fill(new Ellipse2D.Double(x, lineY - size * 0.08, size * 0.16, size * 0.16));
        g.draw(new Line2D.Double(x + size * 0.34, lineY, x + size, lineY));
      }
    }
  }
}
