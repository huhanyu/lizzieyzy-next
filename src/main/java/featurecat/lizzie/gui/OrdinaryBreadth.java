package featurecat.lizzie.gui;

import featurecat.lizzie.analysis.Leelaz;

/**
 * Ordinary breadth ({@code analysisWideRootNoise}) outside engine games, as the toolbar text, its
 * check box and the advanced settings apply it. The typed draft and the effective value are kept
 * apart: a cleared check box applies zero and leaves the draft for re-enabling. The effective value
 * becomes the engine's {@link Leelaz#wrn}, which later analysis records; it is the value sent, not
 * one the engine confirmed.
 */
final class OrdinaryBreadth {
  private static final double MAX = 2;

  private OrdinaryBreadth() {}

  /** The draft's value; text that is not a number counts as zero, as the toolbar always did. */
  static double draftValue(String draft) {
    try {
      return Double.parseDouble(draft);
    } catch (NumberFormatException notNumber) {
      return 0;
    }
  }

  /** Whether the draft is a number in the range the breadth fields accept. */
  static boolean validDraft(String draft) {
    try {
      double value = Double.parseDouble(draft);
      return value >= 0 && value <= MAX;
    } catch (NumberFormatException notNumber) {
      return false;
    }
  }

  /** Applies the draft when enabled, zero otherwise, to {@code engine}. */
  static void apply(Leelaz engine, boolean enabled, String draft) {
    engine.setOrdinaryWrn(enabled ? draftValue(draft) : 0);
  }
}
