package featurecat.lizzie.analysis;

import featurecat.lizzie.Config;
import featurecat.lizzie.ConfigTestHelper;
import featurecat.lizzie.Lizzie;
import featurecat.lizzie.rules.BoardHistoryNode;
import java.awt.Component;
import java.awt.Container;
import java.awt.Window;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import javax.imageio.ImageIO;
import javax.swing.AbstractButton;
import javax.swing.JDialog;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import org.json.JSONArray;
import org.json.JSONObject;

/** Explicit native UI acceptance using an isolated profile and private cached analysis. */
public final class HumanSlStrengthGuiProbe {
  private static <T> T find(Component root, Class<T> type, java.util.function.Predicate<T> test) {
    if (type.isInstance(root) && test.test(type.cast(root))) return type.cast(root);
    if (root instanceof Container container)
      for (Component child : container.getComponents()) {
        T found = find(child, type, test);
        if (found != null) return found;
      }
    return null;
  }

  static void seedFromFixture(JSONObject fixture) {
    BoardHistoryNode node = Lizzie.board.getHistory().getStart();
    int turn = 0;
    while (true) {
      JSONObject analysis = fixture.getJSONArray("base_analyses").getJSONObject(turn);
      var data = node.getData();
      JSONObject root = analysis.getJSONObject("rootInfo");
      data.isKataData = true;
      data.scoreMean = root.getDouble("scoreLead");
      data.winrate = root.getDouble("winrate") * 100;
      data.setPlayouts(root.getInt("visits"));
      data.analysisHeaderSlots = 4;
      data.engineName = "Acceptance frozen analysis";
      data.bestMoves = new ArrayList<>();
      JSONArray candidates = analysis.getJSONArray("moveInfos");
      for (int i = 0; i < candidates.length(); i++) {
        JSONObject raw = candidates.getJSONObject(i);
        MoveData move = new MoveData();
        move.coordinate = raw.getString("move");
        move.order = raw.getInt("order");
        move.isKataData = true;
        move.scoreMean = raw.getDouble("scoreMean");
        move.winrate = raw.getDouble("winrate") * 100;
        move.policy = raw.getDouble("prior") * 100;
        move.playouts = raw.getInt("visits");
        move.variation = List.of(move.coordinate);
        data.bestMoves.add(move);
      }
      Optional<BoardHistoryNode> next = node.next(true);
      if (next.isEmpty()) break;
      node = next.get();
      turn++;
    }
  }

  /** The estimate on Black's rank card of the strength dashboard in {@code window}. */
  private static PlayerStrengthEstimator.SideReport cardReport(Window window) {
    Component card =
        find(window, Component.class, c -> c.getClass().getSimpleName().equals("StrengthRankCard"));
    if (card == null) return null;
    try {
      var field = card.getClass().getDeclaredField("report");
      field.setAccessible(true);
      return (PlayerStrengthEstimator.SideReport) field.get(card);
    } catch (ReflectiveOperationException error) {
      throw new RuntimeException(error);
    }
  }

  /** What the strength dashboard in {@code window} answers to its accessor {@code name}. */
  private static Object dashboardValue(Window window, String name) {
    Component dashboard =
        find(
            window, Component.class, c -> c.getClass().getSimpleName().equals("StrengthDashboard"));
    if (dashboard == null) return null;
    try {
      var accessor = dashboard.getClass().getDeclaredMethod(name);
      accessor.setAccessible(true);
      return accessor.invoke(dashboard);
    } catch (ReflectiveOperationException error) {
      throw new RuntimeException(error);
    }
  }

  /** Chooses {@code model} in the model menu of the strength dashboard in {@code window}. */
  private static void chooseModel(Window window, PlayerStrengthEstimator.StrengthModel model) {
    Component picker =
        find(window, Component.class, c -> "strength40-selector".equals(c.getName()));
    try {
      var choose =
          picker
              .getClass()
              .getDeclaredMethod("choose", PlayerStrengthEstimator.StrengthModel.class);
      choose.setAccessible(true);
      choose.invoke(picker, model);
    } catch (ReflectiveOperationException error) {
      throw new RuntimeException(error);
    }
  }

  private static Window dashboard() {
    for (Window window : Window.getWindows())
      if (window.isVisible()
          && find(window, Component.class, c -> "strength40-selector".equals(c.getName())) != null)
        return window;
    return null;
  }

