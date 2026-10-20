package featurecat.lizzie.gui;

import featurecat.lizzie.analysis.MoveRankDefinition;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.geom.RoundRectangle2D;
import java.util.Map;

/**
 * Colours, type scale and spacing of the player-strength dialog. The light variant goes with the
 * classic and Morandi themes, the dark variant with the Apple style.
 */
final class StrengthStyle {
  final boolean dark;
  final Color background;
  final Color surface;
  final Color sunken;
  final Color raised;
  final Color pressed;
  final Color border;
  final Color divider;
  final Color text;
  final Color secondaryText;
  final Color tertiaryText;
  final Color accent;
  final Color accentSoft;
  final Color onAccent;
  final Color caution;
  final Color negative;
  final Color attention;
  final Color blackShare;
  final Color whiteShare;
  private final boolean morandi;
  private final String fontFamily;
  private final int baseSize;

  StrengthStyle(boolean dark, boolean morandi, String fontFamily, int baseSize) {
    this.dark = dark;
    this.morandi = morandi;
    this.fontFamily = fontFamily == null || fontFamily.isBlank() ? Font.DIALOG : fontFamily;
    this.baseSize = Math.max(10, baseSize);
    background = dark ? new Color(24, 26, 30) : new Color(246, 245, 242);
    surface = dark ? new Color(34, 37, 42) : Color.WHITE;
    sunken = dark ? new Color(45, 49, 55) : new Color(239, 237, 232);
    raised = dark ? new Color(70, 76, 86) : Color.WHITE;
    pressed = dark ? new Color(56, 61, 69) : new Color(228, 225, 218);
    border = dark ? new Color(57, 62, 70) : new Color(226, 223, 216);
    divider = dark ? new Color(47, 51, 58) : new Color(238, 235, 229);
    text = dark ? new Color(240, 243, 246) : new Color(28, 27, 25);
    secondaryText = dark ? new Color(170, 178, 186) : new Color(96, 92, 85);
    tertiaryText = dark ? new Color(138, 146, 156) : new Color(120, 115, 106);
    accent = dark ? new Color(91, 199, 176) : new Color(15, 118, 110);
    accentSoft = dark ? new Color(37, 76, 69) : new Color(226, 241, 238);
    onAccent = dark ? new Color(10, 32, 28) : Color.WHITE;
    caution = dark ? new Color(239, 194, 112) : new Color(176, 92, 18);
    negative = dark ? new Color(246, 160, 151) : new Color(185, 28, 28);
    attention = dark ? new Color(224, 164, 76) : new Color(232, 153, 40);
    blackShare = dark ? new Color(8, 9, 10) : new Color(40, 40, 38);
    whiteShare = dark ? new Color(206, 203, 196) : new Color(214, 210, 201);
  }

  /** A font of the dialog's family {@code delta} points away from the interface font size. */
  Font font(int style, int delta) {
    return FittedFont.withKerning(new Font(fontFamily, style, Math.max(9, baseSize + delta)));
  }

  /** Scales a length laid out for the default 12-point interface font to the configured size. */
  int px(double length) {
    return (int) Math.round(length * Math.max(1.0, baseSize / 12.0));
  }

  Color rankColor(MoveRankDefinition.Rank rank) {
    return rank.color(morandi);
  }

  static Color alpha(Color color, int alpha) {
    return new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha);
  }

  /** The opaque colour {@code amount} of the way from {@code from} to {@code to}. */
  static Color mix(Color from, Color to, double amount) {
    return new Color(
        (int) Math.round(from.getRed() + (to.getRed() - from.getRed()) * amount),
        (int) Math.round(from.getGreen() + (to.getGreen() - from.getGreen()) * amount),
        (int) Math.round(from.getBlue() + (to.getBlue() - from.getBlue()) * amount));
  }

  /** Paints the rounded surface that every card of the dialog sits on. */
  void paintCard(Graphics2D g, int width, int height) {
    double arc = px(14);
    if (!dark) {
      g.setColor(new Color(0, 0, 0, 10));
      g.fill(new RoundRectangle2D.Double(0, 1, width, height - 1, arc, arc));
    }
    RoundRectangle2D card = new RoundRectangle2D.Double(0.5, 0.5, width - 1, height - 2, arc, arc);
    g.setColor(surface);
    g.fill(card);
    g.setColor(border);
    g.draw(card);
  }

  /** A copy of {@code g} that antialiases shapes and draws text the way Swing labels do. */
  static Graphics2D smooth(Graphics g) {
    Graphics2D g2 = (Graphics2D) g.create();
    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
    Object hints = Toolkit.getDefaultToolkit().getDesktopProperty("awt.font.desktophints");
    if (hints instanceof Map<?, ?> map) {
      g2.addRenderingHints(map);
    } else {
      g2.setRenderingHint(
          RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
    }
    return g2;
  }
}
