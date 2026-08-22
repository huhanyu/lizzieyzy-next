package featurecat.lizzie.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import featurecat.lizzie.logging.DiagnosticBundleExporter;
import featurecat.lizzie.analysis.ReadBoardLoggingControl;
import featurecat.lizzie.analysis.ReadBoardLoggingProtocol;
import featurecat.lizzie.analysis.ReadBoardLoggingSnapshot;
import featurecat.lizzie.logging.LoggingLimits;
import featurecat.lizzie.logging.LoggingRuntime;
import featurecat.lizzie.logging.WorkDirectoryResolution;
import featurecat.lizzie.logging.TraceScope;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DiagnosticsDialogTest {
  @TempDir Path tempDir;

  @AfterEach
  void tearDown() {
    LoggingRuntime.resetForTests();
  }

  @Test
  void dialogShowsHealthAndAppliesDiagnosticsOnApply() {
    LoggingRuntime runtime = start();
    DiagnosticsDialog dialog = dialog(runtime, new AtomicInteger(), new ArrayList<>(), () -> true);
    assertTrue(dialog.healthText().contains(runtime.logsDirectory().toString()));
    assertTrue(dialog.healthText().contains("Persistence:"));
    assertTrue(dialog.estimateText().contains("MB"));
    assertFalse(dialog.cancelButton().isVisible());
    assertFalse(dialog.fullLogsEnabledBox().isSelected());
    dialog.diagnosticsEnabledBox().doClick();
    assertTrue(runtime.settings().diagnosticsEnabled());
    dialog.applyCurrentPlan();
    assertFalse(runtime.settings().diagnosticsEnabled());
  }

  @Test
  void applyFailureRestoresUiFromRuntime() {
    LoggingRuntime runtime = start();
    DiagnosticsDialog dialog = dialog(runtime, new AtomicInteger(), new ArrayList<>(), () -> true);
    assertTrue(runtime.settings().diagnosticsEnabled());
    dialog.diagnosticsEnabledBox().setSelected(false);
    try {
      runtime.applySettings(
          runtime.settings().withDiagnosticsEnabled(false),
          settings -> {
            throw new java.io.IOException("disk full");
          });
    } catch (RuntimeException ignored) {
    }
    dialog.refreshFromRuntime();
    assertTrue(dialog.diagnosticsEnabledBox().isSelected());
    assertTrue(runtime.settings().diagnosticsEnabled());
  }

  @Test
  void fullTraceRequiresConfirmationAndRefreshesTitle() {
    LoggingRuntime runtime = start();
    AtomicInteger titles = new AtomicInteger();
    AtomicBoolean confirm = new AtomicBoolean(false);
    DiagnosticsDialog dialog = dialog(runtime, titles, new ArrayList<>(), confirm::get);
    dialog.fullLogsEnabledBox().setSelected(true);
    dialog.applyCurrentPlan();
    assertFalse(runtime.fullTraceActive());
    assertEquals(0, titles.get());
    confirm.set(true);
    dialog.fullLogsEnabledBox().setSelected(true);
    dialog.applyCurrentPlan();
    assertTrue(runtime.fullTraceActive());
    assertEquals(1, titles.get());
    assertTrue(dialog.fullLogsEnabledBox().isSelected());
    assertTrue(dialog.durationText().contains("s"));
    DiagnosticsDialog reopened = dialog(runtime, titles, new ArrayList<>(), () -> true);
    assertTrue(reopened.fullLogsEnabledBox().isSelected());
    assertTrue(reopened.durationText().contains("s"));
    dialog.fullLogsEnabledBox().setSelected(false);
    dialog.applyCurrentPlan();
    assertFalse(runtime.fullTraceActive());
    assertEquals(2, titles.get());
  }

  @Test
  void openFolderAndExportUseWorkDirectoryDiagnostics() throws Exception {
    LoggingRuntime runtime = start();
    List<Path> opened = new ArrayList<>();
    DiagnosticsDialog dialog = dialog(runtime, new AtomicInteger(), opened, () -> true);
    assertTrue(dialog.currentRequest().rawScopes().isEmpty());
    dialog.startFullTraceFromUi();
    assertTrue(dialog.confirmBody().contains("Engine/GTP"));
    assertTrue(dialog.currentRequest().rawScopes().contains(TraceScope.ENGINE_GTP));
    dialog.scopeEngineBox().setSelected(false);
    assertTrue(dialog.currentRequest().rawScopes().contains(TraceScope.ENGINE_GTP));
    Path zip = dialog.exportSynchronously();
    assertTrue(Files.isRegularFile(zip));
    assertTrue(zip.getParent().endsWith("diagnostics"));
    assertTrue(opened.contains(zip.getParent()));
    runtime.stopFullTrace();
    dialog.refreshFromRuntime();
    assertFalse(dialog.fullLogsEnabledBox().isSelected());
    assertTrue(dialog.currentRequest().rawScopes().isEmpty());
    assertTrue(dialog.currentRequest().includeCapture());
    assertFalse(dialog.currentRequest().includeReadBoardTrace());
  }

  @Test
  void hostPaneShowsSessionAndDoesNotExposeHelperToggles() {
    LoggingRuntime runtime = start();
    List<Path> opened = new ArrayList<>();
    DiagnosticsDialog dialog = dialog(runtime, new AtomicInteger(), opened, () -> true);
    dialog.openLogsDirectory();

    assertTrue(dialog.hostSessionText().contains(runtime.applicationLogSessionId()));
    assertTrue(
        dialog.hostAppLogText().contains(runtime.logsDirectory().resolve("app.log").toString()));
    assertTrue(
        dialog.hostCrashLogText().contains(runtime.logsDirectory().resolve("crash.log").toString()));
    assertFalse(dialog.hostAppLogText().contains("readboard"));
    assertFalse(dialog.hostCrashLogText().contains("readboard"));
    assertTrue(dialog.hostPaneText().contains("Engine/GTP"));
    assertTrue(dialog.hostPaneText().contains("ReadBoard/Yike"));
    assertFalse(dialog.hostPaneText().contains("Full Trace"));
    assertFalse(dialog.hostPaneText().contains("Capture"));
    assertTrue(dialog.helperPaneText().contains("Diagnostics"));
    assertTrue(dialog.helperPaneText().contains("Full Logs"));
    assertTrue(dialog.helperPaneText().contains("Capture"));
    assertEquals(runtime.logsDirectory(), opened.get(0));
    assertTrue(opened.get(0).endsWith("logs"));
    assertFalse(opened.get(0).endsWith("readboard"));
  }

  @Test
  void helperPaneRendersDesiredObservedFromHostSnapshot() {
    LoggingRuntime runtime = start();
    ReadBoardLoggingControl control =
        new ReadBoardLoggingControl(ReadBoardLoggingControl.Desired.launchDefaults(false), true);
    control.onCapability(
        ReadBoardLoggingProtocol.tryParseCapability(
            "readboardLoggingV1 dGVzdFByb2Nlc3NJRA off off off healthy 0"));
    RecordingHelper helper = new RecordingHelper(control);
    DiagnosticsDialog dialog =
        dialog(runtime, new AtomicInteger(), new ArrayList<>(), () -> true, helper, () -> true);

    assertTrue(dialog.helperCapabilityText().toLowerCase().contains("ready"));
    assertTrue(dialog.helperPersistenceText().toLowerCase().contains("healthy"));
    assertTrue(dialog.helperDropCountText().contains("0"));
    assertTrue(dialog.helperProcessSessionText().contains("dGVzdFByb2Nlc3NJRA"));
    assertFalse(dialog.helperDiagnosticsBox().isSelected());
    assertEquals("Off", dialog.helperDiagnosticsObservedText());

    dialog.helperDiagnosticsBox().doClick();

    assertEquals(1, helper.sets);
    assertTrue(control.desired().diagnostics);
    assertFalse(control.desired().capture);
    assertFalse(control.desired().trace);
    assertEquals("Not applied", dialog.helperDiagnosticsObservedText());
    assertNotEquals("On", dialog.helperDiagnosticsObservedText());

    control.onObserved(
        ReadBoardLoggingProtocol.tryParseObserved(
            "readboardLoggingObserved "
                + helper.lastRequestId
                + " dGVzdFByb2Nlc3NJRA on off off healthy 0 applied"));
    dialog.refreshFromRuntime();
    assertEquals("On", dialog.helperDiagnosticsObservedText());
    dialog.helperDiagnosticsBox().doClick();
    assertEquals(2, helper.sets);
    assertFalse(dialog.helperDiagnosticsBox().isSelected());
    assertEquals("On", dialog.helperDiagnosticsObservedText());
  }

  @Test
  void helperUnknownAndPathFailureAreDistinct() {
    LoggingRuntime runtime = start();
    ReadBoardLoggingControl unknown =
        new ReadBoardLoggingControl(ReadBoardLoggingControl.Desired.launchDefaults(false), true);
    DiagnosticsDialog unknownDialog =
        dialog(
            runtime,
            new AtomicInteger(),
            new ArrayList<>(),
            () -> true,
            new RecordingHelper(unknown),
            () -> true);
    assertEquals("Unknown", unknownDialog.helperDiagnosticsObservedText());
    assertEquals("Unknown", unknownDialog.helperCaptureObservedText());

    ReadBoardLoggingControl legacy =
        new ReadBoardLoggingControl(ReadBoardLoggingControl.Desired.launchDefaults(false), false);
    DiagnosticsDialog legacyDialog =
        dialog(
            runtime,
            new AtomicInteger(),
            new ArrayList<>(),
            () -> true,
            new RecordingHelper(legacy),
            () -> true);
    assertEquals("Legacy, unconfirmed", legacyDialog.helperDiagnosticsObservedText());

    ReadBoardLoggingControl degraded =
        new ReadBoardLoggingControl(ReadBoardLoggingControl.Desired.launchDefaults(true), true);
    degraded.onCapability(
        ReadBoardLoggingProtocol.tryParseCapability(
            "readboardLoggingV1 dGVzdFByb2Nlc3NJRA on off off degraded 3"));
    DiagnosticsDialog degradedDialog =
        dialog(
            runtime,
            new AtomicInteger(),
            new ArrayList<>(),
            () -> true,
            new RecordingHelper(degraded),
            () -> true);
    assertEquals("On, storage degraded", degradedDialog.helperDiagnosticsObservedText());
    assertNotEquals("Unknown", degradedDialog.helperDiagnosticsObservedText());
    assertTrue(degradedDialog.helperDropCountText().contains("3"));
    assertTrue(degradedDialog.helperPersistenceText().toLowerCase().contains("degraded"));
  }

  @Test
  void captureConfirmationCanCancelAndIsRequiredEvenWhenDiagnosticsOn() {
    LoggingRuntime runtime = start();
    ReadBoardLoggingControl control =
        new ReadBoardLoggingControl(ReadBoardLoggingControl.Desired.launchDefaults(true), true);
    control.onCapability(
        ReadBoardLoggingProtocol.tryParseCapability(
            "readboardLoggingV1 dGVzdFByb2Nlc3NJRA on off off healthy 0"));
    RecordingHelper helper = new RecordingHelper(control);
    AtomicBoolean confirm = new AtomicBoolean(false);
    DiagnosticsDialog dialog =
        dialog(runtime, new AtomicInteger(), new ArrayList<>(), () -> true, helper, confirm::get);

    assertTrue(dialog.helperDiagnosticsBox().isSelected());
    assertEquals("On", dialog.helperDiagnosticsObservedText());
    dialog.helperCaptureBox().doClick();
    assertEquals(0, helper.sets);
    assertFalse(control.desired().capture);
    assertFalse(dialog.helperCaptureBox().isSelected());
    assertTrue(dialog.captureConfirmBody().toLowerCase().contains("capture"));

    confirm.set(true);
    dialog.helperCaptureBox().doClick();
    assertEquals(1, helper.sets);
    assertTrue(control.desired().capture);
    assertTrue(control.desired().diagnostics);
    assertFalse(control.desired().trace);
    assertEquals("Not applied", dialog.helperCaptureObservedText());
  }

  @Test
  void captureMustBeReconfirmedAfterProcessReset() {
    LoggingRuntime runtime = start();
    ReadBoardLoggingControl control =
        new ReadBoardLoggingControl(ReadBoardLoggingControl.Desired.launchDefaults(true), true);
    control.onCapability(
        ReadBoardLoggingProtocol.tryParseCapability(
            "readboardLoggingV1 dGVzdFByb2Nlc3NJRA on off off healthy 0"));
    RecordingHelper helper = new RecordingHelper(control);
    AtomicInteger confirms = new AtomicInteger();
    DiagnosticsDialog dialog =
        dialog(
            runtime,
            new AtomicInteger(),
            new ArrayList<>(),
            () -> true,
            helper,
            () -> {
              confirms.incrementAndGet();
              return true;
            });

    dialog.helperCaptureBox().doClick();
    assertEquals(1, confirms.get());
    control.resetForNewProcess();
    dialog.refreshFromRuntime();
    assertFalse(dialog.helperCaptureBox().isSelected());
    assertEquals("Unknown", dialog.helperCaptureObservedText());

    control.onCapability(
        ReadBoardLoggingProtocol.tryParseCapability(
            "readboardLoggingV1 bmV3UHJvY2Vzcw on off off healthy 0"));
    dialog.refreshFromRuntime();
    dialog.helperCaptureBox().doClick();
    assertEquals(2, confirms.get());
    assertTrue(control.desired().capture);
  }

  @Test
  void helperTogglesAreIndependent() {
    LoggingRuntime runtime = start();
    ReadBoardLoggingControl control =
        new ReadBoardLoggingControl(ReadBoardLoggingControl.Desired.launchDefaults(false), true);
    control.onCapability(
        ReadBoardLoggingProtocol.tryParseCapability(
            "readboardLoggingV1 dGVzdFByb2Nlc3NJRA off off off healthy 0"));
    RecordingHelper helper = new RecordingHelper(control);
    DiagnosticsDialog dialog =
        dialog(runtime, new AtomicInteger(), new ArrayList<>(), () -> true, helper, () -> true);

    dialog.helperTraceBox().doClick();
    assertFalse(control.desired().diagnostics);
    assertFalse(control.desired().capture);
    assertTrue(control.desired().trace);
    assertEquals("Not applied", dialog.helperTraceObservedText());
    assertEquals("Off", dialog.helperDiagnosticsObservedText());
    assertEquals("Off", dialog.helperCaptureObservedText());
  }

  @Test
  void exportRequestForwardsHelperSessionAndReadBoardTraceOptIn() {
    LoggingRuntime runtime = start();
    ReadBoardLoggingControl control =
        new ReadBoardLoggingControl(ReadBoardLoggingControl.Desired.launchDefaults(false), true);
    control.onCapability(
        ReadBoardLoggingProtocol.tryParseCapability(
            "readboardLoggingV1 dGVzdFByb2Nlc3NJRA off off off healthy 0"));
    RecordingHelper helper = new RecordingHelper(control);
    DiagnosticsDialog dialog =
        dialog(runtime, new AtomicInteger(), new ArrayList<>(), () -> true, helper, () -> true);

    assertTrue(dialog.currentRequest().includeCapture());
    assertFalse(dialog.currentRequest().includeReadBoardTrace());
    assertEquals("dGVzdFByb2Nlc3NJRA", dialog.currentRequest().readBoardLogging().processSessionId());
    assertTrue(dialog.currentRequest().rawScopes().isEmpty());

    dialog.helperTraceBox().doClick();
    assertTrue(dialog.currentRequest().includeReadBoardTrace());
    assertTrue(dialog.currentRequest().includeCapture());
    assertTrue(dialog.currentRequest().rawScopes().isEmpty());
  }

  private DiagnosticsDialog dialog(
      LoggingRuntime runtime,
      AtomicInteger titles,
      List<Path> opened,
      java.util.function.BooleanSupplier confirm) {
    return dialog(runtime, titles, opened, confirm, null, null);
  }

  private DiagnosticsDialog dialog(
      LoggingRuntime runtime,
      AtomicInteger titles,
      List<Path> opened,
      java.util.function.BooleanSupplier confirm,
      DiagnosticsDialog.HelperLogging helper,
      java.util.function.BooleanSupplier captureConfirm) {
    DiagnosticBundleExporter exporter =
        new DiagnosticBundleExporter(DiagnosticBundleExporter.defaultOutputDirectory(tempDir));
    return new DiagnosticsDialog(
        runtime,
        null,
        exporter,
        titles::incrementAndGet,
        confirm,
        opened::add,
        helper,
        captureConfirm);
  }

  private static final class RecordingHelper implements DiagnosticsDialog.HelperLogging {
    private final ReadBoardLoggingControl control;
    private int sets;
    private String lastRequestId;

    private RecordingHelper(ReadBoardLoggingControl control) {
      this.control = control;
    }

    @Override
    public ReadBoardLoggingSnapshot snapshot() {
      return control.snapshot();
    }

    @Override
    public boolean requestSet(boolean diagnostics, boolean capture, boolean trace) {
      sets++;
      lastRequestId = control.beginSet(diagnostics, capture, trace).requestId;
      return true;
    }
  }

  private LoggingRuntime start() {
    LoggingRuntime.resetForTests();
    return LoggingRuntime.initialize(
        new WorkDirectoryResolution(tempDir, List.of()),
        new LoggingLimits(64, 32, 32, 32, 7, 1_000_000, 256_000));
  }
}
