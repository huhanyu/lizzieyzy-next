package featurecat.lizzie.gui;

import featurecat.lizzie.Config;
import featurecat.lizzie.Lizzie;
import featurecat.lizzie.analysis.SyncDiagnosticsRecorder;
import featurecat.lizzie.analysis.ReadBoard;
import featurecat.lizzie.analysis.ReadBoardLoggingControl;
import featurecat.lizzie.analysis.ReadBoardLoggingSnapshot;
import featurecat.lizzie.analysis.ReadBoardLoggingProtocol;
import featurecat.lizzie.logging.DiagnosticBundleExporter;
import featurecat.lizzie.logging.DiagnosticBundleRequest;
import featurecat.lizzie.logging.DiagnosticModule;
import featurecat.lizzie.logging.LoggingRuntime;
import featurecat.lizzie.logging.LoggingSettings;
import featurecat.lizzie.logging.LoggingStatus;
import featurecat.lizzie.logging.TraceScope;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.io.IOException;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;

public class DiagnosticsDialog extends JPanel {
  private static final long serialVersionUID = 1L;
  private static final Color PAPER = new Color(246, 247, 249);
  private static final Color INK = new Color(32, 36, 40);
  private static final Color SECONDARY = new Color(48, 52, 58);
  private static final Color MUTED = new Color(90, 96, 104);
  private static final Color HAIRLINE = new Color(221, 223, 227);
  private static final Color DANGER = new Color(176, 64, 64);

  private final LoggingRuntime runtime;
  private final Config config;
  private final DiagnosticBundleExporter exporter;
  private final Runnable titleRefresh;
  private final BooleanSupplier fullTraceConfirmer;
  private final Consumer<Path> folderOpener;
  private final HelperLogging helperLogging;
  private final BooleanSupplier captureConfirmer;
  private final JLabel hostSessionLabel = new JFontLabel("");
  private final JLabel helperCapability = new JFontLabel("");
  private final JLabel helperPersistence = new JFontLabel("");
  private final JLabel helperDropCount = new JFontLabel("");
  private final JLabel helperProcessSession = new JFontLabel("");
  private final JLabel helperCaptureSummary = new JFontLabel("");
  private final JLabel helperDiagnosticsObserved = new JFontLabel("");
  private final JLabel helperTraceObserved = new JFontLabel("");
  private final JLabel helperCaptureObserved = new JFontLabel("");
  private final JCheckBox helperDiagnostics = box();
  private final JCheckBox helperTrace = box();
  private final JCheckBox helperCapture = box();
  private final JCheckBox diagnosticsEnabled = box();
  private final JCheckBox moduleEngine = box();
  private final JCheckBox moduleGtp = box();
  private final JCheckBox moduleReadBoard = box();
  private final JCheckBox moduleNetwork = box();
  private final JCheckBox fullLogsEnabled = box();
  private final JCheckBox scopeEngine = box();
  private final JCheckBox scopeReadBoard = box();
  private final JCheckBox scopeNetwork = box();
  private final JButton apply = new JFontButton(text("DiagnosticsDialog.apply", "Apply"));
  private final JButton exportDefault =
      new JFontButton(text("DiagnosticsDialog.exportDefault", "Export package"));
  private final JButton cancel =
      new JFontButton(text("DiagnosticsDialog.cancelExport", "Cancel export"));
  private final JButton openLogs = new JFontButton(text("DiagnosticsDialog.open", "Open"));
  private final JTextArea statusArea = new JFontTextArea(2, 48);
  private final JLabel durationLabel = new JFontLabel("");
  private final JLabel estimateLabel = new JFontLabel("");
  private final JLabel logsPath = new JFontLabel("");
  private final JLabel hostAppLog = new JFontLabel("");
  private final JLabel hostCrashLog = new JFontLabel("");
  private final JLabel persistenceLabel = new JFontLabel("");
  private final JPanel streamList = new JPanel();
  private final AtomicBoolean cancelExport = new AtomicBoolean();
  private final Timer durationClock = new Timer(1000, event -> refreshDuration());
  private static JDialog openDialog;
  private static DiagnosticsDialog openPanel;

  public static JDialog open(Window owner, LoggingRuntime runtime, Config config) {
    if (openDialog == null) {
      openPanel = new DiagnosticsDialog(runtime, config);
      openDialog = new JDialog(owner);
      openDialog.setTitle(text("DiagnosticsDialog.title", "Diagnostics and Logs"));
      openDialog.setModalityType(JDialog.ModalityType.MODELESS);
      openDialog.setDefaultCloseOperation(JDialog.HIDE_ON_CLOSE);
      openDialog.setContentPane(openPanel);
      openDialog.pack();
      openDialog.setMinimumSize(new Dimension(820, 560));
      openDialog.setLocationRelativeTo(owner);
    } else {
      openPanel.refreshFromRuntime();
    }
    openDialog.setVisible(true);
    openDialog.toFront();
    openPanel.refreshFromRuntime();
    return openDialog;
  }

  public static void notifyRuntimeChanged() {
    if (!SwingUtilities.isEventDispatchThread()) {
      SwingUtilities.invokeLater(DiagnosticsDialog::notifyRuntimeChanged);
      return;
    }
    if (openPanel != null) {
      openPanel.refreshFromRuntime();
    }
  }

