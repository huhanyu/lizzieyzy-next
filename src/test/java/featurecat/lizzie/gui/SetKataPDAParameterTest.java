package featurecat.lizzie.gui;

import static org.junit.jupiter.api.Assertions.*;

import featurecat.lizzie.Config;
import featurecat.lizzie.ConfigTestHelper;
import featurecat.lizzie.Lizzie;
import featurecat.lizzie.analysis.Leelaz;
import java.awt.Component;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsEnvironment;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.concurrent.CompletableFuture;
import javax.swing.JButton;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SetKataPDAParameterTest {
  @TempDir Path root;

  @ParameterizedTest
  @ValueSource(strings = {"dynamic", "static", "off"})
  void preopenedDialogOnlySavesPreferencesUntilPostIdleRestorationFinishes(String mode)
      throws Exception {
    try (Fixture f = new Fixture(root)) {
      SwingUtilities.invokeAndWait(
          () -> {
            f.open(mode);
            CompletableFuture<Void> restoration = new CompletableFuture<>();
            Lizzie.engineGame.trackParameterRestoration(restoration);
            assertFalse(EngineGameDesktop.batchActive(), "product is already Idle");
            assertTrue(EngineGameParameterControls.ordinaryHidden(), "handback is still pending");
            f.apply();
            f.assertRuntimeUnchanged();
            assertTrue(Lizzie.config.chkAutoPDA);
            assertEquals("7.0", Lizzie.config.staticPDAcur);
            assertEquals("2.0", Lizzie.config.dymPDACap);
            assertEquals(mode.equals("dynamic"), Lizzie.config.chkDymPDA);
            assertEquals(mode.equals("static"), Lizzie.config.chkStaticPDA);

            restoration.complete(null);
            assertFalse(EngineGameParameterControls.ordinaryHidden());
            f.apply();
            assertFalse(f.engine.commands.isEmpty(), "editing resumes after successful handback");
            assertEquals(mode.equals("static") ? 7 : 0, f.engine.pda);
            assertEquals(mode.equals("dynamic") ? 2 : 0.5, f.engine.pdaCap);
            assertEquals(!mode.equals("dynamic"), f.engine.isStaticPda);
          });
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"unloaded", "unsupported"})
  void preopenedDialogRechecksCurrentEngineEligibility(String unavailable) throws Exception {
    try (Fixture f = new Fixture(root)) {
      SwingUtilities.invokeAndWait(
          () -> {
            f.open("static");
            if (unavailable.equals("unloaded")) f.engine.isLoaded = false;
            else f.engine.isKataGoPda = false;
            f.apply();
            f.assertRuntimeUnchanged();
            assertEquals("7.0", Lizzie.config.staticPDAcur, "preference remains editable");
          });
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
    final Leelaz previousEngine = Lizzie.leelaz;
    final Menu previousMenu = LizzieFrame.menu;
    final ResourceBundle previousBundle = Lizzie.resourceBundle;
    final RecordingEngine engine;
    final Menu menu;
    SetKataPDA dialog;

    Fixture(Path root) throws Exception {
      Files.createDirectories(root.resolve("save"));
      Lizzie.config = ConfigTestHelper.createBootstrapped(root);
      Lizzie.resourceBundle = ResourceBundle.getBundle("l10n.DisplayStrings", Locale.US);
      Lizzie.frame = allocate(QuietFrame.class);
      menu = allocate(Menu.class);
      menu.txtPDA = new JFontTextField();
      menu.txtPDA.setText("original toolbar");
      LizzieFrame.menu = menu;
      engine = new RecordingEngine();
      engine.isLoaded = true;
      engine.isKataGoPda = true;
      engine.isStaticPda = true;
      engine.pda = 1;
      engine.pdaCap = 0.5;
      Lizzie.setPrimaryEngine(engine);
      Lizzie.engineGame.resetForTest();
      Lizzie.engineGame.replaceChromeForTest(transition -> {});
    }

    void open(String mode) {
      if (GraphicsEnvironment.isHeadless()) {
        try {
          dialog = allocate(HeadlessDialog.class);
          Field bundle = SetKataPDA.class.getDeclaredField("resourceBundle");
          bundle.setAccessible(true);
          bundle.set(dialog, Lizzie.resourceBundle);
          dialog.chkDymPda = new JFontCheckBox();
          dialog.chkStaticPda = new JFontCheckBox();
          dialog.chkNoPDA = new JFontCheckBox();
          dialog.chkAutoPDA = new JFontCheckBox();
          javax.swing.ButtonGroup modes = new javax.swing.ButtonGroup();
          modes.add(dialog.chkDymPda);
          modes.add(dialog.chkStaticPda);
          modes.add(dialog.chkNoPDA);
          dialog.txtDymCap = new JFontTextField();
          Field value = SetKataPDA.class.getDeclaredField("txtStaticCur");
          value.setAccessible(true);
          value.set(dialog, new JFontTextField());
        } catch (Exception failure) {
          throw new AssertionError(failure);
        }
      } else dialog = new SetKataPDA();
      dialog.chkAutoPDA.setSelected(true);
      dialog.txtDymCap.setText("2.0");
      try {
        Field field = SetKataPDA.class.getDeclaredField("txtStaticCur");
        field.setAccessible(true);
        ((JTextField) field.get(dialog)).setText("7.0");
      } catch (ReflectiveOperationException e) {
        throw new AssertionError(e);
      }
      if (mode.equals("dynamic")) dialog.chkDymPda.setSelected(true);
      else if (mode.equals("static")) dialog.chkStaticPda.setSelected(true);
      else dialog.chkNoPDA.setSelected(true);
      dialog.setVisible(true);
    }

    void apply() {
      if (GraphicsEnvironment.isHeadless()) {
        dialog.applySettings();
        return;
      }
      String label = Lizzie.resourceBundle.getString("SetKataPDA.btnApply");
      for (Component component : dialog.getContentPane().getComponents()) {
        if (component instanceof JButton button && label.equals(button.getText())) {
          button.doClick();
          return;
        }
      }
      fail("Apply button missing");
    }

    void assertRuntimeUnchanged() {
      assertEquals(List.of(), engine.commands);
      assertEquals(1, engine.pda);
      assertEquals(0.5, engine.pdaCap);
      assertTrue(engine.isStaticPda);
      assertEquals("original toolbar", menu.txtPDA.getText());
    }

    @Override
    public void close() throws Exception {
      SwingUtilities.invokeAndWait(
          () -> {
            if (dialog != null) dialog.dispose();
            Lizzie.engineGame.resetForTest();
          });
      engine.isNormalEnd = true;
      engine.forceQuit();
      Lizzie.config = previousConfig;
      Lizzie.frame = previousFrame;
      Lizzie.setPrimaryEngine(previousEngine);
      LizzieFrame.menu = previousMenu;
      Lizzie.resourceBundle = previousBundle;
    }
  }

  private static final class HeadlessDialog extends SetKataPDA {
    @Override
    public void setVisible(boolean visible) {}

    @Override
    public void dispose() {}
  }

  private static final class RecordingEngine extends Leelaz {
    final List<String> commands = new ArrayList<>();

    RecordingEngine() throws java.io.IOException {
      super("");
    }

    @Override
    public void sendCommand(String command) {
      commands.add(command);
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
}
