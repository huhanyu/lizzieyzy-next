package featurecat.lizzie.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import featurecat.lizzie.Config;
import featurecat.lizzie.Lizzie;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class CoachPanelTest {
  @Test
  void buttonsNeverTakeKeyboardFocusSoSpaceStaysWithTheBoard() {
    CoachPanel panel = new CoachPanel(null, () -> null);

    for (CoachButton button : panel.buttons()) {
      assertFalse(button.isFocusable(), button.getText());
    }
  }

  @Test
  void actionsOfferTakeBackPassResignAndEndGame() {
    CoachPanel panel = new CoachPanel(null, () -> null);
    List<String> texts = new ArrayList<>();
    for (CoachButton button : panel.buttons()) {
      texts.add(button.getText());
    }

    assertTrue(texts.contains(Lizzie.resourceBundle.getString("HumanSlTraining.takeBack")));
    assertTrue(texts.contains(Lizzie.resourceBundle.getString("HumanSlGame.btn.pass")));
    assertTrue(texts.contains(Lizzie.resourceBundle.getString("HumanSlGame.btn.resign")));
    assertTrue(texts.contains(Lizzie.resourceBundle.getString("CoachPanel.finish")));
  }

  @Test
  void thePanelStaysHiddenUntilAGameAttaches() {
    CoachPanel panel = new CoachPanel(null, () -> null);

    assertFalse(panel.isVisible());
    panel.detach(null);
    assertFalse(panel.isVisible());
  }

  @Test
  void focusLayoutHidesTheSidePanelsAndRestoresThemExactly() throws Exception {
    Config config = allocate(Config.class);
    config.showSubBoard = true;
    config.showCaptured = false;
    config.showVariationGraph = true;
    config.showComment = true;
    config.showStatus = true;
    config.showListPane = false;
    RecordingFrame frame = allocate(RecordingFrame.class);
    frame.calls = new ArrayList<>();
    CoachFocusLayout layout = new CoachFocusLayout();

    layout.enter(frame, config);
    layout.enter(frame, config);

    assertTrue(layout.isActive());
    assertFalse(config.showSubBoard);
    assertFalse(config.showVariationGraph);
    assertFalse(config.showComment);
    assertFalse(config.showStatus);
    assertTrue(frame.calls.contains("varTree false"));
    assertTrue(frame.calls.contains("hideComment"));

    frame.calls.clear();
    layout.exit(frame, config);
    layout.exit(frame, config);

    assertFalse(layout.isActive());
    assertTrue(config.showSubBoard);
    assertFalse(config.showCaptured);
    assertTrue(config.showVariationGraph);
    assertTrue(config.showComment);
    assertTrue(config.showStatus);
    assertFalse(config.showListPane);
    assertEquals(
        List.of("varTree true", "showComment", "refreshContainer", "refresh"), frame.calls);
  }

  @SuppressWarnings("unchecked")
  private static <T> T allocate(Class<T> type) throws Exception {
    java.lang.reflect.Field unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
    unsafeField.setAccessible(true);
    sun.misc.Unsafe unsafe = (sun.misc.Unsafe) unsafeField.get(null);
    return (T) unsafe.allocateInstance(type);
  }

  /** A frame that records what the focus layout asks of it; allocated without a constructor. */
  static class RecordingFrame extends LizzieFrame {
    List<String> calls;

    @Override
    public void setHideListScrollpane(boolean visible) {
      calls.add("list " + visible);
    }

    @Override
    public void setVarTreeVisible(boolean visible) {
      calls.add("varTree " + visible);
    }

    @Override
    public void hideCommentPanel() {
      calls.add("hideComment");
    }

    @Override
    public void setCommentPaneContent() {
      calls.add("showComment");
    }

    @Override
    public void refreshContainer() {
      calls.add("refreshContainer");
    }

    @Override
    public void refresh() {
      calls.add("refresh");
    }
  }
}
