package featurecat.lizzie.gui;

import featurecat.lizzie.Lizzie;
import featurecat.lizzie.analysis.Leelaz;
import featurecat.lizzie.analysis.ReadBoard;
import featurecat.lizzie.rules.Board;
import java.io.File;

/** Exact in-memory state replaced by {@link Board#clear(boolean)} for a new coaching game. */
final class CoachStartSnapshot {
  private final Board board;
  private final Board.ClearStateSnapshot boardState;
  private final File currentFile;
  private final String fileNameTitle;
  private final ReadBoard readBoard;
  private final boolean readBoardFirstSync;
  private final Leelaz foregroundEngine;
  private final boolean outOfPlayoutsLimit;
  private final boolean stopByPlayouts;
  private final WinrateGraph winrateGraph;
  private final double maxScoreLead;
  private final LizzieFrame frame;
  private final boolean wasTrying;

  private CoachStartSnapshot(Board board) {
    this.board = board;
    boardState = board.captureClearState();
    currentFile = LizzieFrame.curFile;
    fileNameTitle = LizzieFrame.fileNameTitle;
    readBoard = Lizzie.frame == null ? null : Lizzie.frame.readBoard;
    readBoardFirstSync = readBoard != null && readBoard.firstSync;
    foregroundEngine = Lizzie.leelaz;
    outOfPlayoutsLimit = foregroundEngine != null && foregroundEngine.outOfPlayoutsLimit;
    stopByPlayouts = foregroundEngine != null && foregroundEngine.stopByPlayouts;
    winrateGraph = LizzieFrame.winrateGraph;
    maxScoreLead = winrateGraph == null ? 0.0 : winrateGraph.maxScoreLeadForModeHandoff();
    frame = Lizzie.frame;
    wasTrying = frame != null && frame.isTrying;
  }

  static CoachStartSnapshot capture() {
    if (Lizzie.board == null || Lizzie.board.getHistory() == null) {
      throw new IllegalStateException("Cannot start AI Coach without an active board history.");
    }
    return new CoachStartSnapshot(Lizzie.board);
  }

  void restore() {
    Throwable restoreFailure = null;
    try {
      if (Lizzie.board != board) {
        throw new IllegalStateException("The active board changed while AI Coach was starting.");
      }
      board.restoreClearState(boardState);
    } catch (RuntimeException | Error failure) {
      restoreFailure = failure;
    } finally {
      LizzieFrame.curFile = currentFile;
      LizzieFrame.fileNameTitle = fileNameTitle;
      if (readBoard != null && Lizzie.frame != null && Lizzie.frame.readBoard == readBoard) {
        readBoard.firstSync = readBoardFirstSync;
      }
      if (foregroundEngine != null && Lizzie.leelaz == foregroundEngine) {
        foregroundEngine.outOfPlayoutsLimit = outOfPlayoutsLimit;
        foregroundEngine.stopByPlayouts = stopByPlayouts;
      }
      if (winrateGraph != null && LizzieFrame.winrateGraph == winrateGraph) {
        winrateGraph.restoreMaxScoreLeadAfterFailedModeHandoff(maxScoreLead);
      }
      if (frame != null && Lizzie.frame == frame) {
        frame.isTrying = wasTrying;
      }
      LizzieFrame.forceRecreate = true;
    }
    try {
      if (Lizzie.frame != null) {
        Lizzie.frame.updateTitle();
        Lizzie.frame.refresh();
      }
    } catch (RuntimeException | Error failure) {
      if (restoreFailure == null) {
        restoreFailure = failure;
      } else if (restoreFailure != failure) {
        restoreFailure.addSuppressed(failure);
      }
    }
    if (restoreFailure instanceof RuntimeException) {
      throw (RuntimeException) restoreFailure;
    }
    if (restoreFailure instanceof Error) {
      throw (Error) restoreFailure;
    }
  }
}
