package featurecat.lizzie.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import featurecat.lizzie.AppLocale;
import featurecat.lizzie.analysis.MoveRankDefinition;
import featurecat.lizzie.analysis.PlayerStrengthEstimator;
import featurecat.lizzie.rules.Stone;
import java.awt.Component;
import java.awt.Container;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;
import javax.swing.JComponent;
import javax.swing.JToggleButton;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class StrengthDashboardTest {
  private static final ResourceBundle BUNDLE = AppLocale.SIMPLIFIED_CHINESE.loadBundle();
  private static final StrengthStyle STYLE = new StrengthStyle(false, false, "Dialog", 12);
  private static final PlayerStrengthEstimator.StrengthModel[] MODELS = {
    PlayerStrengthEstimator.StrengthModel.XGBOOST20TUN,
    PlayerStrengthEstimator.StrengthModel.HUMANSL40
  };

  /** Records what the dashboard asks for. */
  private static final class Recorder implements StrengthDashboard.Actions {
    final List<PlayerStrengthEstimator.StrengthModel> chosen = new ArrayList<>();
    final List<Integer> details = new ArrayList<>();
    boolean backgroundResult;
    int imports;
    int preparations;

    @Override
    public void chooseModel(PlayerStrengthEstimator.StrengthModel model) {
      chosen.add(model);
    }

    @Override
    public void importModel() {
      imports++;
    }

    @Override
    public void showDetails(int view) {
      details.add(view);
    }

    @Override
    public boolean setBackgroundEnabled(boolean enabled) {
      return backgroundResult;
    }

    @Override
    public void prepareNow() {
      preparations++;
    }
  }

  private static StrengthDashboard dashboard(
      PlayerStrengthEstimator.Report report, Recorder actions) {
    StrengthDashboard dashboard =
        new StrengthDashboard(report, MODELS, false, STYLE, BUNDLE, actions);
    dashboard.setSize(STYLE.px(1120), STYLE.px(732));
    layout(dashboard);
    return dashboard;
  }

  private static void layout(Component component) {
    component.doLayout();
    if (component instanceof Container container) {
      for (Component child : container.getComponents()) {
        layout(child);
      }
    }
  }

  private static <T> T find(Component root, Class<T> type) {
    if (type.isInstance(root) && root.isVisible()) {
      return type.cast(root);
    }
    if (root instanceof Container container) {
      for (Component child : container.getComponents()) {
        T found = find(child, type);
        if (found != null) {
          return found;
        }
      }
    }
    return null;
  }

  private static Component named(Component root, String name) {
    if (name.equals(root.getName())) {
      return root;
    }
    if (root instanceof Container container) {
      for (Component child : container.getComponents()) {
        Component found = named(child, name);
        if (found != null) {
          return found;
        }
      }
    }
    return null;
  }

  @Test
  void segmentsSwitchTheViewAndItsSubtitle() throws Exception {
    PlayerStrengthEstimator.Report report = StrengthFixtures.twoMoveReport();
    SwingUtilities.invokeAndWait(
        () -> {
          StrengthDashboard dashboard = dashboard(report, new Recorder());
          assertEquals(StrengthDashboard.ASSESSMENT, dashboard.selectedView());
          assertNotNull(find(dashboard.currentView(), StrengthRankCard.class));

          dashboard.tabs().segment(StrengthDashboard.MATCH).doClick(0);
          assertEquals(
              BUNDLE.getString("PlayerStrengthEstimate.subtitle.match"),
              dashboard.subtitle().getText());
          assertNotNull(find(dashboard.currentView(), StrengthTimeline.class));

          dashboard.tabs().segment(StrengthDashboard.PERFORMANCE).doClick(0);
          assertEquals(
              BUNDLE.getString("PlayerStrengthEstimate.subtitle.performance"),
              dashboard.subtitle().getText());
          assertNotNull(find(dashboard.currentView(), StrengthDistributionCard.class));
        });
  }

  @Test
  void arrowKeysMoveToTheNeighbouringSegment() throws Exception {
    PlayerStrengthEstimator.Report report = StrengthFixtures.twoMoveReport();
    SwingUtilities.invokeAndWait(
        () -> {
          StrengthDashboard dashboard = dashboard(report, new Recorder());
          JToggleButton first = dashboard.tabs().segment(0);
          Object right =
              first.getInputMap(JComponent.WHEN_FOCUSED).get(KeyStroke.getKeyStroke("RIGHT"));
          first.getActionMap().get(right).actionPerformed(new ActionEvent(first, 0, "RIGHT"));
          assertEquals(StrengthDashboard.MATCH, dashboard.selectedView());
          JToggleButton second = dashboard.tabs().segment(1);
          Object left =
              second.getInputMap(JComponent.WHEN_FOCUSED).get(KeyStroke.getKeyStroke("LEFT"));
          second.getActionMap().get(left).actionPerformed(new ActionEvent(second, 0, "LEFT"));
          assertEquals(StrengthDashboard.ASSESSMENT, dashboard.selectedView());
          assertTrue(
              first.getInputMap(JComponent.WHEN_FOCUSED).get(KeyStroke.getKeyStroke("LEFT"))
                  == null,
              "the first segment does not wrap around");
        });
  }

  @Test
  void detailsOpenForTheViewOnScreen() throws Exception {
    PlayerStrengthEstimator.Report report = StrengthFixtures.twoMoveReport();
    Recorder actions = new Recorder();
    SwingUtilities.invokeAndWait(
        () -> {
          StrengthDashboard dashboard = dashboard(report, actions);
          dashboard.detailsButton().doClick(0);
          dashboard.tabs().segment(StrengthDashboard.MATCH).doClick(0);
          dashboard.detailsButton().doClick(0);
          dashboard.importButton().doClick(0);
        });
    assertEquals(List.of(StrengthDashboard.ASSESSMENT, StrengthDashboard.MATCH), actions.details);
    assertEquals(1, actions.imports);
  }

  @Test
  void onlyTheUserChoosesAModel() throws Exception {
    PlayerStrengthEstimator.Report report = StrengthFixtures.twoMoveReport();
    PlayerStrengthEstimator.Report humanSl = StrengthFixtures.emptyReport();
    Recorder actions = new Recorder();
    SwingUtilities.invokeAndWait(
        () -> {
          StrengthDashboard dashboard = dashboard(report, actions);
          assertEquals(report.model, dashboard.picker().getSelectedItem());
          dashboard.showReport(humanSl, MODELS);
          assertEquals(humanSl.model, dashboard.picker().getSelectedItem());
          assertTrue(actions.chosen.isEmpty(), "showing an estimate must not choose a model");

          dashboard.picker().setSelectedItem(PlayerStrengthEstimator.StrengthModel.XGBOOST20TUN);
        });
    assertEquals(List.of(PlayerStrengthEstimator.StrengthModel.XGBOOST20TUN), actions.chosen);
  }

  @Test
  void newEstimateKeepsTheViewOnScreen() throws Exception {
    PlayerStrengthEstimator.Report first = StrengthFixtures.twoMoveReport();
    PlayerStrengthEstimator.Report second =
        StrengthFixtures.report(
            StrengthFixtures.side(
                StrengthFixtures.moves(
                    Stone.BLACK, 1, 7, PlayerStrengthEstimator.MoveCategory.MISTAKE),
                0.0,
                0.0),
            StrengthFixtures.side(
                StrengthFixtures.moves(
                    Stone.WHITE, 2, 5, PlayerStrengthEstimator.MoveCategory.EXCELLENT),
                1.0,
                1.0),
            PlayerStrengthEstimator.StrengthModel.XGBOOST20TUN);
    SwingUtilities.invokeAndWait(
        () -> {
          StrengthDashboard dashboard = dashboard(first, new Recorder());
          dashboard.tabs().segment(StrengthDashboard.PERFORMANCE).doClick(0);
          dashboard.showReport(second, MODELS);
          assertEquals(StrengthDashboard.PERFORMANCE, dashboard.selectedView());
          assertSame(second, dashboard.report());
          StrengthDistributionCard card =
              find(dashboard.currentView(), StrengthDistributionCard.class);
          assertEquals(7, card.counts(true)[MoveRankDefinition.Rank.MISTAKE.ordinal()]);
          assertEquals(5, card.counts(false)[MoveRankDefinition.Rank.BEST.ordinal()]);
        });
  }

  @Test
  void humanSlControlsKeepTheirNames() throws Exception {
    PlayerStrengthEstimator.Report report = StrengthFixtures.twoMoveReport();
    PlayerStrengthEstimator.Report humanSl = StrengthFixtures.emptyReport();
    Recorder actions = new Recorder();
    SwingUtilities.invokeAndWait(
        () -> {
          StrengthDashboard dashboard = dashboard(report, actions);
          assertSame(dashboard.picker(), named(dashboard, "strength40-selector"));
          assertSame(dashboard.backgroundSwitch(), named(dashboard, "strength40-background"));
          assertSame(dashboard.status(), named(dashboard, "strength40-status"));
          assertSame(dashboard.prepareButton(), named(dashboard, "strength40-run"));
          assertFalse(dashboard.prepareButton().isVisible(), "only HumanSL can be prepared");

          dashboard.showReport(humanSl, MODELS);
          assertTrue(dashboard.prepareButton().isVisible());
          dashboard.prepareButton().doClick(0);
        });
    assertEquals(1, actions.preparations);
  }

  @Test
  void backgroundSwitchShowsWhatTheDialogSaved() throws Exception {
    PlayerStrengthEstimator.Report report = StrengthFixtures.twoMoveReport();
    Recorder actions = new Recorder();
    SwingUtilities.invokeAndWait(
        () -> {
          StrengthDashboard dashboard = dashboard(report, actions);
          actions.backgroundResult = false;
          dashboard.backgroundSwitch().doClick(0);
          assertFalse(dashboard.backgroundSwitch().isSelected(), "saving failed");
          actions.backgroundResult = true;
          dashboard.backgroundSwitch().doClick(0);
          assertTrue(dashboard.backgroundSwitch().isSelected());
        });
  }

  @Test
  void noteWarnsWhenTheEstimateIsNotReady() throws Exception {
    PlayerStrengthEstimator.Report humanSl = StrengthFixtures.emptyReport();
    SwingUtilities.invokeAndWait(
        () -> {
          StrengthDashboard dashboard = dashboard(humanSl, new Recorder());
          StrengthNote note = find(dashboard.currentView(), StrengthNote.class);
          assertTrue(note.isCaution());
          assertEquals(BUNDLE.getString("HumanSL40.unavailable"), note.text());
        });
  }

  @Test
  void everyPartFitsTheSmallestWindowInEveryLanguage() throws Exception {
    PlayerStrengthEstimator.Report report = StrengthFixtures.twoMoveReport();
    for (AppLocale locale : AppLocale.values()) {
      if (locale == AppLocale.SYSTEM) {
        continue;
      }
      for (int size : new int[] {12, 16}) {
        StrengthStyle style = new StrengthStyle(false, false, "Dialog", size);
        SwingUtilities.invokeAndWait(
            () -> {
              StrengthDashboard dashboard =
                  new StrengthDashboard(
                      report, MODELS, true, style, locale.loadBundle(), new Recorder());
              dashboard.setSize(style.px(960) - 16, style.px(660) - 28);
              for (int view = 0; view < 3; view++) {
                dashboard.tabs().segment(view).doClick(0);
                layout(dashboard);
                layout(dashboard);
                assertInside(dashboard, locale + " " + size + " view " + view);
              }
              Rectangle tabs =
                  SwingUtilities.convertRectangle(
                      dashboard.tabs().getParent(), dashboard.tabs().getBounds(), dashboard);
              Rectangle controls =
                  SwingUtilities.convertRectangle(
                      dashboard.picker().getParent().getParent(),
                      dashboard.picker().getParent().getBounds(),
                      dashboard);
              assertFalse(tabs.intersects(controls), locale + " " + size + ": tabs and controls");
            });
      }
    }
  }

  /** Every visible component lies within its parent and is at least as wide as it needs. */
  private static void assertInside(Container parent, String where) {
    for (Component child : parent.getComponents()) {
      if (!child.isVisible() || child.getWidth() == 0) {
        continue;
      }
      Rectangle bounds = child.getBounds();
      if (!(parent instanceof javax.swing.JViewport)) {
        assertTrue(
            bounds.x >= 0
                && bounds.y >= 0
                && bounds.x + bounds.width <= parent.getWidth()
                && bounds.y + bounds.height <= parent.getHeight(),
            where
                + ": "
                + child.getClass().getSimpleName()
                + " "
                + bounds
                + " leaves "
                + parent.getClass().getSimpleName()
                + " "
                + parent.getSize());
      }
      if (child instanceof StrengthButton
          || child instanceof StrengthModelPicker
          || child instanceof StrengthSwitch
          || child instanceof StrengthTabs) {
        assertTrue(
            child.getWidth() >= child.getPreferredSize().width,
            where + ": " + child.getClass().getSimpleName() + " is squeezed");
      }
      if (child instanceof Container container) {
        assertInside(container, where);
      }
    }
  }
}
