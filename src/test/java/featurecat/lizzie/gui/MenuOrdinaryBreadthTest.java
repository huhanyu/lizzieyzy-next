package featurecat.lizzie.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import featurecat.lizzie.Config;
import featurecat.lizzie.ConfigTestHelper;
import featurecat.lizzie.Lizzie;
import featurecat.lizzie.analysis.Leelaz;
import featurecat.lizzie.enginegame.EngineGameSnapshotFixtures;
import featurecat.lizzie.rules.Board;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.concurrent.TimeUnit;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The toolbar breadth text and check box, driven through Menu's handlers against controlled engines
 * whose GTP output is captured: the command written and the engine's runtime breadth, which later
 * analysis records, must agree.
 */
class MenuOrdinaryBreadthTest {
  private static final String SET = "kata-set-param analysisWideRootNoise ";

  @TempDir Path root;

  @Test
  void textChecksAndEngineSwitchKeepCommandAndRuntimeBreadthTogether() throws Exception {
    Config previousConfig = Lizzie.config;
    LizzieFrame previousFrame = Lizzie.frame;
    Leelaz previousEngine = Lizzie.leelaz;
    GtpConsolePane previousConsole = Lizzie.gtpConsole;
    Board previousBoard = Lizzie.board;
    ResourceBundle previousBundle = Lizzie.resourceBundle;
    Menu previousMenu = LizzieFrame.menu;
    ControlledEngine first = null;
    ControlledEngine second = null;
    try {
      Files.createDirectories(root.resolve("save"));
      Lizzie.config = ConfigTestHelper.createBootstrapped(root);
      Lizzie.resourceBundle = ResourceBundle.getBundle("l10n.DisplayStrings", Locale.US);
      Lizzie.frame = allocate(LizzieFrame.class);
      Lizzie.gtpConsole = allocate(QuietConsole.class);
      Lizzie.board = new Board();
      EngineGameSnapshotFixtures.publishIdle();
      first = new ControlledEngine();
      second = new ControlledEngine();
      Lizzie.setPrimaryEngine(first);
      Menu menu = allocate(Menu.class);
      menu.chkWRN = new JFontCheckBox();
      menu.txtWRN = new JFontTextField();
      LizzieFrame.menu = menu;
      ControlledEngine primary = first;

      // 0.04 -> 0.1 through the text field with the check box selected.
      edt(
          () -> {
            menu.chkWRN.setSelected(true);
            menu.txtWRN.setText("0.04");
            menu.applyToolbarWrnText();
          });
      primary.awaitCommand(SET + "0.04");
      assertEquals(0.04, primary.wrn);
      edt(
          () -> {
            menu.txtWRN.setText("0.1");
            menu.applyToolbarWrnText();
          });
      primary.awaitCommand(SET + "0.1");
      assertEquals(0.1, primary.wrn, "the runtime breadth follows the sent value, not 0.04");
      assertEquals("0.1", Lizzie.config.txtKataEngineWRN);

      // Invalid enabled drafts must not become runtime or persisted analysis metadata.
      for (String invalid : new String[] {"3", "-0.1", "2.01"}) {
        primary.output.reset();
        edt(
            () -> {
              menu.txtWRN.setText(invalid);
              menu.applyToolbarWrnText();
              menu.applyToolbarWrnSelection();
            });
        primary.fence();
        assertFalse(primary.sent().contains("analysisWideRootNoise"), primary.sent());
        assertEquals(0.1, primary.wrn);
        assertEquals("0.1", Lizzie.config.txtKataEngineWRN);
        assertBreadthRoundTrip(primary, 0.1);
      }
      for (double invalid : new double[] {3, -0.1, Double.NaN, Double.POSITIVE_INFINITY}) {
        primary.output.reset();
        primary.setOrdinaryWrn(invalid);
        primary.fence();
        assertFalse(primary.sent().contains("analysisWideRootNoise"), primary.sent());
        assertEquals(0.1, primary.wrn);
      }
      for (double valid : new double[] {0, 0.04, 2, 0.1}) {
        primary.output.reset();
        primary.setOrdinaryWrn(valid);
        primary.fence();
        assertEquals(valid, primary.wrn);
        assertBreadthRoundTrip(primary, valid);
      }
      edt(
          () -> {
            menu.txtWRN.setText("0.1");
            menu.applyToolbarWrnText();
          });

      // Clearing the check box applies zero and keeps the draft.
      primary.output.reset();
      edt(
          () -> {
            menu.chkWRN.setSelected(false);
            menu.applyToolbarWrnSelection();
          });
      primary.awaitCommand(SET + "0.0");
      assertEquals(0.0, primary.wrn);
      assertFalse(menu.txtWRN.isEnabled());
      assertEquals("0.1", menu.txtWRN.getText());
      assertEquals("0.1", Lizzie.config.txtKataEngineWRN, "the draft is not the effective value");
      assertFalse(Lizzie.config.chkKataEngineWRN);

      // A draft change while cleared stays a draft.
      primary.output.reset();
      edt(
          () -> {
            menu.txtWRN.setText("0.3");
            menu.applyToolbarWrnText();
          });
      primary.awaitCommand(SET + "0.0");
      assertEquals(0.0, primary.wrn);
      assertEquals("0.3", Lizzie.config.txtKataEngineWRN);

      // Re-enabling applies the kept draft.
      primary.output.reset();
      edt(
          () -> {
            menu.chkWRN.setSelected(true);
            menu.applyToolbarWrnSelection();
          });
      primary.awaitCommand(SET + "0.3");
      assertEquals(0.3, primary.wrn);
      assertTrue(menu.txtWRN.isEnabled());
      assertTrue(Lizzie.config.chkKataEngineWRN);

      // Returning from a human genmove game restores metadata as well as the command.
      edt(() -> Lizzie.frame.clearWRNforGame(true));
      primary.setOrdinaryWrn(0);
      primary.awaitCommand(SET + "0.0");
      primary.output.reset();
      edt(() -> Lizzie.frame.restoreWRN(true));
      primary.awaitCommand(SET + "0.3");
      assertEquals(0.3, primary.wrn);

      edt(
          () -> {
            menu.chkWRN.setSelected(false);
            menu.applyToolbarWrnSelection();
            Lizzie.frame.clearWRNforGame(true);
            Lizzie.frame.restoreWRN(true);
          });
      primary.fence();
      assertEquals(0.0, primary.wrn, "disabled draft stays disabled after genmove");
      assertFalse(menu.chkWRN.isSelected());
      assertEquals("0.3", menu.txtWRN.getText());
      primary.output.reset();
      edt(
          () -> {
            menu.chkPDA = new JFontCheckBox();
            menu.txtGfPDA = new JFontTextField("-0.25");
            menu.chkPDA.setSelected(true);
            primary.pda = 0.75;
            menu.refreshRestoredParameters(primary);
          });
      primary.fence();
      assertEquals("0.3", menu.txtWRN.getText(), "unchanged disabled breadth keeps its draft");
      assertFalse(menu.chkWRN.isSelected());
      assertEquals("0.75", menu.txtGfPDA.getText(), "changed restored PDA is projected");
      assertEquals("0.3", Lizzie.config.txtKataEngineWRN);
      assertFalse(Lizzie.config.chkKataEngineWRN);
      assertFalse(primary.sent().contains("kata-set-param"), primary.sent());
      primary.output.reset();
      edt(
          () -> {
            menu.chkWRN.setSelected(true);
            menu.applyToolbarWrnSelection();
          });
      primary.awaitCommand(SET + "0.3");
      primary.fence();

      // Without admission neither the command nor the runtime value changes.
      primary.admitted = false;
      primary.output.reset();
      edt(
          () -> {
            menu.txtWRN.setText("0.5");
            menu.applyToolbarWrnText();
            menu.chkWRN.setSelected(false);
            menu.applyToolbarWrnSelection();
          });
      primary.fence();
      assertFalse(primary.sent().contains("analysisWideRootNoise"), primary.sent());
      assertEquals(0.3, primary.wrn);
      assertEquals("0.3", Lizzie.config.txtKataEngineWRN);
      primary.admitted = true;

      // After an engine switch the toolbar applies to the new primary only.
      ControlledEngine replacement = second;
      first.output.reset();
      Lizzie.setPrimaryEngine(replacement);
      edt(
          () -> {
            menu.chkWRN.setSelected(true);
            menu.txtWRN.setText("0.2");
            menu.applyToolbarWrnText();
          });
      replacement.awaitCommand(SET + "0.2");
      assertEquals(0.2, replacement.wrn);
      first.fence();
      assertFalse(first.sent().contains("analysisWideRootNoise"), first.sent());
      assertEquals(0.3, first.wrn, "the previous engine keeps its own breadth");
    } finally {
      for (ControlledEngine engine : new ControlledEngine[] {first, second}) {
        if (engine == null) continue;
        engine.isNormalEnd = true;
        engine.forceQuit();
      }
      EngineGameSnapshotFixtures.publishIdle();
      Lizzie.config = previousConfig;
      Lizzie.frame = previousFrame;
      Lizzie.setPrimaryEngine(previousEngine);
      Lizzie.gtpConsole = previousConsole;
      Lizzie.board = previousBoard;
      Lizzie.resourceBundle = previousBundle;
      LizzieFrame.menu = previousMenu;
    }
  }

