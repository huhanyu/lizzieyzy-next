package featurecat.lizzie.gui;

import featurecat.lizzie.analysis.MoveRankDefinition;
import featurecat.lizzie.analysis.PlayerStrengthEstimator;
import featurecat.lizzie.rules.Stone;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Builds player-strength reports for tests; the estimator keeps their constructors private. */
final class StrengthFixtures {
  private StrengthFixtures() {}

  static PlayerStrengthEstimator.Sample sample(
      Stone color,
      int moveNumber,
      double winrateLoss,
      Optional<Double> scoreLoss,
      boolean firstChoice,
      int aiRank,
      PlayerStrengthEstimator.MoveCategory category)
      throws Exception {
    Constructor<PlayerStrengthEstimator.Sample> constructor =
        PlayerStrengthEstimator.Sample.class.getDeclaredConstructor(
            Stone.class,
            int.class,
            String.class,
            double.class,
            Optional.class,
            boolean.class,
            int.class,
            PlayerStrengthEstimator.MoveCategory.class,
            MoveRankDefinition.Rank.class,
            double.class,
            double.class,
            double.class);
    constructor.setAccessible(true);
    return constructor.newInstance(
        color,
        moveNumber,
        moveNumber % 2 == 1 ? "A9" : "B9",
        winrateLoss,
        scoreLoss,
        firstChoice,
        aiRank,
        category,
        rankFor(category),
        scoreLoss.orElse(1.0),
        0.35,
        1.0);
  }

  static MoveRankDefinition.Rank rankFor(PlayerStrengthEstimator.MoveCategory category) {
    switch (category) {
      case EXCELLENT:
        return MoveRankDefinition.Rank.BEST;
      case GREAT:
        return MoveRankDefinition.Rank.GOOD;
      case GOOD:
        return MoveRankDefinition.Rank.NORMAL;
      case INACCURACY:
        return MoveRankDefinition.Rank.INACCURACY;
      case MISTAKE:
        return MoveRankDefinition.Rank.MISTAKE;
      default:
        return MoveRankDefinition.Rank.BLUNDER;
    }
  }

  /** {@code count} moves of {@code color}, every other move from {@code firstMove}. */
  static List<PlayerStrengthEstimator.Sample> moves(
      Stone color, int firstMove, int count, PlayerStrengthEstimator.MoveCategory category)
      throws Exception {
    List<PlayerStrengthEstimator.Sample> samples = new ArrayList<>();
    boolean first = category == PlayerStrengthEstimator.MoveCategory.EXCELLENT;
    for (int i = 0; i < count; i++) {
      samples.add(
          sample(
              color,
              firstMove + i * 2,
              first ? 0.0 : 2.0,
              Optional.of(first ? 0.0 : 0.6),
              first,
              first ? 0 : 1,
              category));
    }
    return samples;
  }

  static PlayerStrengthEstimator.SideReport side(
      List<PlayerStrengthEstimator.Sample> samples, double firstChoiceRate, double goodMoveRate)
      throws Exception {
    return side(
        samples,
        PlayerStrengthEstimator.StrengthModel.XGBOOST20TUN,
        1.0,
        firstChoiceRate,
        goodMoveRate,
        "1d",
        PlayerStrengthEstimator.Confidence.HIGH);
  }

  static PlayerStrengthEstimator.SideReport side(
      List<PlayerStrengthEstimator.Sample> samples,
      PlayerStrengthEstimator.StrengthModel model,
      double rankValue,
      double firstChoiceRate,
      double goodMoveRate,
      String strengthBand,
      PlayerStrengthEstimator.Confidence confidence)
      throws Exception {
    Constructor<PlayerStrengthEstimator.SideReport> constructor =
        PlayerStrengthEstimator.SideReport.class.getDeclaredConstructor(
            int.class,
            int.class,
            PlayerStrengthEstimator.StrengthModel.class,
            double.class,
            double.class,
            double.class,
            double.class,
            double.class,
            double.class,
            double.class,
            double.class,
            double.class,
            double.class,
            double.class,
            double.class,
            int[].class,
            double.class,
            double.class,
            double.class,
            double.class,
            String.class,
            PlayerStrengthEstimator.Confidence.class,
            List.class);
    constructor.setAccessible(true);
    int[] counts = new int[MoveRankDefinition.Rank.values().length];
    for (PlayerStrengthEstimator.Sample sample : samples) {
      counts[sample.moveRankCategory.ordinal()]++;
    }
    return constructor.newInstance(
        samples.size(),
        samples.size(),
        model,
        rankValue,
        0.5,
        82.0,
        1.5,
        0.8,
        0.8,
        1.2,
        1.2,
        goodMoveRate,
        firstChoiceRate,
        goodMoveRate,
        goodMoveRate,
        counts,
        0.05,
        0.02,
        0.01,
        0.4,
        strengthBand,
        confidence,
        samples);
  }

  static PlayerStrengthEstimator.Report report(
      PlayerStrengthEstimator.SideReport black,
      PlayerStrengthEstimator.SideReport white,
      PlayerStrengthEstimator.StrengthModel model)
      throws Exception {
    List<PlayerStrengthEstimator.Sample> all = new ArrayList<>(black.samples);
    all.addAll(white.samples);
    Constructor<PlayerStrengthEstimator.Report> constructor =
        PlayerStrengthEstimator.Report.class.getDeclaredConstructor(
            PlayerStrengthEstimator.SideReport.class,
            PlayerStrengthEstimator.SideReport.class,
            PlayerStrengthEstimator.SideReport.class,
            PlayerStrengthEstimator.StrengthModel.class);
    constructor.setAccessible(true);
    return constructor.newInstance(black, white, side(all, 0.5, 1.0), model);
  }

  /**
   * One move a side: Black's first move is the AI's first choice; White's second move is a good
   * move that the AI ranked second.
   */
  static PlayerStrengthEstimator.Report twoMoveReport() throws Exception {
    PlayerStrengthEstimator.Sample black =
        sample(
            Stone.BLACK,
            1,
            1.2,
            Optional.of(0.4),
            true,
            0,
            PlayerStrengthEstimator.MoveCategory.EXCELLENT);
    PlayerStrengthEstimator.Sample white =
        sample(
            Stone.WHITE,
            2,
            3.4,
            Optional.of(1.1),
            false,
            1,
            PlayerStrengthEstimator.MoveCategory.GREAT);
    return report(
        side(List.of(black), 1.0, 1.0),
        side(List.of(white), 0.0, 1.0),
        PlayerStrengthEstimator.StrengthModel.XGBOOST20TUN);
  }

  /** A report of a game with no analysed moves, as HumanSL shows before its data is ready. */
  static PlayerStrengthEstimator.Report emptyReport() throws Exception {
    PlayerStrengthEstimator.SideReport empty =
        side(
            List.of(),
            PlayerStrengthEstimator.StrengthModel.HUMANSL40,
            Double.NaN,
            0,
            0,
            "-",
            PlayerStrengthEstimator.Confidence.LOW);
    return report(empty, empty, PlayerStrengthEstimator.StrengthModel.HUMANSL40);
  }
}
