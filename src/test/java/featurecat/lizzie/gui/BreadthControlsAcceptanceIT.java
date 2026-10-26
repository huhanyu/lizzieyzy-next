package featurecat.lizzie.gui;

import static org.junit.jupiter.api.Assertions.*;

import featurecat.lizzie.Lizzie;
import featurecat.lizzie.analysis.Leelaz;
import featurecat.lizzie.rules.SGFParser;
import java.awt.Dialog;
import java.awt.Robot;
import java.awt.Window;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Opt-in cold-start interaction with the actual application and a real KataGo process. */
public final class BreadthControlsAcceptanceIT {
  @ParameterizedTest
  @ValueSource(strings = {"0", "0.2"})
  void coldStartAllowsBreadthEditsWithoutOpeningFox(String initial) throws Exception {
    org.junit.jupiter.api.Assumptions.assumeTrue(Boolean.getBoolean("breadth.smoke"));
    DesktopProbeProcess.requireDisplay();
    Path result =
        DesktopProbeProcess.run(
            BreadthControlsAcceptanceIT.class,
            "breadth-startup",
            List.of(),
            List.of(
                System.getProperty("breadth.engine"), System.getProperty("breadth.model"), initial),
            120);
    assertTrue(Files.readString(result).contains("PASS"));
  }

