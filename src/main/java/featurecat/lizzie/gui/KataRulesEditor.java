package featurecat.lizzie.gui;

import featurecat.lizzie.Lizzie;
import featurecat.lizzie.analysis.KataGoRules;
import java.awt.Container;
import java.awt.Insets;
import java.util.ResourceBundle;
import javax.swing.AbstractButton;
import javax.swing.ButtonGroup;
import org.json.JSONObject;

/**
 * Rules editor rows shared by the KataGo and analysis rules dialogs. A preset button fills every
 * rule field with that preset; territory scoring locks the area-only fields.
 */
final class KataRulesEditor {
  /** First free y coordinate below the editor rows. */
  static final int BOTTOM = 302;

  final JFontRadioButton rdoArea;
  final JFontRadioButton rdoTerritory;
  final JFontRadioButton rdoSimpleKo;
  final JFontRadioButton rdoPositionKo;
  final JFontRadioButton rdoSituationalKo;
  final JFontRadioButton rdoSuicide;
  final JFontRadioButton rdoNoSuicide;
  final JFontRadioButton rdoNoTax;
  final JFontRadioButton rdoSeKiTax;
  final JFontRadioButton rdoAllTax;
  final JFontRadioButton rdoNoHandicapKomi;
  final JFontRadioButton rdoHandicapKomiN;
  final JFontRadioButton rdoHandicapKomiN1;
  final JFontRadioButton rdoButtonGo;
  final JFontRadioButton rdoNoButtonGo;
  final JFontRadioButton rdoFriendlyPass;
  final JFontRadioButton rdoNoFriendlyPass;
  final JFontButton btnChnRule;
  final JFontButton btnJpnRule;
  final JFontButton btnChnOldRule;
  final JFontButton btnTTRule;
  final JFontButton btnGoeRule;

  private final Container pane;

  KataRulesEditor(Container pane, ResourceBundle strings) {
    this.pane = pane;
    boolean small = Lizzie.config.isFrameFontSmall();
    boolean middle = Lizzie.config.isFrameFontMiddle();
    int thirdColumn = small || middle ? 401 : 450;
    int secondColumnWidth = small || middle ? 119 : 130;

    label(strings, "SetKataRules.lblClassicRules", 10, 9, 91);
    btnChnRule = presetButton(strings, "SetKataRules.btnChnRule", "chinese", 99, 93);
    btnJpnRule = presetButton(strings, "SetKataRules.btnJpnRule", "japanese", 202, 93);
    btnChnOldRule = presetButton(strings, "SetKataRules.btnChnOldRule", "stone-scoring", 305, 101);
    int ttWidth = small || middle ? 142 : 180;
    btnTTRule = presetButton(strings, "SetKataRules.btnTTRule", "tromp-taylor", 416, ttWidth);
    btnGoeRule =
        presetButton(
            strings,
            "SetKataRules.btnGoeRule",
            "goe",
            416 + ttWidth + 10,
            small ? 110 : (middle ? 120 : 150));

    label(strings, "SetKataRules.lblScoringRule", 21, 48, 146);
    rdoArea = radio(strings, "SetKataRules.rdoArea", 173, 50, 105);
    rdoTerritory = radio(strings, "SetKataRules.rdoTerritory", 280, 50, 119);
    group(rdoArea, rdoTerritory);
    rdoArea.addActionListener(event -> updateScoringLocks());
    rdoTerritory.addActionListener(event -> updateScoringLocks());

    label(strings, "SetKataRules.lblKoRule", 21, 85, 129);
    rdoSimpleKo = radio(strings, "SetKataRules.rdoSimpleKo", 173, 87, 105);
    rdoPositionKo = radio(strings, "SetKataRules.rdoPositionKo", 280, 87, secondColumnWidth);
    rdoSituationalKo = radio(strings, "SetKataRules.rdoSituationalKo", thirdColumn, 87, 295);
    group(rdoSimpleKo, rdoPositionKo, rdoSituationalKo);

    label(strings, "SetKataRules.lblMultiStoneSuicide", 21, 122, 146);
    rdoSuicide = radio(strings, "SetKataRules.rdoSuicide", 173, 124, 105);
    rdoNoSuicide = radio(strings, "SetKataRules.rdoNoSuicide", 280, 124, 119);
    group(rdoSuicide, rdoNoSuicide);

    label(strings, "SetKataRules.lblTaxRule", 21, 159, 146);
    rdoNoTax = radio(strings, "SetKataRules.rdoNoTax", 173, 161, 84);
    rdoSeKiTax = radio(strings, "SetKataRules.rdoSeKiTax", 280, 161, secondColumnWidth);
    rdoAllTax = radio(strings, "SetKataRules.rdoAllTax", thirdColumn, 161, 143);
    group(rdoNoTax, rdoSeKiTax, rdoAllTax);

    label(strings, "SetKataRules.lblWhiteHandicapBonus", 21, 196, 153);
    rdoNoHandicapKomi = radio(strings, "SetKataRules.rdoNoHandicapKomi", 173, 198, 105);
    rdoHandicapKomiN = radio(strings, "SetKataRules.rdoHandicapKomiN", 280, 198, 119);
    rdoHandicapKomiN1 = radio(strings, "SetKataRules.rdoHandicapKomiN1", thirdColumn, 198, 143);
    group(rdoNoHandicapKomi, rdoHandicapKomiN, rdoHandicapKomiN1);

    label(strings, "SetKataRules.lblHasButton", 21, 233, 256);
    rdoButtonGo = radio(strings, "SetKataRules.rdoButtonGo", 280, 235, 105);
    rdoNoButtonGo = radio(strings, "SetKataRules.rdoNoButtonGo", thirdColumn, 235, 143);
    group(rdoButtonGo, rdoNoButtonGo);

    label(strings, "SetKataRules.lblFriendlyPassOk", 21, 270, 256);
    rdoFriendlyPass = radio(strings, "SetKataRules.rdoButtonGo", 280, 272, 105);
    rdoNoFriendlyPass = radio(strings, "SetKataRules.rdoNoButtonGo", thirdColumn, 272, 143);
    group(rdoFriendlyPass, rdoNoFriendlyPass);
  }

