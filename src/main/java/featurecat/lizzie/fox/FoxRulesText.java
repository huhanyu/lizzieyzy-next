package featurecat.lizzie.fox;

import featurecat.lizzie.analysis.KataGoRules;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.TreeMap;

/** Rules in the player's language: a built-in preset name, else each field as the rules dialog words it. */
public final class FoxRulesText {
  private record Preset(String name, String key) {}

  private record Field(String name, String labelKey, Map<String, String> valueKeys) {}

  /** One entry per semantically distinct KataGo preset; aliases with identical fields share a label. */
  private static final List<Preset> PRESETS = List.of(
      new Preset("chinese", "Fox.rulesPreset.chinese"),
      new Preset("chinese-ogs", "Fox.rulesPreset.chineseOgs"),
      new Preset("japanese", "Fox.rulesPreset.japaneseKorean"),
      new Preset("stone-scoring", "Fox.rulesPreset.stoneScoring"),
      new Preset("tromp-taylor", "Fox.rulesPreset.trompTaylor"),
      new Preset("aga", "Fox.rulesPreset.agaBga"),
      new Preset("new-zealand", "Fox.rulesPreset.newZealand"),
      new Preset("aga-button", "Fox.rulesPreset.agaButton"));

  private static final List<Field> FIELDS = List.of(
      new Field(KataGoRules.SCORING, "SetKataRules.lblScoringRule", Map.of(
          "AREA", "SetKataRules.rdoArea", "TERRITORY", "SetKataRules.rdoTerritory")),
      new Field(KataGoRules.KO, "SetKataRules.lblKoRule", Map.of(
          "SIMPLE", "SetKataRules.rdoSimpleKo",
          "POSITIONAL", "SetKataRules.rdoPositionKo",
          "SITUATIONAL", "SetKataRules.rdoSituationalKo")),
      new Field(KataGoRules.SUICIDE, "SetKataRules.lblMultiStoneSuicide", Map.of(
          "true", "SetKataRules.rdoSuicide", "false", "SetKataRules.rdoNoSuicide")),
      new Field(KataGoRules.TAX, "SetKataRules.lblTaxRule", Map.of(
          "NONE", "SetKataRules.rdoNoTax", "SEKI", "SetKataRules.rdoSeKiTax", "ALL", "SetKataRules.rdoAllTax")),
      new Field(KataGoRules.WHITE_HANDICAP_BONUS, "SetKataRules.lblWhiteHandicapBonus", Map.of(
          "0", "SetKataRules.rdoNoHandicapKomi",
          "N", "SetKataRules.rdoHandicapKomiN",
          "N-1", "SetKataRules.rdoHandicapKomiN1")),
      new Field(KataGoRules.HAS_BUTTON, "SetKataRules.lblHasButton", Map.of(
          "true", "SetKataRules.rdoButtonGo", "false", "SetKataRules.rdoNoButtonGo")),
      new Field(KataGoRules.FRIENDLY_PASS_OK, "Fox.rulesFriendlyPassOk", Map.of(
          "true", "SetKataRules.rdoSuicide", "false", "SetKataRules.rdoNoSuicide")));

  private FoxRulesText() {}

  public static String describe(KataGoRules rules, ResourceBundle strings) {
    for (Preset preset : PRESETS) {
      if (KataGoRules.parse(preset.name()).orElseThrow().semanticallyEquals(rules)) {
        return strings.getString(preset.key());
      }
    }
    Map<String, Object> remaining = new TreeMap<>(rules.fields());
    List<String> items = new ArrayList<>();
    for (Field field : FIELDS) {
      Object value = remaining.remove(field.name());
      if (value == null) continue;
      String valueKey = field.valueKeys().get(String.valueOf(value));
      items.add(item(strings, label(strings.getString(field.labelKey())),
          valueKey == null ? String.valueOf(value) : strings.getString(valueKey)));
    }
    // Fields the dialog has no wording for keep their KataGo name and value.
    remaining.forEach((name, value) -> {
      if (value != null) items.add(item(strings, name, String.valueOf(value)));
    });
    return String.join(strings.getString("Fox.rulesSeparator"), items);
  }

  /** Dialog labels carry their own trailing colon; the item format supplies one per locale. */
  private static String label(String dialogLabel) {
    return dialogLabel.replaceFirst("\\\\?[:：]\\s*$", "");
  }

  private static String item(ResourceBundle strings, String label, String value) {
    return java.text.MessageFormat.format(strings.getString("Fox.rulesItem"), label, value);
  }
}
