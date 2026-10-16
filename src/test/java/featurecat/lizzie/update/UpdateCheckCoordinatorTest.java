package featurecat.lizzie.update;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import featurecat.lizzie.ConfigTestHelper;
import featurecat.lizzie.Lizzie;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;
import java.util.concurrent.atomic.AtomicInteger;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class UpdateCheckCoordinatorTest {
  @TempDir Path tempDir;

  private featurecat.lizzie.Config previousConfig;
  private ResourceBundle previousResourceBundle;
  private String previousVersion;

  @BeforeEach
  void setUp() {
    previousConfig = Lizzie.config;
    previousResourceBundle = Lizzie.resourceBundle;
    previousVersion = Lizzie.nextVersion;
    Lizzie.config = ConfigTestHelper.createForTests(tempDir.resolve("coordinator-config"));
    Lizzie.config.uiConfig = new JSONObject();
    Lizzie.resourceBundle = null;
    Lizzie.nextVersion = "next-2026-08-01.1";
  }

  @AfterEach
  void tearDown() {
    Lizzie.config = previousConfig;
    Lizzie.resourceBundle = previousResourceBundle;
    Lizzie.nextVersion = previousVersion;
  }

  @Test
  void enablingVisiblePreferencesBeforeReadinessOnlyAuthorizesNextProcess() {
    RecordingDiscovery discovery = new RecordingDiscovery(UpdateCheckResult.noUpdate());
    QueueRunner runner = new QueueRunner();
    UpdateCheckCoordinator current = new UpdateCheckCoordinator(
        discovery, unusedHandoff(), runner, s -> { throw new AssertionError(); });
    current.captureStartupPolicy(UpdatePreferences.autoCheck(), UpdatePreferences.autoDownload(),
        officialSnapshot());
    UpdatePreferences.persistAutoCheck(true);
    UpdatePreferences.persistAutoDownload(true);
    current.applicationReady();
    current.applicationReady();
    assertFalse(current.busy());
    assertTrue(discovery.seen.isEmpty());
    assertTrue(current.checkManually(officialSnapshot()));
    runner.drain();
    assertEquals(1, discovery.seen.size());

    UpdateCheckCoordinator next = new UpdateCheckCoordinator(
        discovery, unusedHandoff(), runner, s -> { throw new AssertionError(); });
    next.captureStartupPolicy(UpdatePreferences.autoCheck(), UpdatePreferences.autoDownload(),
        officialSnapshot());
    assertFalse(next.busy());
    next.applicationReady();
    next.applicationReady();
    runner.drain();
    assertEquals(2, discovery.seen.size());
    assertEquals(UpdateCheckCoordinator.Origin.AUTOMATIC, next.state().origin());
  }

  @Test
  void earlyDisableOrSelectionChangePermanentlyRevokesCapturedStartup() {
    RecordingDiscovery discovery = new RecordingDiscovery(UpdateCheckResult.noUpdate());
    UpdateCheckCoordinator coordinator = new UpdateCheckCoordinator(
        discovery, unusedHandoff(), ImmediateRunner.INSTANCE, s -> { throw new AssertionError(); });
    coordinator.captureStartupPolicy(true, true, officialSnapshot());
    coordinator.invalidateAutomatic();
    assertFalse(coordinator.automaticCheckActive());
    assertFalse(coordinator.automaticDownloadActive());
    coordinator.captureStartupPolicy(true, true, officialSnapshot());
    coordinator.applicationReady();
    coordinator.applicationReady();
    assertTrue(discovery.seen.isEmpty());
    assertNull(coordinator.state().result());
    assertTrue(coordinator.checkManually(officialSnapshot()));
    assertEquals(1, discovery.seen.size());
  }

  @Test
  void completedStartupKeepsAuthorityUntilExplicitPolicyChangeWithoutCancellingManualWork() {
    RecordingDiscovery discovery = new RecordingDiscovery(UpdateCheckResult.noUpdate());
    QueueRunner runner = new QueueRunner();
    UpdateCheckCoordinator coordinator = new UpdateCheckCoordinator(
        discovery, unusedHandoff(), runner, s -> { throw new AssertionError(); });
    coordinator.captureStartupPolicy(true, true, officialSnapshot());
    coordinator.applicationReady();
    runner.drain();
    assertTrue(coordinator.automaticCheckActive());
    assertTrue(coordinator.automaticDownloadActive());
    coordinator.checkManually(officialSnapshot());
    coordinator.invalidateAutomatic();
    assertFalse(coordinator.automaticCheckActive());
    assertFalse(coordinator.automaticDownloadActive());
    runner.drain();
    assertEquals(UpdateCheckCoordinator.Origin.MANUAL, coordinator.state().origin());
    assertEquals(UpdateCheckResult.Reason.NO_UPDATE, coordinator.state().result().reason);
    assertEquals(2, discovery.seen.size());
  }

  @Test
  void startupIsOnceAndManualJoinsUntilTerminalThenCanRetry() {
    QueueRunner runner = new QueueRunner();
    RecordingDiscovery discovery = new RecordingDiscovery(UpdateCheckResult.noUpdate());
    UpdateCheckCoordinator coordinator = new UpdateCheckCoordinator(
        discovery, unusedHandoff(), runner, s -> { throw new AssertionError(); });
    UpdateCheckSelection snapshot = officialSnapshot();
    coordinator.captureStartupPolicy(true, false, snapshot);
    coordinator.applicationReady();
    coordinator.captureStartupPolicy(true, false, snapshot);
    coordinator.applicationReady();
    assertTrue(coordinator.state().checking());
    assertFalse(coordinator.checkManually(snapshot));
    runner.drain();
    assertEquals(1, discovery.seen.size());
    assertSame(snapshot, coordinator.state().selection());
    assertEquals(UpdateCheckResult.Reason.NO_UPDATE, coordinator.state().result().reason);
    assertTrue(coordinator.checkManually(snapshot));
    runner.drain();
    assertEquals(2, discovery.seen.size());
  }

  @Test
  void disabledStartupDoesNotCatchUpWhenEnabled() {
    RecordingDiscovery discovery = new RecordingDiscovery(UpdateCheckResult.noUpdate());
    UpdateCheckCoordinator coordinator = new UpdateCheckCoordinator(
        discovery, unusedHandoff(), ImmediateRunner.INSTANCE, s -> { throw new AssertionError(); });
    coordinator.captureStartupPolicy(false, false, officialSnapshot());
    coordinator.applicationReady();
    coordinator.captureStartupPolicy(true, false, officialSnapshot());
    coordinator.applicationReady();
    assertTrue(discovery.seen.isEmpty());
    assertTrue(coordinator.checkManually(officialSnapshot()));
    assertEquals(1, discovery.seen.size());
  }

  @Test
  void automaticNonOffersRemainDistinctQuietAndRecoverable() {
    for (UpdateCheckResult result : List.of(UpdateCheckResult.unavailableBuild(),
        UpdateCheckResult.unsupportedPlatform(), UpdateCheckResult.noUpdate(),
        UpdateCheckResult.noPackage(), UpdateCheckResult.failure(UpdateCheckResult.FailureKind.FETCH))) {
      UpdateCheckCoordinator coordinator = new UpdateCheckCoordinator(
          s -> result, unusedHandoff(), ImmediateRunner.INSTANCE,
          s -> { throw new AssertionError(); });
      coordinator.captureStartupPolicy(true, false, officialSnapshot());
      coordinator.applicationReady();
      List<UpdateCheckCoordinator.State> view = new ArrayList<>();
      coordinator.subscribe(view::add);
      assertSame(result, view.get(0).result());
      assertFalse(view.get(0).checking());
    }
  }

  @Test
  void automaticOfferSurvivesClosedViewAndRemindsOnlyOnceWithoutHandoff() {
    QueueRunner runner = new QueueRunner();
    FakeHandoff handoff = new FakeHandoff();
    AtomicInteger reminders = new AtomicInteger();
    UpdateCheckCoordinator coordinator = new UpdateCheckCoordinator(
        s -> UpdateCheckResult.offerWindows(windowsPlan()), handoff, runner,
        s -> reminders.incrementAndGet());
    List<UpdateCheckCoordinator.State> states = new ArrayList<>();
    java.util.function.Consumer<UpdateCheckCoordinator.State> view = states::add;
    coordinator.subscribe(view);
    coordinator.captureStartupPolicy(true, false, officialSnapshot());
    coordinator.applicationReady();
    coordinator.unsubscribe(view);
    runner.drain();
    assertEquals(1, reminders.get());
    assertNull(handoff.windowsPlan);
    coordinator.subscribe(view);
    assertTrue(states.get(states.size() - 1).hasOffer());
    coordinator.captureStartupPolicy(true, false, officialSnapshot());
    coordinator.applicationReady();
    assertEquals(1, reminders.get());
    coordinator.openOffer();
    assertSame(coordinator.state().result().windowsPlan, handoff.windowsPlan);
    assertEquals(UpdateCheckCoordinator.Origin.AUTOMATIC, coordinator.state().origin());
  }

  @Test
  void invalidatedAutomaticSuccessAndFailureCannotPublishAndDoNotOverlapManualCheck() {
    for (UpdateCheckResult result : List.of(UpdateCheckResult.offerWindows(windowsPlan()),
        UpdateCheckResult.failure(UpdateCheckResult.FailureKind.FETCH))) {
      QueueRunner runner = new QueueRunner();
      UpdateCheckCoordinator coordinator = new UpdateCheckCoordinator(
          s -> result, unusedHandoff(), runner, s -> { throw new AssertionError(); });
      coordinator.captureStartupPolicy(true, false, officialSnapshot());
      coordinator.applicationReady();
      runner.background.run();
      coordinator.invalidateAutomatic();
      assertNull(coordinator.state().result());
      assertFalse(coordinator.checkManually(officialSnapshot()));
      runner.edt.run();
      assertNull(coordinator.state().result());
      assertFalse(coordinator.state().checking());
      coordinator.captureStartupPolicy(true, false, officialSnapshot());
      coordinator.applicationReady();
      assertNull(coordinator.state().selection());
      assertTrue(coordinator.checkManually(officialSnapshot()));
    }
  }

  @Test
  void manualSnapshotAndHandoffSurviveAutomaticPolicyChanges() {
    for (UpdateCheckResult result : List.of(UpdateCheckResult.offerWindows(windowsPlan()),
        UpdateCheckResult.offerPackage(new PackageUpdatePlan(null, null, "v", "linux", "x64", "cpu")))) {
      QueueRunner runner = new QueueRunner();
      FakeHandoff handoff = new FakeHandoff();
      RecordingDiscovery discovery = new RecordingDiscovery(result);
      UpdateCheckCoordinator coordinator = new UpdateCheckCoordinator(
          discovery, handoff, runner, s -> { throw new AssertionError(); });
      UpdateCheckSelection snapshot = officialSnapshot();
      coordinator.checkManually(snapshot);
      CheckUpdateDialog.persistSelection(UpdateChannel.BETA, UpdateSource.GITHUB);
      coordinator.invalidateAutomatic();
      runner.drain();
      assertSame(snapshot, discovery.seen.get(0));
      assertSame(snapshot, coordinator.state().selection());
      assertEquals(UpdateCheckCoordinator.Origin.MANUAL, coordinator.state().origin());
      assertSame(result.windowsPlan, handoff.windowsPlan);
      assertSame(result.packagePlan, handoff.packagePlan);
    }
  }

  @Test
  void malformedOfferAndThrownDiscoveryBecomeRetryableFailures() {
    for (UpdateCheckCoordinator.Discovery discovery : List.<UpdateCheckCoordinator.Discovery>of(
        s -> UpdateCheckResult.offerWindows(null), s -> { throw new IllegalStateException(); })) {
      UpdateCheckCoordinator coordinator = new UpdateCheckCoordinator(
          discovery, unusedHandoff(), ImmediateRunner.INSTANCE, s -> { throw new AssertionError(); });
      coordinator.captureStartupPolicy(true, false, officialSnapshot());
      coordinator.applicationReady();
      assertEquals(UpdateCheckResult.Reason.FAILURE, coordinator.state().result().reason);
      assertTrue(coordinator.checkManually(officialSnapshot()));
    }
  }

  @Test
  void testChannelPagePersistDoesNotOverwriteOfficialSource() {
    UpdateSource.persist(UpdateSource.OFFICIAL_SITE);
    UpdateChannel.persist(UpdateChannel.STABLE);

    CheckUpdateDialog.persistSelection(UpdateChannel.BETA, UpdateSource.GITHUB);

    assertEquals(UpdateChannel.BETA, UpdateChannel.current());
    assertEquals(UpdateSource.OFFICIAL_SITE, UpdateSource.current());
  }

  @Test
  void officialPagePersistWritesChannelAndSource() {
    UpdateSource.persist(UpdateSource.OFFICIAL_SITE);
    UpdateChannel.persist(UpdateChannel.STABLE);

    CheckUpdateDialog.persistSelection(UpdateChannel.STABLE, UpdateSource.GITHUB);

    assertEquals(UpdateChannel.STABLE, UpdateChannel.current());
    assertEquals(UpdateSource.GITHUB, UpdateSource.current());
  }

  private static UpdateCheckSelection officialSnapshot() {
    return UpdateCheckSelection.of(
        UpdateChannel.STABLE, UpdateSource.OFFICIAL_SITE, "next-2026-08-01.1");
  }

  private static WindowsUpdatePlan windowsPlan() {
    return WindowsUpdatePlan.create(
        UpdateManifest.parse(UpdateManifestTest.validManifest()),
        null,
        "next-2026-06-11.1",
        "opencl");
  }

  private static FakeHandoff unusedHandoff() {
    return new FakeHandoff();
  }

  private static final class FakeHandoff implements UpdateCheckCoordinator.OfferHandoff {
    WindowsUpdatePlan windowsPlan;
    PackageUpdatePlan packagePlan;

    @Override
    public void openWindows(UpdateCheckSelection selection, UpdateCheckResult result) {
      windowsPlan = result.windowsPlan;
    }

    @Override
    public void openPackage(UpdateCheckSelection selection, UpdateCheckResult result) {
      packagePlan = result.packagePlan;
    }
  }

  private static final class RecordingDiscovery implements UpdateCheckCoordinator.Discovery {
    final List<UpdateCheckSelection> seen = new ArrayList<>();
    private final UpdateCheckResult result;

    RecordingDiscovery(UpdateCheckResult result) {
      this.result = result;
    }

    @Override
    public UpdateCheckResult check(UpdateCheckSelection selection) {
      seen.add(selection);
      return result;
    }
  }

  private static final class ImmediateRunner implements UpdateCheckCoordinator.Runner {
    static final ImmediateRunner INSTANCE = new ImmediateRunner();

    @Override
    public void runBackground(Runnable work) {
      work.run();
    }

    @Override
    public void runOnEdt(Runnable work) {
      work.run();
    }
  }

  private static final class QueueRunner implements UpdateCheckCoordinator.Runner {
    private Runnable background;
    private Runnable edt;

    @Override
    public void runBackground(Runnable work) {
      background = work;
    }

    @Override
    public void runOnEdt(Runnable work) {
      edt = work;
    }

    void drain() {
      background.run();
      edt.run();
    }
  }
}