  public DiagnosticsDialog(LoggingRuntime runtime, Config config) {
    this(
        runtime,
        config,
        new DiagnosticBundleExporter(
            DiagnosticBundleExporter.defaultOutputDirectory(runtime.logsDirectory().getParent())),
        DiagnosticsDialog::refreshFrameTitle,
        null,
        DiagnosticsDialog::openFolder);
  }

  DiagnosticsDialog(
      LoggingRuntime runtime,
      Config config,
      DiagnosticBundleExporter exporter,
      Runnable titleRefresh,
      BooleanSupplier fullTraceConfirmer,
      Consumer<Path> folderOpener) {
    this(runtime, config, exporter, titleRefresh, fullTraceConfirmer, folderOpener, null, null);
  }

  DiagnosticsDialog(
      LoggingRuntime runtime,
      Config config,
      DiagnosticBundleExporter exporter,
      Runnable titleRefresh,
      BooleanSupplier fullTraceConfirmer,
      Consumer<Path> folderOpener,
      HelperLogging helperLogging,
      BooleanSupplier captureConfirmer) {
    super(new BorderLayout(0, 8));
    this.runtime = runtime;
    this.config = config;
    this.exporter = exporter;
    this.titleRefresh = titleRefresh == null ? () -> {} : titleRefresh;
    this.fullTraceConfirmer = fullTraceConfirmer;
    this.folderOpener = folderOpener == null ? path -> {} : folderOpener;
    this.helperLogging = helperLogging == null ? new LiveHelperLogging() : helperLogging;
    this.captureConfirmer = captureConfirmer;

    setOpaque(true);
    setBackground(AppleStyleSupport.isAppleStyleEnabled() ? new Color(30, 33, 38) : PAPER);
    setBorder(new EmptyBorder(10, 12, 10, 12));

    durationLabel.setForeground(SECONDARY);
    estimateLabel.setForeground(INK);
    hostAppLog.setForeground(INK);
    hostCrashLog.setForeground(INK);
    logsPath.setForeground(INK);
    persistenceLabel.setForeground(INK);
    streamList.setOpaque(false);
    streamList.setLayout(new GridBagLayout());

    statusArea.setEditable(false);
    statusArea.setLineWrap(true);
    statusArea.setWrapStyleWord(true);
    statusArea.setOpaque(false);
    statusArea.setBorder(new EmptyBorder(0, 2, 0, 2));
    statusArea.setForeground(SECONDARY);

    sizeButton(apply, 96);
    sizeButton(openLogs, 96);
    sizeButton(exportDefault, 120);
    sizeButton(cancel, 96);
    cancel.setVisible(false);

    apply.addActionListener(e -> applyCurrentPlan());
    openLogs.addActionListener(e -> openLogsDirectory());
    diagnosticsEnabled.addActionListener(e -> setModulesEnabled(diagnosticsEnabled.isSelected()));
    exportDefault.addActionListener(e -> exportPackageOffEdt());
    cancel.addActionListener(e -> cancelExport.set(true));
    helperDiagnostics.addActionListener(e -> requestHelperToggle(HelperField.DIAGNOSTICS));
    helperTrace.addActionListener(e -> requestHelperToggle(HelperField.TRACE));
    helperCapture.addActionListener(e -> requestHelperToggle(HelperField.CAPTURE));
    hostSessionLabel.setForeground(INK);
    helperCapability.setForeground(INK);
    helperPersistence.setForeground(INK);
    helperDropCount.setForeground(INK);
    helperProcessSession.setForeground(INK);
    helperCaptureSummary.setForeground(SECONDARY);
    helperDiagnosticsObserved.setForeground(SECONDARY);
    helperTraceObserved.setForeground(SECONDARY);
    helperCaptureObserved.setForeground(SECONDARY);

    JPanel host = column();
    addSection(host, sectionTitle(text("DiagnosticsDialog.hostPane", "LizzieYzy")));
    addSection(host, hostSessionLabel);
    addSection(
        host,
        checkRow(
            text("DiagnosticsDialog.diagnosticsEnabled", "Diagnostic recording"),
            diagnosticsEnabled,
            false,
            null));
    addSection(
        host, checkRow(text("DiagnosticsDialog.module.engine", "Engine"), moduleEngine, true, null));
    addSection(
        host,
        checkRow(text("DiagnosticsDialog.module.gtpSummary", "GTP Summary"), moduleGtp, true, null));
    addSection(
        host,
        checkRow(
            text("DiagnosticsDialog.module.readboardYike", "ReadBoard/Yike"),
            moduleReadBoard,
            true,
            null));
    addSection(
        host,
        checkRow(
            text("DiagnosticsDialog.module.networkRemote", "Network/Remote"),
            moduleNetwork,
            true,
            null));
    addSection(
        host,
        checkRow(
            text("DiagnosticsDialog.fullTrace", "Full Logs"),
            fullLogsEnabled,
            false,
            durationLabel));
    addSection(
        host,
        checkRow(text("DiagnosticsDialog.scope.engineGtp", "Engine/GTP"), scopeEngine, true, null));
    addSection(
        host,
        checkRow(
            text("DiagnosticsDialog.scope.readboardYike", "ReadBoard/Yike"),
            scopeReadBoard,
            true,
            null));
    addSection(
        host,
        checkRow(
            text("DiagnosticsDialog.scope.networkWebsocket", "Network/WebSocket"),
            scopeNetwork,
            true,
            null));
    JLabel migration =
        new JFontLabel(
            "<html>"
                + text(
                    "DiagnosticsDialog.gtpMigrationNote",
                    "Legacy GTP file logging now provides GTP Summary. Raw GTP requires starting Full Logs.")
                + "</html>");
    migration.setForeground(MUTED);
    addSection(host, migration);

    JPanel readBoard = column();
    addSection(readBoard, sectionTitle(text("DiagnosticsDialog.readBoardPane", "ReadBoard")));
    addSection(
        readBoard,
        metaRow(text("DiagnosticsDialog.helperCapability", "Logging support"), helperCapability));
    addSection(
        readBoard,
        metaRow(text("DiagnosticsDialog.helperPersistence", "Persistence"), helperPersistence));
    addSection(
        readBoard,
        metaRow(text("DiagnosticsDialog.helperDropCount", "Dropped events"), helperDropCount));
    addSection(
        readBoard,
        metaRow(
            text("DiagnosticsDialog.helperProcessSession", "Process session"),
            helperProcessSession));
    addSection(
        readBoard,
        helperRow(
            text("DiagnosticsDialog.helperDiagnostics", "Diagnostics"),
            helperDiagnostics,
            helperDiagnosticsObserved));
    addSection(
        readBoard,
        helperRow(
            text("DiagnosticsDialog.helperFullTrace", "Full Logs"),
            helperTrace,
            helperTraceObserved));
    addSection(
        readBoard,
        helperRow(
            text("DiagnosticsDialog.helperCapture", "Capture screenshots"),
            helperCapture,
            helperCaptureObserved));
    addSection(readBoard, helperCaptureSummary);

    JPanel logs = column();
    addSection(logs, sectionTitle(text("DiagnosticsDialog.logsFolder", "Logs")));
    addSection(logs, logsPath);
    addSection(logs, hostAppLog);
    addSection(logs, hostCrashLog);
    addSection(logs, persistenceLabel);
    addSection(logs, streamList);
    addSection(logs, estimateLabel);
    addSection(logs, statusArea);

    JPanel processes = new JPanel(new GridLayout(1, 2, 8, 8));
    processes.setOpaque(false);
    processes.add(sectionCard(host, buttonBar(apply)));
    processes.add(sectionCard(readBoard, null));

    JPanel page = new JPanel(new BorderLayout(0, 8));
    page.setOpaque(false);
    page.add(processes, BorderLayout.CENTER);
    page.add(sectionCard(logs, buttonBar(openLogs, cancel, exportDefault)), BorderLayout.SOUTH);

    JScrollPane scroll = new JScrollPane(page);
    scroll.setBorder(null);
    scroll.setOpaque(false);
    scroll.getViewport().setOpaque(false);
    add(scroll, BorderLayout.CENTER);
    refreshFromRuntime();
  }

