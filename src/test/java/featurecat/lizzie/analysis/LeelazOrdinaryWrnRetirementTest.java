package featurecat.lizzie.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import featurecat.lizzie.Config;
import featurecat.lizzie.Lizzie;
import featurecat.lizzie.gui.BottomToolbar;
import featurecat.lizzie.gui.LizzieFrame;
import featurecat.lizzie.rules.Board;
import featurecat.lizzie.rules.BoardData;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * An ordinary breadth change retires the running analysis before the engine's breadth changes: a
 * late line of the old search is not recorded with the new breadth, and the next search is.
 */
class LeelazOrdinaryWrnRetirementTest {

  @Test
  void lateOldSearchOutputIsNotRecordedWithTheNewBreadth() throws Exception {
    Leelaz previousEngine = Lizzie.leelaz;
    Board previousBoard = Lizzie.board;
    Config previousConfig = Lizzie.config;
    LizzieFrame previousFrame = Lizzie.frame;
    BottomToolbar previousToolbar = LizzieFrame.toolbar;
    featurecat.lizzie.gui.Menu previousMenu = LizzieFrame.menu;
    Leelaz engine = new Leelaz("");
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    try {
      engine.isLoaded = true;
      engine.started = true;
      engine.isKatago = true;
      engine.commandLists.addAll(List.of("stop", "kata-analyze"));
      setField(engine, "endGetCommandList", true);
      setField(engine, "outputStream", new BufferedOutputStream(output));
      setField(engine, "currentCmdNum", 15);
      setField(engine, "cmdNumber", 16);
      Lizzie.leelaz = engine;
      Lizzie.config = allocate(Config.class);
      Lizzie.config.enableLizzieCache = true;
      Lizzie.board = new Board();
      Lizzie.frame = allocate(AcceptingInfoFrame.class);
      LizzieFrame.menu = allocate(SilentMenu.class);
      LizzieFrame.toolbar = allocate(BottomToolbar.class);

      engine.setOrdinaryWrn(0.04);
      engine.sendCommand("kata-analyze W 10");
      engine.setResponseUpToDate();
      parseInfo(engine, info(100, 0.6));
      BoardData node = Lizzie.board.getData();
      assertEquals(100, node.getPlayouts());
      assertEquals(0.04, node.wrn);
      assertTrue(node.wrnKnown);

      // An older queued search must retain the old metadata until the setter is written.
      Lizzie.board.clearBestMovesAfter(Lizzie.board.getHistory().getStart());
      setField(engine, "normalCommandSendInProgress", true);
      engine.sendCommand("kata-analyze W 10");
      engine.setOrdinaryWrn(0.1);
      assertEquals(0.04, engine.wrn, "queued is not physically sent");
      setField(engine, "normalCommandSendInProgress", false);
      engine.sendCommand("name");
      String sent = output.toString(StandardCharsets.UTF_8);
      assertTrue(sent.contains("kata-set-param analysisWideRootNoise 0.1"), sent);
      assertEquals(0.1, engine.wrn);
      // A deeper line the 0.04 search printed before it read the new breadth.
      parseInfo(engine, info(300, 0.7));
      assertEquals(100, node.getPlayouts(), "a retired search must not be adopted");
      assertEquals(60.0, node.winrate, 0.001);
      assertEquals(0.04, node.wrn, "the old result keeps its own breadth");

      // The next search runs with 0.1 and its result records it.
      engine.sendCommand("kata-analyze W 10");
      engine.setResponseUpToDate();
      parseInfo(engine, info(50, 0.65));
      assertEquals(50, node.getPlayouts());
      assertEquals(65.0, node.winrate, 0.001);
      assertEquals(0.1, node.wrn);
      assertTrue(node.wrnKnown);
    } finally {
      Lizzie.leelaz = previousEngine;
      Lizzie.board = previousBoard;
      Lizzie.config = previousConfig;
      Lizzie.frame = previousFrame;
      LizzieFrame.toolbar = previousToolbar;
      LizzieFrame.menu = previousMenu;
    }
  }

  private static String info(int visits, double winrate) {
    return "info move D4 visits "
        + visits
        + " winrate "
        + winrate
        + " scoreLead 1.5 prior 0.2 pv D4 rootInfo visits "
        + visits
        + " winrate "
        + winrate
        + " scoreLead 1.5";
  }

  private static void parseInfo(Leelaz engine, String line) throws Exception {
    Method method = Leelaz.class.getDeclaredMethod("parseLine", String.class);
    method.setAccessible(true);
    method.invoke(engine, line);
  }

  private static void setField(Object target, String name, Object value) throws Exception {
    Field field = Leelaz.class.getDeclaredField(name);
    field.setAccessible(true);
    field.set(target, value);
  }

  @SuppressWarnings("unchecked")
  private static <T> T allocate(Class<T> type) throws Exception {
    Field field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
    field.setAccessible(true);
    return (T) ((sun.misc.Unsafe) field.get(null)).allocateInstance(type);
  }

  private static final class SilentMenu extends featurecat.lizzie.gui.Menu {
    @Override
    public void toggleEngineMenuStatus(boolean pondering, boolean thinking) {}
  }

  private static final class AcceptingInfoFrame extends LizzieFrame {
    @Override
    public void requestAnalysisRefresh() {}

    @Override
    public void requestAnalysisTitleUpdate() {}
  }
}
