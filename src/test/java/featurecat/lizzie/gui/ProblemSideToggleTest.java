package featurecat.lizzie.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import featurecat.lizzie.AppLocale;
import featurecat.lizzie.Lizzie;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;
import javax.swing.JComponent;
import javax.swing.JToggleButton;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ProblemSideToggleTest {
  private ResourceBundle previousBundle;

  @BeforeEach
  void useChineseLabels() {
    previousBundle = Lizzie.resourceBundle;
    Lizzie.resourceBundle = AppLocale.SIMPLIFIED_CHINESE.loadBundle();
  }

  @AfterEach
  void restoreBundle() {
    Lizzie.resourceBundle = previousBundle;
  }

  @Test
  void clickingAHalfChoosesThatSideButRefreshingDoesNot() throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          List<ProblemListSideFilter> chosen = new ArrayList<>();
          ProblemSideToggle toggle = new ProblemSideToggle(chosen::add);
          toggle.update(ProblemListSideFilter.BLACK, 15, 12);
          toggle.update(ProblemListSideFilter.WHITE, 15, 12);
          assertTrue(chosen.isEmpty(), "showing a snapshot must not switch the filter again");
          assertEquals(ProblemListSideFilter.WHITE, toggle.selectedSide());

          toggle.half(ProblemListSideFilter.BLACK).doClick(0);
          assertEquals(List.of(ProblemListSideFilter.BLACK), chosen);
          assertEquals(ProblemListSideFilter.BLACK, toggle.selectedSide());
        });
  }

  @Test
  void arrowKeysMoveToTheOtherSide() throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          List<ProblemListSideFilter> chosen = new ArrayList<>();
          ProblemSideToggle toggle = new ProblemSideToggle(chosen::add);
          toggle.update(ProblemListSideFilter.BLACK, 3, 4);
          JToggleButton black = toggle.half(ProblemListSideFilter.BLACK);
          Object action =
              black.getInputMap(JComponent.WHEN_FOCUSED).get(KeyStroke.getKeyStroke("RIGHT"));
          black.getActionMap().get(action).actionPerformed(new ActionEvent(black, 0, "RIGHT"));
          assertEquals(List.of(ProblemListSideFilter.WHITE), chosen);
          assertEquals(ProblemListSideFilter.WHITE, toggle.selectedSide());
        });
  }

  @Test
  void halvesNameTheSideAndDescribeTheCount() throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          ProblemSideToggle toggle = new ProblemSideToggle(side -> {});
          toggle.update(ProblemListSideFilter.BLACK, 15, 1);
          JToggleButton black = toggle.half(ProblemListSideFilter.BLACK);
          JToggleButton white = toggle.half(ProblemListSideFilter.WHITE);
          assertEquals("黑棋", black.getAccessibleContext().getAccessibleName());
          assertEquals("15 手", black.getAccessibleContext().getAccessibleDescription());
          assertEquals("白棋", white.getAccessibleContext().getAccessibleName());
          assertEquals("1 手", white.getAccessibleContext().getAccessibleDescription());
        });
  }

  @Test
  void narrowSidebarStacksTheHalvesInsteadOfOverlappingThem() throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          ProblemSideToggle toggle = new ProblemSideToggle(side -> {});
          toggle.setFont(new Font(Font.DIALOG, Font.PLAIN, 20));
          toggle.update(ProblemListSideFilter.BLACK, 357, 299);
          int wide = toggle.getPreferredSize().width;
          int narrow = wide - 20;
          assertEquals(wide, toggle.sizeFor(wide).width);
          toggle.setSize(toggle.sizeFor(narrow));
          toggle.doLayout();
          JToggleButton black = toggle.half(ProblemListSideFilter.BLACK);
          JToggleButton white = toggle.half(ProblemListSideFilter.WHITE);
          assertTrue(toggle.getWidth() <= narrow);
          assertFalse(black.getBounds().intersects(white.getBounds()));
          assertTrue(white.getY() >= black.getY() + black.getHeight());
          assertTrue(white.getY() + white.getHeight() <= toggle.getHeight());
        });
  }
}
