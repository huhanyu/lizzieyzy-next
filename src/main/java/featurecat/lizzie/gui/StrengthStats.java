package featurecat.lizzie.gui;

import featurecat.lizzie.analysis.MoveRankDefinition;
import featurecat.lizzie.analysis.PlayerStrengthEstimator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Figures the player-strength views derive from an estimator report. */
final class StrengthStats {
  private StrengthStats() {}

  /** A stretch of one side's moves, summarised on its own. */
  record Segment(int firstMove, int lastMove, PlayerStrengthEstimator.SideReport report) {}

  /**
   * Moves per move-rank tier, indexed by {@link MoveRankDefinition.Rank#ordinal()}. Each move
   * counts once: an AI first choice is Best, and a Best move that is not the first choice counts as
   * Good, so the tiers add up to the analysed moves.
   */
  static int[] tierCounts(PlayerStrengthEstimator.SideReport report) {
    int[] counts = new int[MoveRankDefinition.Rank.values().length];
    if (report == null || report.samples == null) {
      return counts;
    }
    for (PlayerStrengthEstimator.Sample sample : report.samples) {
      if (sample == null) {
        continue;
      }
      MoveRankDefinition.Rank rank = sample.moveRankCategory;
      if (sample.firstChoice) {
        rank = MoveRankDefinition.Rank.BEST;
      } else if (rank == MoveRankDefinition.Rank.BEST) {
        rank = MoveRankDefinition.Rank.GOOD;
      }
      if (rank != null) {
        counts[rank.ordinal()]++;
      }
    }
    return counts;
  }

  /** The last analysed move of the game, at least 1. */
  static int maxMove(PlayerStrengthEstimator.Report report) {
    int maxMove = 1;
    if (report != null && report.overall != null) {
      for (PlayerStrengthEstimator.Sample sample : report.overall.samples) {
        maxMove = Math.max(maxMove, sample.moveNumber);
      }
    }
    return maxMove;
  }

  /** The analysed moves of {@code report} in game order. */
  static List<PlayerStrengthEstimator.Sample> movesInOrder(
      PlayerStrengthEstimator.SideReport report) {
    List<PlayerStrengthEstimator.Sample> samples =
        report == null || report.samples == null
            ? new ArrayList<>()
            : new ArrayList<>(report.samples);
    samples.sort(Comparator.comparingInt(sample -> sample.moveNumber));
    return samples;
  }

  /** How closely the moves of {@code report} resemble an AI's, from 0 to 1. */
  static double aiLikelihood(PlayerStrengthEstimator.SideReport report) {
    if (report == null || !report.hasSamples()) {
      return 0.0;
    }
    double match = clamp((report.matchRate - 0.72) / 0.22);
    double firstChoice = clamp((report.firstChoiceRate - 0.50) / 0.18);
    double loss = clamp((3.2 - report.weightedScoreLoss) / 3.2);
    double mistakes = 1.0 - clamp(report.mistakeRate / 0.05);
    return clamp(0.35 * match + 0.30 * firstChoice + 0.25 * loss + 0.10 * mistakes);
  }

  /** The side's moves in stretches of similar length, each summarised on its own. */
  static List<Segment> segments(PlayerStrengthEstimator.SideReport report) {
    List<PlayerStrengthEstimator.Sample> samples = movesInOrder(report);
    List<Segment> segments = new ArrayList<>();
    if (samples.isEmpty()) {
      return segments;
    }
    int length = segmentLength(report);
    int minimum = minimumSegmentMoves(report);
    int start = 0;
    while (start < samples.size()) {
      int end = Math.min(samples.size(), start + length);
      if (samples.size() - end > 0 && samples.size() - end < minimum) {
        end = samples.size();
      }
      List<PlayerStrengthEstimator.Sample> part = samples.subList(start, end);
      segments.add(
          new Segment(
              part.get(0).moveNumber,
              part.get(part.size() - 1).moveNumber,
              PlayerStrengthEstimator.summarizeSamples(part, report.model)));
      start = end;
    }
    return segments;
  }

  private static int segmentLength(PlayerStrengthEstimator.SideReport report) {
    int sampleCount = Math.max(1, report.sampleCount);
    int minimum = minimumSegmentMoves(report);
    if (sampleCount <= minimum * 2) {
      return sampleCount;
    }
    int targetSegments = Math.max(2, Math.min(9, (int) Math.round(Math.sqrt(sampleCount))));
    return Math.max(minimum, (int) Math.ceil((double) sampleCount / targetSegments));
  }

  static int minimumSegmentMoves(PlayerStrengthEstimator.SideReport report) {
    if (report != null && report.model != null && report.model.isHumanSl40()) {
      return 6;
    }
    int sampleCount = report == null ? 0 : report.sampleCount;
    if (sampleCount < 16) {
      return 3;
    }
    return sampleCount < 40 ? 4 : 5;
  }

  /** Whether a stretch is long enough, and played at 7 dan or above, to be labelled with a rank. */
  static boolean labelsRank(Segment segment, PlayerStrengthEstimator.SideReport report) {
    return segment.report().sampleCount >= minimumSegmentMoves(report)
        && highestDan(segment.report().strengthBand) >= 7;
  }

  /** Whether a stretch is long enough, and close enough to AI play, to be marked. */
  static boolean resemblesAi(Segment segment, PlayerStrengthEstimator.SideReport report) {
    return segment.report().sampleCount >= minimumSegmentMoves(report)
        && aiLikelihood(segment.report()) >= 0.70;
  }

  private static int highestDan(String band) {
    if (band == null) {
      return -1;
    }
    String value = band.trim();
    int dan = value.indexOf('d');
    if (dan <= 0) {
      return -1;
    }
    int start = dan - 1;
    while (start >= 0 && Character.isDigit(value.charAt(start))) {
      start--;
    }
    if (start == dan - 1) {
      return -1;
    }
    return Integer.parseInt(value.substring(start + 1, dan));
  }

  static double clamp(double value) {
    return Math.max(0.0, Math.min(1.0, value));
  }
}
