package featurecat.lizzie.fox;

import static org.junit.jupiter.api.Assertions.assertEquals;

import featurecat.lizzie.analysis.KataGoRules;
import java.util.Locale;
import java.util.ResourceBundle;
import org.junit.jupiter.api.Test;

class FoxRulesTextTest {
  private static final ResourceBundle ZH =
      ResourceBundle.getBundle("l10n.DisplayStrings", Locale.SIMPLIFIED_CHINESE);

  private static KataGoRules preset(String name) {
    return KataGoRules.parse(name).orElseThrow();
  }

  @Test
  void presetsWithIdenticalFieldsShareOneLabel() {
    assertEquals("中国规则", FoxRulesText.describe(preset("chinese"), ZH));
    assertEquals("日韩规则", FoxRulesText.describe(preset("japanese"), ZH));
    assertEquals("日韩规则", FoxRulesText.describe(preset("korean"), ZH));
    assertEquals(FoxRulesText.describe(preset("chinese-ogs"), ZH),
        FoxRulesText.describe(preset("chinese-kgs"), ZH));
    assertEquals(FoxRulesText.describe(preset("aga"), ZH), FoxRulesText.describe(preset("bga"), ZH));
  }

  @Test
  void everyOfficialPresetHasAName() {
    for (String name : KataGoRules.officialPresetNames()) {
      String text = FoxRulesText.describe(preset(name), ZH);
      assertEquals(-1, text.indexOf('='), name + " -> " + text);
      assertEquals(-1, text.indexOf('；'), name + " -> " + text);
    }
  }

  @Test
  void customRulesUseTheRulesDialogWording() {
    KataGoRules custom =
        preset("chinese").overlayEditor("AREA", "SITUATIONAL", false, "ALL", "N-1", false, true);
    assertEquals(
        "胜负判断：数子；打劫规则：同一方落子后禁全同；允许棋块自杀：否；还棋头：所有棋块；"
            + "让子贴还(让N子)：贴还N-1目；数子规则下收后方贴还0.5目：否；未提净死子时允许停一手：是",
        FoxRulesText.describe(custom, ZH));
  }

  @Test
  void valuesAndFieldsWithoutDialogWordingStayRaw() {
    KataGoRules custom = KataGoRules.parse(
        "{\"ko\":\"SPIGHT\",\"scoring\":\"AREA\",\"tax\":\"NONE\",\"suicide\":false,"
            + "\"hasButton\":false,\"whiteHandicapBonus\":\"N\",\"friendlyPassOk\":true,\"extra\":7}")
        .orElseThrow();
    assertEquals(
        "胜负判断：数子；打劫规则：SPIGHT；允许棋块自杀：否；还棋头：无；"
            + "让子贴还(让N子)：贴还N目；数子规则下收后方贴还0.5目：否；未提净死子时允许停一手：是；extra：7",
        FoxRulesText.describe(custom, ZH));
  }
}