  public static void main(String[] args) throws Exception {
    Path work = Path.of(args[3]);
    Path result = Path.of(args[4]);
    try {
      Path config = work.resolve("katago.cfg");
      Files.writeString(
          config,
          "rules = chinese\nkomi = 6.5\nnumSearchThreads = 2\n"
              + "maxVisits = 64\nmaxTime = 1.0\nponderingEnabled = false\n"
              + "analysisWideRootNoise = "
              + args[2]
              + "\nlogAllGTPCommunication = true\nlogSearchInfo = false\nlogDir = "
              + work.resolve("gtp-log")
              + "\n");
      JSONObject entry =
          new JSONObject()
              .put("name", "Breadth acceptance KataGo")
              .put(
                  "command",
                  quote(args[0])
                      + " gtp -model "
                      + quote(args[1])
                      + " -config "
                      + quote(config.toString()))
              .put("preload", false)
              .put("isDefault", true)
              .put("width", 19)
              .put("height", 19)
              .put("komi", 6.5)
              .put("useJavaSSH", false);
      JSONObject settings =
          new JSONObject()
              .put(
                  "leelaz",
                  new JSONObject()
                      .put(
                          "engine-settings-list",
                          new JSONArray()
                              .put(entry)
                              .put(
                                  new JSONObject(entry.toString())
                                      .put("name", "Second KataGo")
                                      .put("isDefault", false))))
              .put(
                  "ui",
                  new JSONObject()
                      .put("first-time-load", false)
                      .put("use-language", 2)
                      .put("autoload-empty", false)
                      .put("autoload-default", true)
                      .put("autoload-last", false)
                      .put("default-engine", 0)
                      .put("auto-quick-analyze-on-load", false)
                      .put("show-wrn-in-menu", true)
                      .put("not-start-pondering", true));
      Files.writeString(work.resolve("config.txt"), settings.toString(2));
      System.setProperty("lizzie.work.dir", work.toString());
      Lizzie.main(new String[0]);
      awaitEdt(() -> Lizzie.frame != null && Lizzie.frame.isShowing(), "main window");
      SwingUtilities.invokeAndWait(
          () -> {
            for (Window window : Window.getWindows())
              if (window instanceof Dialog) window.dispose();
          });
      awaitEdt(
          () ->
              Lizzie.leelaz != null
                  && Lizzie.leelaz.isLoaded()
                  && !Lizzie.leelaz.hasExclusiveGtpWorkInProgress()
                  && LizzieFrame.menu.chkWRN.isEnabled(),
          "cold-start breadth becomes enabled");
      Leelaz engine = Lizzie.leelaz;
      boolean initiallySelected = Double.parseDouble(args[2]) != 0;
      awaitEdt(
          () ->
              LizzieFrame.menu.chkWRN.isSelected() == initiallySelected
                  && Math.abs(
                          Double.parseDouble(LizzieFrame.menu.txtWRN.getText())
                              - Double.parseDouble(args[2]))
                      < 0.00001,
          "startup parameter readback");
      SwingUtilities.invokeAndWait(
          () -> {
            assertEquals(initiallySelected, LizzieFrame.menu.txtWRN.isEnabled());
            assertFalse(featurecat.lizzie.fox.FoxLiveSession.ownsEngine());
            if (!LizzieFrame.menu.chkWRN.isSelected()) LizzieFrame.menu.chkWRN.doClick();
            assertTrue(LizzieFrame.menu.txtWRN.isEnabled());
            LizzieFrame.menu.txtWRN.setText("0.35");
          });
      assertParameter(engine, work, "0.35");
      verifyRecordedBreadth(engine, work, "0.04");
      verifyRecordedBreadth(engine, work, "0.1");
      SwingUtilities.invokeAndWait(() -> LizzieFrame.menu.txtWRN.setText("0.35"));
      SwingUtilities.invokeAndWait(
          () -> {
            LizzieFrame.menu.chkWRN.doClick();
            assertFalse(LizzieFrame.menu.chkWRN.isSelected());
            assertFalse(LizzieFrame.menu.txtWRN.isEnabled());
            Lizzie.frame.clearWRNforGame(true);
            Lizzie.frame.restoreWRN(true);
            assertFalse(LizzieFrame.menu.chkWRN.isSelected());
            assertEquals("0.35", LizzieFrame.menu.txtWRN.getText());
          });
      assertParameter(engine, work, "0");
      SwingUtilities.invokeAndWait(
          () -> {
            LizzieFrame.menu.chkWRN.doClick();
            assertTrue(LizzieFrame.menu.chkWRN.isSelected());
            assertTrue(LizzieFrame.menu.txtWRN.isEnabled());
          });
      assertParameter(engine, work, "0.35");
      SwingUtilities.invokeAndWait(() -> Lizzie.engineManager.reStartEngine());
      awaitEdt(
          () ->
              Lizzie.leelaz.isLoaded()
                  && Lizzie.leelaz.canSetAnalysisWideRootNoise()
                  && LizzieFrame.menu.chkWRN.isEnabled(),
          "breadth after restart");
      SwingUtilities.invokeAndWait(
          () -> {
            if (!LizzieFrame.menu.chkWRN.isSelected()) LizzieFrame.menu.chkWRN.doClick();
            LizzieFrame.menu.txtWRN.setText("0.4");
          });
      assertParameter(Lizzie.leelaz, work, "0.4");
      SwingUtilities.invokeAndWait(
          () -> assertTrue(Lizzie.engineManager.switchEngineIfAvailable(1, true)));
      awaitEdt(
          () ->
              Lizzie.leelaz != engine
                  && Lizzie.leelaz.isLoaded()
                  && Lizzie.leelaz.canSetAnalysisWideRootNoise()
                  && LizzieFrame.menu.chkWRN.isEnabled(),
          "breadth after engine switch");
      SwingUtilities.invokeAndWait(
          () -> {
            if (!LizzieFrame.menu.chkWRN.isSelected()) LizzieFrame.menu.chkWRN.doClick();
            LizzieFrame.menu.txtWRN.setText("0.45");
          });
      assertParameter(Lizzie.leelaz, work, "0.45");
      ImageIO.write(
          new Robot().createScreenCapture(Lizzie.frame.getBounds()),
          "png",
          work.resolve("breadth-enabled.png").toFile());
      Files.writeString(
          result,
          "PASS initial="
              + args[2]
              + " checked/edit=0.35 recorded/saved/reopened=0.04,0.1 unchecked=0 rechecked=0.35 restart=0.4 switch=0.45\n");
      Lizzie.leelaz.sendCommand("quit");
      System.exit(0);
    } catch (Throwable failure) {
      failure.printStackTrace();
      Files.writeString(result, "FAIL " + failure + "\n");
      System.exit(1);
    }
  }