  void applyCurrentPlan() {
    LoggingSettings next =
        runtime
            .settings()
            .withDiagnosticsEnabled(diagnosticsEnabled.isSelected())
            .withDiagnosticModules(selectedModules())
            .withPreferredTraceScopes(selectedScopes());
    try {
      if (config != null) {
        runtime.applySettings(next, config::saveLoggingSettings);
      } else {
        runtime.applySettings(next);
      }
      setStatus(text("DiagnosticsDialog.applied", "Applied"));
    } catch (RuntimeException e) {
      refreshFromRuntime();
      setStatus(text("DiagnosticsDialog.applyFailed", "Apply failed") + ": " + e.getMessage());
      return;
    }
    if (fullLogsEnabled.isSelected()) {
      if (!runtime.fullTraceActive()) {
        startFullTraceFromUi();
      }
    } else if (runtime.fullTraceActive()) {
      stopFullTraceFromUi();
    }
  }

  void startFullTraceFromUi() {
    if (!confirmStart()) {
      refreshFromRuntime();
      return;
    }
    LoggingSettings next = runtime.settings().withPreferredTraceScopes(selectedScopes());
    try {
      if (config != null) {
        runtime.applySettings(next, config::saveLoggingSettings);
      } else {
        runtime.applySettings(next);
      }
    } catch (RuntimeException e) {
      refreshFromRuntime();
      setStatus(text("DiagnosticsDialog.applyFailed", "Apply failed") + ": " + e.getMessage());
      return;
    }
    runtime.startFullTrace(selectedScopes());
    titleRefresh.run();
    refreshFromRuntime();
  }

  void stopFullTraceFromUi() {
    runtime.stopFullTrace();
    titleRefresh.run();
    refreshFromRuntime();
  }

  Path exportSynchronously() throws IOException {
    cancelExport.set(false);
    Path zip = exporter.export(currentRequest(), cancelExport::get);
    folderOpener.accept(zip.getParent());
    refreshEstimate();
    return zip;
  }

