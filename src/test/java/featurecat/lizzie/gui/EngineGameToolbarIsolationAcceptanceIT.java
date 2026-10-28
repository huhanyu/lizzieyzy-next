package featurecat.lizzie.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import featurecat.lizzie.Config;
import featurecat.lizzie.Lizzie;
import featurecat.lizzie.analysis.KataGoRules;
import featurecat.lizzie.analysis.Leelaz;
import featurecat.lizzie.enginegame.Acceptance;
import featurecat.lizzie.enginegame.EngineGameBatchSpec;
import featurecat.lizzie.enginegame.EngineGameOpeningParameters;
import featurecat.lizzie.enginegame.EngineGameOpeningPlan;
import featurecat.lizzie.enginegame.EngineGameOutputChoices;
import featurecat.lizzie.enginegame.EngineGameParameterState;
import featurecat.lizzie.enginegame.EngineGamePlayMode;
import featurecat.lizzie.enginegame.EngineGameResignPolicy;
import featurecat.lizzie.enginegame.EngineGameSideLimits;
import featurecat.lizzie.enginegame.EngineGameSnapshot;
import featurecat.lizzie.enginegame.EngineGameTimeMode;
import featurecat.lizzie.enginegame.EngineParticipantIdentity;
import featurecat.lizzie.enginegame.StartFailure;
import featurecat.lizzie.enginegame.StartObserver;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dialog;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Window;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import javax.imageio.ImageIO;
import javax.swing.AbstractButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JSeparator;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.text.JTextComponent;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Opt-in private-display acceptance for hiding the ordinary breadth/aggressiveness toolbar during
 * an engine game. A child JVM runs the real main window; two {@link EngineGameToolbarGtpPeer}
 * processes are the participants. Lifecycle states are entered through the production accept,
 * pause/resume, stop, layout toggle, window resize and function-search entries; held peer responses
 * keep preparation, the batch interval and parameter restoration open long enough to observe them.
 * The double (top toolbar) layout carries the groups; the single layout is checked as observed, and
 * in this build it attaches no parameter row at all.
 *
 * <p>This proves UI and lifecycle behaviour only. The participants are controlled fixtures, so
 * nothing here shows how real KataGo applies breadth or aggressiveness.
 */
public final class EngineGameToolbarIsolationAcceptanceIT {
  private static final Logger LOG =
      LoggerFactory.getLogger(EngineGameToolbarIsolationAcceptanceIT.class);
  private static final String ENABLE = "lizzie.acceptance.engineGameToolbar";
  private static final String WRN = EngineGameToolbarGtpPeer.WRN;
  private static final String PDA = EngineGameToolbarGtpPeer.PDA;

  /** Values only the engine game writes; the peers classify any other set after them a restore. */
  private static final String GAME_VALUES = "game-values=0.2,0.33,0.4,0.5,-0.5,0.6";

  private static final double[] INITIAL_WRN = {0.05, 0.15};
  private static final double[] INITIAL_PDA = {0.75, -0.25};

  /** FlowLayout gap in the double row; the single row places its tools 2 px after komi. */
  private static final int SLOT_GAP_TOLERANCE = 4;

  @Test
  void ordinaryGroupsStayHiddenFromPreparationThroughHeldRestoration() throws Exception {
    run("ordinary-batch");
  }

  @Test
  void customGroupStaysHiddenAndStartupFailureConverges() throws Exception {
    run("custom-start-failure");
  }

  @Test
  void restoreFailureEndsTheOverrideWithoutEnablingAnUnavailableEngine() throws Exception {
    run("restore-failure");
  }

  @Test
  void customPdaEntryStaysDisabledAfterRestoreFailure() throws Exception {
    run("custom-restore-failure");
  }

  private void run(String mode) throws Exception {
    assumeTrue(Boolean.getBoolean(ENABLE));
    DesktopProbeProcess.requireDisplay();
    Path result =
        DesktopProbeProcess.run(
            getClass(), "engine-game-toolbar-" + mode, List.of(), List.of(mode), 300);
    String text = Files.readString(result);
    assertTrue(text.contains("result=PASS"), text);
    assertTrue(text.contains("clock-helper-closed=PASS"), text);
  }

  public static void main(String[] args) throws Exception {
    Path work = Path.of(args[1]);
    Path result = Path.of(args[2]);
    Probe probe = new Probe(args[0], work, result);
    int exit = 1;
    try {
      probe.run();
      record(result, "mode=" + args[0] + "\nresult=PASS");
      exit = 0;
    } catch (Throwable failure) {
      LOG.error("Engine-game toolbar acceptance failed", failure);
      probe.screenshotQuietly("failure");
      record(result, "result=FAIL\nerror=" + failure.toString().replace('\n', ' '));
    } finally {
      try {
        shutdownClockHelper();
        record(result, "clock-helper-closed=PASS");
      } catch (Throwable failure) {
        exit = 1;
        LOG.error("Engine-game toolbar acceptance cleanup failed", failure);
        record(result, "result=FAIL\ncleanup=clock-helper");
      } finally {
        // DesktopProbeProcess owns and reaps this JVM and both peer processes, even on failure.
        System.exit(exit);
      }
    }
  }

  /** One isolated application run; every assertion is about what the window shows or sends. */
  private static final class Probe {
    private final String mode;
    private final Path work;
    private final Path result;
    private final Path[] peers;
    private final JSONArray entries = new JSONArray();

    /** Why search would refuse breadth before any game; the game's refusal must end with it. */
    private String idleSearchReason;

    /** Group parts each layout showed before the game, keyed by "double layout". */
    private final Map<Boolean, List<String>> idleShown = new java.util.HashMap<>();

    Probe(String mode, Path work, Path result) {
      this.mode = mode;
      this.work = work;
      this.result = result;
      this.peers = new Path[] {work.resolve("peer-0"), work.resolve("peer-1")};
    }

    void run() throws Exception {
      switch (mode) {
        case "ordinary-batch" -> ordinaryBatch();
        case "custom-start-failure" -> customStartFailure();
        case "restore-failure" -> restoreFailure(false);
        case "custom-restore-failure" -> restoreFailure(true);
        default -> throw new IllegalArgumentException(mode);
      }
    }

    // ---- Scenarios ----------------------------------------------------------------------