  static void assertBreadthRoundTrip(Leelaz engine, double expected) throws Exception {
    featurecat.lizzie.analysis.MoveData move = new featurecat.lizzie.analysis.MoveData();
    move.coordinate = "C3";
    move.variation = java.util.List.of("C3");
    move.playouts = 100;
    move.winrate = 55;
    move.isKataData = true;
    var data = Lizzie.board.getData();
    data.isChanged = true;
    data.adoptOrdinaryAnalysis(
        new java.util.ArrayList<>(java.util.List.of(move)),
        "KataGo",
        engine,
        100,
        100,
        null,
        new Object(),
        false,
        false);
    assertTrue(data.wrnKnown);
    assertEquals(expected, data.wrn);
    var parsed =
        featurecat.lizzie.rules.SGFParser.parseSgf(
                featurecat.lizzie.rules.SGFParser.saveToString(false), true)
            .getData();
    assertTrue(parsed.wrnKnown);
    assertEquals(expected, parsed.wrn);
  }

  private static void edt(Runnable action) throws Exception {
    SwingUtilities.invokeAndWait(action);
  }

  @SuppressWarnings("unchecked")
  private static <T> T allocate(Class<T> type) throws Exception {
    Field field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
    field.setAccessible(true);
    return (T) ((sun.misc.Unsafe) field.get(null)).allocateInstance(type);
  }