  DiagnosticBundleRequest currentRequest() {
    Set<TraceScope> raw =
        runtime.fullTraceActive()
            ? runtime.activeTraceScopes()
            : EnumSet.noneOf(TraceScope.class);
    ReadBoardLoggingSnapshot helper = helperLogging.snapshot();
    boolean includeReadBoardTrace =
        helper.desired().trace
            || helper.observedTrace() == ReadBoardLoggingProtocol.Toggle.ON;
    return new DiagnosticBundleRequest(
        runtime,
        raw,
        includeReadBoardTrace,
        true,
        config == null ? new org.json.JSONObject() : config.config,
        SyncDiagnosticsRecorder.getDefault().exportSnapshot(),
        helper,
        Lizzie.nextVersion == null ? "unknown" : Lizzie.nextVersion,
        "unknown");
  }

  String healthText() {
    return renderHealth();
  }

  String statusText() {
    return statusArea.getText();
  }

  String estimateText() {
    return estimateLabel.getText();
  }

  JCheckBox diagnosticsEnabledBox() {
    return diagnosticsEnabled;
  }

  JCheckBox fullLogsEnabledBox() {
    return fullLogsEnabled;
  }

  JButton cancelButton() {
    return cancel;
  }

  JCheckBox scopeEngineBox() {
    return scopeEngine;
  }

  String confirmBody() {
    return text(
            "DiagnosticsDialog.confirmMessage",
            "Selected scopes record game and protocol content. While this is on, exporting a package includes those full logs. Retention is 7 days and 100 MB per log class.")
        + "\n"
        + selectedScopeLabels();
  }

  void openLogsDirectory() {
    folderOpener.accept(runtime.logsDirectory());
  }

  String hostSessionText() {
    return hostSessionLabel.getText();
  }

  String hostAppLogText() {
    return hostAppLog.getText();
  }

  String hostCrashLogText() {
    return hostCrashLog.getText();
  }

  String hostPaneText() {
    return text("DiagnosticsDialog.hostPane", "LizzieYzy")
        + '\n'
        + hostSessionText()
        + '\n'
        + text("DiagnosticsDialog.diagnosticsEnabled", "Diagnostic recording")
        + '\n'
        + text("DiagnosticsDialog.module.engine", "Engine")
        + '\n'
        + text("DiagnosticsDialog.module.gtpSummary", "GTP Summary")
        + '\n'
        + text("DiagnosticsDialog.module.readboardYike", "ReadBoard/Yike")
        + '\n'
        + text("DiagnosticsDialog.module.networkRemote", "Network/Remote")
        + '\n'
        + text("DiagnosticsDialog.fullTrace", "Full Logs")
        + '\n'
        + text("DiagnosticsDialog.scope.engineGtp", "Engine/GTP")
        + '\n'
        + text("DiagnosticsDialog.scope.readboardYike", "ReadBoard/Yike")
        + '\n'
        + text("DiagnosticsDialog.scope.networkWebsocket", "Network/WebSocket");
  }

  String helperPaneText() {
    return text("DiagnosticsDialog.readBoardPane", "ReadBoard")
        + '\n'
        + text("DiagnosticsDialog.helperDiagnostics", "Diagnostics")
        + '\n'
        + text("DiagnosticsDialog.helperFullTrace", "Full Logs")
        + '\n'
        + text("DiagnosticsDialog.helperCapture", "Capture screenshots");
  }

  String helperCapabilityText() {
    return helperCapability.getText();
  }

  String helperPersistenceText() {
    return helperPersistence.getText();
  }

  String helperDropCountText() {
    return helperDropCount.getText();
  }

  String helperProcessSessionText() {
    return helperProcessSession.getText();
  }

  String helperDiagnosticsObservedText() {
    return helperDiagnosticsObserved.getText();
  }

  String helperTraceObservedText() {
    return helperTraceObserved.getText();
  }

  String helperCaptureObservedText() {
    return helperCaptureObserved.getText();
  }

  JCheckBox helperDiagnosticsBox() {
    return helperDiagnostics;
  }

  JCheckBox helperTraceBox() {
    return helperTrace;
  }

  JCheckBox helperCaptureBox() {
    return helperCapture;
  }

  String captureConfirmBody() {
    return text(
        "DiagnosticsDialog.captureConfirmMessage",
        "Screenshot capture writes images and recognition artifacts for this ReadBoard process only. It is not Diagnostics or Full Logs, and it will not stay on after restart. Continue?");
  }

