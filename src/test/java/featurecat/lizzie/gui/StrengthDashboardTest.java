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
import javax.swing.JPopupMenu;
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
  private static final StrengthDashboard.Context ANALYZED =
      new StrengthDashboard.Context(true, false, 326, 326, true);

  /** Records what the dashboard asks for. */
  private static final class Recorder implements StrengthDashboard.Actions {
    final List<PlayerStrengthEstimator.StrengthModel> chosen = new ArrayList<>();
    final List<Integer> details = new ArrayList<>();
    boolean backgroundResult;
    int imports;
    int preparations;
    int analyses;

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

    @Override
    public void analyzeGame() {
      analyses++;
    }
  }

  private static StrengthDashboard dashboard(
      PlayerStrengthEstimator.Report report, StrengthDashboard.Context context, Recorder actions) {
    StrengthDashboard dashboard =
        new StrengthDashboard(report, MODELS, false, context, STYLE, BUNDLE, actions);
    dashboard.setSize(STYLE.px(1120), STYLE.px(732));
    layout(dashboard);
    return dashboard;
  }

  private static StrengthDashboard dashboard(
      PlayerStrengthEstimator.Report report, Recorder actions) {
    return dashboard(report, ANALYZED, actions);
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
    PlayerStrengthEstimator.Report report = StrengthFixtures.readyReport();
    SwingUtilities.invokeAndWait(
        () -> {
          StrengthDashboard dashboard = dashboard(report, new Recorder());
          assertEquals(StrengthDashboard.Stage.READY, dashboard.stage());
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
    PlayerStrengthEstimator.Report report = StrengthFixtures.readyReport();
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
    PlayerStrengthEstimator.Report report = StrengthFixtures.readyReport();
    Recorder actions = new Recorder();
    SwingUtilities.invokeAndWait(
        () -> {
          StrengthDashboard dashboard = dashboard(report, actions);
          dashboard.detailsButton().doClick(0);
          dashboard.tabs().segment(StrengthDashboard.MATCH).doClick(0);
          dashboard.detailsButton().doClick(0);
        });
    assertEquals(List.of(StrengthDashboard.ASSESSMENT, StrengthDashboard.MATCH), actions.details);
  }

  @Test
  void modelMenuNamesEachModelPlainlyAndEndsWithImport() throws Exception {
    PlayerStrengthEstimator.Report report = StrengthFixtures.readyReport();
    Recorder actions = new Recorder();
    SwingUtilities.invokeAndWait(
        () -> {
          StrengthDashboard dashboard = dashboard(report, actions);
          assertEquals(
              BUNDLE.getString("PlayerStrengthEstimate.model.standard"),
              dashboard.picker().getText());
          JPopupMenu menu = dashboard.picker().menu();
          StrengthModelPicker.ModelItem standard =
              (StrengthModelPicker.ModelItem) menu.getComponent(0);
          StrengthModelPicker.ModelItem human =
              (StrengthModelPicker.ModelItem) menu.getComponent(1);
          assertTrue(standard.isCurrent());
          assertFalse(human.isCurrent());
          assertEquals(
              BUNDLE.getString("PlayerStrengthEstimate.model.humanStyle"), human.getText());
          assertEquals(
              BUNDLE.getString("PlayerStrengthEstimate.model.humanStyle.description"),
              human.getAccessibleContext().getAccessibleDescription());

          human.doClick(0);
          ((StrengthModelPicker.ImportItem) menu.getComponent(menu.getComponentCount() - 1))
              .doClick(0);
        });
    assertEquals(List.of(PlayerStrengthEstimator.StrengthModel.HUMANSL40), actions.chosen);
    assertEquals(1, actions.imports);
  }

  @Test
  void modelMenuOpensRightAlignedUnderTheField() throws Exception {
    PlayerStrengthEstimator.Report report = StrengthFixtures.readyReport();
    SwingUtilities.invokeAndWait(
        () -> {
          StrengthModelPicker picker = dashboard(report, new Recorder()).picker();
          java.awt.Dimension menu = picker.menu().getPreferredSize();
          assertTrue(menu.width > picker.getWidth(), "the menu is wider than the field");
          java.awt.Point location = picker.menuLocation(menu);
          assertEquals(picker.getWidth(), location.x + menu.width, "right edges line up");
          assertTrue(location.y >= picker.getHeight(), "it opens below the field");
        });
  }

  @Test
  void onlyTheUserChoosesAModel() throws Exception {
    PlayerStrengthEstimator.Report report = StrengthFixtures.readyReport();
    PlayerStrengthEstimator.Report humanSl = StrengthFixtures.emptyReport();
    Recorder actions = new Recorder();
    SwingUtilities.invokeAndWait(
        () -> {
          StrengthDashboard dashboard = dashboard(report, actions);
          dashboard.showReport(humanSl, MODELS, ANALYZED);
          assertEquals(humanSl.model, dashboard.picker().selectedModel());
          assertTrue(actions.chosen.isEmpty(), "showing an estimate must not choose a model");
          dashboard.picker().choose(PlayerStrengthEstimator.StrengthModel.XGBOOST20TUN);
        });
    assertEquals(List.of(PlayerStrengthEstimator.StrengthModel.XGBOOST20TUN), actions.chosen);
  }

  @Test
  void newEstimateKeepsTheViewOnScreen() throws Exception {
    PlayerStrengthEstimator.Report first = StrengthFixtures.readyReport();
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
          dashboard.showReport(second, MODELS, ANALYZED);
          assertEquals(StrengthDashboard.PERFORMANCE, dashboard.selectedView());
          assertSame(second, dashboard.report());
          StrengthDistributionCard card =
              find(dashboard.currentView(), StrengthDistributionCard.class);
          assertEquals(7, card.counts(true)[MoveRankDefinition.Rank.MISTAKE.ordinal()]);
          assertEquals(5, card.counts(false)[MoveRankDefinition.Rank.BEST.ordinal()]);
        });
  }

  @Test
  void gameWithoutAnalysisOffersToAnalyzeIt() throws Exception {
    PlayerStrengthEstimator.Report empty = emptyStandardReport();
    Recorder actions = new Recorder();
    SwingUtilities.invokeAndWait(
        () -> {
          StrengthDashboard dashboard =
              dashboard(empty, new StrengthDashboard.Context(false, false, 0, 326, true), actions);
          assertEquals(StrengthDashboard.Stage.NEEDS_ANALYSIS, dashboard.stage());
          assertSame(dashboard.analysisState(), dashboard.currentView());
          assertFalse(dashboard.tabs().segment(0).isEnabled());
          assertFalse(dashboard.detailsButton().isEnabled());
          assertFalse(dashboard.footer().isVisible());
          assertFalse(dashboard.analysisState().status().isVisible());
          dashboard.analysisState().actionButton().doClick(0);
        });
    assertEquals(1, actions.analyses);
  }

  @Test
  void runningAnalysisShowsHowFarItGot() throws Exception {
    PlayerStrengthEstimator.Report empty = emptyStandardReport();
    PlayerStrengthEstimator.Report partial = StrengthFixtures.readyReport();
    SwingUtilities.invokeAndWait(
        () -> {
          StrengthDashboard dashboard =
              dashboard(
                  empty, new StrengthDashboard.Context(false, true, 4, 326, true), new Recorder());
          StrengthEmptyState state = dashboard.analysisState();
          assertFalse(state.actionButton().isEnabled(), "the analysis is already running");
          assertTrue(state.status().isVisible());
          assertTrue(state.status().getText().contains("4"));
          assertTrue(state.status().getText().contains("326"));

          dashboard.showReport(
              partial, MODELS, new StrengthDashboard.Context(true, true, 241, 326, true));
          assertEquals(StrengthDashboard.Stage.READY, dashboard.stage());
          assertTrue(dashboard.analysisBanner().isVisible(), "the figures may still change");
          assertTrue(dashboard.analysisBanner().text().contains("241"));

          dashboard.showReport(partial, MODELS, ANALYZED);
          assertFalse(dashboard.analysisBanner().isVisible());
        });
  }

  @Test
  void partlyAnalyzedGameOffersToAnalyzeTheRest() throws Exception {
    PlayerStrengthEstimator.Report report = StrengthFixtures.readyReport();
    Recorder actions = new Recorder();
    SwingUtilities.invokeAndWait(
        () -> {
          StrengthDashboard dashboard =
              dashboard(report, new StrengthDashboard.Context(true, false, 30, 326, true), actions);
          StrengthBanner banner = dashboard.analysisBanner();
          assertTrue(banner.isVisible());
          assertEquals(
              java.text.MessageFormat.format(
                  BUNDLE.getString("PlayerStrengthEstimate.partial"), 30, 326),
              banner.text());
          assertTrue(banner.actionButton().isVisible());
          banner.actionButton().doClick(0);

          dashboard.showReport(
              report, MODELS, new StrengthDashboard.Context(true, false, 300, 326, true));
          assertFalse(banner.isVisible(), "nine in ten moves are enough");
        });
    assertEquals(1, actions.analyses);
  }

  @Test
  void tooShortGameSaysSoInsteadOfOfferingAnalysis() throws Exception {
    PlayerStrengthEstimator.Report empty = emptyStandardReport();
    SwingUtilities.invokeAndWait(
        () -> {
          StrengthDashboard dashboard =
              dashboard(
                  empty, new StrengthDashboard.Context(false, false, 3, 3, true), new Recorder());
          StrengthEmptyState state = dashboard.analysisState();
          assertEquals(StrengthDashboard.Stage.NEEDS_ANALYSIS, dashboard.stage());
          assertTrue(state.isShortGame());
          assertEquals(BUNDLE.getString("PlayerStrengthEstimate.empty.short.title"), state.title());
          assertFalse(state.actionButton().isVisible(), "analysis would not help");

          dashboard.showReport(
              empty, MODELS, new StrengthDashboard.Context(false, false, 0, 40, true));
          assertFalse(state.isShortGame());
          assertEquals(
              BUNDLE.getString("PlayerStrengthEstimate.empty.analysis.title"), state.title());
          assertTrue(state.actionButton().isVisible());
        });
  }

  @Test
  void humanStyleModelFirstOffersItsOneTimeCalculation() throws Exception {
    PlayerStrengthEstimator.Report human = StrengthFixtures.emptyReport();
    Recorder actions = new Recorder();
    SwingUtilities.invokeAndWait(
        () -> {
          StrengthDashboard dashboard =
              dashboard(
                  human, new StrengthDashboard.Context(true, false, 326, 326, false), actions);
          assertEquals(StrengthDashboard.Stage.NEEDS_HUMAN_STYLE, dashboard.stage());
          StrengthEmptyState state = dashboard.humanStyleState();
          assertSame(state, dashboard.currentView());
          assertFalse(dashboard.footer().isVisible(), "the switch is on the page itself");
          assertTrue(state.isDownloadNoteVisible(), "the weights are not installed yet");
          assertSame(state.actionButton(), named(dashboard, "strength40-run"));
          assertSame(state.backgroundSwitch(), named(dashboard, "strength40-background"));
          state.actionButton().doClick(0);

          dashboard.showReport(
              human, MODELS, new StrengthDashboard.Context(true, false, 326, 326, true));
          assertFalse(state.isDownloadNoteVisible());
        });
    assertEquals(1, actions.preparations);
  }

  @Test
  void humanStyleWithoutAnalysisAsksForTheAnalysisFirst() throws Exception {
    PlayerStrengthEstimator.Report human = StrengthFixtures.emptyReport();
    SwingUtilities.invokeAndWait(
        () -> {
          StrengthDashboard dashboard =
              dashboard(
                  human, new StrengthDashboard.Context(false, false, 0, 326, true), new Recorder());
          assertEquals(StrengthDashboard.Stage.NEEDS_ANALYSIS, dashboard.stage());
        });
  }

  @Test
  void preparationProgressShowsOnTheHumanStylePage() throws Exception {
    PlayerStrengthEstimator.Report human = StrengthFixtures.emptyReport();
    SwingUtilities.invokeAndWait(
        () -> {
          StrengthDashboard dashboard = dashboard(human, new Recorder());
          StrengthEmptyState state = dashboard.humanStyleState();
          dashboard.showStatus("84/210", StrengthDashboard.Tone.WORKING, 84, 210);
          assertEquals("84/210", state.status().getText());
          assertFalse(state.actionButton().isEnabled(), "work is under way");
          dashboard.showStatus("off", StrengthDashboard.Tone.IDLE, 0, 0);
          assertTrue(state.actionButton().isEnabled());
        });
  }

  @Test
  void backgroundSwitchAppearsOnlyWithTheHumanStyleFigures() throws Exception {
    PlayerStrengthEstimator.Report standard = StrengthFixtures.readyReport();
    PlayerStrengthEstimator.Report human =
        StrengthFixtures.readyReport(PlayerStrengthEstimator.StrengthModel.HUMANSL40);
    SwingUtilities.invokeAndWait(
        () -> {
          StrengthDashboard dashboard = dashboard(standard, new Recorder());
          assertFalse(dashboard.footer().isVisible(), "the standard model needs no HumanSL data");
          dashboard.showReport(human, MODELS, ANALYZED);
          assertEquals(StrengthDashboard.Stage.READY, dashboard.stage());
          assertTrue(dashboard.footer().isVisible());
        });
  }

  @Test
  void bothBackgroundSwitchesShowWhatTheDialogSaved() throws Exception {
    PlayerStrengthEstimator.Report human = StrengthFixtures.emptyReport();
    Recorder actions = new Recorder();
    SwingUtilities.invokeAndWait(
        () -> {
          StrengthDashboard dashboard = dashboard(human, actions);
          StrengthSwitch page = dashboard.humanStyleState().backgroundSwitch();
          actions.backgroundResult = true;
          page.doClick(0);
          assertTrue(page.isSelected());
          assertTrue(dashboard.backgroundSwitch().isSelected(), "the footer switch follows");
          actions.backgroundResult = false;
          dashboard.backgroundSwitch().doClick(0);
          assertFalse(page.isSelected(), "saving failed, so both show off");
          assertFalse(dashboard.backgroundSwitch().isSelected());
        });
  }

  @Test
  void figuresExplainThemselvesInPlainWords() throws Exception {
    PlayerStrengthEstimator.Report report = StrengthFixtures.readyReport();
    SwingUtilities.invokeAndWait(
        () -> {
          StrengthDashboard dashboard = dashboard(report, new Recorder());
          StrengthCompareCard card = find(dashboard.currentView(), StrengthCompareCard.class);
          for (StrengthCompareCard.Row row : card.rows()) {
            assertNotNull(row.help(), row.label());
            assertFalse(row.help().isBlank(), row.label());
          }
          assertEquals(
              BUNDLE.getString("PlayerStrengthEstimate.help.weightedScoreLoss"),
              card.rows().get(3).help());
        });
  }

  @Test
  void everyPartFitsTheSmallestWindowInEveryLanguage() throws Exception {
    PlayerStrengthEstimator.Report report = StrengthFixtures.readyReport();
    PlayerStrengthEstimator.Report human = StrengthFixtures.emptyReport();
    PlayerStrengthEstimator.Report readyHuman =
        StrengthFixtures.readyReport(PlayerStrengthEstimator.StrengthModel.HUMANSL40);
    PlayerStrengthEstimator.Report empty = emptyStandardReport();
    for (AppLocale locale : AppLocale.values()) {
      if (locale == AppLocale.SYSTEM) {
        continue;
      }
      for (int size : new int[] {12, 16}) {
        StrengthStyle style = new StrengthStyle(false, false, "Dialog", size);
        SwingUtilities.invokeAndWait(
            () -> {
              ResourceBundle bundle = locale.loadBundle();
              StrengthDashboard dashboard =
                  new StrengthDashboard(
                      report,
                      MODELS,
                      true,
                      new StrengthDashboard.Context(true, false, 30, 326, true),
                      style,
                      bundle,
                      new Recorder());
              dashboard.setSize(style.px(960) - 16, style.px(660) - 28);
              for (int view = 0; view < 3; view++) {
                dashboard.tabs().segment(view).doClick(0);
                layout(dashboard);
                layout(dashboard);
                assertInside(dashboard, locale + " " + size + " view " + view);
              }
              dashboard.showReport(readyHuman, MODELS, ANALYZED);
              dashboard.showStatus(longestStatus(bundle), StrengthDashboard.Tone.IDLE, 0, 0);
              layout(dashboard);
              layout(dashboard);
              assertInside(dashboard, locale + " " + size + " human-style figures");
              dashboard.showReport(
                  human, MODELS, new StrengthDashboard.Context(true, true, 9, 326, false));
              dashboard.showStatus("1/2", StrengthDashboard.Tone.WORKING, 1, 2);
              layout(dashboard);
              layout(dashboard);
              assertInside(dashboard, locale + " " + size + " human style");
              dashboard.showReport(
                  empty, MODELS, new StrengthDashboard.Context(false, true, 9, 326, true));
              layout(dashboard);
              layout(dashboard);
              assertInside(dashboard, locale + " " + size + " analysis");
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

  private static PlayerStrengthEstimator.Report emptyStandardReport() throws Exception {
    return StrengthFixtures.emptyReport(PlayerStrengthEstimator.StrengthModel.XGBOOST20TUN);
  }

  /** The longest status the HumanSL footer can show in {@code bundle}. */
  private static String longestStatus(ResourceBundle bundle) {
    String longest = "";
    for (String key :
        new String[] {
          "HumanSL40.backgroundIdle",
          "HumanSL40.backgroundPaused",
          "HumanSL40.backgroundOff",
          "HumanSL40.backgroundReady",
          "HumanSL40.backgroundNeedEngine",
          "HumanSL40.backgroundFailed",
          "HumanSL40.cancelled"
        }) {
      String text = bundle.getString(key);
      longest = text.length() > longest.length() ? text : longest;
    }
    return longest;
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
