package featurecat.lizzie.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Font;
import java.awt.font.TextAttribute;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class StrengthStyleTest {
  @Test
  void textStaysReadableOnLightAndDarkCards() {
    for (boolean dark : new boolean[] {false, true}) {
      StrengthStyle style = new StrengthStyle(dark, false, "Dialog", 12);
      String theme = dark ? "dark" : "light";
      assertContrast(theme + " text", style.text, style.surface, 9.0);
      assertContrast(theme + " secondary text", style.secondaryText, style.surface, 4.5);
      assertContrast(theme + " tertiary text", style.tertiaryText, style.surface, 4.5);
      assertContrast(theme + " accent", style.accent, style.surface, 4.5);
      assertContrast(
          theme + " secondary text on the tab track", style.secondaryText, style.sunken, 4.5);
      assertContrast(theme + " selected tab", style.text, style.raised, 4.5);
      assertContrast(theme + " title on the window", style.text, style.background, 9.0);
      assertContrast(theme + " subtitle on the window", style.secondaryText, style.background, 4.5);
      assertContrast(theme + " caution mark", style.caution, style.surface, 3.0);
      assertContrast(theme + " problem mark", style.negative, style.surface, 3.0);
    }
  }

  @Test
  void everyFontKeepsKerningSoMacOsKeepsTheDigitsOfMixedText() {
    StrengthStyle style = new StrengthStyle(false, false, "Dialog", 12);
    for (int delta : new int[] {-3, 0, 6, 22}) {
      for (int weight : new int[] {Font.PLAIN, Font.BOLD}) {
        Font font = style.font(weight, delta);
        assertEquals(
            TextAttribute.KERNING_ON, font.getAttributes().get(TextAttribute.KERNING), "" + delta);
        assertEquals(
            TextAttribute.KERNING_ON,
            font.deriveFont(30f).getAttributes().get(TextAttribute.KERNING));
      }
    }
  }

  @Test
  void lengthsGrowWithTheInterfaceFontButNeverShrink() {
    assertEquals(22, new StrengthStyle(false, false, "Dialog", 12).px(22));
    assertEquals(22, new StrengthStyle(false, false, "Dialog", 10).px(22));
    assertEquals(33, new StrengthStyle(false, false, "Dialog", 18).px(22));
  }

  static void assertContrast(String label, Color foreground, Color background, double minimum) {
    double contrast = contrast(foreground, background);
    assertTrue(
        contrast >= minimum,
        label + " contrast " + String.format(Locale.US, "%.2f", contrast) + " < " + minimum);
  }

  static double contrast(Color foreground, Color background) {
    double first = luminance(foreground);
    double second = luminance(background);
    return (Math.max(first, second) + 0.05) / (Math.min(first, second) + 0.05);
  }

  private static double luminance(Color color) {
    return 0.2126 * channel(color.getRed())
        + 0.7152 * channel(color.getGreen())
        + 0.0722 * channel(color.getBlue());
  }

  private static double channel(int value) {
    double c = value / 255.0;
    return c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
  }
}