  /** A KataGo process stand-in: real command queue, captured output, switchable admission. */
  private static final class ControlledEngine extends Leelaz {
    private final ByteArrayOutputStream output = new ByteArrayOutputStream();
    private volatile boolean admitted = true;

    private ControlledEngine() throws Exception {
      super("katago gtp");
      var install =
          Leelaz.class.getDeclaredMethod(
              "installFreshCommandOutputForTest", java.io.OutputStream.class);
      install.setAccessible(true);
      install.invoke(this, output);
      started = true;
      isLoaded = true;
      isKatago = true;
    }

    @Override
    public boolean canSetAnalysisWideRootNoise() {
      return admitted;
    }

    private String sent() {
      return output.toString(StandardCharsets.UTF_8);
    }

    private void awaitCommand(String command) throws InterruptedException {
      long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
      while (System.nanoTime() < deadline) {
        for (String line : sent().split("\\R")) {
          if (line.endsWith(command)) return;
        }
        Thread.sleep(5);
      }
      throw new AssertionError("Missing command " + command + " in " + sent());
    }

    /** Waits until a later command is written, so everything queued before it was written too. */
    private void fence() throws InterruptedException {
      sendCommand("name");
      awaitCommand("name");
    }
  }

  private static final class QuietConsole extends GtpConsolePane {
    private QuietConsole() {
      super(null);
    }

    @Override
    public void addLine(String line) {}
  }
}
