package featurecat.lizzie.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import featurecat.lizzie.analysis.Leelaz.MoveFocusCapability;
import org.junit.jupiter.api.Test;

class RightClickMenuCapabilityTest {
  @Test
  void onlyConfirmedUnsupportedEnginesShowUpgradeGuidance() {
    for (boolean paused : new boolean[] {false, true}) {
      assertEquals(
          "RightClickMenu.trackPoint.upgradeRequired",
          RightClickMenu.trackingUnavailableKey(MoveFocusCapability.UNSUPPORTED, paused, false));
      assertEquals(
          "RightClickMenu.trackPoint.upgradeTooltip",
          RightClickMenu.trackingUnavailableKey(MoveFocusCapability.UNSUPPORTED, paused, true));
    }
    for (MoveFocusCapability capability : MoveFocusCapability.values()) {
      if (capability == MoveFocusCapability.UNSUPPORTED) continue;
      assertEquals(
          "RightClickMenu.trackPoint.requiresCapability",
          RightClickMenu.trackingUnavailableKey(capability, false, false));
      assertEquals(
          "RightClickMenu.trackPoint.unavailableTooltip",
          RightClickMenu.trackingUnavailableKey(capability, false, true));
    }
  }

  @Test
  void aSupportedEngineThatIsNotAnalysingAsksToResumeAnalysis() {
    assertEquals(
        "RightClickMenu.trackPoint.paused",
        RightClickMenu.trackingUnavailableKey(MoveFocusCapability.SUPPORTED, true, false));
    assertEquals(
        "RightClickMenu.trackPoint.pausedTooltip",
        RightClickMenu.trackingUnavailableKey(MoveFocusCapability.SUPPORTED, true, true));
    // Resuming would not help an engine whose support is still unknown.
    assertEquals(
        "RightClickMenu.trackPoint.requiresCapability",
        RightClickMenu.trackingUnavailableKey(MoveFocusCapability.UNKNOWN, true, false));
  }
}
