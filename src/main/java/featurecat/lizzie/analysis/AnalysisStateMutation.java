package featurecat.lizzie.analysis;

import java.util.Locale;

/** Payload invalidation policy at a state command's physical write boundary. */
enum AnalysisStateMutation {
  NONE,
  RESET_PAYLOAD,
  RESET_PAYLOAD_AND_SCORE,
  RETAIN_PAYLOAD;

  static AnalysisStateMutation forCommand(String command) {
    if (command == null) return NONE;
    String normalized = command.trim().toLowerCase(Locale.ROOT);
    if (normalized.equals("clear_board")) return RESET_PAYLOAD_AND_SCORE;
    if (normalized.startsWith("komi ")) return RETAIN_PAYLOAD;
    if (normalized.startsWith("play ")
        || normalized.equals("undo")
        || normalized.startsWith("loadsgf ")
        || normalized.equals("set_position")
        || normalized.startsWith("set_position ")
        || normalized.startsWith("boardsize ")
        || normalized.startsWith("rectangular_boardsize ")
        || normalized.startsWith("fixed_handicap ")
        || normalized.startsWith("place_free_handicap ")
        || normalized.startsWith("set_free_handicap ")) return RESET_PAYLOAD;
    return NONE;
  }
}