  /** Dialog width that fits the preset row at the current font size. */
  static int dialogWidth() {
    if (Lizzie.config.isFrameFontSmall()) return 700;
    return Lizzie.config.isFrameFontMiddle() ? 720 : 890;
  }

  void applyPreset(String name) {
    apply(KataGoRules.parse(name).orElseThrow().toJson());
  }

  /** Shows a rules JSON; absent booleans follow KataGo's JSON defaults (false). */
  void apply(JSONObject rules) {
    if (rules == null) {
      return;
    }
    select(rules.optBoolean(KataGoRules.HAS_BUTTON, false), rdoButtonGo, rdoNoButtonGo);
    select(rules.optBoolean(KataGoRules.SUICIDE, false), rdoSuicide, rdoNoSuicide);
    select(
        rules.optBoolean(KataGoRules.FRIENDLY_PASS_OK, false), rdoFriendlyPass, rdoNoFriendlyPass);
    switch (rules.optString(KataGoRules.KO, "")) {
      case "SIMPLE" -> rdoSimpleKo.setSelected(true);
      case "POSITIONAL" -> rdoPositionKo.setSelected(true);
      case "SITUATIONAL" -> rdoSituationalKo.setSelected(true);
      default -> {}
    }
    switch (rules.optString(KataGoRules.SCORING, "")) {
      case "AREA" -> rdoArea.setSelected(true);
      case "TERRITORY" -> rdoTerritory.setSelected(true);
      default -> {}
    }
    switch (rules.optString(KataGoRules.WHITE_HANDICAP_BONUS, "")) {
      case "0" -> rdoNoHandicapKomi.setSelected(true);
      case "N" -> rdoHandicapKomiN.setSelected(true);
      case "N-1" -> rdoHandicapKomiN1.setSelected(true);
      default -> {}
    }
    switch (rules.optString(KataGoRules.TAX, "")) {
      case "NONE" -> rdoNoTax.setSelected(true);
      case "SEKI" -> rdoSeKiTax.setSelected(true);
      case "ALL" -> rdoAllTax.setSelected(true);
      default -> {}
    }
    updateScoringLocks();
  }

