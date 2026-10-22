package featurecat.lizzie.gui;

import featurecat.lizzie.training.MoveFeedback;
import featurecat.lizzie.training.MoveVerdict;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
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
    WinChance winChance,
    MoveCard moveCard,
    String result,
    String resultHint,
    History history) {

  /** How the status pill is coloured. */
  enum Tone {
    ACTIVE,
    WAITING,
    WARNING,
    ALERT,
    DONE
  }

  /**
   * The player's win chance card.
   *
   * @param values percentages, oldest first; empty until the first one is known
   */
  record WinChance(String caption, double[] values) {}

  /**
   * The player's last move, and in 边下边提醒 how it was rated.
   *
   * @param label "Your last move"
   * @param move the move's number and point
   * @param change the win chance change, such as "胜率 −12% · 约亏 4 目", or null
   * @param verdict null when the move is not rated
   */
  record MoveCard(
      String label,
      String move,
      String change,
      MoveVerdict verdict,
      String verdictLabel,
      String explanation,
      boolean judging) {}

  /** 边下边提醒's count of each rating, and the latest rated moves, newest first. */
  record History(String heading, List<Tally> tallies, List<Row> recent) {}

  record Tally(MoveVerdict verdict, String label, int count) {}

  record Row(String number, String move, MoveVerdict verdict, String label, String change) {}

  static final CoachPanelState EMPTY =
      new CoachPanelState(
          "", "", "", Tone.WAITING, false, "", "", "", "", "", null, null, null, null, null);

  /** Rows the history keeps; the panel shows as many as fit. */
  static final int RECENT_ROWS = 8;

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
    MoveReviewer reviewer = game.moveReviewer();
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
        winChance(game, reviewer, opponent, text),
        moveCard(game, reviewer, opponent, text),
        over ? game.resultText() : null,
        over
            ? text.apply(
                "CoachPanel.reviewHint",
                "Start review returns to the usual analysis, with every move's winrate and"
                    + " suggestions.")
            : null,
        reviewer == null ? null : history(reviewer, text));
  }

  private static WinChance winChance(
      HumanSlGameController game,
      MoveReviewer reviewer,
      String opponent,
      BinaryOperator<String> text) {
    if (reviewer != null) {
      return new WinChance(
          MessageFormat.format(
              text.apply("CoachPanel.winChanceRank", "Estimated at {0} level"), opponent),
          game.humanWinrates());
    }
    if (game.mode().isLiveAnalysis()) {
      double[] values = game.humanWinrates();
      return values.length == 0
          ? null
          : new WinChance(text.apply("CoachPanel.winChanceLive", "KataGo live analysis"), values);
    }
    return null;
  }

  private static MoveCard moveCard(
      HumanSlGameController game,
      MoveReviewer reviewer,
      String opponent,
      BinaryOperator<String> text) {
    String lastMove = game.lastHumanMoveText();
    if (lastMove == null) {
      return null;
    }
    String label = text.apply("CoachPanel.lastMove", "Your last move");
    if (reviewer == null) {
      return new MoveCard(label, lastMove, null, null, null, null, false);
    }
    if (reviewer.isJudging()) {
      return new MoveCard(
          label, lastMove, text.apply("CoachPanel.judging", "Rating…"), null, null, null, true);
    }
    Optional<MoveFeedback> latest = reviewer.latest();
    if (latest.isEmpty() || latest.get().moveNumber() != game.lastHumanMoveNumber()) {
      return new MoveCard(
          label,
          lastMove,
          null,
          null,
          null,
          text.apply("CoachPanel.notRated", "This move could not be rated."),
          false);
    }
    MoveFeedback feedback = latest.get();
    return new MoveCard(
        label,
        lastMove,
        change(feedback, text, true),
        feedback.verdict(),
        verdictLabel(feedback.verdict(), text),
        explanation(feedback, opponent, text),
        false);
  }

  private static History history(MoveReviewer reviewer, BinaryOperator<String> text) {
    List<Tally> tallies = new ArrayList<>();
    for (MoveVerdict verdict : MoveVerdict.values()) {
      tallies.add(new Tally(verdict, verdictLabel(verdict, text), reviewer.count(verdict)));
    }
    List<MoveFeedback> moves = reviewer.feedback();
    List<Row> recent = new ArrayList<>();
    for (int i = moves.size() - 1; i >= 0 && recent.size() < RECENT_ROWS; i--) {
      MoveFeedback move = moves.get(i);
      recent.add(
          new Row(
              "#" + move.moveNumber(),
              "pass".equals(move.move()) ? text.apply("HumanSlGame.btn.pass", "Pass") : move.move(),
              move.verdict(),
              verdictLabel(move.verdict(), text),
              change(move, text, false)));
    }
    return new History(text.apply("CoachPanel.recentMoves", "Recent moves"), tallies, recent);
  }

  static String verdictLabel(MoveVerdict verdict, BinaryOperator<String> text) {
    switch (verdict) {
      case BRILLIANT:
        return text.apply("CoachPanel.verdict.brilliant", "Brilliant");
      case SLOW:
        return text.apply("CoachPanel.verdict.slow", "Slow");
      case PROBLEM:
        return text.apply("CoachPanel.verdict.problem", "Questionable");
      case BLUNDER:
        return text.apply("CoachPanel.verdict.blunder", "Blunder");
      case GOOD:
      default:
        return text.apply("CoachPanel.verdict.good", "Good");
    }
  }

  /** "胜率 −12% · 约亏 4 目" on the card, "−12%" in the history. */
  static String change(MoveFeedback feedback, BinaryOperator<String> text, boolean onCard) {
    String percent = signedPercent(feedback.winChange());
    if (!onCard) {
      return percent;
    }
    String change =
        MessageFormat.format(text.apply("CoachPanel.change", "Win chance {0}"), percent);
    long points = Math.round(feedback.pointsLost());
    if (feedback.verdict().isMistake() && points >= 1) {
      change +=
          " · "
              + MessageFormat.format(
                  text.apply("CoachPanel.pointsLost", "about {0} points lost"), points);
    }
    return change;
  }

  static String explanation(MoveFeedback feedback, String opponent, BinaryOperator<String> text) {
    String share =
        Double.isNaN(feedback.humanShare())
            ? ""
            : MessageFormat.format(
                text.apply("CoachPanel.why.share", "{0} of players at this level play it."),
                percent(feedback.humanShare()));
    switch (feedback.verdict()) {
      case BRILLIANT:
        return MessageFormat.format(
            text.apply(
                "CoachPanel.why.brilliant",
                "Only {0} of players at this level find it; their usual move loses ground."),
            percent(feedback.humanShare()));
      case GOOD:
        return join(
            feedback.playedBest()
                ? text.apply("CoachPanel.why.best", "Same move as the AI.")
                : text.apply("CoachPanel.why.good", "Nearly as good as the AI move."),
            share);
      default:
        if (feedback.winBefore() < 0.05 || feedback.winBefore() > 0.95) {
          return MessageFormat.format(
              text.apply(
                  "CoachPanel.why.decided",
                  "The game is all but decided, so points decide: about {0} points lost."),
              Math.round(feedback.pointsLost()));
        }
        return join(
            MessageFormat.format(
                text.apply(
                    "CoachPanel.why.mistake", "At {0} level your win chance fell from {1} to {2}."),
                opponent,
                percent(feedback.winBefore()),
                percent(feedback.winAfter())),
            share);
    }
  }

  /** Two sentences; Chinese and Japanese ones follow each other without a space. */
  static String join(String first, String second) {
    if (second.isEmpty()) {
      return first;
    }
    char last = first.isEmpty() ? ' ' : first.charAt(first.length() - 1);
    return first + (last >= '\u2e80' ? "" : " ") + second;
  }

  /** A share from 0 to 1 as a whole percentage; below half a percent shows as "<1%". */
  static String percent(double share) {
    return share < 0.005 ? "<1%" : Math.round(share * 100.0) + "%";
  }

  /**
   * A change from -1 to 1 as signed whole percentage points, with a real minus sign. A loss rounds
   * the way {@link MoveVerdict} rounds it, so the number shown always matches the rating.
   */
  static String signedPercent(double change) {
    long points = Math.round(Math.abs(change) * 100.0);
    return change < 0.0 && points > 0 ? "−" + points + "%" : "+" + points + "%";
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
