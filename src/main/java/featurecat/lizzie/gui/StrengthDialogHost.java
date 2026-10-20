package featurecat.lizzie.gui;

import featurecat.lizzie.Config;
import featurecat.lizzie.analysis.HumanSlStrengthPreparation;
import featurecat.lizzie.analysis.HumanSlStrengthReuseSession;
import featurecat.lizzie.analysis.PlayerStrengthEstimator;
import featurecat.lizzie.rules.BoardData;
import featurecat.lizzie.rules.BoardHistoryNode;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.function.Supplier;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Connects the player-strength dialog to the main window: the game on its board, its whole-game
 * analysis and its HumanSL preparation.
 */
final class StrengthDialogHost implements StrengthDialog.Host {
  /** HumanSL 40 looks at the first 210 moves of a game. */
  private static final int HUMAN_STYLE_MOVES = 210;

  /** Where the phase boundaries are kept in the ui section of the configuration. */
  static final String PHASE_BOUNDS_KEY = "strength-phase-bounds";

  private final LizzieFrame frame;
  private final Supplier<BoardHistoryNode> mainLine;
  private final Config config;

  /** {@code mainLine} gives the first position of the game on the board. */
  StrengthDialogHost(LizzieFrame frame, Supplier<BoardHistoryNode> mainLine, Config config) {
    this.frame = frame;
    this.mainLine = mainLine;
    this.config = config;
  }

  /** The look of the dialog: the Apple style or not, the colour theme and the interface font. */
  StrengthStyle style() {
    return new StrengthStyle(
        AppleStyleSupport.isAppleStyleEnabled(),
        config.useMorandiColors,
        config.uiFontName,
        Config.frameFontSize);
  }

  @Override
  public int[] phaseBounds() {
    return parseBounds(
        config == null || config.uiConfig == null
            ? null
            : config.uiConfig.optJSONArray(PHASE_BOUNDS_KEY));
  }

  @Override
  public void setPhaseBounds(int[] bounds) throws IOException {
    int[] normal = StrengthPhases.normalize(bounds);
    JSONObject ui = new JSONObject(config.uiConfig.toString());
    ui.put(PHASE_BOUNDS_KEY, new JSONArray(normal));
    config.saveConfigSections(ui, config.leelazConfig);
  }

  /** The boundaries kept in {@code saved}, or the defaults when there are none or they are odd. */
  static int[] parseBounds(JSONArray saved) {
    if (saved == null || saved.length() != 2) {
      return StrengthPhases.DEFAULT_BOUNDS.clone();
    }
    int opening = saved.optInt(0, -1);
    int middle = saved.optInt(1, -1);
    if (opening <= 0 || middle <= opening) {
      return StrengthPhases.DEFAULT_BOUNDS.clone();
    }
    return StrengthPhases.normalize(new int[] {opening, middle});
  }

  @Override
  public PlayerStrengthEstimator.Report report() {
    if (!PlayerStrengthEstimator.activeModel().isHumanSl40()) {
      return PlayerStrengthEstimator.estimate(mainLine.get());
    }
    PlayerStrengthEstimator.Report ready = humanSlReport();
    return ready != null
        ? ready
        : PlayerStrengthEstimator.estimate(null, PlayerStrengthEstimator.StrengthModel.HUMANSL40);
  }

  private PlayerStrengthEstimator.Report humanSlReport() {
    try {
      return frame.humanSlStrengthPreparation().readyReport(HumanSlStrengthReuseSession.capture());
    } catch (RuntimeException incomplete) {
      return null;
    }
  }

  @Override
  public PlayerStrengthEstimator.StrengthModel activeModel() {
    return PlayerStrengthEstimator.activeModel();
  }

  @Override
  public void setActiveModel(PlayerStrengthEstimator.StrengthModel model) {
    PlayerStrengthEstimator.setActiveModel(model);
  }

  @Override
  public PlayerStrengthEstimator.StrengthModel[] models() {
    return PlayerStrengthEstimator.selectableModels();
  }

  @Override
  public PlayerStrengthEstimator.StrengthModel importModel(Path path) throws IOException {
    return PlayerStrengthEstimator.importStrengthModel(path);
  }

  @Override
  public HumanSlStrengthPreparation.Status preparationStatus() {
    return frame.humanSlStrengthPreparation().status();
  }

  /** Changes whenever the HumanSL preparation or the analysis of the moves it reads does. */
  @Override
  public long preparationStamp() {
    HumanSlStrengthPreparation preparation = frame.humanSlStrengthPreparation();
    long stamp = preparation.status().revision();
    stamp = 31 * stamp + preparation.contextKey().hashCode();
    return 31 * stamp + analysisStamp(mainLine.get(), HUMAN_STYLE_MOVES);
  }

  @Override
  public boolean backgroundEnabled() {
    return HumanSlStrengthPreparation.backgroundEnabled();
  }

  @Override
  public void setBackgroundEnabled(boolean enabled) throws IOException {
    HumanSlStrengthPreparation.saveBackgroundEnabled(enabled);
    frame.humanSlStrengthPreparation().resetBackground();
    frame.pollHumanSlPreparation();
  }

  @Override
  public void prepareNow() {
    HumanSlStrengthDialog.prepare(frame);
  }

  @Override
  public boolean analyzing() {
    return frame.isWholeGameAnalysisStartingOrRunning()
        || frame.hasActiveAutomaticQuickAnalysis()
        || frame.isFlashAnalysisRunning();
  }

  @Override
  public void analyzeGame() {
    frame.flashAnalyzeGame(true, false);
  }

  @Override
  public StrengthDialog.Progress analysisProgress() {
    return analysisProgress(mainLine.get());
  }

  @Override
  public boolean analysisReady() {
    return PlayerStrengthEstimator.estimate(
            mainLine.get(), PlayerStrengthEstimator.StrengthModel.XGBOOST20TUN)
        .hasEnoughData();
  }

  @Override
  public boolean humanStyleInstalled() {
    try {
      return HumanSlStrengthPreparation.discoverAssets().human() != null;
    } catch (RuntimeException unavailable) {
      return false;
    }
  }

  /** How many moves of the main line after {@code start} have analysis, of how many. */
  static StrengthDialog.Progress analysisProgress(BoardHistoryNode start) {
    int analyzed = 0;
    int total = 0;
    for (var next = start.next(true); next.isPresent(); next = next.get().next(true)) {
      total++;
      BoardData data = next.get().getData();
      synchronized (data) {
        if (data.getPlayouts() > 0) {
          analyzed++;
        }
      }
    }
    return new StrengthDialog.Progress(analyzed, total);
  }

  /**
   * Changes whenever the analysis of {@code start} or of one of the first {@code moves} moves of
   * the main line after it does.
   */
  static long analysisStamp(BoardHistoryNode start, int moves) {
    long stamp = System.identityHashCode(start);
    BoardHistoryNode node = start;
    for (int move = 0; ; move++) {
      BoardData data = node.getData();
      synchronized (data) {
        stamp = 31 * stamp + data.getPlayouts();
        stamp = 31 * stamp + Double.doubleToLongBits(data.scoreMean);
        stamp = 31 * stamp + Double.doubleToLongBits(data.winrate);
        stamp = 31 * stamp + Objects.hashCode(data.engineName);
        stamp = 31 * stamp + System.identityHashCode(data.bestMoves);
        stamp = 31 * stamp + System.identityHashCode(data.bestMovesOutOfRange);
      }
      var next = node.next(true);
      if (next.isEmpty() || move == moves) {
        return stamp;
      }
      node = next.get();
    }
  }
}