    /**
     * Ordinary WRN+PDA in a two-game batch: held preparation, running with a game-parameter edit,
     * paused with layout switches and a narrow-window re-layout, the held batch interval, a layout
     * switch in the running second game, then a user stop whose restoration is held.
     */
    private void ordinaryBatch() throws Exception {
      launch(true, true, "KataGo", new String[0], new String[0]);
      baselines(
          List.of("wrnFieldPanel", "chkWRN", "txtWRN", "pdaFieldPanel", "chkPDA", "txtGfPDA"),
          List.of("lblCustomPda", "txtPDA", "customPDAMorePanel"));
      Map<String, String> before = preferences();
      write("config-before.json", before);
      SetKataEngines draft =
          edt(
              () -> {
                SetKataEngines dialog = new SetKataEngines();
                dialog.setVisible(true);
                ((JTextField) field(SetKataEngines.class, "txtPDA", dialog)).setText("1.25");
                ((JTextField) field(SetKataEngines.class, "txtWRN", dialog)).setText("0.27");
                return dialog;
              });

      arm(0, "get-param");
      AtomicReference<StartFailure> failure = new AtomicReference<>();
      accept(spec(true, 14, new EngineGameOpeningParameters(0.2, 0.4, 0.5, -0.5)), failure);
      awaitHeld(0, "get-param");
      assertTrue(edt(() -> Lizzie.engineGame.current().starting()), "preparation is held");
      awaitHidden("preparing");
      denySearch("preparing", before);
      screenshot("preparing");
      release(0, "get-param");

      awaitGame(failure, () -> Lizzie.engineGame.current().playing(), "first game runs");
      awaitHidden("running");
      assertEntry("running", true, true);
      editSecondBreadthThroughEntry(0.6);
      record("game-parameter-entry-edit=PASS");

      edt(
          () -> {
            Lizzie.engineGame.pause();
            return null;
          });
      await(() -> Lizzie.engineGame.current().paused(), 10, "game pauses");
      awaitHidden("paused");
      assertEntry("paused", true, true);
      waitQuiet();
      int[] pausedMark = mark();
      switchLayout(false);
      awaitHidden("paused-single-layout");
      switchLayout(true);
      awaitHidden("paused-double-relayout");
      narrowRelayout("paused");
      denySearch("paused", before);
      edt(
          () -> {
            LizzieFrame.menu.updateMenuStatusForEngine();
            LizzieFrame.menu.refreshBreadthAdmission();
            return null;
          });
      drain();
      awaitHidden("paused-refreshed");
      assertNoSettersOrAnalysis(pausedMark, "paused layout switches and refresh");
      screenshot("paused");

      arm(0, "rules-restore");
      edt(
          () -> {
            Lizzie.engineGame.resume();
            return null;
          });
      awaitHeld(0, "rules-restore");
      assertTrue(
          edt(() -> Lizzie.engineGame.current().betweenGames()),
          "first game's retirement holds the batch interval");
      awaitHidden("between-games");
      assertEntry("between-games", true, false);
      denySearch("between-games", before);
      screenshot("between-games");
      release(0, "rules-restore");

      awaitGame(
          failure,
          () ->
              Lizzie.engineGame.current().playing()
                  && Lizzie.engineGame.current().view().gameOrdinal() == 2,
          "second game runs");
      // Armed first: should the game reach its move limit, its restoration is still held.
      arm(0, "param-restore");
      switchLayout(false);
      awaitHidden("running-second-game-single-layout");
      switchLayout(true);
      awaitHidden("running-second-game-double-relayout");
      assertTrue(edt(() -> Lizzie.engineGame.current().playing()), "switched while running");
      screenshot("running-second-game");

      SwingUtilities.invokeLater(Lizzie.engineGame::stop);
      awaitHeld(0, "param-restore");
      await(
          () -> Lizzie.engineGame.current() instanceof EngineGameSnapshot.Idle,
          10,
          "stop publishes idle");
      awaitHidden("restoring");
      denySearch("restoring", before);
      assertEntry("restoring", false, false);
      SetKataEngines parameters =
          edt(
              () -> {
                SetKataEngines dialog = new SetKataEngines();
                dialog.setVisible(true);
                return dialog;
              });
      assertParameterEditors(parameters, false);
      assertParameterEditors(draft, false);
      int[] restoreMark = mark();
      edt(
          () -> {
            ((AbstractButton) field(SetKataEngines.class, "btnApply", draft)).doClick();
            draft.dispose();
            return null;
          });
      waitQuiet();
      assertNoSettersOrAnalysis(restoreMark, "advanced-save-during-restoration");
      assertPreferences(before, preferences(), "advanced-save-during-restoration", false);
      screenshot("restoring");
      release(0, "param-restore");

      awaitShown("restored");
      assertNull(edt(() -> Lizzie.engineGame.parameterRestoreFailure()));
      assertParameterEditors(parameters, true);
      edt(
          () -> {
            parameters.dispose();
            return null;
          });
      afterSettlement(before, "ordinary-batch");
    }

    private void assertParameterEditors(SetKataEngines dialog, boolean enabled) throws Exception {
      edt(
          () -> {
            for (String name : List.of("chkEditPDA", "chkEditWRN")) {
              assertEquals(
                  enabled,
                  ((AbstractButton) field(SetKataEngines.class, name, dialog)).isEnabled(),
                  name + " must follow ordinary parameter ownership through restoration");
            }
            for (String name : List.of("txtPDA", "txtWRN")) {
              JComponent input = (JComponent) field(SetKataEngines.class, name, dialog);
              if (!enabled) assertFalse(input.isEnabled(), name + " remains locked during restore");
            }
            return null;
          });
      record("advanced-parameter-editors.enabled=" + enabled);
    }

    /**
     * A dedicated-PDA primary (custom PDA group) with ordinary WRN. Preparation is held, a rejected
     * opening setter fails the start, then a single game is re-laid out while running and ends
     * naturally at its move limit.
     */
    private void customStartFailure() throws Exception {
      launch(true, true, "KataGoPda", new String[0], new String[] {"reject-set=0.33"});
      assertTrue(edt(() -> Lizzie.leelaz.isKataGoPda), "primary is a dedicated-PDA KataGo");
      baselines(
          List.of("wrnFieldPanel", "chkWRN", "txtWRN", "lblCustomPda", "txtPDA", "more2"),
          List.of("pdaFieldPanel", "chkPDA", "txtGfPDA"));
      Map<String, String> before = preferences();
      write("config-before.json", before);

      arm(0, "get-param");
      AtomicReference<StartFailure> failure = new AtomicReference<>();
      accept(spec(false, 200, new EngineGameOpeningParameters(0.2, 0.33, 0, 0.5)), failure);
      awaitHeld(0, "get-param");
      awaitHidden("preparing");
      denySearch("preparing", before);
      screenshot("preparing");
      Set<Window> windows = edt(Probe::showingWindows);
      release(0, "get-param");
      await(() -> failure.get() != null, 40, "rejected opening setter fails the start");
      record("start-failure=" + failure.get().getClass().getSimpleName());
      awaitShown("after-start-failure");
      assertFalse(edt(() -> Lizzie.engineGame.ordinaryParametersHidden()));
      screenshot("after-start-failure");
      dismissNewWindows(windows, "start-failure");
      assertPreferences(before, preferences(), "start failure", true);

      AtomicReference<StartFailure> secondFailure = new AtomicReference<>();
      accept(spec(false, 12, new EngineGameOpeningParameters(0.2, 0.4, 0, 0.5)), secondFailure);
      awaitGame(secondFailure, () -> Lizzie.engineGame.current().playing(), "game runs");
      awaitHidden("running");
      assertEntry("running", true, true);
      switchLayout(false);
      awaitHidden("running-single-layout");
      switchLayout(true);
      awaitHidden("running-double-relayout");
      narrowRelayout("running");
      assertTrue(edt(() -> Lizzie.engineGame.current().playing()), "re-laid out while running");
      screenshot("running");

      await(
          () ->
              Lizzie.engineGame.current() instanceof EngineGameSnapshot.Idle
                  && Lizzie.engineGame.lastSummary() != null,
          60,
          "natural completion at the move limit");
      awaitShown("restored");
      assertNull(edt(() -> Lizzie.engineGame.parameterRestoreFailure()));
      afterSettlement(before, "custom-start-failure");
    }

