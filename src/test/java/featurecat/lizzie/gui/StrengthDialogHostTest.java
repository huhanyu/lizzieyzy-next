package featurecat.lizzie.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import featurecat.lizzie.analysis.AnalysisEngine;
import featurecat.lizzie.rules.BoardData;
import featurecat.lizzie.rules.BoardHistoryNode;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StrengthDialogHostTest {
  /** The first position of a game and {@code moves} moves after it, none analysed. */
  private static List<BoardHistoryNode> game(int moves) {
    List<BoardHistoryNode> nodes = new ArrayList<>();
    BoardHistoryNode node = new BoardHistoryNode(BoardData.empty(19, 19));
    nodes.add(node);
    for (int i = 0; i < moves; i++) {
      node = node.add(new BoardHistoryNode(BoardData.empty(19, 19)));
      nodes.add(node);
    }
    return nodes;
  }

  @Test
  void progressCountsTheAnalysedMovesOfTheMainLine() {
    List<BoardHistoryNode> game = game(5);
    assertEquals(
        new StrengthDialog.Progress(0, 5), StrengthDialogHost.analysisProgress(game.get(0)));
    game.get(0).getData().setPlayouts(50);
    game.get(1).getData().setPlayouts(50);
    game.get(4).getData().setPlayouts(50);
    assertEquals(
        new StrengthDialog.Progress(2, 5),
        StrengthDialogHost.analysisProgress(game.get(0)),
        "the first position is not a move");
    assertEquals(
        new StrengthDialog.Progress(0, 0), StrengthDialogHost.analysisProgress(game(0).get(0)));
  }

  @Test
  void stampFollowsTheAnalysisOfTheMovesItReads() {
    List<BoardHistoryNode> game = game(5);
    BoardHistoryNode start = game.get(0);
    long before = StrengthDialogHost.analysisStamp(start, 3);
    game.get(3).getData().setPlayouts(80);
    long analysed = StrengthDialogHost.analysisStamp(start, 3);
    assertNotEquals(before, analysed);
    game.get(4).getData().setPlayouts(80);
    assertEquals(
        analysed, StrengthDialogHost.analysisStamp(start, 3), "move 4 is beyond the first three");
    assertNotEquals(analysed, StrengthDialogHost.analysisStamp(start, 5));
  }

  @Test
  void flashAnalysisCountsAsAnalyzingFromTheStartOfItsEngine() throws Exception {
    LizzieFrame frame = allocate(LizzieFrame.class);
    StrengthDialogHost host = new StrengthDialogHost(frame, () -> null);
    assertFalse(host.analyzing());

    setField(frame, "flashAnalysisStarting", true);
    assertTrue(host.analyzing(), "the analysis engine is still starting");
    setField(frame, "flashAnalysisStarting", false);

    AnalysisEngine engine = allocate(AnalysisEngine.class);
    Map<Integer, BoardHistoryNode> requests = new HashMap<>();
    requests.put(1, game(1).get(1));
    setField(engine, "analyzeMap", requests);
    frame.analysisEngine = engine;
    assertTrue(host.analyzing(), "results are still coming in");
    setField(engine, "responseCount", 1);
    assertFalse(host.analyzing(), "every result is in");
  }

  @SuppressWarnings("unchecked")
  private static <T> T allocate(Class<T> type) throws Exception {
    Field unsafe = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
    unsafe.setAccessible(true);
    return (T) ((sun.misc.Unsafe) unsafe.get(null)).allocateInstance(type);
  }

  private static void setField(Object target, String name, Object value) throws Exception {
    Class<?> type = target.getClass();
    while (true) {
      try {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
        return;
      } catch (NoSuchFieldException missing) {
        type = type.getSuperclass();
        if (type == null) {
          throw missing;
        }
      }
    }
  }
}
