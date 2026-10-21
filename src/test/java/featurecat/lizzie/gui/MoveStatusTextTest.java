package featurecat.lizzie.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Locale;
import java.util.ResourceBundle;
import org.junit.jupiter.api.Test;

class MoveStatusTextTest {
  private static final ResourceBundle ZH =
      ResourceBundle.getBundle("l10n.DisplayStrings", Locale.SIMPLIFIED_CHINESE);
  private static final ResourceBundle EN =
      ResourceBundle.getBundle("l10n.DisplayStrings", Locale.US);

  @Test
  void leadNamesTheSideAheadInsteadOfANegativeBlackLead() {
    assertEquals("白领先 7.3 目", MoveStatusText.lead(ZH, -7.3));
    assertEquals("黑领先 2.0 目", MoveStatusText.lead(ZH, 2.04));
    assertEquals("均势", MoveStatusText.lead(ZH, -0.04));
    assertEquals("White leads by 7.3", MoveStatusText.lead(EN, -7.3));
  }

  @Test
  void previousMoveShowsTheCandidateRankOnlyWhenTheMoveWasACandidate() {
    assertEquals("上一手(#3) -0.3% -1.9目", MoveStatusText.previousMove(ZH, 3, -0.34, -1.88));
    assertEquals("上一手 +1.2%", MoveStatusText.previousMove(ZH, 0, 1.2, null));
    assertEquals("上一手(#1) 0.0% 0.0目", MoveStatusText.previousMove(ZH, 1, -0.01, 0.02));
  }

  @Test
  void statusLineIsOneShortLineWithTheKeysToPress() {
    assertEquals(
        "KataGo · 分析中 · 空格 暂停 · 按住 X 看快捷键",
        StatusOverlay.line(
            "KataGo",
            StatusOverlay.state(ZH, false, true),
            StatusOverlay.hint(ZH, false, true, false)));
    assertEquals(
        "KataGo · 已暂停 · 空格 继续",
        StatusOverlay.line(
            "KataGo",
            StatusOverlay.state(ZH, false, false),
            StatusOverlay.hint(ZH, false, false, true)));
    assertEquals(
        "KataGo · 自动分析中",
        StatusOverlay.line(
            "KataGo",
            StatusOverlay.state(ZH, true, false),
            StatusOverlay.hint(ZH, true, false, true)),
        "the main engine is paused while a game is analysed automatically, so no pause hint");
  }

  @Test
  void statusStripReservesOneLineOnceTheEngineIsLoaded() {
    int oneLine = StatusOverlay.reservedHeight(12, true);
    int loading = StatusOverlay.reservedHeight(12, false);
    assertTrue(oneLine < 32, "one small line, not the old three-line strip: " + oneLine);
    assertTrue(loading > oneLine, "a loading cause and its remedy keep two lines");
  }
}
