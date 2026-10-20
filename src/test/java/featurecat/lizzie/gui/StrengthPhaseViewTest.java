package featurecat.lizzie.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import featurecat.lizzie.AppLocale;
import featurecat.lizzie.analysis.PlayerStrengthEstimator;
import featurecat.lizzie.rules.Stone;
import java.text.MessageFormat;
import java.util.List;
import java.util.ResourceBundle;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class StrengthPhaseViewTest {
  private static final ResourceBundle BUNDLE = AppLocale.SIMPLIFIED_CHINESE.loadBundle();
  private static final StrengthStyle STYLE = new StrengthStyle(false, false, "Dialog", 12);
  private static final String BLACK = "\u9ed1\u68cb";
  private static final String WHITE = "\u767d\u68cb";

  private static PlayerStrengthEstimator.SideReport side(Stone color, double rank)
      throws Exception {
    return StrengthFixtures.side(
        StrengthFixtures.moves(
            color,
            color == Stone.BLACK ? 1 : 2,
            Double.isNaN(rank) ? 2 : 12,
            PlayerStrengthEstimator.MoveCategory.GREAT),
        PlayerStrengthEstimator.StrengthModel.XGBOOST20TUN,
        rank,
        0.5,
        0.8,
        "5d",
        PlayerStrengthEstimator.Confidence.MEDIUM);
  }

  private static StrengthPhases.Phase phase(int index, double black, double white)
      throws Exception {
    return new StrengthPhases.Phase(
        index, index * 50 + 1, index * 50 + 50, side(Stone.BLACK, black), side(Stone.WHITE, white));
  }

  private static String text(String key, Object... arguments) {
    return MessageFormat.format(BUNDLE.getString("PlayerStrengthEstimate." + key), arguments);
  }

  @Test
  void reviewNamesEachSidesClearlyWeakestPhase() throws Exception {
    List<StrengthPhases.Phase> phases =
        List.of(phase(0, 6.0, 3.0), phase(1, 6.1, 6.0), phase(2, 3.5, 6.2));
    assertEquals(
        text(
            "phase.reviewBoth",
            text("phase.reviewItem", BLACK, text("phase.endgame")),
            text("phase.reviewItem", WHITE, text("phase.opening"))),
        StrengthPhaseView.review(phases, BLACK, WHITE, BUNDLE));
    List<StrengthPhases.Phase> blackOnly =
        List.of(phase(0, 6.0, 6.0), phase(1, 6.1, 6.1), phase(2, 3.5, 6.2));
    assertEquals(
        text("phase.review", text("phase.reviewItem", BLACK, text("phase.endgame"))),
        StrengthPhaseView.review(blackOnly, BLACK, WHITE, BUNDLE));
  }

  @Test
  void sameWeakestPhaseForBothSidesIsNamedOnce() throws Exception {
    List<StrengthPhases.Phase> phases =
        List.of(phase(0, 6.0, 6.0), phase(1, 3.0, 3.5), phase(2, 6.2, 6.1));
    assertEquals(
        text("phase.reviewSame", text("phase.middle")),
        StrengthPhaseView.review(phases, BLACK, WHITE, BUNDLE));
  }

  @Test
  void steadyPlayIsSaidSoAndTooLittleDataSaysNothing() throws Exception {
    List<StrengthPhases.Phase> steady =
        List.of(phase(0, 6.0, 5.0), phase(1, 6.2, 5.2), phase(2, 6.1, 5.1));
    assertEquals(
        BUNDLE.getString("PlayerStrengthEstimate.phase.steady"),
        StrengthPhaseView.review(steady, BLACK, WHITE, BUNDLE));
    List<StrengthPhases.Phase> sparse =
        List.of(phase(0, 6.0, 5.0), phase(1, Double.NaN, Double.NaN), phase(2, Double.NaN, 5.0));
    assertNull(StrengthPhaseView.review(sparse, BLACK, WHITE, BUNDLE));
  }

  @Test
  void cardSaysWhoPlayedThePhaseBetterAndMarksBestAndWeakest() throws Exception {
    List<StrengthPhases.Phase> phases =
        List.of(phase(0, 6.0, 3.0), phase(1, 6.5, 6.4), phase(2, 3.5, 6.2));
    List<StrengthPhases.Phase> closeEndgame =
        List.of(phase(0, 6.0, 3.0), phase(1, 6.5, 6.4), phase(2, 5.8, 6.3));
    SwingUtilities.invokeAndWait(
        () -> {
          StrengthPhaseCard opening =
              new StrengthPhaseCard(phases, 0, false, BLACK, WHITE, BUNDLE, STYLE);
          assertEquals(text("phase.clearly", BLACK), opening.verdict());
          assertEquals(text("phase.weakest"), opening.badge(false));
          StrengthPhaseCard middle =
              new StrengthPhaseCard(phases, 1, false, BLACK, WHITE, BUNDLE, STYLE);
          assertEquals(text("phase.even"), middle.verdict());
          assertEquals(text("phase.best"), middle.badge(true));
          assertEquals(text("phase.best"), middle.badge(false));
          StrengthPhaseCard endgame =
              new StrengthPhaseCard(closeEndgame, 2, false, BLACK, WHITE, BUNDLE, STYLE);
          assertEquals(text("phase.slightly", WHITE), endgame.verdict());
        });
  }

  @Test
  void cardExplainsAMissingRank() throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          try {
            StrengthPhases.Phase unplayed =
                new StrengthPhases.Phase(2, 151, 120, side(Stone.BLACK, 5), side(Stone.WHITE, 5));
            assertEquals(
                text("phase.notReached"),
                new StrengthPhaseCard(List.of(unplayed), 0, false, BLACK, WHITE, BUNDLE, STYLE)
                    .verdict());
            StrengthPhases.Phase late =
                new StrengthPhases.Phase(
                    2, 211, 300, side(Stone.BLACK, Double.NaN), side(Stone.WHITE, Double.NaN));
            assertEquals(
                text("phase.humanStyleLimit", 210),
                new StrengthPhaseCard(List.of(late), 0, true, BLACK, WHITE, BUNDLE, STYLE)
                    .verdict());
            assertEquals(
                text("phase.tooFew"),
                new StrengthPhaseCard(List.of(late), 0, false, BLACK, WHITE, BUNDLE, STYLE)
                    .verdict());
          } catch (Exception error) {
            throw new AssertionError(error);
          }
        });
  }

  @Test
  void viewRecomputesThePhasesWhileADividerMoves() throws Exception {
    PlayerStrengthEstimator.Report report = StrengthFixtures.readyReport();
    int[][] committed = new int[1][];
    SwingUtilities.invokeAndWait(
        () -> {
          StrengthPhaseView view =
              new StrengthPhaseView(
                  report,
                  StrengthPhases.DEFAULT_BOUNDS,
                  200,
                  BLACK,
                  WHITE,
                  BUNDLE,
                  STYLE,
                  bounds -> committed[0] = bounds);
          assertEquals(60, view.card(0).phase().last());
          view.timeline().moveDivider(0, 100);
          assertEquals(100, view.card(0).phase().last(), "cards follow the drag");
          assertNull(committed[0], "nothing is saved until the drag ends");
          assertTrue(view.getComponentCount() > 0);
        });
  }
}
