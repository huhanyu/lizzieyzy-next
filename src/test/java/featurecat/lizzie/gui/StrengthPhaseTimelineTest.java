package featurecat.lizzie.gui;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import featurecat.lizzie.AppLocale;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;
import javax.swing.JComponent;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class StrengthPhaseTimelineTest {
  private static final ResourceBundle BUNDLE = AppLocale.SIMPLIFIED_CHINESE.loadBundle();
  private static final StrengthStyle STYLE = new StrengthStyle(false, false, "Dialog", 12);

  private final List<int[]> changes = new ArrayList<>();
  private final List<int[]> commits = new ArrayList<>();

  private StrengthPhaseTimeline timeline(int[] bounds, int lastMove) {
    StrengthPhaseTimeline timeline =
        new StrengthPhaseTimeline(bounds, lastMove, BUNDLE, STYLE, changes::add, commits::add);
    timeline.setSize(STYLE.px(900), timeline.getPreferredSize().height);
    timeline.doLayout();
    return timeline;
  }

  private static void press(StrengthPhaseTimeline.Divider divider, String key) {
    Object name = divider.getInputMap(JComponent.WHEN_FOCUSED).get(KeyStroke.getKeyStroke(key));
    divider.getActionMap().get(name).actionPerformed(new ActionEvent(divider, 0, key));
  }

  @Test
  void arrowKeysMoveADividerByOneOrTenMoves() throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          StrengthPhaseTimeline timeline = timeline(StrengthPhases.DEFAULT_BOUNDS, 300);
          press(timeline.divider(0), "RIGHT");
          assertArrayEquals(new int[] {61, 150}, timeline.phaseBounds());
          press(timeline.divider(1), "shift LEFT");
          assertArrayEquals(new int[] {61, 140}, timeline.phaseBounds());
          assertEquals(2, commits.size(), "each key press is a finished change");
          assertEquals(2, changes.size());
          assertEquals(
              "\u5e03\u5c40\u4e0e\u4e2d\u76d8\u7684\u5206\u754c\uff1a\u7b2c 61 \u624b",
              timeline.divider(0).getAccessibleContext().getAccessibleName());
        });
  }

  @Test
  void dividersKeepEveryPhaseAtLeastTenMovesLong() throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          StrengthPhaseTimeline timeline = timeline(new int[] {60, 75}, 300);
          assertTrue(timeline.moveDivider(0, 70));
          assertEquals(65, timeline.phaseBounds()[0], "the middle game keeps 10 moves");
          assertFalse(timeline.moveDivider(0, 66), "it cannot get any shorter");
          assertTrue(timeline.moveDivider(0, 3));
          assertEquals(StrengthPhases.MIN_PHASE_MOVES, timeline.phaseBounds()[0]);
          assertTrue(timeline.moveDivider(1, 1000));
          assertEquals(300 - StrengthPhases.MIN_PHASE_MOVES, timeline.phaseBounds()[1]);
        });
  }

  @Test
  void dragFollowsThePointer() throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          StrengthPhaseTimeline timeline = timeline(StrengthPhases.DEFAULT_BOUNDS, 300);
          assertEquals(100, timeline.moveAt(timeline.xOf(100)));
          timeline.moveDivider(1, timeline.moveAt(timeline.xOf(220)));
          assertArrayEquals(new int[] {60, 220}, timeline.phaseBounds());
        });
  }

  @Test
  void resetPutsTheDefaultsBackAndSavesThem() throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          StrengthPhaseTimeline timeline = timeline(new int[] {90, 200}, 300);
          timeline.reset();
          assertArrayEquals(StrengthPhases.DEFAULT_BOUNDS, timeline.phaseBounds());
          assertArrayEquals(StrengthPhases.DEFAULT_BOUNDS, commits.get(0));
        });
  }

  @Test
  void shortGameShowsWhereItEnded() throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          StrengthPhaseTimeline timeline = timeline(StrengthPhases.DEFAULT_BOUNDS, 120);
          assertEquals(160, timeline.axisEnd(), "far enough to show the second divider");
          assertTrue(timeline.bandLabel(1).endsWith("61\u2013120"));
          assertTrue(
              timeline
                  .bandLabel(2)
                  .endsWith(BUNDLE.getString("PlayerStrengthEstimate.phase.unplayed")));
          assertEquals(300, timeline(StrengthPhases.DEFAULT_BOUNDS, 300).axisEnd());
        });
  }
}
