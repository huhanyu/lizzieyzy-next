package featurecat.lizzie.gui;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import featurecat.lizzie.Config;
import featurecat.lizzie.ConfigTestHelper;
import featurecat.lizzie.Lizzie;
import featurecat.lizzie.analysis.Leelaz;
import featurecat.lizzie.rules.Board;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsEnvironment;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SetKataEnginesParameterTest {
  @TempDir Path root;

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void currentEngineValuesDoNotReplaySavedPreferences(boolean autoLoad) throws Exception {
    assumeFalse(GraphicsEnvironment.isHeadless());
    try (Fixture f = new Fixture(root)) {
      Lizzie.config.autoLoadKataEnginePDA = autoLoad;
      Lizzie.config.autoLoadKataEngineWRN = autoLoad;
      SwingUtilities.invokeAndWait(
          () -> {
            f.open();
            assertFalse(f.check("chkEditPDA").isSelected());
            assertEquals("0.0", f.text("txtPDA").getText());
            assertTrue(f.check("chkEditWRN").isSelected());
            assertEquals("0.04", f.text("txtWRN").getText());
            assertEquals(autoLoad, f.check("chkAutoLoadPDA").isSelected());
            f.check("chkWRNInMenu").doClick();
            f.apply();
          });
      f.fence();
      assertFalse(f.sent().contains("kata-set-param"), f.sent());
      assertEquals("2.0", Lizzie.config.txtKataEnginePDA);
      assertEquals("0.88", Lizzie.config.txtKataEngineWRN);
      assertEquals(autoLoad, Lizzie.config.autoLoadKataEnginePDA);
      assertEquals(autoLoad, Lizzie.config.autoLoadKataEngineWRN);
      assertEquals("1.5", Lizzie.config.autoLoadTxtKataEnginePDA);
      assertEquals("0.3", Lizzie.config.autoLoadTxtKataEngineWRN);
    }
  }

  @Test
  void breadthEditDoesNotReplayAggressivenessOrReplaceItsAutoloadValue() throws Exception {
    assumeFalse(GraphicsEnvironment.isHeadless());
    try (Fixture f = new Fixture(root)) {
      Lizzie.config.autoLoadKataEnginePDA = true;
      SwingUtilities.invokeAndWait(
          () -> {
            f.open();
            f.text("txtWRN").setText("0.1");
            f.check("chkAutoLoadWRN").doClick();
            f.apply();
          });
      f.fence();
      assertTrue(f.sent().contains("kata-set-param analysisWideRootNoise 0.1"), f.sent());
      assertFalse(f.sent().contains("playoutDoublingAdvantage"), f.sent());
      assertEquals(0.1, f.engine.wrn);
      assertEquals("0.1", Lizzie.config.autoLoadTxtKataEngineWRN);
      assertEquals("1.5", Lizzie.config.autoLoadTxtKataEnginePDA);
      assertEquals("2.0", Lizzie.config.txtKataEnginePDA);
    }
  }

  @Test
  void autoloadOnlyEditDoesNotChangeCurrentParameters() throws Exception {
    assumeFalse(GraphicsEnvironment.isHeadless());
    try (Fixture f = new Fixture(root)) {
      SwingUtilities.invokeAndWait(
          () -> {
            f.open();
            f.check("chkAutoLoadWRN").doClick();
            f.apply();
          });
      f.fence();
      assertFalse(f.sent().contains("kata-set-param"), f.sent());
      assertTrue(Lizzie.config.autoLoadKataEngineWRN);
      assertEquals("0.04", Lizzie.config.autoLoadTxtKataEngineWRN);
      assertEquals("0.88", Lizzie.config.txtKataEngineWRN);
      assertEquals(0.04, f.engine.wrn);
    }
  }

  @Test
  void disablingCurrentParameterKeepsItsDraftAndIndependentAutoload() throws Exception {
    assumeFalse(GraphicsEnvironment.isHeadless());
    try (Fixture f = new Fixture(root)) {
      f.engine.pda = 2;
      f.menu.chkPDA.setSelected(true);
      f.menu.txtGfPDA.setText("2.0");
      Lizzie.config.autoLoadKataEnginePDA = true;
      SwingUtilities.invokeAndWait(
          () -> {
            f.open();
            f.check("chkEditPDA").doClick();
            assertTrue(f.check("chkAutoLoadPDA").isSelected());
            f.apply();
          });
      f.fence();
      assertTrue(f.sent().contains("kata-set-param playoutDoublingAdvantage 0"), f.sent());
      assertFalse(f.sent().contains("analysisWideRootNoise"), f.sent());
      assertTrue(Lizzie.config.autoLoadKataEnginePDA);
      assertEquals("1.5", Lizzie.config.autoLoadTxtKataEnginePDA);
      SwingUtilities.invokeAndWait(
          () -> {
            f.open();
            assertFalse(f.check("chkEditPDA").isSelected());
            assertEquals("2.0", f.text("txtPDA").getText());
          });
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"3", "-0.1", "2.01"})
  void invalidBreadthSavePreservesRuntimeAndAnalysisMetadata(String invalid) throws Exception {
    try (Fixture f = new Fixture(root)) {
      // Exercise the actual save path without requiring native dialog construction.
      SetKataEngines dialog = allocate(SetKataEngines.class);
      SwingUtilities.invokeAndWait(
          () -> {
            try {
              for (String name :
                  new String[] {
                    "chkEditPDA",
                    "chkAutoLoadPDA",
                    "chkWRNInMenu",
                    "chkPDAInMenu",
                    "chkEditWRN",
                    "chkAutoLoadWRN"
                  }) {
                Field field = SetKataEngines.class.getDeclaredField(name);
                field.setAccessible(true);
                JCheckBox check = new JCheckBox();
                check.setSelected(name.equals("chkEditWRN"));
                field.set(dialog, check);
              }
              for (String name :
                  new String[] {
                    "txtPDA", "txtWRN", "loadedPda", "loadedWrn", "loadedAutoPda", "loadedAutoWrn"
                  }) {
                Field field = SetKataEngines.class.getDeclaredField(name);
                field.setAccessible(true);
                Object value =
                    switch (name) {
                      case "txtPDA" -> new JFontTextField("0.0");
                      case "txtWRN" -> new JFontTextField(invalid);
                      case "loadedPda", "loadedAutoPda" -> "false 0.0";
                      case "loadedWrn" -> "true 0.04";
                      default -> "false " + invalid;
                    };
                field.set(dialog, value);
              }
              var save =
                  SetKataEngines.class.getDeclaredMethod(
                      "applyExistingSettings", Leelaz.class, boolean.class);
              save.setAccessible(true);
              save.invoke(dialog, f.engine, false);
            } catch (ReflectiveOperationException failure) {
              throw new AssertionError(failure);
            }
          });
      f.fence();
      assertFalse(f.sent().contains("analysisWideRootNoise"), f.sent());
      assertEquals(0.04, f.engine.wrn);
      assertEquals("0.88", Lizzie.config.txtKataEngineWRN);
      MenuOrdinaryBreadthTest.assertBreadthRoundTrip(f.engine, 0.04);
    }
  }

  private static <T> T allocate(Class<T> type) throws Exception {
    Field field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
    field.setAccessible(true);
    return type.cast(((sun.misc.Unsafe) field.get(null)).allocateInstance(type));
  }

  private static final class Fixture implements AutoCloseable {
    final Config previousConfig = Lizzie.config;
    final LizzieFrame previousFrame = Lizzie.frame;
    final GtpConsolePane previousConsole = Lizzie.gtpConsole;
    final Leelaz previousEngine = Lizzie.leelaz;
    final Board previousBoard = Lizzie.board;
    final Menu previousMenu = LizzieFrame.menu;
    final ResourceBundle previousBundle = Lizzie.resourceBundle;
    final ByteArrayOutputStream output = new ByteArrayOutputStream();
    final LinkedBlockingQueue<String> lines = new LinkedBlockingQueue<>();
    final Leelaz engine;
    final QuietMenu menu;
    SetKataEngines dialog;

    Fixture(Path root) throws Exception {
      Files.createDirectories(root.resolve("save"));
      Lizzie.config = ConfigTestHelper.createBootstrapped(root);
      Lizzie.config.chkKataEnginePDA = true;
      Lizzie.config.txtKataEnginePDA = "2.0";
      Lizzie.config.chkKataEngineWRN = true;
      Lizzie.config.txtKataEngineWRN = "0.88";
      Lizzie.config.autoLoadKataEnginePDA = false;
      Lizzie.config.autoLoadKataEngineWRN = false;
      Lizzie.config.autoLoadTxtKataEnginePDA = "1.5";
      Lizzie.config.autoLoadTxtKataEngineWRN = "0.3";
      Lizzie.resourceBundle = ResourceBundle.getBundle("l10n.DisplayStrings", Locale.US);
      Lizzie.frame = allocate(QuietFrame.class);
      Lizzie.gtpConsole = allocate(QuietConsole.class);
      Lizzie.board = new Board();
      menu = allocate(QuietMenu.class);
      menu.chkPDA = new JFontCheckBox();
      menu.txtGfPDA = new JFontTextField();
      menu.txtGfPDA.setText("0.0");
      menu.chkWRN = new JFontCheckBox();
      menu.chkWRN.setSelected(true);
      menu.txtWRN = new JFontTextField();
      menu.txtWRN.setText("0.04");
      LizzieFrame.menu = menu;
      engine = new Leelaz("katago gtp");
      var install =
          Leelaz.class.getDeclaredMethod(
              "installFreshCommandOutputForTest", java.io.OutputStream.class);
      install.setAccessible(true);
      install.invoke(
          engine,
          new java.io.OutputStream() {
            final StringBuilder line = new StringBuilder();

            @Override
            public void write(int b) {
              output.write(b);
              if (b == '\n') {
                lines.add(line.toString());
                line.setLength(0);
              } else line.append((char) b);
            }
          });
      engine.started = true;
      engine.isLoaded = true;
      engine.isKatago = true;
      engine.pda = 0;
      engine.wrn = 0.04;
      Lizzie.setPrimaryEngine(engine);
    }

    void open() {
      if (dialog != null) dialog.dispose();
      dialog = new SetKataEngines();
      dialog.setVisible(true);
    }

    <T> T component(String name, Class<T> type) {
      try {
        Field field = SetKataEngines.class.getDeclaredField(name);
        field.setAccessible(true);
        return type.cast(field.get(dialog));
      } catch (ReflectiveOperationException e) {
        throw new AssertionError(e);
      }
    }

    JCheckBox check(String name) {
      return component(name, JCheckBox.class);
    }

    JTextField text(String name) {
      return component(name, JTextField.class);
    }

    void apply() {
      component("btnApply", JButton.class).doClick();
    }

    String sent() {
      return output.toString(StandardCharsets.UTF_8);
    }

    void fence() throws Exception {
      engine.sendCommand("name");
      String line;
      do {
        line = lines.poll(2, TimeUnit.SECONDS);
        assertNotNull(line, "command queue did not reach name fence");
      } while (!line.endsWith("name"));
    }

    @Override
    public void close() throws Exception {
      SwingUtilities.invokeAndWait(
          () -> {
            if (dialog != null) dialog.dispose();
          });
      engine.isNormalEnd = true;
      engine.forceQuit();
      Lizzie.config = previousConfig;
      Lizzie.frame = previousFrame;
      Lizzie.gtpConsole = previousConsole;
      Lizzie.setPrimaryEngine(previousEngine);
      Lizzie.board = previousBoard;
      LizzieFrame.menu = previousMenu;
      Lizzie.resourceBundle = previousBundle;
    }
  }

  private static final class QuietFrame extends LizzieFrame {
    @Override
    public GraphicsConfiguration getGraphicsConfiguration() {
      return GraphicsEnvironment.getLocalGraphicsEnvironment()
          .getDefaultScreenDevice()
          .getDefaultConfiguration();
    }
  }

  private static final class QuietConsole extends GtpConsolePane {
    QuietConsole() {
      super(null);
    }

    @Override
    public void addLine(String line) {}
  }

  private static final class QuietMenu extends Menu {
    @Override
    public void updateMenuStatusForEngine() {}

    @Override
    public void refreshBreadthAdmission() {}
  }
}
