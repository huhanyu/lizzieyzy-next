package featurecat.lizzie.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class BrowserFrameYikeSyncControlTest {
  @Test
  void yikeBridgeMessagesAreAcceptedOnlyFromTheMainYikeFrame() {
    String room = "https://home.yikeweiqi.com/#/live/new-room/123456";
    assertTrue(BrowserFrame.isTrustedYikeQuerySource(true, true, room));
    assertTrue(BrowserFrame.isTrustedYikeQuerySource(true, true, "http://yikeweiqi.com/#/game"));
    assertTrue(
        BrowserFrame.isTrustedYikeQuerySource(true, true, "https://WWW.YikeWeiqi.com/#/unite/9"));

    assertFalse(BrowserFrame.isTrustedYikeQuerySource(true, false, room), "embedded frame");
    assertFalse(BrowserFrame.isTrustedYikeQuerySource(false, true, room), "not a Yike window");
    assertFalse(
        BrowserFrame.isTrustedYikeQuerySource(true, true, "https://yikeweiqi.com.evil.example/"));
    assertFalse(
        BrowserFrame.isTrustedYikeQuerySource(true, true, "https://evilyikeweiqi.com/#/live"));
    assertFalse(
        BrowserFrame.isTrustedYikeQuerySource(
            true, true, "https://evil.example/?next=https://home.yikeweiqi.com/"));
    assertFalse(BrowserFrame.isTrustedYikeQuerySource(true, true, "file:///tmp/yikeweiqi.com"));
    assertFalse(BrowserFrame.isTrustedYikeQuerySource(true, true, "chrome-error://chromewebdata/"));
    assertFalse(BrowserFrame.isTrustedYikeQuerySource(true, true, ""));
    assertFalse(BrowserFrame.isTrustedYikeQuerySource(true, true, null));
  }

  @Test
  void onlyYikeBridgeRequestsAreSubjectToTheFrameCheck() {
    assertTrue(BrowserFrame.isYikeQuery("yikeUnite:{}"));
    assertTrue(BrowserFrame.isYikeQuery("yikeGeometry:{}"));
    assertFalse(BrowserFrame.isYikeQuery("somethingElse:{}"));
    assertFalse(BrowserFrame.isYikeQuery(null));
  }

  @Test
  void yikeAutoSyncRequiresYikeBrowserSyncEnabled() {
    assertFalse(BrowserFrame.shouldAutoStartYikeSync(false, true));
    assertFalse(BrowserFrame.shouldAutoStartYikeSync(true, false));
    assertTrue(BrowserFrame.shouldAutoStartYikeSync(true, true));
  }

  @Test
  void yikePlatformSignalRequiresRecognizedRoomUrl() {
    assertFalse(
        BrowserFrame.shouldSyncYikeCurrentAddressFromPlatformSignal(
            true, false, "https://home.yikeweiqi.com/#/live", ""));
    assertFalse(
        BrowserFrame.shouldSyncYikeCurrentAddressFromPlatformSignal(
            true, false, "https://home.yikeweiqi.com/#/game", ""));
    assertTrue(
        BrowserFrame.shouldSyncYikeCurrentAddressFromPlatformSignal(
            true, false, "https://home.yikeweiqi.com/#/unite/66304678", ""));
  }

  @Test
  void yikePlatformSignalOnWaitingPageKeepsListenerButDoesNotStartRoomSync() {
    assertTrue(
        BrowserFrame.shouldKeepYikeListenerEnabledOnPage(
            true, "https://home.yikeweiqi.com/#/live"));
    assertTrue(
        BrowserFrame.shouldKeepYikeListenerEnabledOnPage(
            true, "https://home.yikeweiqi.com/#/game"));
    assertFalse(
        BrowserFrame.shouldCreateOrRefreshYikeRoomSession(
            true, true, "https://home.yikeweiqi.com/#/live", ""));
  }

  @Test
  void firstLiveRoomEntryCanBootstrapWithoutPriorUniteSession() {
    assertTrue(
        BrowserFrame.shouldCreateOrRefreshYikeRoomSession(
            true, true, "https://home.yikeweiqi.com/#/live/new-room/186538/0/0", ""));
  }

  @Test
  void sameRoomSignalDoesNotRebuildSession() {
    String url = "https://home.yikeweiqi.com/#/unite/66304678";

    assertFalse(BrowserFrame.shouldCreateOrRefreshYikeRoomSession(true, true, url, url));
  }

  @Test
  void yikePlatformSignalIsIdempotentForSameRecognizedRoom() {
    assertFalse(
        BrowserFrame.shouldSyncYikeCurrentAddressFromPlatformSignal(
            true,
            true,
            "https://home.yikeweiqi.com/#/unite/66304678",
            "https://home.yikeweiqi.com/#/unite/66304678"));
    assertTrue(
        BrowserFrame.shouldSyncYikeCurrentAddressFromPlatformSignal(
            true,
            true,
            "https://home.yikeweiqi.com/#/live/new-room/186530/0/0",
            "https://home.yikeweiqi.com/#/unite/66304678"));
  }

  @Test
  void explicitReadboardStartSignalKeepsSameRoomIdempotent() {
    String sameRoomUrl = "https://home.yikeweiqi.com/#/unite/66304678";
    assertFalse(
        BrowserFrame.shouldStartYikeSyncFromReadboardSignal(true, true, sameRoomUrl, sameRoomUrl));
    assertTrue(
        BrowserFrame.shouldStartYikeSyncFromReadboardSignal(true, false, sameRoomUrl, sameRoomUrl));
  }

  @Test
  void yikeAutoSyncRequiresRecognizedRoomUrl() {
    assertFalse(BrowserFrame.shouldAutoStartYikeSyncForAddress(true, true, ""));
    assertFalse(
        BrowserFrame.shouldAutoStartYikeSyncForAddress(
            true, true, "https://home.yikeweiqi.com/#/live"));
    assertTrue(
        BrowserFrame.shouldAutoStartYikeSyncForAddress(
            true, true, "https://home.yikeweiqi.com/#/live/new-room/186530/0/0"));
    assertTrue(
        BrowserFrame.shouldAutoStartYikeSyncForAddress(
            true, true, "https://home.yikeweiqi.com/#/game/play/1/15630642"));
  }

  @Test
  void selectsFreshObservedLiveRoomForSameSessionBeforeStaleBrowserUrl() {
    assertEquals(
        "https://home.yikeweiqi.com/#/live/new-room/186602/0/0",
        BrowserFrame.selectYikeCurrentAddress(
            "https://home.yikeweiqi.com/#/live/new-room/186602/0/0",
            "https://home.yikeweiqi.com/#/live/room/186602/0/0",
            "https://home.yikeweiqi.com/#/live/room/186602/0/0"));
  }

  @Test
  void staleObservedRoomDoesNotOverrideCurrentBrowserAddress() {
    assertEquals(
        "https://home.yikeweiqi.com/#/unite/66372511",
        BrowserFrame.selectYikeCurrentAddress(
            "https://home.yikeweiqi.com/#/live/new-room/186602/0/0",
            "https://home.yikeweiqi.com/#/unite/66372511",
            "https://home.yikeweiqi.com/#/unite/66372511",
            false));
    assertEquals(
        "https://home.yikeweiqi.com/#/game",
        BrowserFrame.selectYikeCurrentAddress(
            "https://home.yikeweiqi.com/#/live/new-room/186602/0/0",
            "https://home.yikeweiqi.com/#/game",
            "https://home.yikeweiqi.com/#/game",
            false));
  }

  @Test
  void freshObservedRoomDoesNotOverrideDifferentCurrentBoardSession() {
    assertEquals(
        "https://home.yikeweiqi.com/#/unite/66372511",
        BrowserFrame.selectYikeCurrentAddress(
            "https://home.yikeweiqi.com/#/live/new-room/186602/0/0",
            "https://home.yikeweiqi.com/#/unite/66372511",
            "https://home.yikeweiqi.com/#/unite/66372511",
            true));
    assertEquals(
        "https://home.yikeweiqi.com/#/game/play/1/15630642",
        BrowserFrame.selectYikeCurrentAddress(
            "https://home.yikeweiqi.com/#/live/new-room/186602/0/0",
            "https://home.yikeweiqi.com/#/game/play/1/15630642",
            "https://home.yikeweiqi.com/#/game/play/1/15630642",
            true));
  }

  @Test
  void waitingPageDoesNotAcceptObservedRoomUrl() {
    assertFalse(
        BrowserFrame.shouldAcceptYikeObservedPageUrl(
            "https://home.yikeweiqi.com/#/live/new-room/186602/0/0",
            "https://home.yikeweiqi.com/#/live",
            "https://home.yikeweiqi.com/#/live"));
    assertFalse(
        BrowserFrame.shouldAcceptYikeObservedPageUrl(
            "https://home.yikeweiqi.com/#/live/new-room/186602/0/0",
            "https://home.yikeweiqi.com/#/game",
            "https://home.yikeweiqi.com/#/game"));
  }

  @Test
  void observedRoomUrlRequiresRecognizedSessionKey() {
    assertFalse(
        BrowserFrame.shouldAcceptYikeObservedPageUrl(
            "https://home.yikeweiqi.com/#/live",
            "https://home.yikeweiqi.com/#/unite/66372511",
            "https://home.yikeweiqi.com/#/unite/66372511"));
    assertTrue(
        BrowserFrame.shouldAcceptYikeObservedPageUrl(
            "https://home.yikeweiqi.com/#/live/new-room/186602/0/0", "", ""));
    assertTrue(
        BrowserFrame.shouldAcceptYikeObservedPageUrl(
            "https://home.yikeweiqi.com/#/live/new-room/186602/0/0",
            "https://home.yikeweiqi.com/#/live/room/186602/0/0",
            "https://home.yikeweiqi.com/#/live/room/186602/0/0"));
  }

  @Test
  void observedYikePageUrlExpiresAfterThirtySeconds() {
    assertFalse(BrowserFrame.isYikeObservedPageUrlFresh(2000L, 0L));
    assertFalse(BrowserFrame.isYikeObservedPageUrlFresh(2000L, 3000L));
    assertTrue(BrowserFrame.isYikeObservedPageUrlFresh(31_000L, 1000L));
    assertFalse(BrowserFrame.isYikeObservedPageUrlFresh(31_001L, 1000L));
  }

  @Test
  void fallsBackToBrowserOrAddressWhenObservedIsNotRoom() {
    assertEquals(
        "https://home.yikeweiqi.com/#/unite/66372511",
        BrowserFrame.selectYikeCurrentAddress(
            "https://home.yikeweiqi.com/#/live",
            "https://home.yikeweiqi.com/#/unite/66372511",
            "https://home.yikeweiqi.com/#/game"));
    assertEquals(
        "https://home.yikeweiqi.com/#/unite/66372511",
        BrowserFrame.selectYikeCurrentAddress(
            "",
            "https://home.yikeweiqi.com/#/game",
            "https://home.yikeweiqi.com/#/unite/66372511"));
  }
}