  /** Edited fields over {@code base}, keeping fields the editor does not show. */
  KataGoRules overlay(KataGoRules base) {
    if (base == null) {
      return KataGoRules.fromJson(toJson());
    }
    return base.overlayEditor(
        scoring(),
        ko(),
        rdoSuicide.isSelected(),
        tax(),
        whiteHandicapBonus(),
        rdoButtonGo.isSelected(),
        rdoFriendlyPass.isSelected());
  }

  /** Edited fields as KataGo rules JSON; unselected choice fields are omitted. */
  JSONObject toJson() {
    JSONObject json = new JSONObject();
    putIfSelected(json, KataGoRules.SCORING, scoring());
    putIfSelected(json, KataGoRules.KO, ko());
    json.put(KataGoRules.SUICIDE, rdoSuicide.isSelected());
    putIfSelected(json, KataGoRules.TAX, tax());
    putIfSelected(json, KataGoRules.WHITE_HANDICAP_BONUS, whiteHandicapBonus());
    json.put(KataGoRules.HAS_BUTTON, rdoButtonGo.isSelected());
    json.put(KataGoRules.FRIENDLY_PASS_OK, rdoFriendlyPass.isSelected());
    return json;
  }

  /** Territory scoring ignores the button and agreed dead stones always hold, so both lock. */
  private void updateScoringLocks() {
    boolean territory = rdoTerritory.isSelected();
    if (territory) {
      rdoFriendlyPass.setSelected(true);
    }
    rdoButtonGo.setEnabled(!territory);
    rdoNoButtonGo.setEnabled(!territory);
    rdoFriendlyPass.setEnabled(!territory);
    rdoNoFriendlyPass.setEnabled(!territory);
  }

  private String scoring() {
    return rdoArea.isSelected() ? "AREA" : rdoTerritory.isSelected() ? "TERRITORY" : "";
  }

  private String ko() {
    return rdoSimpleKo.isSelected()
        ? "SIMPLE"
        : rdoPositionKo.isSelected() ? "POSITIONAL" : rdoSituationalKo.isSelected() ? "SITUATIONAL" : "";
  }

  private String tax() {
    return rdoNoTax.isSelected()
        ? "NONE"
        : rdoSeKiTax.isSelected() ? "SEKI" : rdoAllTax.isSelected() ? "ALL" : "";
  }

  private String whiteHandicapBonus() {
    return rdoNoHandicapKomi.isSelected()
        ? "0"
        : rdoHandicapKomiN.isSelected() ? "N" : rdoHandicapKomiN1.isSelected() ? "N-1" : "";
  }

  private static void putIfSelected(JSONObject json, String key, String value) {
    if (!value.isEmpty()) {
      json.put(key, value);
    }
  }

  private static void select(boolean yes, AbstractButton yesButton, AbstractButton noButton) {
    (yes ? yesButton : noButton).setSelected(true);
  }

  private void label(ResourceBundle strings, String key, int x, int y, int width) {
    JFontLabel label = new JFontLabel(strings.getString(key));
    label.setBounds(x, y, width, 27);
    pane.add(label);
  }

  private JFontRadioButton radio(ResourceBundle strings, String key, int x, int y, int width) {
    JFontRadioButton radio = new JFontRadioButton(strings.getString(key));
    radio.setBounds(x, y, width, 23);
    pane.add(radio);
    return radio;
  }

  private JFontButton presetButton(
      ResourceBundle strings, String key, String preset, int x, int width) {
    JFontButton button = new JFontButton(strings.getString(key));
    button.setMargin(new Insets(0, 0, 0, 0));
    button.setFocusable(false);
    button.addActionListener(event -> applyPreset(preset));
    button.setBounds(x, 11, width, 23);
    pane.add(button);
    return button;
  }

  private static void group(AbstractButton... buttons) {
    ButtonGroup group = new ButtonGroup();
    for (AbstractButton button : buttons) {
      group.add(button);
    }
  }
}
