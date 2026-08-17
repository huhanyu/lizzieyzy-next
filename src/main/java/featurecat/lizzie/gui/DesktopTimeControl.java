package featurecat.lizzie.gui;

import featurecat.lizzie.Config;
import featurecat.lizzie.analysis.Leelaz;
import featurecat.lizzie.analysis.remote.RemoteComputeConfig;
import java.util.List;

public final class DesktopTimeControl {
  public enum Mode {
    ENGINE_OWNED,
    FIXED,
    RAW_ADVANCED,
    KATAGO_ADVANCED
  }

  public enum SideMode {
    ENGINE_OWNED,
    FIXED,
    RAW_ADVANCED
  }

  private DesktopTimeControl() {}

  static Mode selectedMode(boolean rawAdvanced, boolean kataGoAdvanced) {
    return selectedMode(rawAdvanced, kataGoAdvanced, false);
  }

  static Mode selectedMode(boolean rawAdvanced, boolean kataGoAdvanced, boolean engineOwned) {
    if (engineOwned) return Mode.ENGINE_OWNED;
    if (rawAdvanced) return Mode.RAW_ADVANCED;
    if (kataGoAdvanced) return Mode.KATAGO_ADVANCED;
    return Mode.FIXED;
  }

  static boolean rejectsHumanGame(Leelaz engine, Mode mode, boolean noClock) {
    return !noClock
        && mode != Mode.ENGINE_OWNED
        && mode != Mode.FIXED
        && isWebSocket(engine);
  }

  public static boolean rejectsEngineGame(
      List<Leelaz> engines, int blackEngineIndex, int whiteEngineIndex, boolean advancedClock) {
    SideMode mode = advancedClock ? SideMode.RAW_ADVANCED : SideMode.FIXED;
    return rejectsEngineGame(engines, blackEngineIndex, whiteEngineIndex, mode, mode);
  }

  public static boolean rejectsEngineGame(
      List<Leelaz> engines,
      int blackEngineIndex,
      int whiteEngineIndex,
      SideMode black,
      SideMode white) {
    return (black == SideMode.RAW_ADVANCED && isWebSocket(engines.get(blackEngineIndex)))
        || (white == SideMode.RAW_ADVANCED && isWebSocket(engines.get(whiteEngineIndex)));
  }

  static boolean submitHumanSelection(
      Config config,
      Leelaz engine,
      Mode mode,
      int kataTimeType,
      boolean noClock,
      Runnable unsupportedWarning) {
    if (rejectsHumanGame(engine, mode, noClock)) {
      unsupportedWarning.run();
      return false;
    }
    commitHumanSelection(config, mode, kataTimeType);
    return true;
  }

  static void commitHumanSelection(Config config, Mode mode, int kataTimeType) {
    config.advanceTimeSettings = mode == Mode.RAW_ADVANCED;
    config.kataTimeSettings = mode == Mode.KATAGO_ADVANCED;
    config.genmoveGameNoTime = mode == Mode.ENGINE_OWNED;
    config.kataTimeType = kataTimeType;
    config.uiConfig.put("advance-time-settings", config.advanceTimeSettings);
    config.uiConfig.put("kata-time-settings", config.kataTimeSettings);
    config.uiConfig.put("genmove-game-notime", config.genmoveGameNoTime);
    config.uiConfig.put("kata-time-type", config.kataTimeType);
  }

  public static boolean shouldEmitClientTimeOverride(Mode mode) {
    return mode != Mode.ENGINE_OWNED;
  }

  public static boolean shouldSendHumanTimeOnEngineReady(
      boolean genmovePlaying, boolean analysisPlaying) {
    return genmovePlaying && !analysisPlaying;
  }

  static void commitEngineGameSelection(Config config, boolean advancedClock) {
    config.pkAdvanceTimeSettings = advancedClock;
    config.uiConfig.put("pk-advance-time-settings", advancedClock);
  }

  public static void commitEngineGameSelection(Config config, SideMode black, SideMode white) {
    config.uiConfig.put("pk-black-time-mode", black.name());
    config.uiConfig.put("pk-white-time-mode", white.name());
    config.pkAdvanceTimeSettings =
        black == SideMode.RAW_ADVANCED && white == SideMode.RAW_ADVANCED;
    config.uiConfig.put("pk-advance-time-settings", config.pkAdvanceTimeSettings);
  }


  static SideMode selectedEngineGameSideMode(boolean advanced, boolean engineOwned) {
    if (engineOwned) return SideMode.ENGINE_OWNED;
    if (advanced) return SideMode.RAW_ADVANCED;
    return SideMode.FIXED;
  }

  public static SideMode loadEngineGameSideMode(Config config, boolean black) {
    String key = black ? "pk-black-time-mode" : "pk-white-time-mode";
    if (config.uiConfig.has(key)) {
      return SideMode.valueOf(config.uiConfig.getString(key));
    }
    return config.pkAdvanceTimeSettings ? SideMode.RAW_ADVANCED : SideMode.FIXED;
  }

  public static void applyEngineGameTime(
      Leelaz engine, SideMode mode, int fixedSeconds, String advancedCommand) {
    if (mode == SideMode.ENGINE_OWNED) return;
    if (mode == SideMode.FIXED) {
      sendEngineGameFixedTime(engine, fixedSeconds);
      return;
    }
    engine.sendCommand(advancedCommand);
  }

  static int fixedSecondsForToolbar(
      SideMode mode, boolean toolbarTimeSelected, int parsedSeconds) {
    return mode == SideMode.FIXED && toolbarTimeSelected ? parsedSeconds : -1;
  }

  public static void sendEngineGameFixedTime(Leelaz engine, int seconds) {
    if (seconds <= 0) return;
    if (isWebSocket(engine)) {
      engine.sendCommand("kata-time_settings none");
      engine.sendCommand(LizzieFrame.buildKataGoFixedMoveTimeCommand(seconds));
    } else {
      engine.sendCommand(LizzieFrame.buildDefaultAiMoveTimeSettings(seconds));
    }
  }

  static boolean isWebSocket(Leelaz engine) {
    return engine != null
        && RemoteComputeConfig.isCustomWebSocketEngineCommand(engine.getEngineCommand());
  }
}
