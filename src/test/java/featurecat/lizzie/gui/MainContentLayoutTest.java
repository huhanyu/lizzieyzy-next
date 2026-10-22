package featurecat.lizzie.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class MainContentLayoutTest {
  @Test
  void theCoachPanelTakesTheRightEdgeBetweenTheTopAndTheToolbar() {
    MainContentLayout layout = MainContentLayout.of(1600, 900, 30, 40, true, 34, 360);

    assertEquals(1240, layout.mainPanel.width);
    assertEquals(1240, layout.coachPanel.x);
    assertEquals(360, layout.coachPanel.width);
    assertEquals(layout.mainPanel.y, layout.coachPanel.y);
    assertEquals(layout.mainPanel.height, layout.coachPanel.height);
    assertEquals(900 - 34, layout.coachPanel.y + layout.coachPanel.height);
    assertEquals(1600, layout.toolbar.width);
  }

  @Test
  void withoutAGameTheBoardKeepsTheWholeWidth() {
    MainContentLayout layout = MainContentLayout.of(1600, 900, 30, 40, true, 34, 0);

    assertEquals(1600, layout.mainPanel.width);
    assertEquals(0, layout.coachPanel.width);
  }

  @Test
  void theCoachPanelNeverTakesMoreThanHalfOfANarrowWindow() {
    MainContentLayout layout = MainContentLayout.of(600, 700, 0, 0, false, 30, 360);

    assertEquals(300, layout.coachPanel.width);
    assertEquals(300, layout.mainPanel.width);
  }
}