    /**
     * Ordinary PDA hidden by the user. Both participants reject the batch restore, so the
     * production failure policy stops them; the override must end, controls of the unavailable
     * engine must not be operable, and a restarted (qualified) engine shows the user's display
     * choices without any batch value.
     */
    private void restoreFailure(boolean custom) throws Exception {
      String[] reject = {"reject-param-restore"};
      launch(true, false, custom ? "KataGoPda" : "KataGo", reject, reject);
      baselines(
          custom
              ? List.of("wrnFieldPanel", "chkWRN", "txtWRN", "lblCustomPda", "txtPDA", "more2")
              : List.of("wrnFieldPanel", "chkWRN", "txtWRN"),
          custom
              ? List.of("pdaFieldPanel", "chkPDA", "txtGfPDA")
              : List.of("pdaFieldPanel", "chkPDA", "txtGfPDA", "lblCustomPda", "txtPDA"));
      Map<String, String> before = preferences();
      write("config-before.json", before);

      AtomicReference<StartFailure> failure = new AtomicReference<>();
      accept(
          spec(false, 200, new EngineGameOpeningParameters(0.2, 0.4, custom ? 0 : 0.5, -0.5)),
          failure);
      awaitGame(failure, () -> Lizzie.engineGame.current().playing(), "game runs");
      awaitHidden("running");
      int launchedProcesses = incarnations(0) + incarnations(1);
      Set<Window> windows = edt(Probe::showingWindows);
      SwingUtilities.invokeLater(Lizzie.engineGame::stop);
      await(() -> Lizzie.engineGame.parameterRestoreFailure() != null, 40, "restore failure");
      String detail = edt(() -> Lizzie.engineGame.parameterRestoreFailure());
      record("restore-failure-detail=" + detail.replace('\n', ' '));
      await(
          () -> !Lizzie.engineGame.ordinaryParametersHidden(),
          30,
          "failed restoration still ends the game override");
      String needle = normalized(detail.split(";")[0]);
      AtomicReference<String> report = new AtomicReference<>();
      awaitOffEdt(
          () -> {
            report.set(edt(() -> windowTextContaining(windows, needle)));
            return report.get() != null;
          },
          10,
          "restore failure reported to the user");
      record("restore-failure-report=" + report.get());
      screenshot("restore-failed");
      dismissNewWindows(windows, "restore-failure");

      // While the current engine is unavailable its ordinary controls may show, never operate.
      boolean unavailableSeen = false;
      long until = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(1500);
      while (System.nanoTime() < until) {
        String violation = edt(Probe::enabledWhileUnavailable);
        if (violation != null) throw new AssertionError(violation);
        unavailableSeen |= !edt(() -> qualified(Lizzie.leelaz));
        Thread.sleep(50);
      }
      record("unavailable-engine-window=" + (unavailableSeen ? "OBSERVED" : "NOT_OBSERVED"));

      edt(
          () -> {
            Lizzie.engineManager.reStartEngine();
            return null;
          });
      await(
          () ->
              qualified(Lizzie.leelaz)
                  && showing(LizzieFrame.menu.chkWRN)
                  && LizzieFrame.menu.chkWRN.isEnabled(),
          60,
          "qualified replacement shows enabled ordinary breadth");
      assertTrue(incarnations(0) + incarnations(1) > launchedProcesses, "a new process runs");
      awaitShown("replacement");
      if (custom) assertTrue(edt(() -> LizzieFrame.menu.more2.isEnabled()));
      assertFalse(edt(() -> Lizzie.engineGame.ordinaryParametersHidden()));
      int current = edt(() -> Lizzie.engineManager.engineList.indexOf(Lizzie.leelaz));
      assertTrue(current >= 0, "current engine is a configured participant");
      for (String line : commands(current)) {
        assertFalse(isGameValue(line), "replacement received a batch value: " + line);
      }
      record("replacement-clean=PASS");
      Map<String, String> after = preferences();
      write("config-after.json", after);
      // The replacement may be the other participant, whose own startup values the fields show.
      assertPreferences(before, after, "restore failure and replacement", false);
      screenshot("replacement");
    }

    // ---- Launch and baseline ------------------------------------------------------------

    private void launch(
        boolean doubleMenu,
        boolean showPda,
        String firstName,
        String[] firstFlags,
        String[] secondFlags)
        throws Exception {
      String[] names = {firstName, "KataGo"};
      String[][] flags = {firstFlags, secondFlags};
      Path javaExecutable =
          Path.of(
              System.getProperty("java.home"),
              "bin",
              "java" + (System.getProperty("os.name", "").startsWith("Windows") ? ".exe" : ""));
      for (int index = 0; index < 2; index++) {
        Files.createDirectories(peers[index]);
        StringBuilder command =
            new StringBuilder()
                .append(quote(javaExecutable.toString()))
                .append(" -cp ")
                .append(quote(System.getProperty("java.class.path")))
                .append(' ')
                .append(EngineGameToolbarGtpPeer.class.getName())
                .append(' ')
                .append(quote(peers[index].toString()))
                .append(' ')
                .append(names[index])
                .append(' ')
                .append(INITIAL_WRN[index])
                .append(' ')
                .append(INITIAL_PDA[index])
                .append(' ')
                .append(GAME_VALUES);
        for (String flag : flags[index]) command.append(' ').append(flag);
        entries.put(
            new JSONObject()
                .put("name", "Toolbar peer " + (index + 1))
                .put("command", command.toString())
                .put("preload", true)
                .put("isDefault", index == 0)
                .put("width", 19)
                .put("height", 19)
                .put("komi", 7.5)
                .put("useJavaSSH", false));
      }
      JSONObject ui =
          new JSONObject()
              .put("first-time-load", false)
              .put("use-language", 2)
              .put("autoload-empty", false)
              .put("autoload-default", true)
              .put("autoload-last", false)
              .put("default-engine", 0)
              .put("auto-quick-analyze-on-load", false)
              .put("not-start-pondering", true)
              .put("autoload-kata-engine-wrn", false)
              .put("autoload-kata-engine-pda", false)
              .put("engine-pk-ponder", false)
              .put("play-sound", false)
              .put("show-double-menu", doubleMenu)
              .put("show-wrn-in-menu", true)
              .put("show-pda-in-menu", showPda);
      Files.writeString(
          work.resolve("config.txt"),
          new JSONObject()
              .put("ui", ui)
              .put("leelaz", new JSONObject().put("engine-settings-list", entries))
              .toString(2));
      System.setProperty("lizzie.work.dir", work.toString());
      DesktopProbeProcess.phase(result, "lizzie-main");
      Lizzie.main(new String[0]);
      await(() -> Lizzie.frame != null && Lizzie.frame.isShowing(), 40, "application window");
      await(
          () ->
              Lizzie.engineManager != null
                  && Lizzie.engineManager.engineList.size() == 2
                  && Lizzie.engineManager.engineList.stream()
                      .allMatch(
                          e ->
                              e.isLoaded()
                                  && e.isStarted()
                                  && e.isKatago
                                  && !e.hasExclusiveGtpWorkInProgress()),
          70,
          "both controlled participants ready");
      assertEquals(doubleMenu, edt(() -> Lizzie.config.showDoubleMenu));
      edt(
          () -> {
            for (Window window : Window.getWindows()) {
              if (window instanceof Dialog && window.isShowing()) window.dispose();
            }
            return null;
          });
      record("participants-ready=PASS");
    }

