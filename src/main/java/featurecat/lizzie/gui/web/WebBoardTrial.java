package featurecat.lizzie.gui.web;

import featurecat.lizzie.gui.LizzieFrame;

/**
 * Ends a web viewer's trial board from the desktop. The window's Sync menu and the bottom bar's
 * sync popup offer the same command.
 */
public final class WebBoardTrial {
  private WebBoardTrial() {}

  /** Whether a trial can be ended now: the web board is running and the window shows a trial. */
  public static boolean canExit(WebBoardManager manager, LizzieFrame frame) {
    return manager != null && manager.isRunning() && frame != null && frame.isTrialActive();
  }

  /** Returns the window to the game and tells web viewers that the trial ended. */
  public static void exit(WebBoardManager manager) {
    if (manager == null || !manager.isRunning()) return;
    manager.forceExitTrial();
    WebBoardDataCollector collector = manager.getCollector();
    if (collector != null) collector.broadcastTrialState(null);
  }
}
