package featurecat.lizzie.gui;

import featurecat.lizzie.rules.Board;
import featurecat.lizzie.training.MoveFeedback;
import featurecat.lizzie.training.PositionReview;
import java.util.Optional;

/**
 * 边下边提醒's help within one turn, with its marks on the board. After a 恶手 the AI holds its reply
 * while the player decides: take the move back, see the AI's choice, or play on. On the player's
 * turn a hint shows the area first and the point second. Everything here belongs to the event
 * thread.
 */
final class CoachAssist {
  enum Hint {
    NONE,
    /** Asked for before the position's review was ready. */
    WAITING,
    AREA,
    POINT
  }

  private final CoachBoardMarks marks = new CoachBoardMarks();
  private MoveFeedback blunder;
  private boolean bestShown;
  private Hint hint = Hint.NONE;
  private boolean hintOpen;
  private String hintMove;
  private double hintShare = Double.NaN;
  private int hintArea = -1;
  private int hintsUsed;

  CoachBoardMarks marks() {
    return marks;
  }

  /** The AI holds its reply to {@code feedback} until the player decides. */
  void decideAbout(MoveFeedback feedback) {
    clearTurn();
    blunder = feedback;
  }

  /** The 恶手 the player is deciding about. */
  Optional<MoveFeedback> decision() {
    return Optional.ofNullable(blunder);
  }

  boolean isBestShown() {
    return bestShown;
  }

  /** 看 AI 推荐: marks the AI's choice in the position before the 恶手. */
  void showBest() {
    if (blunder == null) {
      return;
    }
    int[] point = coordinates(blunder.bestMove());
    if (point != null) {
      marks.clear();
      marks.showPoint(point[0], point[1]);
      bestShown = true;
    }
  }

  /**
   * The hint button: first the area of the AI's choice, then the point. Pressed again after the
   * point, it only shows the card again.
   *
   * @param review the review of the position the player is to move in, or null while it runs
   */
  void hint(PositionReview review) {
    hintOpen = true;
    if (review == null || review.best().isEmpty()) {
      if (hint == Hint.NONE) {
        hint = Hint.WAITING;
      }
      return;
    }
    String best = review.best().get().move();
    int[] point = coordinates(best);
    if (point == null) {
      // KataGo would pass here: there is no area to show.
      if (hint != Hint.POINT) {
        hintsUsed++;
      }
      hintMove = best;
      hintShare = review.humanShare(best);
      hint = Hint.POINT;
      marks.clear();
      return;
    }
    if (hint == Hint.NONE || hint == Hint.WAITING) {
      hintMove = best;
      hintShare = review.humanShare(best);
      hintArea = CoachBoardMarks.areaOf(point[0], point[1], Board.boardWidth, Board.boardHeight);
      marks.clear();
      marks.showArea(CoachBoardMarks.areaBounds(hintArea, Board.boardWidth, Board.boardHeight));
      hint = Hint.AREA;
      hintsUsed++;
    } else if (hint == Hint.AREA) {
      marks.clear();
      marks.showPoint(point[0], point[1]);
      hint = Hint.POINT;
    }
  }

  /** The position's review arrived: a hint asked for meanwhile can be given now. */
  void reviewArrived(PositionReview review) {
    if (hint == Hint.WAITING && review != null) {
      hint = Hint.NONE;
      hint(review);
    }
  }

  /** 我自己想 or 知道了: the card closes and the marks stay until the player moves. */
  void closeHint() {
    hintOpen = false;
    if (hint == Hint.WAITING) {
      hint = Hint.NONE;
    }
  }

  Hint hintStep() {
    return hint;
  }

  boolean isHintOpen() {
    return hintOpen;
  }

  String hintMove() {
    return hintMove;
  }

  double hintShare() {
    return hintShare;
  }

  /** The hint's area, 0 to 8 as {@link CoachBoardMarks#areaOf}, or -1. */
  int hintArea() {
    return hintArea;
  }

  /** Positions in this game where the player asked for a hint. */
  int hintsUsed() {
    return hintsUsed;
  }

  /** The player moved, took back or the game ended: the turn's help and marks are gone. */
  void clearTurn() {
    blunder = null;
    bestShown = false;
    hint = Hint.NONE;
    hintOpen = false;
    hintMove = null;
    hintShare = Double.NaN;
    hintArea = -1;
    marks.clear();
  }

  private static int[] coordinates(String move) {
    if (move == null || "pass".equalsIgnoreCase(move.trim())) {
      return null;
    }
    int[] point = Board.convertNameToCoordinates(move.trim());
    return point != null && Board.isValid(point[0], point[1]) ? point : null;
  }
}
