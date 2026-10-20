package featurecat.lizzie.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import featurecat.lizzie.AppLocale;
import featurecat.lizzie.analysis.HumanSlStrengthPreparation;
import featurecat.lizzie.analysis.PlayerStrengthEstimator;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class StrengthDialogTest {
  private static final ResourceBundle BUNDLE = AppLocale.SIMPLIFIED_CHINESE.loadBundle();
  private static final StrengthStyle STYLE = new StrengthStyle(false, false, "Dialog", 12);

  /** A main window that hands out canned estimates and counts what the dialog asks of it. */
  private static final class FakeHost implements StrengthDialog.Host {
    PlayerStrengthEstimator.StrengthModel active = PlayerStrengthEstimator.StrengthModel.HUMANSL40;
    PlayerStrengthEstimator.Report next;
    HumanSlStrengthPreparation.Status status =
        new HumanSlStrengthPreparation.Status("HumanSL40.backgroundOff", 0, 0, 0);
    long stamp;
    int reports;
    int preparations;
    final List<PlayerStrengthEstimator.StrengthModel> activated = new ArrayList<>();

    FakeHost(PlayerStrengthEstimator.Report report) {
      next = report;
    }

    @Override
    public PlayerStrengthEstimator.Report report() {
      reports++;
      return next;
    }

    @Override
    public PlayerStrengthEstimator.StrengthModel activeModel() {
      return active;
    }

    @Override
    public void setActiveModel(PlayerStrengthEstimator.StrengthModel model) {
      active = model;
      activated.add(model);
    }

    @Override
    public PlayerStrengthEstimator.StrengthModel[] models() {
      return new PlayerStrengthEstimator.StrengthModel[] {
        PlayerStrengthEstimator.StrengthModel.XGBOOST20TUN,
        PlayerStrengthEstimator.StrengthModel.HUMANSL40
      };
    }

    @Override
    public PlayerStrengthEstimator.StrengthModel importModel(Path path) throws IOException {
      throw new IOException("not used");
    }

    @Override
    public HumanSlStrengthPreparation.Status preparationStatus() {
      return status;
    }

    @Override
    public long preparationStamp() {
      return stamp;
    }

    @Override
    public boolean backgroundEnabled() {
      return false;
    }

    @Override
    public void setBackgroundEnabled(boolean enabled) {}

    @Override
    public void prepareNow() {
      preparations++;
    }
  }

  @Test
  void humanSlEstimateIsRebuiltOnlyWhenItsDataChanges() throws Exception {
    FakeHost host = new FakeHost(StrengthFixtures.emptyReport());
    PlayerStrengthEstimator.Report ready = StrengthFixtures.twoMoveReport();
    SwingUtilities.invokeAndWait(
        () -> {
          StrengthDialog dialog = new StrengthDialog(host, STYLE, BUNDLE);
          assertEquals(1, host.reports);
          dialog.refresh();
          assertEquals(2, host.reports, "the first check shows the newest data");
          dialog.refresh();
          dialog.refresh();
          assertEquals(2, host.reports, "nothing changed");

          host.next = ready;
          host.stamp = 42;
          dialog.refresh();
          assertEquals(3, host.reports);
          assertSame(ready, dialog.dashboard().report());
        });
  }

  @Test
  void otherModelsAreNotRebuiltByTheTimer() throws Exception {
    FakeHost host = new FakeHost(StrengthFixtures.twoMoveReport());
    host.active = PlayerStrengthEstimator.StrengthModel.XGBOOST20TUN;
    SwingUtilities.invokeAndWait(
        () -> {
          StrengthDialog dialog = new StrengthDialog(host, STYLE, BUNDLE);
          host.stamp = 7;
          dialog.refresh();
          assertEquals(1, host.reports);
        });
  }

  @Test
  void statusFollowsThePreparationOnEveryTick() throws Exception {
    FakeHost host = new FakeHost(StrengthFixtures.twoMoveReport());
    host.active = PlayerStrengthEstimator.StrengthModel.XGBOOST20TUN;
    SwingUtilities.invokeAndWait(
        () -> {
          StrengthDialog dialog = new StrengthDialog(host, STYLE, BUNDLE);
          assertEquals(
              BUNDLE.getString("HumanSL40.backgroundOff"), dialog.dashboard().status().getText());
          host.status = new HumanSlStrengthPreparation.Status("HumanSL40.progress", 3, 10, 1);
          dialog.refresh();
          assertEquals(
              java.text.MessageFormat.format(BUNDLE.getString("HumanSL40.progress"), 3, 10),
              dialog.dashboard().status().getText());
        });
  }

  @Test
  void choosingAModelActivatesItAndShowsItsEstimate() throws Exception {
    FakeHost host = new FakeHost(StrengthFixtures.twoMoveReport());
    host.active = PlayerStrengthEstimator.StrengthModel.XGBOOST20TUN;
    PlayerStrengthEstimator.Report humanSl = StrengthFixtures.emptyReport();
    SwingUtilities.invokeAndWait(
        () -> {
          StrengthDialog dialog = new StrengthDialog(host, STYLE, BUNDLE);
          dialog.chooseModel(PlayerStrengthEstimator.StrengthModel.XGBOOST20TUN);
          assertEquals(1, host.reports, "choosing the active model again does nothing");

          host.next = humanSl;
          dialog.chooseModel(PlayerStrengthEstimator.StrengthModel.HUMANSL40);
          assertEquals(List.of(PlayerStrengthEstimator.StrengthModel.HUMANSL40), host.activated);
          assertSame(humanSl, dialog.dashboard().report());

          dialog.chooseModel(PlayerStrengthEstimator.StrengthModel.HUMANSL40);
          assertEquals(3, host.reports, "choosing HumanSL again looks for new data");
        });
  }

  @Test
  void preparingNowShowsTheNewEstimate() throws Exception {
    FakeHost host = new FakeHost(StrengthFixtures.emptyReport());
    PlayerStrengthEstimator.Report ready = StrengthFixtures.twoMoveReport();
    SwingUtilities.invokeAndWait(
        () -> {
          StrengthDialog dialog = new StrengthDialog(host, STYLE, BUNDLE);
          host.next = ready;
          dialog.prepareNow();
          assertEquals(1, host.preparations);
          assertSame(ready, dialog.dashboard().report());
        });
  }

  @Test
  void failuresUseTheShortBackgroundMessages() {
    for (String key :
        new String[] {"HumanSL40.failed", "HumanSL40.invalidModel", "HumanSL40.invalidResponse"}) {
      assertEquals(
          BUNDLE.getString("HumanSL40.backgroundFailed"),
          StrengthDialog.statusText(new HumanSlStrengthPreparation.Status(key, 1, 2, 0), BUNDLE));
    }
    assertEquals(
        BUNDLE.getString("HumanSL40.backgroundNeedEngine"),
        StrengthDialog.statusText(
            new HumanSlStrengthPreparation.Status("HumanSL40.needEngine", 0, 0, 0), BUNDLE));
  }

  @Test
  void statusDotSaysHowThePreparationIsDoing() {
    assertEquals(StrengthDashboard.Tone.READY, StrengthDialog.tone("HumanSL40.backgroundReady"));
    assertEquals(StrengthDashboard.Tone.WORKING, StrengthDialog.tone("HumanSL40.progress"));
    assertEquals(StrengthDashboard.Tone.WORKING, StrengthDialog.tone("HumanSL40.preparing"));
    assertEquals(StrengthDashboard.Tone.IDLE, StrengthDialog.tone("HumanSL40.backgroundOff"));
    assertEquals(StrengthDashboard.Tone.IDLE, StrengthDialog.tone("HumanSL40.backgroundPaused"));
    assertEquals(StrengthDashboard.Tone.PROBLEM, StrengthDialog.tone("HumanSL40.needEngine"));
    assertEquals(StrengthDashboard.Tone.PROBLEM, StrengthDialog.tone("HumanSL40.failed"));
  }

  @Test
  void detailPanelShowsEveryFigureOrTheMatchChart() throws Exception {
    PlayerStrengthEstimator.Report report = StrengthFixtures.twoMoveReport();
    SwingUtilities.invokeAndWait(
        () -> {
          var table = StrengthDialog.detailPanel(report, false, STYLE, BUNDLE);
          assertTrue(table.getComponent(0) instanceof StrengthDetailTable);
          StrengthDetailTable rows = (StrengthDetailTable) table.getComponent(0);
          assertEquals(10, rows.rows().size());
          assertEquals(
              BUNDLE.getString("PlayerStrengthEstimate.match.strength"), rows.rows().get(0)[0]);
          var chart = StrengthDialog.detailPanel(report, true, STYLE, BUNDLE);
          assertTrue(chart.getComponent(0) instanceof StrengthSegmentChart);
        });
  }
}
