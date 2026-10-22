package featurecat.lizzie.gui;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.function.BinaryOperator;

/** What the AI Coach panel shows at one moment, read from the game on the event thread. */
record CoachPanelState(
    String title,
    String subtitle,
    String status,
    Tone tone,
    boolean aiPlaysBlack,
    String humanLine,
    String humanDetail,
    String aiLine,
    String aiDetail,
    String timeLine,
    double[] winrates,
    String lastMove,
    String result,
    String resultHint) {

  /** How the status pill is coloured. */
  enum Tone {
    ACTIVE,
    WAITING,
    WARNING,
    ALERT,
    DONE
  }

  static final CoachPanelState EMPTY =
      new CoachPanelState(
          "", "", "", Tone.WAITING, false, "", "", "", "", "", new double[0], null, null, null);

  /**
   * @param text looks up a localized string by key, with an English fallback
   */
  static CoachPanelState of(HumanSlGameController game, BinaryOperator<String> text) {
    String opponent = game.opponentLabel();
    boolean humanBlack = game.humanPlaysBlack();
    String black = text.apply("CoachPanel.black", "Black");
    String white = text.apply("CoachPanel.white", "White");
    int[] captures = game.captures();
    String captured = text.apply("CoachPanel.captures", "{0} captured");
    String status;
    Tone tone;
    if (game.isExitRecoveryPending()) {
      status = text.apply("CoachPanel.status.cleanup", "Cleanup needed");
      tone = Tone.ALERT;
    } else if (game.isStopRequested()) {
      status = text.apply("CoachPanel.status.stopping", "Ending");
      tone = Tone.WAITING;
    } else if (game.isGameOver()) {
      status = text.apply("CoachPanel.status.gameOver", "Game over");
      tone = Tone.DONE;
    } else if (game.isCounting()) {
      status = text.apply("CoachPanel.status.counting", "Counting");
      tone = Tone.WARNING;
    } else if (game.hasAiFailure()) {
      status = text.apply("CoachPanel.status.aiFailed", "AI did not move");
      tone = Tone.ALERT;
    } else if (game.isAiThinking() || !game.isHumanTurn()) {
      status = text.apply("CoachPanel.status.aiThinking", "AI thinking");
      tone = Tone.WAITING;
    } else {
      status = text.apply("CoachPanel.status.yourTurn", "Your turn");
      tone = Tone.ACTIVE;
    }
    boolean over = game.isGameOver();
    return new CoachPanelState(
        MessageFormat.format(text.apply("CoachPanel.opponent", "{0} AI"), opponent),
        (humanBlack
                ? text.apply("CoachPanel.aiWhite", "Plays White")
                : text.apply("CoachPanel.aiBlack", "Plays Black"))
            + " · "
            + MessageFormat.format(
                text.apply("CoachPanel.imitates", "Plays like a {0} player"), opponent),
        status,
        tone,
        !humanBlack,
        text.apply("CoachPanel.you", "You") + " · " + (humanBlack ? black : white),
        MessageFormat.format(captured, captures[0]),
        "AI · " + (humanBlack ? white : black),
        MessageFormat.format(captured, captures[1]),
        MessageFormat.format(
            text.apply("CoachPanel.time", "Time: you {0} · AI {1} · komi {2}"),
            clock(game.humanElapsedMillis()),
            clock(game.aiElapsedMillis()),
            komi(game.gameKomi())),
        game.humanWinrates(),
        game.lastHumanMoveText(),
        over ? game.resultText() : null,
        over
            ? text.apply(
                "CoachPanel.reviewHint",
                "Start review returns to the usual analysis, with every move's winrate and"
                    + " suggestions.")
            : null);
  }

  static String clock(long millis) {
    long seconds = Math.max(0L, millis) / 1000L;
    return String.format(Locale.ROOT, "%02d:%02d", seconds / 60L, seconds % 60L);
  }

  static String komi(double komi) {
    return komi == Math.rint(komi)
        ? String.valueOf((long) komi)
        : String.format(Locale.ROOT, "%.1f", komi);
  }
}