  void refreshFromRuntime() {
    LoggingSettings settings = runtime.settings();
    diagnosticsEnabled.setSelected(settings.diagnosticsEnabled());
    moduleEngine.setSelected(settings.diagnosticModules().contains(DiagnosticModule.ENGINE));
    moduleGtp.setSelected(settings.diagnosticModules().contains(DiagnosticModule.GTP_SUMMARY));
    moduleReadBoard.setSelected(
        settings.diagnosticModules().contains(DiagnosticModule.READBOARD_YIKE));
    moduleNetwork.setSelected(
        settings.diagnosticModules().contains(DiagnosticModule.NETWORK_REMOTE));
    fullLogsEnabled.setSelected(runtime.fullTraceActive());
    scopeEngine.setSelected(settings.preferredTraceScopes().contains(TraceScope.ENGINE_GTP));
    scopeReadBoard.setSelected(settings.preferredTraceScopes().contains(TraceScope.READBOARD_YIKE));
    scopeNetwork.setSelected(settings.preferredTraceScopes().contains(TraceScope.NETWORK_WEBSOCKET));
    setModulesEnabled(settings.diagnosticsEnabled());
    logsPath.setText(runtime.logsDirectory().toAbsolutePath().toString());
    hostAppLog.setText("app.log: " + runtime.logsDirectory().resolve("app.log").toAbsolutePath());
    hostCrashLog.setText(
        "crash.log: " + runtime.logsDirectory().resolve("crash.log").toAbsolutePath());
    hostSessionLabel.setText(
        text("DiagnosticsDialog.hostSession", "Host session")
            + ": "
            + runtime.applicationLogSessionId());
    hostSessionLabel.setToolTipText(hostSessionLabel.getText());
    logsPath.setToolTipText(logsPath.getText());
    LoggingStatus status = runtime.status();
    persistenceLabel.setText(
        format(
            "DiagnosticsDialog.persistenceEnabled",
            "Persistence: {0}",
            status.persistenceEnabled()
                ? text("DiagnosticsDialog.presentation.on", "On")
                : text("DiagnosticsDialog.presentation.off", "Off")));
    streamList.removeAll();
    int row = 0;
    for (LoggingStatus.StreamStatus stream : status.streams()) {
      Color color = stream.reason() == null ? INK : DANGER;
      streamList.add(streamName(stream), streamConstraint(0, row, 0));
      JFontLabel state = new JFontLabel(streamStatusText(stream));
      state.setForeground(color);
      streamList.add(state, streamConstraint(1, row, 1));
      JFontLabel dropped =
          new JFontLabel(
              format("DiagnosticsDialog.dropped", "dropped {0}", stream.droppedCount()));
      dropped.setForeground(color);
      streamList.add(dropped, streamConstraint(2, row, 0));
      row++;
    }
    GridBagConstraints glue = streamConstraint(0, row, 1);
    glue.gridwidth = 3;
    glue.weighty = 1;
    glue.fill = GridBagConstraints.VERTICAL;
    streamList.add(Box.createVerticalGlue(), glue);
    streamList.revalidate();
    streamList.repaint();
    refreshDuration();
    refreshHelperFromSnapshot();
    refreshEstimate();
  }

  String durationText() {
    return durationLabel.getText();
  }

  private void requestHelperToggle(HelperField field) {
    ReadBoardLoggingSnapshot current = helperLogging.snapshot();
    boolean diagnostics = helperDiagnostics.isSelected();
    boolean capture = helperCapture.isSelected();
    boolean trace = helperTrace.isSelected();
    if (field == HelperField.CAPTURE && capture && !current.desired().capture) {
      if (!confirmCapture()) {
        helperCapture.setSelected(false);
        return;
      }
    }
    if (!helperLogging.requestSet(diagnostics, capture, trace)) {
      refreshHelperFromSnapshot();
      return;
    }
    refreshHelperFromSnapshot();
  }

  private boolean confirmCapture() {
    if (captureConfirmer != null) {
      return captureConfirmer.getAsBoolean();
    }
    int choice =
        JOptionPane.showConfirmDialog(
            this,
            captureConfirmBody(),
            text("DiagnosticsDialog.captureConfirmTitle", "Enable ReadBoard capture?"),
            JOptionPane.OK_CANCEL_OPTION,
            JOptionPane.WARNING_MESSAGE);
    return choice == JOptionPane.OK_OPTION;
  }

  private void refreshHelperFromSnapshot() {
    ReadBoardLoggingSnapshot snapshot = helperLogging.snapshot();
    helperCapability.setText(capabilityText(snapshot));
    helperPersistence.setText(persistenceText(snapshot.persistence()));
    helperDropCount.setText(
        snapshot.dropCount() < 0 ? "—" : Integer.toString(snapshot.dropCount()));
    helperProcessSession.setText(
        snapshot.processSessionId().isEmpty()
            ? text("DiagnosticsDialog.capability.none", "none")
            : snapshot.processSessionId());
    helperProcessSession.setToolTipText(helperProcessSession.getText());
    helperDiagnostics.setSelected(snapshot.desired().diagnostics);
    helperTrace.setSelected(snapshot.desired().trace);
    helperCapture.setSelected(snapshot.desired().capture);
    helperDiagnosticsObserved.setText(presentationText(snapshot.diagnosticsPresentation()));
    helperTraceObserved.setText(presentationText(snapshot.tracePresentation()));
    helperCaptureObserved.setText(presentationText(snapshot.capturePresentation()));
    helperCaptureSummary.setText(
        text("DiagnosticsDialog.captureSummary", "Capture session")
            + ": "
            + formatCaptureSummary(snapshot));
  }