  private static void screenshot(Window window, Path output) throws Exception {
    AtomicReference<BufferedImage> captured = new AtomicReference<>();
    SwingUtilities.invokeAndWait(
        () -> {
          Component root = window instanceof JDialog dialog ? dialog.getRootPane() : window;
          BufferedImage image =
              new BufferedImage(root.getWidth(), root.getHeight(), BufferedImage.TYPE_INT_RGB);
          var graphics = image.createGraphics();
          root.printAll(graphics);
          graphics.dispose();
          captured.set(image);
        });
    ImageIO.write(captured.get(), "png", output.toFile());
  }

  public static void main(String[] args) throws Exception {
    Path work = Path.of(args[0]).toAbsolutePath();
    JSONObject fixture = new JSONObject(Files.readString(Path.of(args[2])));
    Files.createDirectories(work);
    Files.createDirectories(work.resolve("save"));
    System.setProperty("lizzie.work.dir", work.toString());
    Config config = ConfigTestHelper.createBootstrapped(work);
    config
        .uiConfig
        .put("first-time-load", false)
        .put("autoload-default", false)
        .put("autoload-last", false)
        .put("autoload-empty", true)
        .put("auto-quick-analyze-on-load", false)
        .put(
            "analysis-engine-command",
            HumanSlStrengthReuseSession.quoteCommandArgument(
                    System.getProperty(
                        "lizzie.strength.humansl40.engine", "/opt/homebrew/bin/katago"))
                + " analysis");
    config.save();
    Thread monitor =
        new Thread(
            () -> {
              try {
                long deadline =
                    System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(180);
                while (Lizzie.frame == null || !Lizzie.frame.isVisible()) {
                  if (System.nanoTime() > deadline)
                    throw new IllegalStateException("Startup timeout");
                  Thread.sleep(200);
                }
                Thread.sleep(1200);
                AtomicReference<HumanSlStrengthReuseSession.Capture> before =
                    new AtomicReference<>();
                SwingUtilities.invokeAndWait(
                    () -> {
                      seedFromFixture(fixture);
                      before.set(HumanSlStrengthReuseSession.capture());
                    });
                SwingUtilities.invokeLater(() -> Lizzie.frame.showPlayerStrengthEstimate());
                Window original;
                while ((original = dashboard()) == null) {
                  if (System.nanoTime() > deadline)
                    throw new IllegalStateException("Dashboard timeout");
                  Thread.sleep(100);
                }
                screenshot(original, work.resolve("dashboard-before.png"));
                Window selected = original;
                SwingUtilities.invokeLater(
                    () -> chooseModel(selected, PlayerStrengthEstimator.StrengthModel.HUMANSL40));
                // The dashboard shows the new model in place, in the same window.
                AtomicReference<Boolean> switched = new AtomicReference<>(false);
                while (!switched.get()) {
                  SwingUtilities.invokeAndWait(
                      () -> {
                        for (Window window : Window.getWindows()) {
                          if (window.isVisible()
                              && window instanceof featurecat.lizzie.gui.HumanSlStrengthDialog
                              && find(window, JTextField.class, f -> true) != null)
                            throw new AssertionError("Unexpected model settings");
                        }
                        var report =
                            (PlayerStrengthEstimator.Report) dashboardValue(selected, "report");
                        switched.set(report != null && report.model.isHumanSl40());
                      });
                  if (System.nanoTime() > deadline)
                    throw new IllegalStateException("Assessment timeout");
                  Thread.sleep(200);
                }
                Window pending = selected;
                Thread.sleep(400);
                SwingUtilities.invokeAndWait(
                    () -> {
                      for (Window window : Window.getWindows())
                        if (window.isVisible()
                            && window instanceof featurecat.lizzie.gui.HumanSlStrengthDialog)
                          throw new AssertionError("Selecting the model started a progress dialog");
                      var background =
                          find(
                              pending,
                              javax.swing.JCheckBox.class,
                              c -> "strength40-background".equals(c.getName()) && c.isShowing());
                      if (background == null || background.isSelected())
                        throw new AssertionError("Background computation must require opt-in");
                      background.doClick();
                      if (!HumanSlStrengthPreparation.backgroundEnabled())
                        throw new AssertionError("On preference was not saved");
                      background.doClick();
                      if (HumanSlStrengthPreparation.backgroundEnabled())
                        throw new AssertionError("Off preference was not saved");
                    });
                screenshot(pending, work.resolve("dashboard-manual-pending.png"));
                SwingUtilities.invokeLater(
                    () ->
                        find(
                                pending,
                                AbstractButton.class,
                                b -> "strength40-run".equals(b.getName()) && b.isShowing())
                            .doClick());
                AtomicReference<Boolean> prepared = new AtomicReference<>(false);
                while (!prepared.get()) {
                  SwingUtilities.invokeAndWait(
                      () -> {
                        var report = cardReport(pending);
                        prepared.set(
                            report != null
                                && report.model.isHumanSl40()
                                && Double.isFinite(report.rankValue)
                                && report.sampleCount >= 6);
                      });
                  if (System.nanoTime() > deadline)
                    throw new IllegalStateException("Manual supplement timeout");
                  Thread.sleep(150);
                }
                Window completed = pending;
                AtomicReference<String> after = new AtomicReference<>();
                SwingUtilities.invokeAndWait(
                    () -> after.set(HumanSlStrengthReuseSession.capture().base().toString()));
                if (!before.get().base().toString().equals(after.get()))
                  throw new AssertionError("Board analysis was modified");
                screenshot(completed, work.resolve("dashboard-humansl40.png"));
                Window finalDashboard = completed;
                for (String tab : new String[] {"match", "performance"}) {
                  SwingUtilities.invokeAndWait(
                      () ->
                          find(
                                  finalDashboard,
                                  AbstractButton.class,
                                  b ->
                                      Lizzie.resourceBundle
                                          .getString("PlayerStrengthEstimate.tab." + tab)
                                          .equals(b.getText()))
                              .doClick());
                  screenshot(finalDashboard, work.resolve("dashboard-" + tab + ".png"));
                }
                SwingUtilities.invokeLater(
                    () ->
                        find(
                                finalDashboard,
                                AbstractButton.class,
                                b ->
                                    Lizzie.resourceBundle
                                        .getString("PlayerStrengthEstimate.detailData")
                                        .equals(b.getText()))
                            .doClick());
                Window detail = null;
                while (detail == null) {
                  for (Window window : Window.getWindows())
                    if (window.isVisible()
                        && window instanceof JDialog dialog
                        && dialog
                            .getTitle()
                            .contains(
                                Lizzie.resourceBundle.getString(
                                    "PlayerStrengthEstimate.detailData"))) detail = window;
                  if (System.nanoTime() > deadline)
                    throw new IllegalStateException("Detail timeout");
                  Thread.sleep(100);
                }
                screenshot(detail, work.resolve("dashboard-details.png"));
                Window shownDetail = detail;
                SwingUtilities.invokeAndWait(
                    () -> {
                      shownDetail.dispose();
                      BoardHistoryNode node = Lizzie.board.getHistory().getStart();
                      while (true) {
                        node.getData().clearAnalysisPayloadState();
                        var next = node.next(true);
                        if (next.isEmpty()) break;
                        node = next.get();
                      }
                    });
                AtomicReference<Boolean> cleared = new AtomicReference<>(false);
                while (!cleared.get()) {
                  SwingUtilities.invokeAndWait(
                      () -> {
                        var report =
                            (PlayerStrengthEstimator.Report)
                                dashboardValue(finalDashboard, "report");
                        cleared.set(
                            report != null
                                && !report.black.hasSamples()
                                && "NEEDS_ANALYSIS"
                                    .equals(
                                        String.valueOf(dashboardValue(finalDashboard, "stage"))));
                      });
                  if (System.nanoTime() > deadline)
                    throw new IllegalStateException(
                        "Clearing analysis left a stale strength report");
                  Thread.sleep(150);
                }
                screenshot(finalDashboard, work.resolve("dashboard-cleared.png"));
                Files.writeString(
                    work.resolve("gui-evidence.json"),
                    new JSONObject()
                        .put("original_dropdown_selected", true)
                        .put("selection_does_not_start_progress", true)
                        .put("manual_button_starts_supplement", true)
                        .put("background_defaults_off", true)
                        .put("original_dashboard_results", true)
                        .put("finite_card_prediction", true)
                        .put("original_match_and_performance_views", true)
                        .put("swing_component_render", true)
                        .put("original_details_visible", true)
                        .put("no_model_path_fields", true)
                        .put("board_analysis_unchanged", true)
                        .put("clearing_analysis_removes_stale_report", true)
                        .put("isolated_profile", true)
                        .toString(2));
                System.out.println(
                    "PASS original dropdown/dashboard/details, no model settings, board analysis unchanged");
                SwingUtilities.invokeAndWait(
                    () -> {
                      for (Window window : Window.getWindows()) window.dispose();
                    });
                System.exit(0);
              } catch (Throwable failure) {
                failure.printStackTrace();
                System.exit(1);
              }
            },
            "strength40-native-acceptance");
    monitor.setDaemon(true);
    monitor.start();
    Lizzie.main(new String[] {args[1]});
  }
}
