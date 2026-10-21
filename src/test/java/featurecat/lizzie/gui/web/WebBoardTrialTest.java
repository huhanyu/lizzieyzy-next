package featurecat.lizzie.gui.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import featurecat.lizzie.Lizzie;
import featurecat.lizzie.analysis.Leelaz;
import featurecat.lizzie.gui.LizzieFrame;
import featurecat.lizzie.rules.Board;
import featurecat.lizzie.rules.BoardData;
import featurecat.lizzie.rules.BoardHistoryNode;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Delayed;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class WebBoardTrialTest {
  private Leelaz previousEngine;
  private WebBoardManager previousManager;
  private WebBoardManager manager;

  @BeforeEach
  void installManager() throws Exception {
    previousEngine = Lizzie.leelaz;
    previousManager = Lizzie.webBoardManager;
    Lizzie.leelaz = new Leelaz("");
    manager = new WebBoardManager();
    manager.setOverrideSinkForTest(node -> {});
    Lizzie.webBoardManager = manager;
  }

  @AfterEach
  void restore() {
    Lizzie.leelaz = previousEngine;
    Lizzie.webBoardManager = previousManager;
  }

  @Test
  void offeredOnlyWhileTheWebBoardRunsAndTheWindowShowsATrial() throws Exception {
    LizzieFrame frame = allocate(LizzieFrame.class);

    assertFalse(WebBoardTrial.canExit(null, frame));
    assertFalse(WebBoardTrial.canExit(manager, frame), "web board stopped");
    manager.setRunningForTest(true);
    assertFalse(WebBoardTrial.canExit(manager, frame), "no trial on screen");
    assertFalse(WebBoardTrial.canExit(manager, null));

    frame.setDisplayNodeOverride(anyNode());

    assertTrue(WebBoardTrial.canExit(manager, frame));
  }

  @Test
  void exitEndsTheTrialAndTellsWebViewers() {
    RecordingCollector collector = new RecordingCollector();
    manager.setCollectorForTest(collector);
    manager.enterTrial("viewer", anyNode());
    manager.setRunningForTest(true);

    WebBoardTrial.exit(manager);

    assertNull(manager.getTrialOwnerForTest());
    assertFalse(collector.broadcasts.isEmpty());
    assertNull(collector.broadcasts.get(collector.broadcasts.size() - 1));
  }

  @Test
  void exitLeavesTheTrialAloneWhileTheWebBoardIsStopped() {
    manager.setCollectorForTest(new RecordingCollector());
    manager.enterTrial("viewer", anyNode());

    WebBoardTrial.exit(manager);
    WebBoardTrial.exit(null);

    assertEquals("viewer", manager.getTrialOwnerForTest());
  }

  private static BoardHistoryNode anyNode() {
    return new BoardHistoryNode(BoardData.empty(Board.boardWidth, Board.boardHeight));
  }

  @SuppressWarnings("unchecked")
  private static <T> T allocate(Class<T> type) throws Exception {
    Field field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
    field.setAccessible(true);
    return (T) ((sun.misc.Unsafe) field.get(null)).allocateInstance(type);
  }

  private static final class RecordingCollector extends WebBoardDataCollector {
    private final List<WebBoardManager.TrialSession> broadcasts = new ArrayList<>();

    @Override
    public void runOnExecutor(Runnable r) {
      r.run();
    }

    @Override
    public ScheduledFuture<?> scheduleOnExecutor(Runnable r, long delay, TimeUnit u) {
      return new NeverFires();
    }

    @Override
    public void broadcastTrialState(WebBoardManager.TrialSession s) {
      broadcasts.add(s);
    }

    @Override
    public void onBoardStateChanged() {}
  }

  private static final class NeverFires implements ScheduledFuture<Object> {
    public boolean cancel(boolean mayInterruptIfRunning) {
      return true;
    }

    public boolean isCancelled() {
      return false;
    }

    public boolean isDone() {
      return false;
    }

    public Object get() {
      return null;
    }

    public Object get(long t, TimeUnit u) {
      return null;
    }

    public long getDelay(TimeUnit u) {
      return 0;
    }

    public int compareTo(Delayed o) {
      return 0;
    }
  }
}