  private static String capabilityText(ReadBoardLoggingSnapshot snapshot) {
    if (snapshot.status() == ReadBoardLoggingControl.Status.LEGACY_UNCONFIRMED
        || snapshot.diagnosticsPresentation()
            == ReadBoardLoggingControl.Presentation.LEGACY_UNCONFIRMED) {
      return text("DiagnosticsDialog.presentation.legacy", "Legacy, unconfirmed");
    }
    if (snapshot.capabilityKnown()) {
      return text("DiagnosticsDialog.capability.ready", "ready");
    }
    return text("DiagnosticsDialog.capability.none", "none");
  }

  private static String presentationText(ReadBoardLoggingControl.Presentation presentation) {
    if (presentation == null) {
      return text("DiagnosticsDialog.presentation.unknown", "Unknown");
    }
    switch (presentation) {
      case ON:
        return text("DiagnosticsDialog.presentation.on", "On");
      case ON_STORAGE_DEGRADED:
        return text("DiagnosticsDialog.presentation.onDegraded", "On, storage degraded");
      case ON_STORAGE_UNAVAILABLE:
        return text("DiagnosticsDialog.presentation.onUnavailable", "On, storage unavailable");
      case NOT_APPLIED:
        return text("DiagnosticsDialog.presentation.notApplied", "Not applied");
      case LEGACY_UNCONFIRMED:
        return text("DiagnosticsDialog.presentation.legacy", "Legacy, unconfirmed");
      case OFF:
        return text("DiagnosticsDialog.presentation.off", "Off");
      case UNKNOWN:
      default:
        return text("DiagnosticsDialog.presentation.unknown", "Unknown");
    }
  }

  private static JPanel helperRow(String name, JCheckBox desired, JLabel observed) {
    JPanel row = checkRow(name, desired, false, observed);
    return row;
  }

  private static JLabel sectionTitle(String name) {
    JFontLabel label = new JFontLabel(name);
    label.setFont(label.getFont().deriveFont(Font.BOLD));
    return label;
  }

  interface HelperLogging {
    ReadBoardLoggingSnapshot snapshot();

    boolean requestSet(boolean diagnostics, boolean capture, boolean trace);
  }

  private enum HelperField {
    DIAGNOSTICS,
    TRACE,
    CAPTURE
  }

  private static final class LiveHelperLogging implements HelperLogging {
    @Override
    public ReadBoardLoggingSnapshot snapshot() {
      ReadBoard board = Lizzie.frame == null ? null : Lizzie.frame.readBoard;
      return board == null ? ReadBoardLoggingSnapshot.detached() : board.loggingSnapshot();
    }

    @Override
    public boolean requestSet(boolean diagnostics, boolean capture, boolean trace) {
      ReadBoard board = Lizzie.frame == null ? null : Lizzie.frame.readBoard;
      return board != null && board.requestLoggingSet(diagnostics, capture, trace);
    }
  }

  private void refreshDuration() {
    Instant started = runtime.fullTraceStartedAt();
    boolean tracing = runtime.fullTraceActive() && started != null;
    durationLabel.setText(
        tracing
            ? text("DiagnosticsDialog.duration", "Duration")
                + " "
                + Duration.between(started, Instant.now()).toSeconds()
                + "s"
            : text("DiagnosticsDialog.traceOff", "Off"));
    if (tracing && isShowing()) {
      if (!durationClock.isRunning()) {
        durationClock.start();
      }
    } else if (durationClock.isRunning()) {
      durationClock.stop();
    }
  }

  private String renderHealth() {
    return text("DiagnosticsDialog.logsFolder", "Logs")
        + ": "
        + runtime.logsDirectory()
        + '\n'
        + text("DiagnosticsDialog.diagnosticsFolder", "Diagnostics")
        + ": "
        + DiagnosticBundleExporter.defaultOutputDirectory(runtime.logsDirectory().getParent())
        + '\n'
        + persistenceLabel.getText()
        + '\n'
        + renderStreams();
  }

  private String renderStreams() {
    StringBuilder body = new StringBuilder();
    for (LoggingStatus.StreamStatus stream : runtime.status().streams()) {
      body.append(streamLine(stream)).append('\n');
    }
    return body.toString();
  }

  private static String streamLine(LoggingStatus.StreamStatus stream) {
    if (stream.reason() == null) {
      return format(
          "DiagnosticsDialog.streamHealthy",
          "{0}: healthy, dropped {1}",
          stream.stream(),
          stream.droppedCount());
    }
    return format(
        "DiagnosticsDialog.streamUnhealthy",
        "{0}: {1}, dropped {2}, recovered {3}, first {4}, last {5}",
        stream.stream(),
        stream.reason(),
        stream.droppedCount(),
        stream.recovered()
            ? text("DiagnosticsDialog.yes", "yes")
            : text("DiagnosticsDialog.no", "no"),
        stream.firstOccurrence(),
        stream.lastOccurrence());
  }

