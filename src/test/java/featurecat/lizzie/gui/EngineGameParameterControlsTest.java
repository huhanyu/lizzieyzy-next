package featurecat.lizzie.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import featurecat.lizzie.Lizzie;
import featurecat.lizzie.enginegame.EngineGamePlayMode;
import featurecat.lizzie.enginegame.EngineGameSnapshot;
import featurecat.lizzie.enginegame.EngineGameSnapshotFixtures;
import featurecat.lizzie.enginegame.GameActivity;
import featurecat.lizzie.enginegame.RunState;
import java.util.List;
import java.util.ResourceBundle;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The game menu's "game parameters" entry is the way into the panel when the top toolbar is hidden
 * or combined into the menu bar; it follows the running engine game whenever the menu opens. The
 * ordinary breadth/aggressiveness toolbar is covered, and function search refuses it, for the whole
 * batch including its parameter restoration.
 */
class EngineGameParameterControlsTest {
  private ResourceBundle previousBundle;

  @BeforeEach
  void useBundle() {
    previousBundle = Lizzie.resourceBundle;
    Lizzie.resourceBundle = ResourceBundle.getBundle("l10n.DisplayStrings");
  }

  @AfterEach
  void restoreBundle() {
    Lizzie.resourceBundle = previousBundle;
  }

  @Test
  void gameMenuOffersThePanelWhileAnAnalysisGameIsRunningOrPaused() throws Exception {
    AtomicReference<EngineGameSnapshot> snapshot = new AtomicReference<>(idle());
    SwingUtilities.invokeAndWait(
        () -> {
          JMenu menu = new JMenu("game");
          JMenuItem entry = EngineGameParameterControls.addMenuEntry(menu, snapshot::get);
          assertEquals(
              Lizzie.resourceBundle.getString("EngineGameParameters.entry"), entry.getText());
          assertTrue(java.util.List.of(menu.getMenuComponents()).contains(entry));

          snapshot.set(EngineGameSnapshotFixtures.playingAnalysis());
          open(menu);
          assertTrue(entry.isVisible() && entry.isEnabled(), "running analysis game");

          snapshot.set(
              EngineGameSnapshotFixtures.playing(EngineGamePlayMode.ANALYSIS, RunState.PAUSED));
          open(menu);
          assertTrue(entry.isVisible() && entry.isEnabled(), "paused analysis game");
        });
  }

  @Test
  void gameMenuHidesThePanelOutsideAnAnalysisGame() throws Exception {
    AtomicReference<EngineGameSnapshot> snapshot =
        new AtomicReference<>(EngineGameSnapshotFixtures.playingAnalysis());
    SwingUtilities.invokeAndWait(
        () -> {
          JMenu menu = new JMenu("game");
          JMenuItem entry = EngineGameParameterControls.addMenuEntry(menu, snapshot::get);
          open(menu);
          assertTrue(entry.isVisible());

          snapshot.set(idle());
          open(menu);
          assertFalse(entry.isVisible(), "no engine game");

          snapshot.set(
              EngineGameSnapshotFixtures.playing(EngineGamePlayMode.GENMOVE, RunState.RUNNING));
          open(menu);
          assertFalse(entry.isVisible(), "genmove game has no game parameters");
        });
  }

  @Test
  void ordinaryToolbarStaysCoveredInEveryBatchPhaseAndSearchRefusalIsScoped() {
    Lizzie.engineGame.resetForTest();
    Lizzie.engineGame.replaceChromeForTest(transition -> {});
    try {
      EngineGameSnapshot.BatchActive starting =
          (EngineGameSnapshot.BatchActive) EngineGameSnapshotFixtures.starting();
      List<EngineGameSnapshot> covered =
          List.of(
              starting,
              EngineGameSnapshotFixtures.playingAnalysis(),
              EngineGameSnapshotFixtures.playing(EngineGamePlayMode.ANALYSIS, RunState.PAUSED),
              new EngineGameSnapshot.BatchActive(starting.batch(), new GameActivity.BetweenGames()),
              EngineGameSnapshotFixtures.playing(EngineGamePlayMode.GENMOVE, RunState.RUNNING));
      for (EngineGameSnapshot snapshot : covered) {
        Lizzie.engineGame.publishSnapshotForTest(snapshot);
        assertTrue(EngineGameParameterControls.ordinaryHidden(), snapshot.toString());
        assertTrue(EngineGameParameterControls.blocksSearch("toolbar.wrn"), snapshot.toString());
        assertTrue(EngineGameParameterControls.blocksSearch("engine.pda"), snapshot.toString());
        assertFalse(
            EngineGameParameterControls.blocksSearch("toolbar.time-limit"),
            "only the hidden groups are refused: " + snapshot);
      }

      EngineGameSnapshotFixtures.publishIdle();
      assertFalse(EngineGameParameterControls.ordinaryHidden(), "no game, nothing restoring");
      assertFalse(EngineGameParameterControls.blocksSearch("toolbar.wrn"));
      assertFalse(EngineGameParameterControls.blocksSearch("engine.pda"));
    } finally {
      Lizzie.engineGame.resetForTest();
    }
  }

  @Test
  void idleBatchStaysCoveredUntilItsParameterRestorationCompletes() {
    Lizzie.engineGame.resetForTest();
    Lizzie.engineGame.replaceChromeForTest(transition -> {});
    try {
      CompletableFuture<Void> restoration = new CompletableFuture<>();
      Lizzie.engineGame.trackParameterRestoration(restoration);
      EngineGameSnapshotFixtures.publishIdle();
      assertTrue(EngineGameParameterControls.ordinaryHidden(), "restoring after the batch");
      assertTrue(EngineGameParameterControls.blocksSearch("toolbar.wrn"), "restoring");

      restoration.complete(null);
      assertFalse(EngineGameParameterControls.ordinaryHidden(), "restoration settled");
      assertFalse(EngineGameParameterControls.blocksSearch("toolbar.wrn"), "restoration settled");
    } finally {
      Lizzie.engineGame.resetForTest();
    }
  }

  private static EngineGameSnapshot idle() {
    return new EngineGameSnapshot.Idle();
  }

  /** Opening a menu selects it, which is when Swing notifies its menu listeners. */
  private static void open(JMenu menu) {
    menu.setSelected(false);
    menu.setSelected(true);
  }
}
