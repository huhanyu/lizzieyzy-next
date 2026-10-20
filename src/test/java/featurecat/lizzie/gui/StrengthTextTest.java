package featurecat.lizzie.gui;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import featurecat.lizzie.AppLocale;
import featurecat.lizzie.analysis.PlayerStrengthEstimator;
import java.util.ResourceBundle;
import org.junit.jupiter.api.Test;

class StrengthTextTest {
  private static final String TOP_PRO_BAND = "11d\u4e00\u7ebf\u804c\u4e1a";
  private static final String PRO_BAND = "10d\u804c\u4e1a";

  @Test
  void chineseBandsUseDanAndKyuInsteadOfMachineTranslatedWords() {
    ResourceBundle simplified = AppLocale.SIMPLIFIED_CHINESE.loadBundle();
    assertEquals("\u4e1a\u4f593-4\u6bb5", StrengthText.band("3-4d", simplified));
    assertEquals("\u4e1a\u4f596-10\u7ea7", StrengthText.band("6-10k", simplified));
    assertEquals("\u4e1a\u4f597\u6bb5", StrengthText.band("7d", simplified));
    ResourceBundle traditional = AppLocale.TRADITIONAL_CHINESE.loadBundle();
    assertEquals("\u696d\u99183-4\u6bb5", StrengthText.band("3-4d", traditional));
    assertEquals("\u696d\u991811-15\u7d1a", StrengthText.band("11-15k", traditional));
  }

  @Test
  void everyLanguageNamesEveryBandInItsOwnWords() {
    for (AppLocale locale : AppLocale.values()) {
      if (locale == AppLocale.SYSTEM) {
        continue;
      }
      ResourceBundle bundle = locale.loadBundle();
      String danRange = StrengthText.band("3-4d", bundle);
      assertTrue(danRange.contains("3") && danRange.contains("4"), locale + ": " + danRange);
      String kyuRange = StrengthText.band("11-15k", bundle);
      assertTrue(kyuRange.contains("11") && kyuRange.contains("15"), locale + ": " + kyuRange);
      assertEquals(
          bundle.getString("PlayerStrengthEstimate.rank.topPro"),
          StrengthText.band(TOP_PRO_BAND, bundle));
      assertEquals(
          bundle.getString("PlayerStrengthEstimate.rank.pro"), StrengthText.band(PRO_BAND, bundle));
      assertEquals(
          bundle.getString("PlayerStrengthEstimate.rank.ai"), StrengthText.band("12d AI", bundle));
      assertEquals(
          bundle.getString("PlayerStrengthEstimate.rank.beginner"),
          StrengthText.band("Beginner", bundle));
      for (String band : new String[] {"3-4d", "11-15k", "7d", TOP_PRO_BAND, "12d AI"}) {
        String text = StrengthText.band(band, bundle);
        assertFalse(text.contains("__"), locale + " has a placeholder in " + text);
        assertFalse(text.contains("god"), locale + " has untranslated text in " + text);
      }
    }
  }

  @Test
  void englishNamesProfessionalBandsInEnglish() {
    ResourceBundle english = AppLocale.ENGLISH.loadBundle();
    assertEquals("Top professional", StrengthText.band(TOP_PRO_BAND, english));
    assertEquals("Professional", StrengthText.band(PRO_BAND, english));
    assertEquals("3-4 dan", StrengthText.band("3-4d", english));
    assertEquals("-", StrengthText.band("-", english));
    assertEquals("-", StrengthText.band(null, english));
  }

  @Test
  void rankNumberIsSplitFromItsUnitSoItCanBeSetLarger() {
    assertArrayEquals(
        new String[] {"\u4e1a\u4f59", "9.0", "\u6bb5"},
        StrengthText.splitNumber("\u4e1a\u4f599.0\u6bb5"));
    assertArrayEquals(new String[] {"", "9.0", "dan"}, StrengthText.splitNumber("9.0 dan"));
    assertArrayEquals(
        new String[] {"\uc544\ub9c8", "9.0", "\ub2e8"},
        StrengthText.splitNumber("\uc544\ub9c8 9.0\ub2e8"));
    assertArrayEquals(
        new String[] {"", "12.3", "Semi-god/AI"}, StrengthText.splitNumber("12.3 Semi-god/AI"));
    assertArrayEquals(new String[] {"", "-", ""}, StrengthText.splitNumber("-"));
  }

  @Test
  void tiersFollowTheRankScaleThresholds() {
    assertEquals(0, StrengthText.tier(Double.NaN));
    assertEquals(0, StrengthText.tier(9.99));
    assertEquals(1, StrengthText.tier(10.0));
    assertEquals(2, StrengthText.tier(11.5));
    assertEquals(3, StrengthText.tier(12.0));
    assertEquals(3, StrengthText.tier(13.0));
  }

  @Test
  void modelNamesAreReadable() {
    assertEquals(
        "XGBoost 20TUN",
        StrengthText.modelName(PlayerStrengthEstimator.StrengthModel.XGBOOST20TUN));
    assertEquals(
        "HumanSL 40", StrengthText.modelName(PlayerStrengthEstimator.StrengthModel.HUMANSL40));
    assertEquals("-", StrengthText.modelName(null));
  }

  @Test
  void scaleTooltipListsEveryTierInFull() throws Exception {
    ResourceBundle bundle = AppLocale.SIMPLIFIED_CHINESE.loadBundle();
    String tooltip = StrengthText.scaleTooltip(StrengthFixtures.twoMoveReport().black, bundle);
    assertTrue(tooltip.contains("12+"));
    assertTrue(tooltip.contains("11+"));
    assertTrue(tooltip.contains("10+"));
    assertTrue(tooltip.contains("&lt;10"));
    assertFalse(tooltip.contains("..."));
    assertFalse(tooltip.contains("\u2026"));
  }
}