    /**
     * What each layout shows before any game, observed rather than assumed. The double layout must
     * show the scenario's groups, or the probe would be vacuous; the single layout is recorded as
     * it is (in this build it attaches no parameter row at all).
     */
    private void baselines(List<String> requiredInDouble, List<String> forbiddenInDouble)
        throws Exception {
      edt(
          () -> {
            LizzieFrame.menu.updateMenuStatusForEngine();
            return null;
          });
      drain();
      await(
          () -> {
            List<String> shown = shownNames();
            return shown.containsAll(requiredInDouble)
                && forbiddenInDouble.stream().noneMatch(shown::contains);
          },
          30,
          "idle double layout shows " + requiredInDouble + " and not " + forbiddenInDouble);
      idleShown.put(true, settledShownNames());
      record("idle-shown.double=" + idleShown.get(true));
      assertTrue(edt(() -> LizzieFrame.menu.chkWRN.isEnabled()), "idle breadth is operable");
      idleSearchReason = edt(() -> searchController().unavailableReason("toolbar.wrn"));
      record("search.idle.toolbar.wrn=" + idleSearchReason);
      assertEntry("idle", false, false);
      screenshot("idle-double");
      switchLayout(false);
      idleShown.put(false, settledShownNames());
      record(
          "idle-shown.single="
              + idleShown.get(false)
              + " komi-row-attached="
              + edt(() -> showing(LizzieFrame.menu.komiPanel)));
      screenshot("idle-single");
      switchLayout(true);
      awaitShown("idle-double-again");
    }

    private void afterSettlement(Map<String, String> before, String label) throws Exception {
      assertEntry("settled", false, false);
      assertEquals(
          idleSearchReason,
          edt(() -> searchController().unavailableReason("toolbar.wrn")),
          "search denial ends with the game");
      waitQuiet();
      int[] settledMark = mark();
      switchLayout(false);
      awaitShown("settled-single-layout");
      switchLayout(true);
      awaitShown("settled-double-layout");
      edt(
          () -> {
            LizzieFrame.menu.updateMenuStatusForEngine();
            LizzieFrame.menu.refreshBreadthAdmission();
            return null;
          });
      drain();
      awaitShown("settled-refreshed");
      assertTrue(edt(() -> LizzieFrame.menu.chkWRN.isEnabled()), "restored breadth is operable");
      waitQuiet();
      assertNoSettersOrAnalysis(settledMark, "settled layout switches and refresh");
      Map<String, String> after = preferences();
      write("config-after.json", after);
      assertPreferences(before, after, label, false);
      edt(
          () -> {
            assertEquals(
                Lizzie.leelaz.wrn, Double.parseDouble(LizzieFrame.menu.txtWRN.getText()), 1e-9);
            if (!Lizzie.leelaz.isKataGoPda)
              assertEquals(
                  Lizzie.leelaz.pda, Double.parseDouble(LizzieFrame.menu.txtGfPDA.getText()), 1e-9);
            return null;
          });
      screenshot("settled");
      record("settled-visibility-refresh-silent=PASS");
    }

    // ---- Engine game entries ------------------------------------------------------------

    private EngineGameBatchSpec spec(
        boolean batch, int maxMoves, EngineGameOpeningParameters openingParameters) {
      EngineGameSideLimits limits =
          new EngineGameSideLimits(
              EngineGameTimeMode.FIXED, 1, "", -1, -1, new EngineGameResignPolicy(1000, 2, 0.0));
      return new EngineGameBatchSpec(
          identity(0),
          identity(1),
          EngineGamePlayMode.ANALYSIS,
          limits,
          limits,
          7.5,
          0,
          new EngineGameOpeningPlan.EmptyBoard(),
          batch,
          batch,
          batch ? 2 : 1,
          true,
          maxMoves,
          new EngineGameOutputChoices(false, false, "toolbar-acceptance"),
          KataGoRules.parse("chinese").orElseThrow(),
          openingParameters);
    }

    private EngineParticipantIdentity identity(int index) {
      JSONObject entry = entries.getJSONObject(index);
      return new EngineParticipantIdentity(entry.getString("command"), entry.getString("name"));
    }

    /** Production accept on the event thread without waiting, so a held prepare cannot block. */
    private void accept(EngineGameBatchSpec spec, AtomicReference<StartFailure> failure)
        throws Exception {
      AtomicReference<Object> acceptance = new AtomicReference<>();
      SwingUtilities.invokeLater(
          () -> {
            try {
              acceptance.set(
                  Lizzie.engineGame.accept(
                      spec,
                      new StartObserver() {
                        @Override
                        public void playing() {}

                        @Override
                        public void startFailed(StartFailure cause) {
                          failure.set(cause);
                        }
                      }));
            } catch (Throwable error) {
              acceptance.set(error);
            }
          });
      awaitOffEdt(() -> acceptance.get() != null, 15, "accept returns");
      assertInstanceOf(Acceptance.Accepted.class, acceptance.get(), "engine game accepted");
    }

    private void awaitGame(
        AtomicReference<StartFailure> failure, Callable<Boolean> condition, String label)
        throws Exception {
      await(
          () -> {
            assertNull(failure.get(), "unexpected start failure");
            return condition.call();
          },
          60,
          label);
    }

