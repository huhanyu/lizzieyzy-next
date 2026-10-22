package featurecat.lizzie.gui;

import java.awt.Rectangle;

/** Where the board area, the bottom toolbar and the AI Coach panel go in the window's content. */
final class MainContentLayout {
  final Rectangle mainPanel;
  final Rectangle toolbar;
  final Rectangle coachPanel;

  private MainContentLayout(Rectangle mainPanel, Rectangle toolbar, Rectangle coachPanel) {
    this.mainPanel = mainPanel;
    this.toolbar = toolbar;
    this.coachPanel = coachPanel;
  }

  /**
   * @param coachPanelWidth width of the AI Coach panel at the right edge, 0 while no coaching game
   *     runs. The board area gives up that width, at most half the window, and the panel spans the
   *     board area's height.
   */
  static MainContentLayout of(
      int contentWidth,
      int contentHeight,
      int windowMenuHeight,
      int topPanelHeight,
      boolean includeTopPanelInBoard,
      int toolbarHeight,
      int coachPanelWidth) {
    int width = Math.max(0, contentWidth);
    int height = Math.max(0, contentHeight);
    int top =
        Math.max(0, windowMenuHeight) + (includeTopPanelInBoard ? Math.max(0, topPanelHeight) : 0);
    int toolbarH = Math.max(0, toolbarHeight);
    int boardHeight = Math.max(0, height - top - toolbarH);
    int coachWidth = Math.max(0, Math.min(coachPanelWidth, width / 2));
    return new MainContentLayout(
        new Rectangle(0, top, width - coachWidth, boardHeight),
        new Rectangle(0, height - toolbarH, width, toolbarH),
        new Rectangle(width - coachWidth, top, coachWidth, boardHeight));
  }
}