  private void refreshEstimate() {
    try {
      long bytes = exporter.estimateUncompressedBytes(currentRequest());
      estimateLabel.setText(
          text("DiagnosticsDialog.estimate", "Estimated size")
              + ": "
              + String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0)));
    } catch (IOException e) {
      estimateLabel.setText("");
    }
  }

  private Set<DiagnosticModule> selectedModules() {
    EnumSet<DiagnosticModule> modules = EnumSet.noneOf(DiagnosticModule.class);
    if (moduleEngine.isSelected()) {
      modules.add(DiagnosticModule.ENGINE);
    }
    if (moduleGtp.isSelected()) {
      modules.add(DiagnosticModule.GTP_SUMMARY);
    }
    if (moduleReadBoard.isSelected()) {
      modules.add(DiagnosticModule.READBOARD_YIKE);
    }
    if (moduleNetwork.isSelected()) {
      modules.add(DiagnosticModule.NETWORK_REMOTE);
    }
    return modules;
  }

  private Set<TraceScope> selectedScopes() {
    EnumSet<TraceScope> scopes = EnumSet.noneOf(TraceScope.class);
    if (scopeEngine.isSelected()) {
      scopes.add(TraceScope.ENGINE_GTP);
    }
    if (scopeReadBoard.isSelected()) {
      scopes.add(TraceScope.READBOARD_YIKE);
    }
    if (scopeNetwork.isSelected()) {
      scopes.add(TraceScope.NETWORK_WEBSOCKET);
    }
    if (scopes.isEmpty()) {
      return EnumSet.allOf(TraceScope.class);
    }
    return scopes;
  }

  private void exportPackageOffEdt() {
    cancelExport.set(false);
    DiagnosticBundleRequest request = currentRequest();
    exportDefault.setEnabled(false);
    cancel.setVisible(true);
    setStatus(text("DiagnosticsDialog.exporting", "Exporting..."));
    revalidate();
    Thread worker =
        new Thread(
            () -> {
              try {
                Path zip = exporter.export(request, cancelExport::get);
                SwingUtilities.invokeLater(
                    () -> {
                      folderOpener.accept(zip.getParent());
                      finishExport(
                          text("DiagnosticsDialog.exportSuccess", "Exported to:")
                              + " "
                              + zip.getFileName());
                    });
              } catch (Exception e) {
                SwingUtilities.invokeLater(
                    () ->
                        finishExport(
                            text("DiagnosticsDialog.exportFailure", "Export failed:")
                                + " "
                                + e.getMessage()));
              }
            },
            "diagnostic-export");
    worker.setDaemon(true);
    worker.start();
  }

  private void finishExport(String message) {
    exportDefault.setEnabled(true);
    cancel.setVisible(false);
    cancelExport.set(false);
    setStatus(message);
    refreshEstimate();
    revalidate();
  }

  private void setModulesEnabled(boolean enabled) {
    moduleEngine.setEnabled(enabled);
    moduleGtp.setEnabled(enabled);
    moduleReadBoard.setEnabled(enabled);
    moduleNetwork.setEnabled(enabled);
  }

  private void setStatus(String value) {
    statusArea.setText(value == null ? "" : value);
  }

  private static void refreshFrameTitle() {
    if (Lizzie.frame != null) {
      Lizzie.frame.updateTitle();
    }
  }

  private boolean confirmStart() {
    if (fullTraceConfirmer != null) {
      return fullTraceConfirmer.getAsBoolean();
    }
    int choice =
        JOptionPane.showConfirmDialog(
            Lizzie.frame,
            confirmBody(),
            text("DiagnosticsDialog.confirmTitle", "Start Full Logs?"),
            JOptionPane.OK_CANCEL_OPTION,
            JOptionPane.WARNING_MESSAGE);
    return choice == JOptionPane.OK_OPTION;
  }

  private String selectedScopeLabels() {
    StringBuilder labels = new StringBuilder();
    appendScopeLabel(labels, scopeEngine, "DiagnosticsDialog.scope.engineGtp", "Engine/GTP");
    appendScopeLabel(
        labels, scopeReadBoard, "DiagnosticsDialog.scope.readboardYike", "ReadBoard/Yike");
    appendScopeLabel(
        labels, scopeNetwork, "DiagnosticsDialog.scope.networkWebsocket", "Network/WebSocket");
    return labels.length() == 0
        ? text("DiagnosticsDialog.scope.engineGtp", "Engine/GTP")
            + ", "
            + text("DiagnosticsDialog.scope.readboardYike", "ReadBoard/Yike")
            + ", "
            + text("DiagnosticsDialog.scope.networkWebsocket", "Network/WebSocket")
        : labels.toString();
  }

  private static void appendScopeLabel(
      StringBuilder labels, JCheckBox box, String key, String fallback) {
    if (!box.isSelected()) {
      return;
    }
    if (labels.length() > 0) {
      labels.append(", ");
    }
    labels.append(text(key, fallback));
  }

  private static void openFolder(Path directory) {
    try {
      if (Desktop.isDesktopSupported()) {
        java.nio.file.Files.createDirectories(directory);
        Desktop.getDesktop().open(directory.toFile());
      }
    } catch (IOException ignored) {
    }
  }

  private static JCheckBox box() {
    return new JFontCheckBox("");
  }

  private static JPanel checkRow(String name, JCheckBox box, boolean child, JComponent extra) {
    JFontLabel label = new JFontLabel(name);
    if (!child) {
      label.setFont(label.getFont().deriveFont(Font.BOLD));
    }
    JPanel east = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
    east.setOpaque(false);
    if (extra != null) {
      east.add(extra);
    }
    east.add(box);
    JPanel row = new JPanel(new BorderLayout(12, 0));
    row.setOpaque(false);
    row.setBorder(new EmptyBorder(2, child ? 20 : 0, 2, 0));
    row.add(label, BorderLayout.CENTER);
    row.add(east, BorderLayout.EAST);
    return row;
  }

  private static JPanel metaRow(String name, JLabel value) {
    JPanel row = new JPanel(new BorderLayout(12, 0));
    row.setOpaque(false);
    row.setBorder(new EmptyBorder(2, 0, 2, 0));
    JFontLabel label = new JFontLabel(name);
    label.setForeground(SECONDARY);
    value.setHorizontalAlignment(JLabel.RIGHT);
    row.add(label, BorderLayout.WEST);
    row.add(value, BorderLayout.EAST);
    return row;
  }

  private static JPanel buttonBar(JComponent... buttons) {
    JPanel bar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
    bar.setOpaque(false);
    for (JComponent button : buttons) {
      bar.add(button);
    }
    return bar;
  }

  private static void sizeButton(JButton button, int width) {
    Dimension size = new Dimension(width, 28);
    button.setPreferredSize(size);
    button.setMinimumSize(size);
    button.setMargin(new Insets(2, 10, 2, 10));
  }

  private static void addSection(JPanel parent, JComponent child) {
    child.setAlignmentX(LEFT_ALIGNMENT);
    if (parent.getComponentCount() > 0) {
      parent.add(Box.createVerticalStrut(4));
    }
    parent.add(child);
  }

  private static JPanel column() {
    JPanel panel = new JPanel();
    panel.setOpaque(false);
    panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
    return panel;
  }

  private static JPanel sectionCard(JPanel body, JComponent footer) {
    JPanel card = new JPanel(new BorderLayout(0, 8));
    card.setOpaque(false);
    card.setBorder(
        BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(HAIRLINE), new EmptyBorder(10, 12, 10, 12)));
    body.setOpaque(false);
    card.add(body, BorderLayout.NORTH);
    if (footer != null) {
      card.add(footer, BorderLayout.SOUTH);
    }
    return card;
  }

  private static JLabel streamName(LoggingStatus.StreamStatus stream) {
    JFontLabel name = new JFontLabel(stream.stream().name());
    name.setForeground(stream.reason() == null ? INK : DANGER);
    return name;
  }

  private static String streamStatusText(LoggingStatus.StreamStatus stream) {
    return stream.reason() == null
        ? text("DiagnosticsDialog.persistence.healthy", "Healthy")
        : stream.reason();
  }

  private static GridBagConstraints streamConstraint(int column, int row, double weightx) {
    GridBagConstraints constraints = new GridBagConstraints();
    constraints.gridx = column;
    constraints.gridy = row;
    constraints.weightx = weightx;
    constraints.fill = GridBagConstraints.HORIZONTAL;
    constraints.anchor = GridBagConstraints.WEST;
    constraints.insets = new Insets(1, 0, 1, column < 2 ? 12 : 0);
    return constraints;
  }


  private static String formatCaptureSummary(ReadBoardLoggingSnapshot snapshot) {
    if (snapshot.processSessionId() == null || snapshot.processSessionId().isEmpty()) {
      return text("DiagnosticsDialog.captureSummary.none", "no capture session");
    }
    return format(
        "DiagnosticsDialog.captureSummaryDetail",
        "process session={0}, desired={1}, observed={2}, persistence={3}",
        snapshot.processSessionId(),
        snapshot.desired().capture
            ? text("DiagnosticsDialog.presentation.on", "On")
            : text("DiagnosticsDialog.presentation.off", "Off"),
        toggleText(snapshot.observedCapture()),
        persistenceText(snapshot.persistence()));
  }

  private static String persistenceText(ReadBoardLoggingProtocol.Persistence persistence) {
    if (persistence == null) {
      return text("DiagnosticsDialog.capability.none", "none");
    }
    switch (persistence) {
      case HEALTHY:
        return text("DiagnosticsDialog.persistence.healthy", "Healthy");
      case DEGRADED:
        return text("DiagnosticsDialog.persistence.degraded", "Degraded");
      case UNAVAILABLE:
        return text("DiagnosticsDialog.persistence.unavailable", "Unavailable");
      default:
        return persistence.name().toLowerCase(Locale.ROOT).replace('_', '-');
    }
  }

  private static String toggleText(ReadBoardLoggingProtocol.Toggle toggle) {
    if (toggle == null) {
      return text("DiagnosticsDialog.presentation.unknown", "Unknown");
    }
    switch (toggle) {
      case ON:
        return text("DiagnosticsDialog.presentation.on", "On");
      case OFF:
        return text("DiagnosticsDialog.presentation.off", "Off");
      case UNKNOWN:
      default:
        return text("DiagnosticsDialog.presentation.unknown", "Unknown");
    }
  }

  private static String format(String key, String fallback, Object... arguments) {
    return MessageFormat.format(text(key, fallback), arguments);
  }

  private static String text(String key, String fallback) {
    try {
      if (Lizzie.resourceBundle != null) {
        return Lizzie.resourceBundle.getString(key);
      }
    } catch (MissingResourceException ignored) {
    }
    return fallback;
  }
}
