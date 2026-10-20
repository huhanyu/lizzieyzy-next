package featurecat.lizzie.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import featurecat.lizzie.AppLocale;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.ResourceBundle;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class StrengthScaleTest {
  private static final ResourceBundle BUNDLE = AppLocale.SIMPLIFIED_CHINESE.loadBundle();
  private static final StrengthStyle STYLE = new StrengthStyle(false, false, "Dialog", 12);

  @Test
  void oneKyuAndTwoDanSitClearlyApart() {
    StrengthScale scale = new StrengthScale(BUNDLE, STYLE);
    double width = 360;
    double kyu = scale.x(0.7, width);
    double dan = scale.x(2.1, width);
    double firstDan = scale.x(1.0, width);
    assertTrue(kyu < firstDan && firstDan < dan, "1.3 kyu, then the 1 dan tick, then 2.1 dan");
    assertTrue(dan - kyu >= 18, "they were 8 pixels apart on the old scale: " + (dan - kyu));
  }

  @Test
  void filledTrackIsOneUnbrokenColourPastTheFirstDan() {
    StrengthScale scale = new StrengthScale(BUNDLE, STYLE);
    int width = 300;
    BufferedImage image = new BufferedImage(width, 40, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = image.createGraphics();
    try {
      scale.paint(g, 0, 0, width, List.of(new StrengthScale.Marker(3.0, null)), true);
    } finally {
      g.dispose();
    }
    int y = scale.markerSize() / 2;
    int end = (int) scale.x(3.0, width) - scale.markerSize();
    assertTrue(end > scale.x(1.0, width) + 4, "the fill passes the 1 dan tick");
    for (int x = STYLE.px(6); x < end; x++) {
      assertEquals(STYLE.accent.getRGB(), image.getRGB(x, y), "pixel " + x + " of the fill");
    }
  }

  @Test
  void rulerRisesWithTheRankAndKeepsItsEnds() {
    StrengthScale scale = new StrengthScale(BUNDLE, STYLE);
    double previous = -1;
    for (double value = -20; value <= 14; value += 0.25) {
      double x = scale.x(value, 400);
      assertTrue(x >= previous, "at " + value);
      previous = x;
    }
    assertEquals(0, scale.x(-30, 400), 1e-6);
    assertEquals(400, scale.x(20, 400), 1e-6);
    assertEquals(1, StrengthScale.tier(1.0));
    assertEquals(0, StrengthScale.tier(0.99));
    assertEquals(4, StrengthScale.tier(12.5));
  }

  @Test
  void ticksNameKyuDanAndTheProfessionalTiers() {
    List<String> labels =
        StrengthScale.ticks(BUNDLE).stream()
            .map(StrengthScale.Tick::label)
            .filter(label -> label != null)
            .collect(Collectors.toList());
    for (String expected :
        new String[] {
          "10\u7ea7",
          "5\u7ea7",
          "1\u6bb5",
          "3\u6bb5",
          "5\u6bb5",
          "7\u6bb5",
          "9\u6bb5",
          "\u804c\u4e1a",
          "\u4e00\u7ebf",
          "AI"
        }) {
      assertTrue(labels.contains(expected), expected + " in " + labels);
    }
  }

  @Test
  void narrowRulerStillPaintsInEveryLanguage() {
    for (AppLocale locale : AppLocale.values()) {
      if (locale == AppLocale.SYSTEM) {
        continue;
      }
      StrengthScale scale = new StrengthScale(locale.loadBundle(), STYLE);
      BufferedImage image = new BufferedImage(200, 60, BufferedImage.TYPE_INT_ARGB);
      Graphics2D g = image.createGraphics();
      try {
        scale.paint(
            g,
            0,
            0,
            200,
            List.of(
                new StrengthScale.Marker(2.0, Boolean.TRUE),
                new StrengthScale.Marker(2.0, Boolean.FALSE)),
            false);
      } finally {
        g.dispose();
      }
    }
  }
}
