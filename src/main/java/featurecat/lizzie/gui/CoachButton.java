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
    QUIET,
    /** A teal outline, such as 提示 and 再给一步. */
    ACCENT,
    /** A secondary button on the warm 恶手 card. */
    WARM,
    /** A text-only button on the warm 恶手 card. */
    WARM_QUIET
  }

  private Kind kind;
  private String shortcut;

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

  static CoachButton accent() {
    return new CoachButton(Kind.ACCENT);
  }

  /** A key drawn faintly after the label, such as "H". */
  void setShortcut(String value) {
    shortcut = value;
    repaint();
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
        case ACCENT:
          fill = new Color(61, 190, 170, hover ? 48 : 31);
          border = new Color(0x2f, 0x7f, 0x73);
          text = CoachPanel.ACCENT_TEXT;
          break;
        case WARM:
          fill = hover ? new Color(0x3d, 0x2a, 0x26) : new Color(0x33, 0x23, 0x1f);
          border = new Color(0x5a, 0x44, 0x41);
          text = new Color(0xf4, 0xe2, 0xdf);
          break;
        case WARM_QUIET:
          fill = hover ? new Color(0x33, 0x23, 0x1f) : null;
          border = null;
          text = new Color(0xd9, 0xbd, 0xb8);
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
      boolean bold = kind == Kind.PRIMARY || kind == Kind.ACCENT;
      Font font = HumanSlTrainingStyle.fontForText(label, bold ? Font.BOLD : Font.PLAIN, 14);
      g.setFont(font);
      FontMetrics metrics = g.getFontMetrics();
      String key = shortcut == null ? "" : shortcut;
      Font keyFont = HumanSlTrainingStyle.fontForText(key, Font.PLAIN, 12);
      int keyWidth = key.isEmpty() ? 0 : 8 + g.getFontMetrics(keyFont).stringWidth(key);
      int x = (width - metrics.stringWidth(label) - keyWidth) / 2;
      int y = (height - metrics.getHeight()) / 2 + metrics.getAscent();
      g.setColor(text);
      g.drawString(label, x, y);
      if (!key.isEmpty()) {
        g.setFont(keyFont);
        g.setColor(new Color(text.getRed(), text.getGreen(), text.getBlue(), 150));
        g.drawString(key, x + metrics.stringWidth(label) + 8, y);
      }
    } finally {
      g.dispose();
    }
  }
}
