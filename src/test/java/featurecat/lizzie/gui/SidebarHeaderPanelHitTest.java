package featurecat.lizzie.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import featurecat.lizzie.AppLocale;
import featurecat.lizzie.Lizzie;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.Collections;
import java.util.ResourceBundle;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SidebarHeaderPanelHitTest {
  private ResourceBundle previousBundle;

  @BeforeEach
  void useStableSimplifiedChineseLabels() {
    previousBundle = Lizzie.resourceBundle;
    Lizzie.resourceBundle = AppLocale.SIMPLIFIED_CHINESE.loadBundle();
  }

  @AfterEach
  void restoreApplicationBundle() {
    Lizzie.resourceBundle = previousBundle;
  }

  @Test
  void classicProblemTabUsesTheWholeVisibleLabel() {
    FontMetrics metrics = headerMetrics();

    assertEquals(0, SidebarHeaderPanel.primarySegmentIndexAt(new Point(18, 25), false, metrics));
    assertEquals(
        1,
        SidebarHeaderPanel.primarySegmentIndexAt(new Point(58, 25), false, metrics),
        "clicking the first character of 问题手 should switch to the problem list.");
    assertEquals(
        1,
        SidebarHeaderPanel.primarySegmentIndexAt(new Point(72, 25), false, metrics),
        "the old midpoint split cut through 问题手 and sent this click to 评论.");
    assertEquals(1, SidebarHeaderPanel.primarySegmentIndexAt(new Point(90, 25), false, metrics));
    assertEquals(
        1,
        SidebarHeaderPanel.primarySegmentIndexAt(new Point(58, 39), false, metrics),
        "the whole classic tab row should be clickable, not only the glyph pixels.");
    assertEquals(
        1,
        SidebarHeaderPanel.primarySegmentIndexAt(new Point(102, 25), false, metrics),
        "keep a forgiving right side for 问题手 without covering the inline filters.");
  }

  @Test
  void classicSeparatorGapDoesNotSwitchTabs() {
    FontMetrics metrics = headerMetrics();

    assertEquals(-1, SidebarHeaderPanel.primarySegmentIndexAt(new Point(45, 25), false, metrics));
  }

  @Test
  void appleSegmentedControlStillUsesFullHalves() {
    FontMetrics metrics = headerMetrics();

    assertEquals(0, SidebarHeaderPanel.primarySegmentIndexAt(new Point(20, 24), true, metrics));
    assertEquals(1, SidebarHeaderPanel.primarySegmentIndexAt(new Point(96, 24), true, metrics));
    assertEquals(-1, SidebarHeaderPanel.primarySegmentIndexAt(new Point(150, 24), true, metrics));
  }

  @Test
  void headerIsOneRowInBothViews() {
    FontMetrics metrics = headerMetrics();
    assertEquals(48, SidebarHeaderPanel.preferredHeight(false));
    assertEquals(56, SidebarHeaderPanel.preferredHeight(true));
    for (int width : new int[] {NARROW_SIDEBAR, WIDE_SIDEBAR}) {
      assertEquals(48, SidebarHeaderPanel.headerLayout(false, metrics, width, "").height);
      assertEquals(56, SidebarHeaderPanel.headerLayout(true, metrics, width, "").height);
    }
  }

  @Test
  void classicEnglishCommentsAndProblemsDoNotOverlap() {
    useEnglishLabels();
    FontMetrics metrics = headerMetrics();
    SidebarHeaderPanel.HeaderLayout layout =
        SidebarHeaderPanel.headerLayout(false, metrics, WIDE_SIDEBAR, "");

    assertFalse(
        layout.comments.intersects(layout.problems),
        "Comments and Problem moves must not share pixels.");
    assertEquals(
        0,
        SidebarHeaderPanel.primarySegmentIndexAt(
            center(layout.comments), false, metrics, WIDE_SIDEBAR));
    assertEquals(
        1,
        SidebarHeaderPanel.primarySegmentIndexAt(
            center(layout.problems), false, metrics, WIDE_SIDEBAR));
  }

  @Test
  void appleEnglishSegmentedControlFitsProblemMoves() {
    useEnglishLabels();
    FontMetrics metrics = headerMetrics();
    SidebarHeaderPanel.HeaderLayout layout =
        SidebarHeaderPanel.headerLayout(true, metrics, WIDE_SIDEBAR, "");
    int problemsWidth =
        metrics.stringWidth(Lizzie.resourceBundle.getString("SidebarHeader.problems"));

    assertTrue(
        layout.problems.width >= problemsWidth,
        "Apple half-width must fit Problem moves, not clip it into a 66px cell.");
    assertFalse(layout.comments.intersects(layout.problems));
    assertEquals(
        0,
        SidebarHeaderPanel.primarySegmentIndexAt(
            center(layout.comments), true, metrics, WIDE_SIDEBAR));
    assertEquals(
        1,
        SidebarHeaderPanel.primarySegmentIndexAt(
            center(layout.problems), true, metrics, WIDE_SIDEBAR));
  }

  @Test
  void statusStaysClearOfTheTabsInBothStyles() {
    useEnglishLabels();
    FontMetrics metrics = headerMetrics();
    String progress = SidebarHeaderPanel.progressLabelFor(snapshot(229, 229, false));
    for (boolean apple : new boolean[] {false, true}) {
      SidebarHeaderPanel.HeaderLayout layout =
          SidebarHeaderPanel.headerLayout(apple, metrics, WIDE_SIDEBAR, progress);

      assertFalse(layout.progress.isEmpty());
      assertFalse(layout.progress.intersects(layout.comments));
      assertFalse(layout.progress.intersects(layout.problems));
      assertTrue(layout.progress.x + layout.progress.width <= WIDE_SIDEBAR);
      assertEquals(
          1,
          SidebarHeaderPanel.primarySegmentIndexAt(
              center(layout.problems), apple, metrics, WIDE_SIDEBAR, progress));
    }
  }

  @Test
  void statusIsHiddenRatherThanDrawnOverTheTabs() {
    useEnglishLabels();
    FontMetrics metrics = headerMetrics();
    String progress = SidebarHeaderPanel.progressLabelFor(snapshot(228, 229, true));
    for (boolean apple : new boolean[] {false, true}) {
      assertTrue(
          SidebarHeaderPanel.headerLayout(apple, metrics, NARROW_SIDEBAR, progress)
              .progress
              .isEmpty());
    }
  }

  @Test
  void progressLabelExplainsTheRightSideCounter() {
    assertEquals("", SidebarHeaderPanel.progressLabelFor(snapshot(0, 0, false)));
    assertEquals("评估中 228/229", SidebarHeaderPanel.progressLabelFor(snapshot(228, 229, true)));
    assertEquals("已评估 228/229", SidebarHeaderPanel.progressLabelFor(snapshot(228, 229, false)));
    assertEquals("评估完成", SidebarHeaderPanel.progressLabelFor(snapshot(229, 229, false)));
    assertEquals(
        "问题手评估进度：已评估 228/229。", SidebarHeaderPanel.progressTooltipFor(snapshot(228, 229, false)));
  }

  private static FontMetrics headerMetrics() {
    BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
    Graphics2D graphics = image.createGraphics();
    try {
      graphics.setFont(new Font("Dialog", Font.BOLD, 12));
      return graphics.getFontMetrics();
    } finally {
      graphics.dispose();
    }
  }

  private static final int WIDE_SIDEBAR = 500;
  private static final int NARROW_SIDEBAR = 200;

  private static void useEnglishLabels() {
    Lizzie.resourceBundle = AppLocale.ENGLISH.loadBundle();
  }

  private static Point center(Rectangle bounds) {
    return new Point(bounds.x + bounds.width / 2, bounds.y + bounds.height / 2);
  }

  private static ProblemListSnapshot snapshot(int analyzedMoves, int totalMoves, boolean running) {
    return new ProblemListSnapshot(
        ProblemListMetric.WINRATE_LOSS,
        Collections.emptyList(),
        Collections.emptyList(),
        analyzedMoves,
        totalMoves,
        running);
  }
}
