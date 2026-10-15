package featurecat.lizzie.fox;

import static org.junit.jupiter.api.Assertions.*;

import featurecat.lizzie.Config;
import featurecat.lizzie.Lizzie;
import featurecat.lizzie.analysis.Leelaz;
import featurecat.lizzie.gui.LizzieFrame;
import featurecat.lizzie.rules.Board;
import featurecat.lizzie.rules.BoardData;
import featurecat.lizzie.rules.BoardHistoryList;
import featurecat.lizzie.rules.Stone;
import featurecat.lizzie.rules.Zobrist;
import java.lang.reflect.Field;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FoxConfirmedHistoryTest {
  private int previousWidth;
  private int previousHeight;
  private Config previousConfig;
  private Board previousBoard;
  private LizzieFrame previousFrame;
  private Leelaz previousEngine;

  /** Global move callbacks are not under test; the history's own board state is. */
  static final class QuietBoard extends Board {
    @Override public void clearAfterMove() {}
  }

  @BeforeEach void smallBoard() throws Exception {
    previousWidth = Board.boardWidth;
    previousHeight = Board.boardHeight;
    previousConfig = Lizzie.config;
    previousBoard = Lizzie.board;
    previousFrame = Lizzie.frame;
    previousEngine = Lizzie.leelaz;
    Board.boardWidth = Board.boardHeight = 5;
    Zobrist.init();
    Field unsafe = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
    unsafe.setAccessible(true);
    sun.misc.Unsafe allocator = (sun.misc.Unsafe) unsafe.get(null);
    Lizzie.config = (Config) allocator.allocateInstance(Config.class);
    Lizzie.board = (Board) allocator.allocateInstance(QuietBoard.class);
    Lizzie.frame = (LizzieFrame) allocator.allocateInstance(LizzieFrame.class);
    Lizzie.setPrimaryEngine(null);
  }

  @AfterEach void restore() {
    Board.boardWidth = previousWidth;
    Board.boardHeight = previousHeight;
    Zobrist.init();
    Lizzie.config = previousConfig;
    Lizzie.board = previousBoard;
    Lizzie.frame = previousFrame;
    Lizzie.setPrimaryEngine(previousEngine);
  }

  @Test void confirmedCaptureFollowsGoRulesWhenNoCaptureSettingIsOn() {
    Lizzie.config.noCapture = true;
    BoardHistoryList history = new BoardHistoryList(BoardData.empty(5, 5));
    String[] moves = {"W1,1", "B1,0", "W4,4", "B0,1", "W4,3", "B2,1", "W4,2", "B1,2", "W3,3", "B1,1"};
    int number = 0;
    for (String move : moves) {
      char color = move.charAt(0);
      String[] xy = move.substring(1).split(",");
      FoxGameState.Placement placement = new FoxGameState.Placement(
          ++number, Integer.parseInt(xy[0]), Integer.parseInt(xy[1]), color);
      FoxLiveSession.appendConfirmed(history, new FoxGameState.Action(placement, color, number), false);
    }
    assertEquals(Stone.BLACK, history.getStones()[Board.getIndex(1, 1)],
        "captured white stone is removed, so Black can reuse the point");
    assertEquals(1, history.getData().blackCaptures);
    assertTrue(Lizzie.config.noCapture, "the user's ordinary setting is untouched");
  }
}
