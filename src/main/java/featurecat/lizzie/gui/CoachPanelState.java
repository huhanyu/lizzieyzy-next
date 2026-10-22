package featurecat.lizzie.gui;

import featurecat.lizzie.training.GameResult;
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
    Prompt prompt,
    Summary summary,
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
   * @param marked a percentage to mark in red, the chance before a 恶手, or NaN
   */
  record WinChance(String caption, double[] values, double marked) {}

  /** What a button on a {@link Prompt} or the {@link Summary} does. */
  enum Action {
    RETRY,
    SHOW_BEST,
    CONTINUE,
    MORE_HINT,
    CLOSE_HINT,
    REVIEW,
    PLAY_AGAIN
  }

  /**
   * The game's end: who won, by how much, a line of numbers, and the way on.
   *
   * @param won whether the player won, for the headline's colour
   * @param stats such as "共 200 手 · 关键失误 3 处 · 提示 2 次 · 悔棋 1 次"
   */
  record Summary(
      String label,
      String headline,
      boolean won,
      String result,
      String stats,
      String hint,
      List<Choice> choices) {}

  record Choice(Action action, String label, CoachButton.Kind kind) {}

  /**
   * A card that asks the player something: the decision after a 恶手, or a hint.
   *
   * @param warm the warm style of the 恶手 card
   * @param headline the large line, such as "胜率 −23%", or null
   * @param detail beside the headline, such as "约亏 8 目", or null
   */
  record Prompt(
      boolean warm,
      String tag,
      MoveVerdict verdict,
      String title,
      String headline,
      String detail,
      String body,
      List<Choice> choices) {}

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

  /**
   * 边下边提醒's count of each rating, and the latest rated moves, newest first; once the game is over,
   * its biggest mistakes instead, which open the review at that move when clicked.
   */
  record History(String heading, List<Tally> tallies, List<Row> recent, boolean clickable) {}

  record Tally(MoveVerdict verdict, String label, int count) {}

  record Row(
      int moveNumber,
      String number,
      String move,
      MoveVerdict verdict,
      String label,
      String change) {}

  /** The biggest mistakes the summary lists. */
  static final int KEY_MISTAKES = 3;

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
    } else if (game.isAwaitingDecision()) {
      status = text.apply("CoachPanel.status.decide", "Your call");
      tone = Tone.WARNING;
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
            + (game.isAwaitingDecision()
                ? text.apply("CoachPanel.waitsForYou", "Waits for your decision")
                : MessageFormat.format(
                    text.apply("CoachPanel.imitates", "Plays like a {0} player"), opponent)),
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
        reviewer == null ? null : prompt(game, opponent, text),
        over ? summary(game, reviewer, text) : null,
        reviewer == null ? null : history(reviewer, over, text));
  }

  private static WinChance winChance(
      HumanSlGameController game,
      MoveReviewer reviewer,
      String opponent,
      BinaryOperator<String> text) {
    if (reviewer != null) {
      double marked =
          game.isAwaitingDecision()
              ? game.assist().decision().map(f -> f.winBefore() * 100.0).orElse(Double.NaN)
              : Double.NaN;
      return new WinChance(
          MessageFormat.format(
              text.apply("CoachPanel.winChanceRank", "Estimated at {0} level"), opponent),
          game.humanWinrates(),
          marked);
    }
    if (game.mode().isLiveAnalysis()) {
      double[] values = game.humanWinrates();
      return values.length == 0
          ? null
          : new WinChance(
              text.apply("CoachPanel.winChanceLive", "KataGo live analysis"), values, Double.NaN);
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

  private static Prompt prompt(
      HumanSlGameController game, String opponent, BinaryOperator<String> text) {
    CoachAssist assist = game.assist();
    Optional<MoveFeedback> blunder = assist.decision();
    if (game.isAwaitingDecision() && blunder.isPresent()) {
      MoveFeedback feedback = blunder.get();
      String body = explanation(feedback, opponent, text);
      if (assist.isBestShown()) {
        body =
            join(
                body,
                MessageFormat.format(
                    text.apply(
                        "CoachPanel.bestMove",
                        "The AI would play {0}; {1} of players at this level find it."),
                    moveName(feedback.bestMove(), text),
                    percent(feedback.bestShare())));
      }
      List<Choice> choices = new ArrayList<>();
      choices.add(
          new Choice(
              Action.RETRY,
              text.apply("CoachPanel.retry", "Play it again"),
              CoachButton.Kind.PRIMARY));
      if (!assist.isBestShown()) {
        choices.add(
            new Choice(
                Action.SHOW_BEST,
                text.apply("CoachPanel.showBest", "See the AI move"),
                CoachButton.Kind.WARM));
      }
      choices.add(
          new Choice(
              Action.CONTINUE,
              text.apply("CoachPanel.continue", "Keep it and play on"),
              CoachButton.Kind.WARM_QUIET));
      long points = Math.round(feedback.pointsLost());
      return new Prompt(
          true,
          verdictLabel(feedback.verdict(), text),
          feedback.verdict(),
          MessageFormat.format(
              text.apply("CoachPanel.moveTitle", "Move {0} · {1}"),
              feedback.moveNumber(),
              moveName(feedback.move(), text)),
          MessageFormat.format(
              text.apply("CoachPanel.change", "Win chance {0}"),
              signedPercent(feedback.winChange())),
          points >= 1
              ? MessageFormat.format(
                  text.apply("CoachPanel.pointsLost", "about {0} points lost"), points)
              : null,
          body,
          choices);
    }
    if (!assist.isHintOpen()) {
      return null;
    }
    Choice self =
        new Choice(
            Action.CLOSE_HINT,
            text.apply("CoachPanel.hint.self", "Let me think"),
            CoachButton.Kind.SECONDARY);
    switch (assist.hintStep()) {
      case AREA:
        return new Prompt(
            false,
            MessageFormat.format(text.apply("CoachPanel.hint.step", "Hint {0}/2"), 1),
            null,
            MessageFormat.format(
                text.apply("CoachPanel.hint.area", "Look around the {0}"),
                areaName(assist.hintArea(), text)),
            null,
            null,
            text.apply(
                "CoachPanel.hint.areaBody",
                "Try to find it yourself first. If it does not come, ask for one more hint."),
            List.of(
                new Choice(
                    Action.MORE_HINT,
                    text.apply("CoachPanel.hint.more", "One more hint"),
                    CoachButton.Kind.ACCENT),
                self));
      case POINT:
        return new Prompt(
            false,
            MessageFormat.format(text.apply("CoachPanel.hint.step", "Hint {0}/2"), 2),
            null,
            MessageFormat.format(
                text.apply("CoachPanel.hint.point", "The AI would play {0}"),
                moveName(assist.hintMove(), text)),
            null,
            null,
            Double.isNaN(assist.hintShare())
                ? ""
                : MessageFormat.format(
                    text.apply("CoachPanel.why.share", "{0} of players at this level play it."),
                    percent(assist.hintShare())),
            List.of(
                new Choice(
                    Action.CLOSE_HINT,
                    text.apply("CoachPanel.hint.gotIt", "Got it"),
                    CoachButton.Kind.SECONDARY)));
      default:
        return new Prompt(
            false,
            text.apply("CoachPanel.hint", "Hint"),
            null,
            text.apply("CoachPanel.hint.waiting", "Reading the position…"),
            null,
            null,
            "",
            List.of(self));
    }
  }

  private static String moveName(String move, BinaryOperator<String> text) {
    return move == null || "pass".equalsIgnoreCase(move.trim())
        ? text.apply("HumanSlGame.btn.pass", "Pass")
        : move.trim();
  }

  /** The name of one of the nine areas of {@link CoachBoardMarks#areaOf}. */
  static String areaName(int area, BinaryOperator<String> text) {
    switch (area) {
      case 0:
        return text.apply("CoachPanel.area.topLeft", "top left corner");
      case 1:
        return text.apply("CoachPanel.area.top", "top side");
      case 2:
        return text.apply("CoachPanel.area.topRight", "top right corner");
      case 3:
        return text.apply("CoachPanel.area.left", "left side");
      case 5:
        return text.apply("CoachPanel.area.right", "right side");
      case 6:
        return text.apply("CoachPanel.area.bottomLeft", "bottom left corner");
      case 7:
        return text.apply("CoachPanel.area.bottom", "bottom side");
      case 8:
        return text.apply("CoachPanel.area.bottomRight", "bottom right corner");
      case 4:
      default:
        return text.apply("CoachPanel.area.center", "center");
    }
  }

  private static Summary summary(
      HumanSlGameController game, MoveReviewer reviewer, BinaryOperator<String> text) {
    GameResult result = game.result();
    String headline;
    boolean won = false;
    if (result == null || !result.isCounted()) {
      headline = text.apply("CoachPanel.gameOver", "Game over");
    } else if (result.isDraw()) {
      headline = text.apply("HumanSlGame.draw", "Draw");
    } else {
      won = result.blackWins() == game.humanPlaysBlack();
      headline =
          won
              ? text.apply("CoachPanel.youWon", "You won")
              : text.apply("CoachPanel.youLost", "You lost");
    }
    List<String> stats = new ArrayList<>();
    stats.add(
        MessageFormat.format(text.apply("CoachPanel.stat.moves", "{0} moves"), game.movesPlayed()));
    if (reviewer != null) {
      stats.add(
          MessageFormat.format(
              text.apply("CoachPanel.stat.mistakes", "{0} key mistakes"), reviewer.keyMistakes()));
      stats.add(
          MessageFormat.format(
              text.apply("CoachPanel.stat.hints", "{0} hints"), game.assist().hintsUsed()));
    }
    stats.add(
        MessageFormat.format(
            text.apply("CoachPanel.stat.takeBacks", "{0} take-backs"), game.takeBackCount()));
    return new Summary(
        text.apply("CoachPanel.gameOver", "Game over"),
        headline,
        won,
        game.resultText(),
        String.join(" · ", stats),
        text.apply(
            "CoachPanel.reviewHint",
            "Start review returns to the usual analysis, with every move's winrate and"
                + " suggestions."),
        List.of(
            new Choice(
                Action.REVIEW,
                text.apply("CoachPanel.review", "Start review"),
                CoachButton.Kind.PRIMARY),
            new Choice(
                Action.PLAY_AGAIN,
                text.apply("CoachPanel.playAgain", "Play again"),
                CoachButton.Kind.SECONDARY)));
  }

  private static History history(MoveReviewer reviewer, boolean over, BinaryOperator<String> text) {
    List<Tally> tallies = new ArrayList<>();
    for (MoveVerdict verdict : MoveVerdict.values()) {
      tallies.add(new Tally(verdict, verdictLabel(verdict, text), reviewer.count(verdict)));
    }
    List<MoveFeedback> moves;
    if (over) {
      moves = reviewer.worstMistakes(KEY_MISTAKES);
    } else {
      List<MoveFeedback> all = reviewer.feedback();
      moves = new ArrayList<>();
      for (int i = all.size() - 1; i >= 0 && moves.size() < RECENT_ROWS; i--) {
        moves.add(all.get(i));
      }
    }
    List<Row> rows = new ArrayList<>();
    for (MoveFeedback move : moves) {
      rows.add(
          new Row(
              move.moveNumber(),
              "#" + move.moveNumber(),
              "pass".equals(move.move()) ? text.apply("HumanSlGame.btn.pass", "Pass") : move.move(),
              move.verdict(),
              verdictLabel(move.verdict(), text),
              change(move, text, false)));
    }
    String heading =
        over
            ? text.apply("CoachPanel.keyMoves", "Worth a second look · click to go there")
            : text.apply("CoachPanel.recentMoves", "Recent moves");
    return new History(heading, tallies, rows, over && !rows.isEmpty());
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
