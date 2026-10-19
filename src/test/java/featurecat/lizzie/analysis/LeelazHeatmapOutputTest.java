package featurecat.lizzie.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import featurecat.lizzie.Lizzie;
import featurecat.lizzie.gui.LizzieFrame;
import featurecat.lizzie.rules.Board;
import featurecat.lizzie.rules.BoardData;
import featurecat.lizzie.rules.BoardHistoryList;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Locks the heat-map output handling of {@link Leelaz} (KataGo kata-raw-nn and Leela heatmap). */
class LeelazHeatmapOutputTest {
  private static final int SIZE = 3;
  private int previousWidth;
  private int previousHeight;
  private Board previousBoard;
  private LizzieFrame previousFrame;

  @BeforeEach
  void setUp() throws Exception {
    previousWidth = Board.boardWidth;
    previousHeight = Board.boardHeight;
    previousBoard = Lizzie.board;
    previousFrame = Lizzie.frame;
    Board.boardWidth = SIZE;
    Board.boardHeight = SIZE;
    Board board = allocate(Board.class);
    board.setHistory(new BoardHistoryList(BoardData.empty(SIZE, SIZE)));
    Lizzie.board = board;
    Lizzie.frame = allocate(CountingFrame.class);
    CountingFrame.REFRESHES.set(0);
  }

  @AfterEach
  void tearDown() {
    Board.boardWidth = previousWidth;
    Board.boardHeight = previousHeight;
    Lizzie.board = previousBoard;
    Lizzie.frame = previousFrame;
  }

  @Test
  void kataRawNnResponseFillsHeatmapAndRefreshesOnceWhenOwnershipIsComplete() throws Exception {
    Leelaz engine = kataGoHeatmapEngine();

    feed(
        engine,
        "= symmetry 3",
        "whiteWin 0.25",
        "whiteLoss 0.75",
        "whiteLead -1.5",
        "policy",
        "0.1 0.2 0.3",
        "0.0 NAN 0.5",
        "0.6 0.7",
        "0.6 0.7 0.8",
        "policyPass 0.01",
        "whiteOwnership",
        "0.1 -0.2 0.3",
        "1 0 -1");

    assertEquals(3, engine.symmetry);
    assertEquals(0.25, engine.heatwinrate);
    assertEquals(-1.5, engine.heatScore);
    assertEquals(
        List.of(100.0, 200.0, 300.0, 0.0, 0.0, 500.0, 600.0, 700.0, 800.0), engine.heatPolicy);
    assertEquals(0, CountingFrame.REFRESHES.get(), "nothing is shown before ownership is complete");
    assertFalse(Lizzie.frame.isShowingHeatmap);

    feed(engine, "0.5 0.5 0.5", "0.4 0.4 0.4");

    // Black to play: white ownership is negated for display.
    assertEquals(List.of(-0.1, 0.2, -0.3, -1.0, -0.0, 1.0, -0.5, -0.5, -0.5), engine.heatOwnership);
    assertEquals(List.of(100, 200, 300, 0, 0, 500, 600, 700, 800), engine.heatcount);
    assertEquals(1, CountingFrame.REFRESHES.get());
    assertTrue(Lizzie.frame.isShowingHeatmap);
  }

  @Test
  void whiteToPlayKeepsOwnershipSignAndANewResponseStartsFresh() throws Exception {
    Leelaz engine = kataGoHeatmapEngine();
    Lizzie.board.getHistory().getData().blackToPlay = false;

    feed(engine, "= symmetry 0", "policy", "1 1 1", "1 1 1", "1 1 1", "whiteOwnership");
    feed(engine, "0.1 0.2 0.3", "0.4 0.5 0.6", "0.7 0.8 0.9");
    assertEquals(List.of(0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8, 0.9), engine.heatOwnership);
    assertEquals(0, engine.symmetry);
    assertEquals(1, CountingFrame.REFRESHES.get());

    feed(engine, "= symmetry 5", "policy", "0 0 0");
    assertEquals(5, engine.symmetry);
    assertEquals(List.of(0.0, 0.0, 0.0), engine.heatPolicy);
    assertTrue(engine.heatOwnership.isEmpty());
  }

  @Test
  void leelaHeatmapCollectsCountsAndWinrate() throws Exception {
    Leelaz engine = new Leelaz("");
    engine.isheatmap = true;

    feed(engine, " 1 2 3", " 4 5 6", "notes", " 7 8 9", "pass: 0", "winrate: 0.55");

    assertEquals(List.of(1, 2, 3, 4, 5, 6, 7, 8, 9), engine.heatcount);
    assertEquals(1, CountingFrame.REFRESHES.get());
    assertEquals(0.55, engine.heatwinrate);
    assertTrue(Lizzie.frame.isShowingHeatmap);
  }

  @Test
  void outputIsIgnoredWhenNoHeatmapWasRequested() throws Exception {
    Leelaz engine = kataGoHeatmapEngine();
    engine.isheatmap = false;

    feed(engine, "= symmetry 2", "whiteWin 0.9", "policy", "1 1 1");

    assertEquals(0, engine.symmetry);
    assertEquals(-1.0, engine.heatwinrate);
    assertTrue(engine.heatPolicy.isEmpty());
  }

  private static Leelaz kataGoHeatmapEngine() throws Exception {
    Leelaz engine = new Leelaz("");
    engine.isheatmap = true;
    engine.isKatago = true;
    engine.iskataHeatmapShowOwner = false;
    return engine;
  }

  private static void feed(Leelaz engine, String... lines) throws Exception {
    Method method = Leelaz.class.getDeclaredMethod("parseHeatMap", String.class);
    method.setAccessible(true);
    for (String line : lines) {
      method.invoke(engine, line);
    }
  }

  @SuppressWarnings("unchecked")
  private static <T> T allocate(Class<T> type) throws Exception {
    Field field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
    field.setAccessible(true);
    return (T) ((sun.misc.Unsafe) field.get(null)).allocateInstance(type);
  }

  private static final class CountingFrame extends LizzieFrame {
    // Static because the frame is allocated without running field initializers.
    static final AtomicInteger REFRESHES = new AtomicInteger();

    @Override
    public void refresh() {
      REFRESHES.incrementAndGet();
    }
  }
}
