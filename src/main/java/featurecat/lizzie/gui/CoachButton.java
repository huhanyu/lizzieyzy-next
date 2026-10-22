package featurecat.lizzie.gui;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.JButton;

/**
 * A button of the AI Coach panel: a rounded fill drawn by hand so it looks the same under every
 * look and feel. It never takes keyboard focus, so Space and the board's shortcuts stay with the
 * board.
 */
final class CoachButton extends JButton {
  enum Kind {
    PRIMARY,
    SECONDARY,
    QUIET
  }

  private Kind kind;

  private CoachButton(Kind kind) {
    this.kind = kind;
    setFocusable(false);
    setContentAreaFilled(false);
    setBorderPainted(false);
    setFocusPainted(false);
    setOpaque(false);
    setRolloverEnabled(true);
    setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
  }

  static CoachButton primary() {
    return new CoachButton(Kind.PRIMARY);
  }

  static CoachButton secondary() {
    return new CoachButton(Kind.SECONDARY);
  }

  static CoachButton quiet() {
    return new CoachButton(Kind.QUIET);
  }

  Kind kind() {
    return kind;
  }

  void setKind(Kind value) {
    if (kind != value) {
      kind = value;
      repaint();
    }
  }

  @Override
  public Dimension getPreferredSize() {
    return new Dimension(super.getPreferredSize().width, 40);
  }

  @Override
  protected void paintComponent(Graphics graphics) {
    Graphics2D g = (Graphics2D) graphics.create();
    try {
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g.setRenderingHint(
          RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
      boolean enabled = isEnabled();
      boolean hover = enabled && getModel().isRollover();
      boolean pressed = enabled && getModel().isArmed() && getModel().isPressed();
      int width = getWidth();
      int height = getHeight();
      Color fill;
      Color border;
      Color text;
      switch (kind) {
        case PRIMARY:
          fill =
              pressed
                  ? new Color(0x2f, 0xa3, 0x91)
                  : hover ? new Color(0x52, 0xcf, 0xbb) : CoachPanel.ACCENT;
          border = null;
          text = new Color(0x0b, 0x26, 0x22);
          break;
        case QUIET:
          fill = hover ? CoachPanel.SURFACE : null;
          border = null;
          text = CoachPanel.MUTED;
          break;
        case SECONDARY:
        default:
          fill =
              pressed
                  ? new Color(0x1c, 0x20, 0x22)
                  : hover ? new Color(0x2a, 0x30, 0x33) : CoachPanel.RAISED;
          border = new Color(0x34, 0x3b, 0x3e);
          text = CoachPanel.TEXT;
          break;
      }
      if (!enabled) {
        g.setComposite(
            java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, 0.42f));
      }
      if (fill != null) {
        g.setColor(fill);
        g.fillRoundRect(0, 0, width - 1, height - 1, 20, 20);
      }
      if (border != null) {
        g.setColor(border);
        g.drawRoundRect(0, 0, width - 1, height - 1, 20, 20);
      }
      String label = getText() == null ? "" : getText();
      Font font =
          HumanSlTrainingStyle.fontForText(
              label, kind == Kind.PRIMARY ? Font.BOLD : Font.PLAIN, 14);
      g.setFont(font);
      FontMetrics metrics = g.getFontMetrics();
      g.setColor(text);
      int x = (width - metrics.stringWidth(label)) / 2;
      int y = (height - metrics.getHeight()) / 2 + metrics.getAscent();
      g.drawString(label, x, y);
    } finally {
      g.dispose();
    }
  }
}