  private static void verifyRecordedBreadth(Leelaz engine, Path work, String value)
      throws Exception {
    double expected = Double.parseDouble(value);
    SwingUtilities.invokeAndWait(() -> LizzieFrame.menu.txtWRN.setText(value));
    assertParameter(engine, work, value);
    SwingUtilities.invokeAndWait(engine::ponder);
    awaitEdt(
        () ->
            Lizzie.board.getData().getPlayouts() > 0
                && Lizzie.board.getData().wrnKnown
                && Lizzie.board.getData().wrn == expected,
        "fresh analysis breadth=" + value);
    SwingUtilities.invokeAndWait(
        () -> {
          if (engine.isPondering()) engine.togglePonder();
          SGFParser.appendComment();
          assertTrue(
              Lizzie.board
                  .getData()
                  .comment
                  .contains(Lizzie.resourceBundle.getString("SGFParse.wrn") + value));
          Lizzie.frame.appendComment();
          Lizzie.frame.refresh();
        });
    awaitEdt(
        () ->
            visibleCommentContains(
                Lizzie.frame.getContentPane(),
                Lizzie.resourceBundle.getString("SGFParse.wrn") + value),
        "visible breadth=" + value);
    new Robot().waitForIdle();
    ImageIO.write(
        new Robot().createScreenCapture(Lizzie.frame.getBounds()),
        "png",
        work.resolve("analysis-wrn-" + value + ".png").toFile());
    Path saved = work.resolve("analysis-wrn-" + value + ".sgf");
    SwingUtilities.invokeAndWait(
        () -> {
          try {
            SGFParser.save(Lizzie.board, saved.toString());
            assertTrue(SGFParser.load(saved.toString(), false));
            assertTrue(Lizzie.board.getData().wrnKnown);
            assertEquals(expected, Lizzie.board.getData().wrn);
            SGFParser.appendComment();
            assertTrue(
                Lizzie.board
                    .getData()
                    .comment
                    .contains(Lizzie.resourceBundle.getString("SGFParse.wrn") + value));
          } catch (java.io.IOException failure) {
            throw new java.io.UncheckedIOException(failure);
          }
        });
    awaitEdt(() -> engine.canSetAnalysisWideRootNoise(), "reopened board ready");
  }

  private static void assertParameter(Leelaz engine, Path work, String expected) throws Exception {
    Map<Path, String> before = gtp(work);
    engine.sendCommand("kata-get-param analysisWideRootNoise");
    await(
        () ->
            gtp(work).entrySet().stream()
                .anyMatch(
                    entry ->
                        entry
                            .getValue()
                            .substring(before.getOrDefault(entry.getKey(), "").length())
                            .matches(
                                "(?s).*Controller: kata-get-param analysisWideRootNoise\\R[^\\n]*: = "
                                    + java.util.regex.Pattern.quote(expected)
                                    + "(?:\\.0+)?\\s.*")),
        "engine breadth=" + expected);
  }

  private static Map<Path, String> gtp(Path work) {
    try (var paths = Files.list(work.resolve("gtp-log"))) {
      Map<Path, String> text = new HashMap<>();
      for (Path path : paths.toList()) text.put(path, Files.readString(path));
      return text;
    } catch (java.io.IOException failure) {
      throw new IllegalStateException(failure);
    }
  }

  private static boolean visibleCommentContains(java.awt.Container parent, String expected) {
    for (java.awt.Component child : parent.getComponents()) {
      if (child instanceof javax.swing.JTextPane pane
          && pane.isShowing()
          && pane.getText().contains(expected)) return true;
      if (child instanceof java.awt.Container container
          && visibleCommentContains(container, expected)) return true;
    }
    return false;
  }

  private static void awaitEdt(BooleanSupplier condition, String label) throws Exception {
    await(
        () -> {
          AtomicBoolean ready = new AtomicBoolean();
          try {
            SwingUtilities.invokeAndWait(() -> ready.set(condition.getAsBoolean()));
          } catch (Exception failure) {
            throw new IllegalStateException(failure);
          }
          return ready.get();
        },
        label);
  }

  private static void await(BooleanSupplier condition, String label) throws Exception {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(45);
    while (!condition.getAsBoolean() && System.nanoTime() < deadline) Thread.sleep(25);
    assertTrue(condition.getAsBoolean(), label);
  }

  private static String quote(String text) {
    return "\"" + text + "\"";
  }
}