    /**
     * Visible/enabled state of the game menu's "game parameters" entry, refreshed the way opening
     * the menu does, and of the toolbar button when that is on screen.
     */
    private void assertEntry(String state, boolean visible, boolean enabled) throws Exception {
      String observed =
          edt(
              () -> {
                JMenuItem item = gameParametersItem();
                assertNotNull(item, "game menu carries the game-parameters entry");
                JMenu parent = (JMenu) ((JPopupMenu) item.getParent()).getInvoker();
                parent.setSelected(false);
                parent.setSelected(true);
                parent.setSelected(false);
                Component button = (Component) field(Menu.class, "doubleMenuGameParameters");
                String text =
                    "menu.visible=" + item.isVisible() + " menu.enabled=" + item.isEnabled();
                assertEquals(visible, item.isVisible(), state + " entry visibility");
                assertEquals(enabled, visible && item.isEnabled(), state + " entry enablement");
                if (button != null && button.isShowing()) {
                  text += " button.enabled=" + button.isEnabled();
                  assertEquals(enabled, button.isEnabled(), state + " button enablement");
                }
                return text;
              });
      record("entry." + state + "=" + observed);
    }

    /** Opens the panel from the game menu entry and edits only Engine 2's breadth. */
    private void editSecondBreadthThroughEntry(double value) throws Exception {
      int[] before = mark();
      edt(
          () -> {
            JMenuItem item = gameParametersItem();
            JMenu parent = (JMenu) ((JPopupMenu) item.getParent()).getInvoker();
            parent.setSelected(true);
            parent.setSelected(false);
            assertTrue(item.isVisible() && item.isEnabled(), "entry operable while running");
            item.doClick();
            EngineGameParametersDialog dialog = null;
            for (Window window : Window.getWindows()) {
              if (window instanceof EngineGameParametersDialog found && found.isVisible()) {
                dialog = found;
              }
            }
            assertNotNull(dialog, "entry opens the production parameters panel");
            List<JTextField> fields = new ArrayList<>();
            textFields(dialog.getContentPane(), fields);
            assertEquals(4, fields.size(), "two participants x breadth/aggressiveness");
            assertTrue(fields.get(2).isEnabled(), "Engine 2 breadth editable while running");
            fields.get(2).setText(Double.toString(value));
            dialog.getRootPane().getDefaultButton().doClick();
            return null;
          });
      await(() -> !EngineGameParametersDialog.isOpen(), 20, "panel closes after confirmation");
      await(
          () -> {
            EngineGameParameterState state = Lizzie.engineGame.parameterState();
            if (state == null || state.pending()) return false;
            assertNull(state.failure());
            EngineGameParameterState.Value wrn = state.second().wrn();
            return wrn.confirmed() != null
                && wrn.target() == null
                && Math.abs(wrn.confirmed() - value) < 1e-9;
          },
          20,
          "Engine 2 confirms the edited breadth");
      List<String> first = since(0, before);
      List<String> second = since(1, before);
      assertTrue(
          first.stream().noneMatch(line -> line.startsWith("kata-set-param")),
          "Engine 1 is not written: " + first);
      assertEquals(
          1,
          second.stream()
              .filter(line -> line.equals("kata-set-param " + WRN + " " + plain(value)))
              .count(),
          "Engine 2 receives exactly the edited breadth: " + second);
      assertTrue(
          second.stream().noneMatch(line -> line.startsWith("kata-set-param " + PDA)),
          "Engine 2 aggressiveness untouched: " + second);
    }

    // ---- Layout and search --------------------------------------------------------------

    /** The production layout toggle, the same action as the toolbar's expand/collapse button. */
    private void switchLayout(boolean doubleMenu) throws Exception {
      edt(
          () -> {
            if (Lizzie.config.showDoubleMenu != doubleMenu) {
              LizzieFrame.menu.btnDoubleMenu.doClick();
            }
            return null;
          });
      await(() -> Lizzie.config.showDoubleMenu == doubleMenu, 10, "layout switch");
      drain();
      record("layout-switch=" + (doubleMenu ? "double" : "single"));
    }

    /**
     * Function search for the hidden controls is refused through the controller's activation and
     * the toolbar locator, opens nothing, reveals nothing and changes no preference.
     */
    private void denySearch(String state, Map<String, String> before) throws Exception {
      Map<String, String> beforeSearch = preferences();
      boolean layout = edt(() -> Lizzie.config.showDoubleMenu);
      Set<Window> windows = edt(Probe::showingWindows);
      for (String id : List.of("toolbar.wrn", "engine.pda")) {
        String reason = edt(() -> searchController().activate(id));
        assertNotNull(reason, state + ": search activation of " + id + " is refused");
        if ("toolbar.wrn".equals(id) && idleSearchReason != null) {
          assertFalse(reason.equals(idleSearchReason), state + ": refusal is the game's own");
        }
        assertFalse(edt(() -> LizzieFrame.menu.locateTopFunction(id)), state + ": locator " + id);
        record("search." + state + "." + id + "=" + reason);
      }
      drain();
      assertEquals(layout, edt(() -> Lizzie.config.showDoubleMenu), state + ": layout kept");
      assertEquals(
          Set.of(),
          edt(
              () ->
                  showingWindows().stream()
                      .filter(window -> !windows.contains(window))
                      .map(window -> window.getClass().getSimpleName())
                      .collect(Collectors.toSet())),
          state + ": search opened no window");
      assertPreferences(beforeSearch, preferences(), state + " search", true);
      assertPreferences(before, preferences(), state + " against idle", false);
      awaitHidden(state + "-after-search");
    }

    private static FunctionSearchController searchController() throws Exception {
      return (FunctionSearchController) field(LizzieFrame.class, "functionSearch", Lizzie.frame);
    }

    // ---- Toolbar observation ------------------------------------------------------------

    private static Map<String, Component> groupComponents() throws Exception {
      Menu menu = LizzieFrame.menu;
      Map<String, Component> group = new LinkedHashMap<>();
      group.put("chkWRN", menu.chkWRN);
      group.put("lblWRN", menu.lblWRN);
      group.put("txtWRN", menu.txtWRN);
      group.put("lblWRNForDouble", menu.lblWRNForDouble);
      group.put("wrnFieldPanel", menu.wrnFieldPanel);
      group.put("chkPDA", menu.chkPDA);
      group.put("lblGfPDA", menu.lblGfPDA);
      group.put("txtGfPDA", menu.txtGfPDA);
      group.put("lblGfPDAForDouble", menu.lblGfPDAForDouble);
      group.put("pdaFieldPanel", menu.pdaFieldPanel);
      group.put("lblPDASpinner", menu.lblPDASpinner);
      group.put("lblCustomPda", menu.lblCustomPda);
      group.put("txtPDA", menu.txtPDA);
      group.put("more2", menu.more2);
      group.put("customPDAMorePanel", menu.customPDAMorePanel);
      group.put("sepForPdaWrn", (Component) field(Menu.class, "sepForPdaWrn"));
      return group;
    }

    /** On screen and not clipped away by its containers: what the user can actually see. */
    private static boolean showing(Component component) {
      if (component == null
          || !component.isShowing()
          || component.getWidth() <= 0
          || component.getHeight() <= 0) {
        return false;
      }
      return !(component instanceof JComponent swing) || !swing.getVisibleRect().isEmpty();
    }

