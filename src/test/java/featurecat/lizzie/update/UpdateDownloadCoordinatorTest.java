package featurecat.lizzie.update;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class UpdateDownloadCoordinatorTest {
  private final UpdateCheckSelection selection =
      UpdateCheckSelection.of(
          UpdateChannel.STABLE, UpdateSource.OFFICIAL_SITE, "next-2026-08-01.1");
  private final UpdateCheckResult offer =
      UpdateCheckResult.offerPackage(new PackageUpdatePlan(null, null, "v", "linux", "x64", "cpu"));

  @Test
  void automaticDownloadIsAppOwnedAndOnlyExplicitReadyActionApplies() {
    Fixture f = new Fixture();
    List<UpdateCheckCoordinator.State> view = new ArrayList<>();
    f.coordinator.subscribe(view::add);
    f.start();
    assertTrue(f.coordinator.state().downloading());
    assertEquals(0, f.reminders);
    assertFalse(f.coordinator.checkManually(selection));
    f.coordinator.openOffer();
    assertEquals(0, f.handoffs);
    f.runner.work();
    assertEquals(0, f.applies);
    f.runner.deliver();
    assertTrue(f.coordinator.state().ready());
    assertEquals(1, f.reminders);
    assertEquals(0, f.applies);
    f.coordinator.captureStartupPolicy(true, true, selection);
    f.coordinator.applicationReady();
    assertEquals(1, f.prepares);
    f.coordinator.openOffer();
    assertEquals(1, f.handoffs);
    assertEquals(0, f.applies, "Viewing a ready offer must not install it");
    f.coordinator.startDownload(offer, true);
    assertEquals(1, f.prepares, "Explicit confirmation must reuse prepared files");
    assertEquals(1, f.applies);
    assertEquals(UpdateCheckCoordinator.Origin.AUTOMATIC, f.coordinator.state().origin());
  }

  @Test
  void disableDownloadDuringQueuedDiscoveryKeepsCheckOnlyOffer() {
    Fixture f = new Fixture();
    f.coordinator.captureStartupPolicy(true, true, selection);
    f.coordinator.applicationReady();
    f.runner.work();
    f.coordinator.disableAutomaticDownload();
    f.runner.deliver();
    assertTrue(f.coordinator.state().hasOffer());
    assertFalse(f.coordinator.state().downloading());
    assertEquals(1, f.reminders);
    assertEquals(0, f.prepares);
  }

  @Test
  void invalidatedDiscoveryCannotQueueAnAutomaticDownload() {
    Fixture f = new Fixture();
    f.coordinator.captureStartupPolicy(true, true, selection);
    f.coordinator.applicationReady();
    f.runner.work();
    f.coordinator.invalidateAutomatic();
    f.runner.deliver();
    assertNull(f.coordinator.state().result());
    assertTrue(f.runner.background.isEmpty());
    assertEquals(0, f.prepares);
    assertEquals(0, f.reminders);
  }

  @Test
  void lateSuccessAndFailureCannotRestoreInvalidatedAutomaticTarget() {
    for (boolean fail : List.of(false, true)) {
      Fixture f = new Fixture();
      f.fail = fail;
      f.start();
      f.runner.work();
      f.coordinator.invalidateAutomatic();
      assertNull(f.coordinator.state().result());
      assertFalse(f.coordinator.checkManually(selection));
      f.runner.deliver();
      assertNull(f.coordinator.state().result());
      f.coordinator.openOffer();
      assertEquals(0, f.applies);
      assertEquals(0, f.reminders);
      assertTrue(f.coordinator.checkManually(selection));
    }
  }

  @Test
  void cancellationFencesQueuedSuccessAndRetryWaitsForPhysicalWriter() {
    Fixture f = new Fixture();
    f.start();
    f.runner.work();
    f.coordinator.cancelDownload();
    f.coordinator.startDownload(offer, false);
    assertEquals(1, f.prepares);
    f.runner.deliver();
    assertEquals(
        UpdateCheckCoordinator.DownloadPhase.CANCELLED, f.coordinator.state().download().phase());
    assertEquals(0, f.reminders);
    assertEquals(0, f.applies);
    f.coordinator.startDownload(offer, false);
    f.runner.work();
    f.runner.deliver();
    assertTrue(f.coordinator.state().ready());
    assertEquals(2, f.prepares);
  }

  @Test
  void manualImmediateDownloadRetainsAuthorityAcrossAutomaticPolicyChanges() {
    Fixture f = new Fixture();
    f.coordinator.checkManually(selection);
    f.runner.work();
    f.runner.deliver();
    assertEquals(1, f.handoffs);
    f.coordinator.startDownload(offer, true);
    f.coordinator.invalidateAutomatic();
    f.coordinator.disableAutomaticDownload();
    f.runner.work();
    f.runner.deliver();
    assertEquals(1, f.applies);
    assertEquals(0, f.reminders);
  }

  @Test
  void disablingDownloadFencesQueuedSuccessAndFailureWithoutCancellingManualConsent() {
    for (boolean fail : List.of(false, true)) {
      Fixture automatic = new Fixture();
      automatic.fail = fail;
      automatic.start();
      automatic.runner.work();
      automatic.coordinator.disableAutomaticDownload();
      automatic.runner.deliver();
      assertEquals(
          UpdateCheckCoordinator.DownloadPhase.CANCELLED,
          automatic.coordinator.state().download().phase());
      assertNull(automatic.coordinator.state().download().error());
      assertEquals(0, automatic.reminders);
      assertEquals(0, automatic.applies);
    }
    Fixture explicit = new Fixture();
    explicit.coordinator.captureStartupPolicy(true, false, selection);
    explicit.coordinator.applicationReady();
    explicit.runner.work();
    explicit.runner.deliver();
    explicit.coordinator.startDownload(offer, true);
    explicit.coordinator.invalidateAutomatic();
    explicit.coordinator.disableAutomaticDownload();
    explicit.runner.work();
    explicit.runner.deliver();
    assertEquals(1, explicit.applies);
  }

  @Test
  void corruptDownloadIsRetryableAndLaunchFailurePreservesReady() {
    Fixture f = new Fixture();
    f.fail = true;
    f.start();
    f.runner.work();
    f.runner.deliver();
    assertEquals(
        UpdateCheckCoordinator.DownloadPhase.FAILED, f.coordinator.state().download().phase());
    assertEquals(0, f.applies);
    assertEquals(0, f.reminders);
    f.fail = false;
    f.coordinator.startDownload(offer, false);
    f.runner.work();
    f.runner.deliver();
    f.failApply = true;
    f.coordinator.startDownload(offer, true);
    assertTrue(f.coordinator.state().ready());
    assertNotNull(f.coordinator.state().download().error());
    f.failApply = false;
    f.coordinator.startDownload(offer, true);
    assertEquals(2, f.applies);
  }

  @Test
  void earlyDownloadDisableCannotBeRestoredBeforeReadiness() {
    Fixture f = new Fixture();
    f.coordinator.captureStartupPolicy(true, true, selection);
    f.coordinator.disableAutomaticDownload();
    f.coordinator.captureStartupPolicy(true, true, selection);
    assertFalse(f.coordinator.busy());
    f.coordinator.applicationReady();
    f.runner.work();
    f.runner.deliver();
    assertTrue(f.coordinator.state().hasOffer());
    assertEquals(1, f.reminders);
    assertEquals(0, f.prepares);
    assertEquals(0, f.applies);
    assertTrue(f.runner.background.isEmpty());
  }

  @Test
  void enablingDownloadBeforeReadinessCannotExpandCheckOnlySnapshot() {
    Fixture f = new Fixture();
    f.coordinator.captureStartupPolicy(true, false, selection);
    f.coordinator.captureStartupPolicy(true, true, selection);
    f.coordinator.applicationReady();
    f.runner.work();
    f.runner.deliver();
    assertEquals(1, f.reminders);
    assertEquals(0, f.prepares);
    assertEquals(0, f.applies);
  }

  @Test
  void pausedAutomaticWorkStillCancelsAndFencesLateCompletion() {
    for (boolean invalidate : List.of(false, true)) {
      Fixture f = new Fixture();
      f.start();
      f.coordinator.togglePause();
      assertTrue(f.coordinator.state().paused());
      assertTrue(f.coordinator.busy());
      if (invalidate) f.coordinator.invalidateAutomatic();
      else f.coordinator.disableAutomaticDownload();
      f.coordinator.togglePause();
      assertFalse(f.coordinator.checkManually(selection));
      f.runner.work();
      f.runner.deliver();
      assertFalse(f.coordinator.busy());
      assertEquals(0, f.prepares);
      assertEquals(0, f.applies);
      assertEquals(0, f.reminders);
      if (invalidate) assertNull(f.coordinator.state().result());
      else
        assertEquals(
            UpdateCheckCoordinator.DownloadPhase.CANCELLED,
            f.coordinator.state().download().phase());
    }
  }

  private final class Fixture
      implements UpdateCheckCoordinator.DownloadService, UpdateCheckCoordinator.OfferHandoff {
    final QueueRunner runner = new QueueRunner();
    int prepares;
    int applies;
    int reminders;
    int handoffs;
    boolean fail;
    boolean failApply;
    final UpdateCheckCoordinator coordinator =
        new UpdateCheckCoordinator(s -> offer, this, runner, s -> reminders++, this);

    void start() {
      coordinator.captureStartupPolicy(true, true, selection);
      coordinator.applicationReady();
      runner.work();
      runner.deliver();
    }

    public Path prepare(
        UpdateCheckResult result,
        ResumableDownloader.Control control,
        ResumableDownloader.ProgressListener listener)
        throws Exception {
      prepares++;
      if (fail) throw new java.io.IOException("integrity failure");
      return Path.of("verified");
    }

    public void apply(UpdateCheckResult result, Path path) throws Exception {
      applies++;
      if (failApply) throw new java.io.IOException("helper unavailable");
    }

    public void openWindows(UpdateCheckSelection s, UpdateCheckResult r) {
      handoffs++;
    }

    public void openPackage(UpdateCheckSelection s, UpdateCheckResult r) {
      handoffs++;
    }
  }

  private static final class QueueRunner implements UpdateCheckCoordinator.Runner {
    final ArrayDeque<Runnable> background = new ArrayDeque<>();
    final ArrayDeque<Runnable> edt = new ArrayDeque<>();

    public void runBackground(Runnable work) {
      background.add(work);
    }

    public void runOnEdt(Runnable work) {
      edt.add(work);
    }

    void work() {
      background.remove().run();
    }

    void deliver() {
      while (!edt.isEmpty()) edt.remove().run();
    }
  }
}
