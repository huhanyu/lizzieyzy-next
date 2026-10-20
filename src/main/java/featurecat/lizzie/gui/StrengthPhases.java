package featurecat.lizzie.gui;

import featurecat.lizzie.analysis.PlayerStrengthEstimator;
import java.util.ArrayList;
import java.util.List;

/**
 * A game split into opening, middle game and endgame at two boundaries, with each side's moves of
 * each phase estimated on their own.
 */
final class StrengthPhases {
  /** The last move of the opening and of the middle game, unless the user moves them. */
  static final int[] DEFAULT_BOUNDS = {60, 150};

  /** The fewest moves a phase can be made to span. */
  static final int MIN_PHASE_MOVES = 10;

  /** The fewest analysed moves of a side that a phase estimate is shown for. */
  static final int MIN_SAMPLES = 6;

  /** Rank differences below this are even; from {@link #CLEAR} on one side is clearly better. */
  static final double EVEN = 0.3;

  static final double CLEAR = 1.0;

  /** HumanSL 40 reads the first 210 moves of a game. */
  static final int HUMAN_STYLE_MOVES = 210;

  /** The key of each phase's name, in order. */
  static final String[] NAMES = {"opening", "middle", "endgame"};

  /**
   * One phase: its moves {@code first} to {@code last} (the end of the game for the endgame) and
   * each side's estimate over its own moves in it.
   */
  record Phase(
      int index,
      int first,
      int last,
      PlayerStrengthEstimator.SideReport black,
      PlayerStrengthEstimator.SideReport white) {
    String name() {
      return NAMES[index];
    }

    /** Whether the game reached this phase at all. */
    boolean played() {
      return last >= first;
    }

    PlayerStrengthEstimator.SideReport side(boolean isBlack) {
      return isBlack ? black : white;
    }

    boolean ranked(boolean isBlack) {
      return StrengthPhases.ranked(side(isBlack));
    }

    /** Black's rank minus White's, or NaN unless both are ranked. */
    double lead() {
      return ranked(true) && ranked(false) ? black.rankValue - white.rankValue : Double.NaN;
    }
  }

  private StrengthPhases() {}

  /**
   * {@code bounds} made usable: two boundaries in increasing order, every phase at least {@link
   * #MIN_PHASE_MOVES} long. Anything else gives the defaults.
   */
  static int[] normalize(int[] bounds) {
    if (bounds == null || bounds.length != 2) {
      return DEFAULT_BOUNDS.clone();
    }
    int opening = Math.max(MIN_PHASE_MOVES, bounds[0]);
    int middle = Math.max(opening + MIN_PHASE_MOVES, bounds[1]);
    return new int[] {opening, middle};
  }

  static boolean isDefault(int[] bounds) {
    int[] normal = normalize(bounds);
    return normal[0] == DEFAULT_BOUNDS[0] && normal[1] == DEFAULT_BOUNDS[1];
  }

  /** The phases of the game of {@code report}, whose last move is {@code lastMove}. */
  static List<Phase> split(PlayerStrengthEstimator.Report report, int[] bounds, int lastMove) {
    int[] normal = normalize(bounds);
    int[][] ranges = {
      {1, Math.min(normal[0], lastMove)},
      {normal[0] + 1, Math.min(normal[1], lastMove)},
      {normal[1] + 1, lastMove}
    };
    List<Phase> phases = new ArrayList<>();
    for (int i = 0; i < ranges.length; i++) {
      int first = ranges[i][0];
      int last = ranges[i][1];
      phases.add(
          new Phase(
              i,
              first,
              last,
              estimate(report.black, report.model, first, last),
              estimate(report.white, report.model, first, last)));
    }
    return phases;
  }

  private static PlayerStrengthEstimator.SideReport estimate(
      PlayerStrengthEstimator.SideReport side,
      PlayerStrengthEstimator.StrengthModel model,
      int first,
      int last) {
    List<PlayerStrengthEstimator.Sample> part = new ArrayList<>();
    if (side != null) {
      for (PlayerStrengthEstimator.Sample sample : side.samples) {
        if (sample.moveNumber >= first && sample.moveNumber <= last) {
          part.add(sample);
        }
      }
    }
    return PlayerStrengthEstimator.summarizeSamples(part, model);
  }

  /** Whether {@code side} has enough moves for its rank to be shown. */
  static boolean ranked(PlayerStrengthEstimator.SideReport side) {
    return side != null && side.sampleCount >= MIN_SAMPLES && Double.isFinite(side.rankValue);
  }

  /**
   * The phase in which one side played best ({@code best}) or worst, or -1 when fewer than two of
   * its phases are ranked or they lie within {@link #EVEN} of each other.
   */
  static int extreme(List<Phase> phases, boolean isBlack, boolean best) {
    int found = -1;
    double highest = Double.NEGATIVE_INFINITY;
    double lowest = Double.POSITIVE_INFINITY;
    int ranked = 0;
    for (Phase phase : phases) {
      if (!phase.ranked(isBlack)) {
        continue;
      }
      ranked++;
      double value = phase.side(isBlack).rankValue;
      if (best ? value > highest : value < lowest) {
        found = phase.index();
      }
      highest = Math.max(highest, value);
      lowest = Math.min(lowest, value);
    }
    return ranked >= 2 && highest - lowest >= EVEN ? found : -1;
  }

  /** Whether a side's weakest phase is far enough below its best one to be worth a review. */
  static boolean worthReviewing(List<Phase> phases, boolean isBlack) {
    int best = extreme(phases, isBlack, true);
    int worst = extreme(phases, isBlack, false);
    return best >= 0
        && worst >= 0
        && phases.get(best).side(isBlack).rankValue - phases.get(worst).side(isBlack).rankValue
            >= 2 * EVEN;
  }
}