    /**
     * Null when no part of either group shows, the slot they occupy leaves no gap, and no separator
     * is left doubled; otherwise what the user would see instead.
     */
    private static String hiddenViolation() throws Exception {
      List<String> leaks = new ArrayList<>();
      for (Map.Entry<String, Component> entry : groupComponents().entrySet()) {
        if (showing(entry.getValue())) leaks.add(entry.getKey());
      }
      if (!leaks.isEmpty()) return "visible: " + leaks;
      return Lizzie.config.showDoubleMenu ? doubleRowViolation() : singleRowViolation();
    }

    private static String doubleRowViolation() {
      JComponent komi = LizzieFrame.menu.komiPanel;
      if (!showing(komi)) return "double row has no komi anchor";
      Container row = komi.getParent();
      List<Component> visible =
          Arrays.stream(row.getComponents()).filter(Probe::showing).collect(Collectors.toList());
      int index = visible.indexOf(komi);
      if (index + 1 >= visible.size()) return null;
      Component next = visible.get(index + 1);
      boolean sameRow =
          next.getY() < komi.getY() + komi.getHeight()
              && next.getY() + next.getHeight() > komi.getY();
      int gap = next.getX() - (komi.getX() + komi.getWidth());
      if (sameRow && gap > SLOT_GAP_TOLERANCE) {
        return "double row gap " + gap + "px after komi before " + describe(next);
      }
      if (separator(next) && index + 2 < visible.size() && separator(visible.get(index + 2))) {
        return "double row keeps a useless separator after komi";
      }
      return null;
    }

    private static String singleRowViolation() {
      JComponent komi = LizzieFrame.menu.komiPanel;
      Component tools = LizzieFrame.menu.toolPanel;
      // This build attaches no parameter row in the single layout: nothing can show or gap.
      if (!showing(komi) || !showing(tools)) return null;
      int right = 0;
      Component predecessor = null;
      for (Component child : komi.getComponents()) {
        if (child == tools || !showing(child) || child.getX() >= tools.getX()) continue;
        if (child.getX() + child.getWidth() > right) {
          right = child.getX() + child.getWidth();
          predecessor = child;
        }
      }
      int gap = tools.getX() - right;
      return gap > SLOT_GAP_TOLERANCE
          ? "single row gap " + gap + "px after " + describe(predecessor)
          : null;
    }

    private static boolean separator(Component component) {
      return component instanceof JSeparator
          || component.getClass().getSimpleName().equals("ToolbarSeparator");
    }

    private static String describe(Component component) {
      if (component == null) return "nothing";
      String text =
          component instanceof AbstractButton button
              ? button.getText()
              : component instanceof JLabel label ? label.getText() : "";
      return component.getClass().getSimpleName()
          + (text == null || text.isEmpty() ? "" : "'" + text + "'")
          + "@"
          + component.getX();
    }

    private static String rowDump() throws Exception {
      StringBuilder dump = new StringBuilder();
      for (Map.Entry<String, Component> entry : groupComponents().entrySet()) {
        Component component = entry.getValue();
        dump.append(entry.getKey())
            .append(component == null ? "=null" : showing(component) ? "=SHOWN" : "=hidden")
            .append(' ');
      }
      return dump.toString().trim();
    }

    /** Hidden within the refresh delay, then continuously hidden for a sampling window. */
    private void awaitHidden(String state) throws Exception {
      long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
      while (true) {
        String violation = edt(Probe::hiddenViolation);
        if (violation == null) break;
        if (System.nanoTime() > deadline) {
          throw new AssertionError(state + ": ordinary parameters not hidden: " + violation);
        }
        Thread.sleep(25);
      }
      long until = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(500);
      while (System.nanoTime() < until) {
        String violation = edt(Probe::hiddenViolation);
        if (violation != null) {
          throw new AssertionError(state + ": ordinary parameters reappeared: " + violation);
        }
        Thread.sleep(50);
      }
      record(
          "hidden."
              + state
              + "=PASS layout="
              + (edt(() -> Lizzie.config.showDoubleMenu) ? "double" : "single")
              + " predicate="
              + edt(() -> Lizzie.engineGame.ordinaryParametersHidden())
              + " "
              + edt(Probe::rowDump));
    }

    private static List<String> shownNames() throws Exception {
      List<String> shown = new ArrayList<>();
      for (Map.Entry<String, Component> entry : groupComponents().entrySet()) {
        if (showing(entry.getValue())) shown.add(entry.getKey());
      }
      return shown;
    }

    /** The shown parts once queued layout work has stopped changing them. */
    private List<String> settledShownNames() throws Exception {
      List<String> previous = null;
      long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
      while (System.nanoTime() < deadline) {
        drain();
        Thread.sleep(250);
        List<String> current = edt(Probe::shownNames);
        if (current.equals(previous)) return current;
        previous = current;
      }
      throw new AssertionError("toolbar never settled: " + previous);
    }

    /** The current layout shows exactly what it showed before the game, per user preferences. */
    private void awaitShown(String state) throws Exception {
      long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
      while (true) {
        String problem =
            edt(
                () -> {
                  List<String> expected = idleShown.get(Lizzie.config.showDoubleMenu);
                  List<String> shown = shownNames();
                  return shown.equals(expected) ? null : "shown " + shown + " expected " + expected;
                });
        if (problem == null) break;
        if (System.nanoTime() > deadline) {
          throw new AssertionError(state + ": groups not restored per preferences: " + problem);
        }
        Thread.sleep(50);
      }
      record("shown." + state + "=PASS " + edt(Probe::rowDump));
    }

    /**
     * Narrows the window so the double row re-lays out (and wraps), checks the groups stay hidden
     * without gaps, then restores the width and checks again.
     */
    private void narrowRelayout(String state) throws Exception {
      Rectangle original = edt(() -> Lizzie.frame.getBounds());
      int originalRowHeight = edt(() -> LizzieFrame.menu.komiPanel.getParent().getHeight());
      edt(
          () -> {
            Lizzie.frame.setSize(Math.max(640, original.width / 2), original.height);
            Lizzie.frame.validate();
            return null;
          });
      drain();
      awaitHidden(state + "-narrow-window");
      record(
          "relayout."
              + state
              + "=row-height "
              + originalRowHeight
              + "->"
              + edt(() -> LizzieFrame.menu.komiPanel.getParent().getHeight()));
      screenshot(state + "-narrow-window");
      edt(
          () -> {
            Lizzie.frame.setBounds(original);
            Lizzie.frame.validate();
            return null;
          });
      drain();
      awaitHidden(state + "-restored-width");
    }

