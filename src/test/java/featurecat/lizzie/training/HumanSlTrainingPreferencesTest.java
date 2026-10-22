package featurecat.lizzie.training;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import featurecat.lizzie.training.HumanSlTrainingConfig.PlayerColor;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

class HumanSlTrainingPreferencesTest {
  @Test
  void roundTripsEveryDurableCoachChoice() {
    JSONObject ui = new JSONObject().put("unrelated-setting", 42);
    HumanSlTrainingConfig selected =
        HumanSlTrainingConfig.builder()
            .mode(TrainingMode.LIVE_ANALYSIS)
            .rank(12, false)
            .playerColor(PlayerColor.WHITE)
            .pace(HumanThinkTime.Pace.SLOW)
            .handicap(2)
            .komi(0.5)
            .fromCurrentPosition(true)
            .build();

    HumanSlTrainingPreferences.store(ui, selected, true);
    HumanSlTrainingPreferences.SavedSettings restored = HumanSlTrainingPreferences.load(ui);

    assertEquals(42, ui.getInt("unrelated-setting"));
    assertEquals(TrainingMode.LIVE_ANALYSIS, restored.config().mode);
    assertEquals(OpponentPreset.RANK, restored.config().opponentPreset);
    assertEquals(12, restored.config().rank);
    assertFalse(restored.config().danRank);
    assertEquals(PlayerColor.WHITE, restored.config().playerColor);
    assertEquals(HumanThinkTime.Pace.SLOW, restored.config().pace);
    assertEquals(10, restored.config().moveTimeSeconds, "the search budget is not a setting");
    assertEquals(2, restored.config().handicap);
    assertEquals(0.5, restored.config().komi);
    assertFalse(restored.config().fromCurrentPosition);
    assertTrue(restored.advancedVisible());
  }

  @Test
  void missingSettingsUseTheProductDefaults() {
    HumanSlTrainingPreferences.SavedSettings restored =
        HumanSlTrainingPreferences.load(new JSONObject());

    assertEquals(TrainingMode.LIVE_FEEDBACK, restored.config().mode);
    assertEquals(OpponentPreset.RANK, restored.config().opponentPreset);
    assertEquals(3, restored.config().rank);
    assertTrue(restored.config().danRank);
    assertEquals(PlayerColor.RANDOM, restored.config().playerColor);
    assertEquals(10, restored.config().moveTimeSeconds);
    assertEquals(0, restored.config().handicap);
    assertEquals(7.5, restored.config().komi);
    assertFalse(restored.advancedVisible());
  }

  @Test
  void aSavedChoiceOfAnOlderModeIsKept() {
    JSONObject saved = new JSONObject().put("mode", "POST_GAME_REVIEW");
    JSONObject ui = new JSONObject().put(HumanSlTrainingPreferences.UI_CONFIG_KEY, saved);

    assertEquals(TrainingMode.POST_GAME_REVIEW, HumanSlTrainingPreferences.load(ui).config().mode);
  }

  @Test
  void liveFeedbackRoundTrips() {
    JSONObject ui = new JSONObject();
    HumanSlTrainingPreferences.store(
        ui, HumanSlTrainingConfig.builder().mode(TrainingMode.LIVE_FEEDBACK).build(), false);

    assertEquals(TrainingMode.LIVE_FEEDBACK, HumanSlTrainingPreferences.load(ui).config().mode);
  }

  @Test
  void thePaceRoundTrips() {
    JSONObject ui = new JSONObject();
    HumanSlTrainingPreferences.store(
        ui, HumanSlTrainingConfig.builder().pace(HumanThinkTime.Pace.FAST).build(), false);

    assertEquals(HumanThinkTime.Pace.FAST, HumanSlTrainingPreferences.load(ui).config().pace);
  }

  @Test
  void theOldTimeSettingBecomesAPace() {
    assertEquals(HumanThinkTime.Pace.NATURAL, paceAfterSaving(10));
    assertEquals(HumanThinkTime.Pace.NATURAL, paceAfterSaving(30));
    assertEquals(HumanThinkTime.Pace.SLOW, paceAfterSaving(60));
    assertEquals(HumanThinkTime.Pace.SLOW, paceAfterSaving(24 * 60 * 60));
  }

  private static HumanThinkTime.Pace paceAfterSaving(int moveTimeSeconds) {
    JSONObject saved = new JSONObject().put("move-time-seconds", moveTimeSeconds);
    JSONObject ui = new JSONObject().put(HumanSlTrainingPreferences.UI_CONFIG_KEY, saved);
    return HumanSlTrainingPreferences.load(ui).config().pace;
  }

  @Test
  void theRetiredProPresetsBecomeNineDan() {
    for (String preset : new String[] {"MODERN_PRO", "ONLINE_9D"}) {
      JSONObject saved =
          new JSONObject().put("opponent-preset", preset).put("rank", 2).put("dan-rank", false);
      JSONObject ui = new JSONObject().put(HumanSlTrainingPreferences.UI_CONFIG_KEY, saved);

      HumanSlTrainingConfig config = HumanSlTrainingPreferences.load(ui).config();

      assertEquals(OpponentPreset.RANK, config.opponentPreset, preset);
      assertEquals("rank_9d", config.humanSlProfile(), preset);
    }
  }

  @Test
  void legacyLiveCorrectionSettingsRestoreAsLiveAnalysis() {
    JSONObject saved =
        new JSONObject()
            .put("mode", "LIVE_CORRECTION")
            .put("opponent-preset", "MODERN_PRO")
            .put("player-color", "BLACK");
    JSONObject ui = new JSONObject().put(HumanSlTrainingPreferences.UI_CONFIG_KEY, saved);

    HumanSlTrainingPreferences.SavedSettings restored = HumanSlTrainingPreferences.load(ui);

    assertEquals(TrainingMode.LIVE_ANALYSIS, restored.config().mode);
    assertEquals(OpponentPreset.RANK, restored.config().opponentPreset);
    assertEquals(PlayerColor.BLACK, restored.config().playerColor);
  }

  @Test
  void corruptOrFutureValuesFallBackWithoutBreakingTheDialog() {
    JSONObject saved =
        new JSONObject()
            .put("mode", "FUTURE_MODE")
            .put("opponent-preset", "UNKNOWN_STYLE")
            .put("rank", 99)
            .put("dan-rank", false)
            .put("player-color", "PURPLE")
            .put("move-time-seconds", 17)
            .put("handicap", 99)
            .put("komi", "not-a-number")
            .put("advanced-visible", true);
    JSONObject ui = new JSONObject().put(HumanSlTrainingPreferences.UI_CONFIG_KEY, saved);

    HumanSlTrainingPreferences.SavedSettings restored = HumanSlTrainingPreferences.load(ui);

    assertEquals(TrainingMode.LIVE_FEEDBACK, restored.config().mode);
    assertEquals(OpponentPreset.RANK, restored.config().opponentPreset);
    assertEquals(20, restored.config().rank);
    assertFalse(restored.config().danRank);
    assertEquals(PlayerColor.RANDOM, restored.config().playerColor);
    assertEquals(10, restored.config().moveTimeSeconds);
    assertEquals(9, restored.config().handicap);
    assertEquals(7.5, restored.config().komi);
    assertTrue(restored.advancedVisible());
  }
}
