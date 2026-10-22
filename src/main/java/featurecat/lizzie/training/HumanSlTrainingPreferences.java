package featurecat.lizzie.training;

import java.util.Objects;
import org.json.JSONObject;

/** Locale-independent persistence for the most recently started AI Coach game. */
public final class HumanSlTrainingPreferences {
  public static final String UI_CONFIG_KEY = "human-sl-training-last-settings";
  private static final int UNLIMITED_MOVE_TIME_SECONDS = 24 * 60 * 60;

  private HumanSlTrainingPreferences() {}

  public record SavedSettings(HumanSlTrainingConfig config, boolean advancedVisible) {
    public SavedSettings {
      Objects.requireNonNull(config, "config");
    }
  }

  public static SavedSettings load(JSONObject uiConfig) {
    HumanSlTrainingConfig defaults = HumanSlTrainingConfig.builder().build();
    if (uiConfig == null) {
      return new SavedSettings(defaults, false);
    }
    JSONObject saved = uiConfig.optJSONObject(UI_CONFIG_KEY);
    if (saved == null) {
      return new SavedSettings(defaults, false);
    }

    TrainingMode mode = enumValue(saved, "mode", TrainingMode.class, defaults.mode).choice();
    OpponentPreset opponent =
        enumValue(saved, "opponent-preset", OpponentPreset.class, defaults.opponentPreset);
    boolean danRank = saved.optBoolean("dan-rank", defaults.danRank);
    int rank = saved.optInt("rank", defaults.rank);
    if (opponent != OpponentPreset.RANK) {
      // The 职业棋风 and 网络 9 段 presets are gone; the strongest rank comes closest to both.
      opponent = OpponentPreset.RANK;
      rank = 9;
      danRank = true;
    }
    HumanSlTrainingConfig.PlayerColor color =
        enumValue(
            saved, "player-color", HumanSlTrainingConfig.PlayerColor.class, defaults.playerColor);
    int savedMoveTime = saved.optInt("move-time-seconds", defaults.moveTimeSeconds);
    HumanThinkTime.Pace pace =
        saved.has("pace")
            ? enumValue(saved, "pace", HumanThinkTime.Pace.class, defaults.pace)
            : paceForOldMoveTime(savedMoveTime);
    int handicap = saved.optInt("handicap", defaults.handicap);
    double komi = saved.optDouble("komi", defaults.komi);
    if (!Double.isFinite(komi)) {
      komi = defaults.komi;
    }

    HumanSlTrainingConfig config =
        HumanSlTrainingConfig.builder()
            .mode(mode)
            .opponentPreset(opponent)
            .rank(rank, danRank)
            .playerColor(color)
            .pace(pace)
            .handicap(handicap)
            .komi(komi)
            // Starting from the current board is an action for one game, not a durable default.
            .fromCurrentPosition(false)
            .build();
    return new SavedSettings(config, saved.optBoolean("advanced-visible", false));
  }

  public static void store(
      JSONObject uiConfig, HumanSlTrainingConfig config, boolean advancedVisible) {
    Objects.requireNonNull(uiConfig, "uiConfig");
    Objects.requireNonNull(config, "config");
    JSONObject saved = new JSONObject();
    saved.put("mode", config.mode.name());
    saved.put("opponent-preset", config.opponentPreset.name());
    saved.put("rank", config.rank);
    saved.put("dan-rank", config.danRank);
    saved.put("player-color", config.playerColor.name());
    saved.put("pace", config.pace.name());
    saved.put("handicap", config.handicap);
    saved.put("komi", config.komi);
    saved.put("advanced-visible", advancedVisible);
    uiConfig.put(UI_CONFIG_KEY, saved);
  }

  /**
   * The old 用时 setting chose the AI's time per move. Its successor, AI 思考时间, only paces the reply:
   * a minute or no limit becomes 慢, anything shorter 自然.
   */
  static HumanThinkTime.Pace paceForOldMoveTime(int seconds) {
    return seconds == 60 || seconds == UNLIMITED_MOVE_TIME_SECONDS
        ? HumanThinkTime.Pace.SLOW
        : HumanThinkTime.Pace.NATURAL;
  }

  private static <E extends Enum<E>> E enumValue(
      JSONObject saved, String key, Class<E> type, E fallback) {
    String value = saved.optString(key, "").trim();
    if (value.isEmpty()) {
      return fallback;
    }
    try {
      return Enum.valueOf(type, value);
    } catch (IllegalArgumentException ignored) {
      return fallback;
    }
  }
}