    /** Null unless an ordinary control can be operated while the current engine is unusable. */
    private static String enabledWhileUnavailable() throws Exception {
      if (qualified(Lizzie.leelaz)) return null;
      Menu menu = LizzieFrame.menu;
      List<String> enabled = new ArrayList<>();
      Map<String, Component> controls = new LinkedHashMap<>();
      controls.put("chkWRN", menu.chkWRN);
      controls.put("txtWRN", menu.txtWRN);
      controls.put("chkPDA", menu.chkPDA);
      controls.put("txtGfPDA", menu.txtGfPDA);
      controls.put("txtPDA", menu.txtPDA);
      controls.put("more2", menu.more2);
      for (Map.Entry<String, Component> entry : controls.entrySet()) {
        Component control = entry.getValue();
        if (showing(control) && control.isEnabled()) enabled.add(entry.getKey());
      }
      return enabled.isEmpty() ? null : "operable while engine unavailable: " + enabled;
    }

    private static boolean qualified(Leelaz engine) throws Exception {
      if (engine == null || !engine.isStarted() || !engine.isLoaded() || !engine.isKatago) {
        return false;
      }
      Method live = Leelaz.class.getDeclaredMethod("isCurrentLiveEngineIncarnation", Object.class);
      live.setAccessible(true);
      return (boolean) live.invoke(engine, engine.engineIncarnationToken());
    }

    // ---- Preferences --------------------------------------------------------------------

    /**
     * Display/check preferences and parameter configuration as held in memory, persisted in the
     * work directory's config file, and shown in the ordinary fields. Layout itself is excluded:
     * the probe changes it deliberately through the production toggle.
     */
    private Map<String, String> preferences() throws Exception {
      Map<String, String> values = new TreeMap<>();
      edt(
          () -> {
            for (Field field : Config.class.getFields()) {
              if (!Modifier.isStatic(field.getModifiers()) && parameterKey(field.getName())) {
                values.put("config." + field.getName(), String.valueOf(field.get(Lizzie.config)));
              }
            }
            uiValues("memory.ui.", Lizzie.config.uiConfig, values);
            values.put(
                "memory.engines",
                Lizzie.config.leelazConfig == null
                    ? ""
                    : String.valueOf(Lizzie.config.leelazConfig.opt("engine-settings-list")));
            Menu menu = LizzieFrame.menu;
            values.put("field.chkWRN", Boolean.toString(menu.chkWRN.isSelected()));
            values.put("field.txtWRN", menu.txtWRN.getText());
            values.put("field.chkPDA", Boolean.toString(menu.chkPDA.isSelected()));
            values.put("field.txtGfPDA", menu.txtGfPDA.getText());
            values.put("field.txtPDA", menu.txtPDA.getText());
            return null;
          });
      Path persisted = work.resolve("config.txt");
      if (Files.isRegularFile(persisted)) {
        JSONObject file = persistedConfig(persisted);
        JSONObject ui = file.optJSONObject("ui");
        if (ui != null) uiValues("file.ui.", ui, values);
        JSONObject leelaz = file.optJSONObject("leelaz");
        values.put(
            "file.engines",
            leelaz == null ? "" : String.valueOf(leelaz.opt("engine-settings-list")));
      }
      return values;
    }

    /** The application may be rewriting the file; a torn read is retried, not compared. */
    private static JSONObject persistedConfig(Path file) throws Exception {
      for (int attempt = 1; ; attempt++) {
        try {
          return new JSONObject(Files.readString(file));
        } catch (org.json.JSONException torn) {
          if (attempt == 5) throw torn;
          Thread.sleep(100);
        }
      }
    }

    private static void uiValues(String prefix, JSONObject ui, Map<String, String> values) {
      for (String key : ui.keySet()) {
        if (parameterKey(key)) values.put(prefix + key, String.valueOf(ui.opt(key)));
      }
    }

    private static boolean parameterKey(String name) {
      String lower = name.toLowerCase(Locale.ROOT);
      return lower.contains("wrn") || lower.contains("pda");
    }

    /**
     * Fields are compared while the foreground identity is unchanged. After handback, configuration
     * remains invariant and displayed values are checked against the actual foreground participant.
     */
    private void assertPreferences(
        Map<String, String> before, Map<String, String> after, String when, boolean fields)
        throws Exception {
      List<String> changed = new ArrayList<>();
      for (String key : before.keySet()) {
        if (!fields && key.startsWith("field.")) continue;
        if (!String.valueOf(before.get(key)).equals(String.valueOf(after.get(key)))) {
          changed.add(key + ": " + before.get(key) + " -> " + after.get(key));
        }
      }
      for (String key : after.keySet()) {
        if (!before.containsKey(key)) changed.add(key + ": <absent> -> " + after.get(key));
      }
      assertEquals(List.of(), changed, when + " changed preferences or parameter configuration");
      record("preferences-unchanged." + when.replace(' ', '-') + "=PASS");
    }

    // ---- Peer evidence ------------------------------------------------------------------

    private void arm(int peer, String label) throws Exception {
      Files.deleteIfExists(peers[peer].resolve("held-" + label + ".txt"));
      Files.deleteIfExists(peers[peer].resolve("released-" + label + ".txt"));
      Files.deleteIfExists(peers[peer].resolve("release-" + label));
      Files.writeString(peers[peer].resolve("hold-" + label), "");
    }

    private void awaitHeld(int peer, String label) throws Exception {
      Path held = peers[peer].resolve("held-" + label + ".txt");
      awaitOffEdt(
          () -> Files.exists(held),
          60,
          "peer " + (peer + 1) + " holds " + label + " (fixture cannot stabilise this state)");
      record("hold." + label + "=" + Files.readString(held).trim());
    }

    private void release(int peer, String label) throws Exception {
      Files.writeString(peers[peer].resolve("release-" + label), "");
      Path released = peers[peer].resolve("released-" + label + ".txt");
      awaitOffEdt(() -> Files.exists(released), 10, "peer releases " + label);
      assertEquals(
          "released",
          Files.readString(released).trim(),
          label + " observations finished inside the hold");
    }

    private List<String> commands(int peer) throws Exception {
      Path latest = Path.of(Files.readString(peers[peer].resolve("latest.txt")).trim());
      Path log = latest.resolve("commands.log");
      if (!Files.exists(log)) return List.of();
      return Files.readAllLines(log).stream()
          .map(line -> line.trim().replaceFirst("^\\d+\\s+", ""))
          .collect(Collectors.toList());
    }

    private int incarnations(int peer) throws Exception {
      try (var children = Files.list(peers[peer])) {
        return (int)
            children
                .filter(path -> path.getFileName().toString().startsWith("incarnation-"))
                .count();
      }
    }

    private int[] mark() throws Exception {
      return new int[] {commands(0).size(), commands(1).size()};
    }

    private List<String> since(int peer, int[] mark) throws Exception {
      List<String> all = commands(peer);
      return all.subList(Math.min(mark[peer], all.size()), all.size());
    }

