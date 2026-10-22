package featurecat.lizzie.gui;

import featurecat.lizzie.Config;

/**
 * While an AI Coach game runs, the board shares the window only with the coach panel. The side
 * panels (comment, variation tree, candidate list, sub-board, captures and status) are hidden
 * without touching the saved settings, and come back as they were when the game ends, the way the
 * temporary game panel does it.
 */
final class CoachFocusLayout {
  private boolean active;
  private boolean subBoard;
  private boolean captured;
  private boolean variationGraph;
  private boolean comment;
  private boolean status;
  private boolean listPane;

  boolean isActive() {
    return active;
  }

  void enter(LizzieFrame frame, Config config) {
    if (active || frame == null || config == null) {
      return;
    }
    active = true;
    subBoard = config.showSubBoard;
    captured = config.showCaptured;
    variationGraph = config.showVariationGraph;
    comment = config.showComment;
    status = config.showStatus;
    listPane = config.showListPane;
    config.showSubBoard = false;
    config.showCaptured = false;
    config.showVariationGraph = false;
    config.showComment = false;
    config.showStatus = false;
    config.showListPane = false;
    frame.setHideListScrollpane(false);
    frame.setVarTreeVisible(false);
    frame.hideCommentPanel();
    frame.refreshContainer();
    frame.refresh();
  }

  void exit(LizzieFrame frame, Config config) {
    if (!active || frame == null || config == null) {
      return;
    }
    active = false;
    config.showSubBoard = subBoard;
    config.showCaptured = captured;
    config.showVariationGraph = variationGraph;
    config.showComment = comment;
    config.showStatus = status;
    config.showListPane = listPane;
    if (config.showListPane()) {
      frame.setHideListScrollpane(true);
    }
    frame.setVarTreeVisible(config.showVariationGraph);
    if (config.showComment) {
      frame.setCommentPaneContent();
    }
    frame.refreshContainer();
    frame.refresh();
  }
}
