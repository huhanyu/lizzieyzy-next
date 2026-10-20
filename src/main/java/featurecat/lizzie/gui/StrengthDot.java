package featurecat.lizzie.gui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import javax.swing.Icon;

/** The coloured dot before a status line of the strength dialog; its colour tells how it goes. */
final class StrengthDot implements Icon {
  private final StrengthStyle style;
  private final int size;
  private Color color;

  StrengthDot(StrengthStyle style, int size) {
    this.style = style;
    this.size = size;
    color = style.tertiaryText;
  }

  void setTone(StrengthDashboard.Tone tone) {
    color =
        switch (tone) {
          case WORKING -> style.caution;
          case READY -> style.accent;
          case PROBLEM -> style.negative;
          default -> style.tertiaryText;
        };
  }

  Color color() {
    return color;
  }

  @Override
  public void paintIcon(Component component, Graphics graphics, int x, int y) {
    Graphics2D g = StrengthStyle.smooth(graphics);
    try {
      StrengthPaint.dot(g, color, x + size / 2.0, y + size / 2.0, size);
    } finally {
      g.dispose();
    }
  }

  @Override
  public int getIconWidth() {
    return size;
  }

  @Override
  public int getIconHeight() {
    return size;
  }
}