    private void assertNoSettersOrAnalysis(int[] mark, String when) throws Exception {
      for (int peer = 0; peer < 2; peer++) {
        List<String> sent = since(peer, mark);
        record("commands-after-mark." + when.replace(' ', '-') + ".peer" + (peer + 1) + "=" + sent);
        for (String line : sent) {
          assertFalse(
              line.startsWith("kata-set-param")
                  || line.startsWith("kata-analyze")
                  || line.startsWith("genmove")
                  || line.startsWith("kata-genmove"),
              when + " sent " + line + " to participant " + (peer + 1));
        }
      }
    }

    /** No new command for a second; follow-up refreshes of the last transition have settled. */
    private void waitQuiet() throws Exception {
      int[] previous = mark();
      long stableSince = System.nanoTime();
      long deadline = stableSince + TimeUnit.SECONDS.toNanos(15);
      while (System.nanoTime() - stableSince < TimeUnit.SECONDS.toNanos(1)) {
        if (System.nanoTime() > deadline) throw new AssertionError("participants never quiet");
        Thread.sleep(100);
        int[] current = mark();
        if (!Arrays.equals(previous, current)) {
          previous = current;
          stableSince = System.nanoTime();
        }
      }
      drain();
    }

    private static boolean isGameValue(String line) {
      for (String value : GAME_VALUES.substring("game-values=".length()).split(",")) {
        if (line.equals("kata-set-param " + WRN + " " + value)
            || line.equals("kata-set-param " + PDA + " " + value)) {
          return true;
        }
      }
      return false;
    }

    // ---- Windows, screenshots, records --------------------------------------------------

    private static Set<Window> showingWindows() {
      return Arrays.stream(Window.getWindows())
          .filter(Window::isShowing)
          .collect(Collectors.toSet());
    }

    private static String windowTextContaining(Set<Window> before, String needle) {
      for (Window window : showingWindows()) {
        if (before.contains(window)) continue;
        String text = normalized(componentText(window));
        if (text.contains(needle)) return text;
      }
      return null;
    }

    private static String normalized(String text) {
      return text.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim();
    }

    private void dismissNewWindows(Set<Window> before, String label) throws Exception {
      List<String> shown =
          edt(
              () -> {
                List<String> texts = new ArrayList<>();
                for (Window window : showingWindows()) {
                  if (before.contains(window) || window == Lizzie.frame) continue;
                  texts.add(window.getClass().getSimpleName() + ":" + componentText(window));
                  window.dispose();
                }
                return texts;
              });
      record("windows." + label + "=" + shown.toString().replace('\n', ' '));
    }

    private static String componentText(Component component) {
      StringBuilder text = new StringBuilder();
      if (component instanceof JLabel label && label.getText() != null) {
        text.append(label.getText()).append(' ');
      } else if (component instanceof JTextComponent field && field.getText() != null) {
        text.append(field.getText()).append(' ');
      } else if (component instanceof AbstractButton button && button.getText() != null) {
        text.append(button.getText()).append(' ');
      }
      if (component instanceof Container container) {
        for (Component child : container.getComponents()) text.append(componentText(child));
      }
      return text.toString();
    }

    private static JMenuItem gameParametersItem() {
      String text = Lizzie.resourceBundle.getString("EngineGameParameters.entry");
      Menu menu = LizzieFrame.menu;
      for (int index = 0; index < menu.getMenuCount(); index++) {
        JMenuItem found = find(menu.getMenu(index), text);
        if (found != null) return found;
      }
      return null;
    }

    private static JMenuItem find(JMenu menu, String text) {
      if (menu == null) return null;
      for (Component child : menu.getMenuComponents()) {
        if (child instanceof JMenu submenu) {
          JMenuItem found = find(submenu, text);
          if (found != null) return found;
        } else if (child instanceof JMenuItem item && text.equals(item.getText())) {
          return item;
        }
      }
      return null;
    }

    private static void textFields(Container parent, List<JTextField> result) {
      for (Component component : parent.getComponents()) {
        if (component instanceof JTextField field) result.add(field);
        else if (component instanceof Container child) textFields(child, result);
      }
    }

    private void screenshot(String name) throws Exception {
      drain();
      Rectangle bounds = edt(() -> Lizzie.frame.getBounds());
      Path directory = Files.createDirectories(work.resolve("screenshots"));
      Path file = directory.resolve(name + ".png");
      if (!ImageIO.write(new Robot().createScreenCapture(bounds), "png", file.toFile())) {
        throw new AssertionError("PNG writer unavailable");
      }
      record("screenshot." + name + "=" + file);
    }

    void screenshotQuietly(String name) {
      try {
        if (Lizzie.frame != null) screenshot(name);
      } catch (Throwable failure) {
        LOG.warn("Failure screenshot unavailable", failure);
      }
    }

    private void write(String name, Map<String, String> values) throws Exception {
      Files.writeString(work.resolve(name), new JSONObject(values).toString(2));
      record("evidence." + name + "=" + work.resolve(name));
    }

    private void record(String text) throws Exception {
      EngineGameToolbarIsolationAcceptanceIT.record(result, text);
    }
  }

  // ---- Shared helpers -------------------------------------------------------------------

  private static Object field(Class<?> owner, String name) throws Exception {
    return field(owner, name, LizzieFrame.menu);
  }

  private static Object field(Class<?> owner, String name, Object target) throws Exception {
    Field field = owner.getDeclaredField(name);
    field.setAccessible(true);
    return field.get(target);
  }

  private static String plain(double value) {
    return java.math.BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
  }

  private static String quote(String text) {
    return "\"" + text + "\"";
  }

  private static void record(Path result, String text) throws Exception {
    Files.writeString(result, text + "\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
  }

  private static <T> T edt(Callable<T> action) throws Exception {
    FutureTask<T> task = new FutureTask<>(action);
    SwingUtilities.invokeLater(task);
    return task.get(10, TimeUnit.SECONDS);
  }

  /** Lets queued layout refreshes (each refresh may queue another) run before observing. */
  private static void drain() throws Exception {
    for (int i = 0; i < 4; i++) edt(() -> null);
  }

  private static void await(Callable<Boolean> condition, int seconds, String label)
      throws Exception {
    awaitOffEdt(() -> edt(condition), seconds, label);
  }

  private static void awaitOffEdt(Callable<Boolean> condition, int seconds, String label)
      throws Exception {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds);
    do {
      if (condition.call()) return;
      Thread.sleep(25);
    } while (System.nanoTime() < deadline);
    throw new AssertionError("Timed out waiting for " + label);
  }

  private static void shutdownClockHelper() throws Exception {
    Process helper =
        edt(
            () -> {
              if (Lizzie.frame == null) return null;
              Process process =
                  (Process) field(LizzieFrame.class, "processClockHelper", Lizzie.frame);
              Lizzie.frame.shutdownClockHelper();
              return process;
            });
    assertTrue(
        helper == null || helper.waitFor(2, TimeUnit.SECONDS),
        "Clock helper survived acceptance cleanup");
  }
}
