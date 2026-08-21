package featurecat.lizzie.gui;

import featurecat.lizzie.Config;
import featurecat.lizzie.Lizzie;
import featurecat.lizzie.analysis.SyncDiagnosticsRecorder;
import featurecat.lizzie.logging.DiagnosticBundleExporter;
import featurecat.lizzie.logging.DiagnosticBundleRequest;
import featurecat.lizzie.logging.DiagnosticModule;
import featurecat.lizzie.logging.LoggingRuntime;
import featurecat.lizzie.logging.LoggingSettings;
import featurecat.lizzie.logging.LoggingStatus;
import featurecat.lizzie.logging.TraceScope;
import java.awt.BorderLayout;
import java.awt.Desktop;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Window;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.MissingResourceException;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;

public class DiagnosticsDialog extends JPanel {
  private static final long serialVersionUID = 1L;

  private final LoggingRuntime runtime;
  private final Config config;
  private final DiagnosticBundleExporter exporter;
  private final Runnable titleRefresh;
  private final BooleanSupplier fullTraceConfirmer;
  private final Consumer<Path> folderOpener;
  private final JCheckBox diagnosticsEnabled =
      new JCheckBox(text("DiagnosticsDialog.diagnosticsEnabled", "Diagnostics"));
  private final JCheckBox moduleEngine =
      new JCheckBox(text("DiagnosticsDialog.module.engine", "Engine"));
  private final JCheckBox moduleGtp =
      new JCheckBox(text("DiagnosticsDialog.module.gtpSummary", "GTP Summary"));
  private final JCheckBox moduleReadBoard =
      new JCheckBox(text("DiagnosticsDialog.module.readboardYike", "ReadBoard/Yike"));
  private final JCheckBox moduleNetwork =
      new JCheckBox(text("DiagnosticsDialog.module.networkRemote", "Network/Remote"));
  private final JCheckBox scopeEngine =
      new JCheckBox(text("DiagnosticsDialog.scope.engineGtp", "Engine/GTP"));
  private final JCheckBox scopeReadBoard =
      new JCheckBox(text("DiagnosticsDialog.scope.readboardYike", "ReadBoard/Yike"));
  private final JCheckBox scopeNetwork =
      new JCheckBox(text("DiagnosticsDialog.scope.networkWebsocket", "Network/WebSocket"));
  private final JCheckBox includeRaw =
      new JCheckBox(text("DiagnosticsDialog.exportRaw", "Include current Full Trace"));
  private final JTextArea healthArea = new JTextArea(8, 60);
  private final JTextArea statusArea = new JTextArea(2, 60);
  private final JLabel durationLabel =
      new JLabel(text("DiagnosticsDialog.duration", "Duration") + ": —");
  private final JLabel estimateLabel = new JLabel("");
  private final AtomicBoolean cancelExport = new AtomicBoolean();
  private Instant traceStartedAt;

  public static JDialog open(Window owner, LoggingRuntime runtime, Config config) {
    JDialog dialog = new JDialog(owner);
    dialog.setTitle(text("DiagnosticsDialog.title", "Diagnostics and Logs"));
    dialog.setModalityType(JDialog.ModalityType.MODELESS);
    dialog.setDefaultCloseOperation(JDialog.HIDE_ON_CLOSE);
    dialog.setContentPane(new DiagnosticsDialog(runtime, config));
    dialog.setSize(820, 560);
    dialog.setLocationRelativeTo(owner);
    dialog.setVisible(true);
    return dialog;
  }

  public DiagnosticsDialog(LoggingRuntime runtime, Config config) {
    this(
        runtime,
        config,
        new DiagnosticBundleExporter(
            DiagnosticBundleExporter.defaultOutputDirectory(runtime.logsDirectory().getParent())),
        DiagnosticsDialog::refreshFrameTitle,
        DiagnosticsDialog::confirmFullTrace,
        DiagnosticsDialog::openFolder);
  }

  DiagnosticsDialog(
      LoggingRuntime runtime,
      Config config,
      DiagnosticBundleExporter exporter,
      Runnable titleRefresh,
      BooleanSupplier fullTraceConfirmer,
      Consumer<Path> folderOpener) {
    super(new BorderLayout(8, 8));
    this.runtime = runtime;
    this.config = config;
    this.exporter = exporter;
    this.titleRefresh = titleRefresh == null ? () -> {} : titleRefresh;
    this.fullTraceConfirmer = fullTraceConfirmer == null ? () -> true : fullTraceConfirmer;
    this.folderOpener = folderOpener == null ? path -> {} : folderOpener;

    healthArea.setEditable(false);
    statusArea.setEditable(false);

    JPanel modules = new JPanel(new GridLayout(0, 2, 8, 4));
    modules.setBorder(
        BorderFactory.createTitledBorder(text("DiagnosticsDialog.diagnosticsEnabled", "Diagnostics")));
    modules.add(diagnosticsEnabled);
    modules.add(moduleEngine);
    modules.add(moduleGtp);
    modules.add(moduleReadBoard);
    modules.add(moduleNetwork);

    JPanel scopes = new JPanel(new GridLayout(0, 2, 8, 4));
    scopes.setBorder(
        BorderFactory.createTitledBorder(text("DiagnosticsDialog.fullTrace", "Full Trace")));
    scopes.add(scopeEngine);
    scopes.add(scopeReadBoard);
    scopes.add(scopeNetwork);
    scopes.add(durationLabel);

    JButton apply = new JButton(text("DiagnosticsDialog.apply", "Apply"));
    JButton start = new JButton(text("DiagnosticsDialog.startFullTrace", "Start Full Trace"));
    JButton stop = new JButton(text("DiagnosticsDialog.stopFullTrace", "Stop Full Trace"));
    JButton openLogs = new JButton(text("DiagnosticsDialog.openLogs", "Open log folder"));
    JButton openDiagnostics =
        new JButton(text("DiagnosticsDialog.openDiagnostics", "Open diagnostics folder"));
    JButton exportDefault =
        new JButton(text("DiagnosticsDialog.exportDefault", "Export default package"));
    JButton cancel = new JButton(text("DiagnosticsDialog.cancelExport", "Cancel export"));

    apply.addActionListener(e -> applyCurrentPlan());
    diagnosticsEnabled.addActionListener(e -> applyCurrentPlan());
    moduleEngine.addActionListener(e -> applyCurrentPlan());
    moduleGtp.addActionListener(e -> applyCurrentPlan());
    moduleReadBoard.addActionListener(e -> applyCurrentPlan());
    moduleNetwork.addActionListener(e -> applyCurrentPlan());
    scopeEngine.addActionListener(e -> applyCurrentPlan());
    scopeReadBoard.addActionListener(e -> applyCurrentPlan());
    scopeNetwork.addActionListener(e -> applyCurrentPlan());
    start.addActionListener(e -> startFullTraceFromUi());
    stop.addActionListener(e -> stopFullTraceFromUi());
    openLogs.addActionListener(e -> folderOpener.accept(runtime.logsDirectory()));
    openDiagnostics.addActionListener(
        e ->
            folderOpener.accept(
                DiagnosticBundleExporter.defaultOutputDirectory(
                    runtime.logsDirectory().getParent())));
    exportDefault.addActionListener(e -> exportPackageOffEdt());
    cancel.addActionListener(e -> cancelExport.set(true));
    includeRaw.addActionListener(e -> refreshEstimate());

    JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
    buttons.add(apply);
    buttons.add(start);
    buttons.add(stop);
    buttons.add(openLogs);
    buttons.add(openDiagnostics);
    buttons.add(exportDefault);
    buttons.add(cancel);
    buttons.add(includeRaw);
    buttons.add(estimateLabel);

    JLabel migration =
        new JLabel(
            text(
                "DiagnosticsDialog.gtpMigrationNote",
                "Legacy GTP file logging now provides GTP Summary. Raw GTP requires temporary explicit Full Trace."));

    JPanel content = new JPanel(new BorderLayout(8, 8));
    content.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
    JPanel north = new JPanel(new BorderLayout(8, 8));
    north.add(modules, BorderLayout.NORTH);
    north.add(scopes, BorderLayout.CENTER);
    north.add(migration, BorderLayout.SOUTH);
    content.add(north, BorderLayout.NORTH);
    content.add(new JScrollPane(healthArea), BorderLayout.CENTER);
    JPanel south = new JPanel(new BorderLayout(4, 4));
    south.add(statusArea, BorderLayout.CENTER);
    south.add(buttons, BorderLayout.SOUTH);
    content.add(south, BorderLayout.SOUTH);
    add(content);
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
    }
  }

  void startFullTraceFromUi() {
    if (!fullTraceConfirmer.getAsBoolean()) {
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
    traceStartedAt = Instant.now();
    titleRefresh.run();
    refreshFromRuntime();
  }

  void stopFullTraceFromUi() {
    runtime.stopFullTrace();
    traceStartedAt = null;
    titleRefresh.run();
    refreshFromRuntime();
  }

  Path exportSynchronously() throws IOException {
    cancelExport.set(false);
    try {
      return exporter.export(currentRequest(), cancelExport::get);
    } finally {
      includeRaw.setSelected(false);
      refreshEstimate();
    }
  }


  JCheckBox includeRawBox() {
    return includeRaw;
  }
  DiagnosticBundleRequest currentRequest() {
    Set<TraceScope> raw =
        includeRaw.isSelected() && runtime.fullTraceActive()
            ? selectedScopes()
            : EnumSet.noneOf(TraceScope.class);
    return new DiagnosticBundleRequest(
        runtime,
        raw,
        config == null ? new org.json.JSONObject() : config.config,
        SyncDiagnosticsRecorder.getDefault().exportSnapshot(),
        Lizzie.nextVersion == null ? "unknown" : Lizzie.nextVersion);
  }

  String healthText() {
    return healthArea.getText();
  }

  String statusText() {
    return statusArea.getText();
  }

  JCheckBox diagnosticsEnabledBox() {
    return diagnosticsEnabled;
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
    scopeEngine.setSelected(settings.preferredTraceScopes().contains(TraceScope.ENGINE_GTP));
    scopeReadBoard.setSelected(settings.preferredTraceScopes().contains(TraceScope.READBOARD_YIKE));
    scopeNetwork.setSelected(settings.preferredTraceScopes().contains(TraceScope.NETWORK_WEBSOCKET));
    includeRaw.setEnabled(runtime.fullTraceActive());
    if (!runtime.fullTraceActive()) {
      includeRaw.setSelected(false);
    }
    healthArea.setText(renderHealth());
    durationLabel.setText(
        text("DiagnosticsDialog.duration", "Duration")
            + ": "
            + (runtime.fullTraceActive() && traceStartedAt != null
                ? Duration.between(traceStartedAt, Instant.now()).toSeconds() + "s"
                : "—"));
    refreshEstimate();
  }

  private String renderHealth() {
    StringBuilder body = new StringBuilder();
    body.append(text("DiagnosticsDialog.logsFolder", "Logs"))
        .append(": ")
        .append(runtime.logsDirectory())
        .append('\n');
    body.append(text("DiagnosticsDialog.diagnosticsFolder", "Diagnostics"))
        .append(": ")
        .append(
            DiagnosticBundleExporter.defaultOutputDirectory(runtime.logsDirectory().getParent()))
        .append('\n');
    LoggingStatus status = runtime.status();
    body.append("persistenceEnabled=").append(status.persistenceEnabled()).append('\n');
    for (LoggingStatus.StreamStatus stream : status.streams()) {
      body.append(stream.stream())
          .append(" reason=")
          .append(stream.reason() == null ? "healthy" : stream.reason())
          .append(" dropped=")
          .append(stream.droppedCount())
          .append(" recovered=")
          .append(stream.recovered())
          .append(" first=")
          .append(stream.firstOccurrence())
          .append(" last=")
          .append(stream.lastOccurrence())
          .append('\n');
    }
    return body.toString();
  }

  private void refreshEstimate() {
    try {
      long bytes = exporter.estimateUncompressedBytes(currentRequest());
      estimateLabel.setText(text("DiagnosticsDialog.estimate", "Estimated size") + ": " + bytes);
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
    includeRaw.setSelected(false);
    refreshEstimate();
    setStatus(text("DiagnosticsDialog.exporting", "Exporting..."));
    Thread worker =
        new Thread(
            () -> {
              try {
                Path zip = exporter.export(request, cancelExport::get);
                SwingUtilities.invokeLater(
                    () ->
                        setStatus(
                            text("DiagnosticsDialog.exportSuccess", "Exported to:")
                                + " "
                                + zip.getFileName()));
              } catch (Exception e) {
                SwingUtilities.invokeLater(
                    () ->
                        setStatus(
                            text("DiagnosticsDialog.exportFailure", "Export failed:")
                                + " "
                                + e.getMessage()));
              }
            },
            "diagnostic-export");
    worker.setDaemon(true);
    worker.start();
  }

  private void setStatus(String value) {
    statusArea.setText(value == null ? "" : value);
  }

  private static void refreshFrameTitle() {
    if (Lizzie.frame != null) {
      Lizzie.frame.updateTitle();
    }
  }

  private static boolean confirmFullTrace() {
    int choice =
        JOptionPane.showConfirmDialog(
            Lizzie.frame,
            text(
                "DiagnosticsDialog.confirmMessage",
                "Selected scopes may record game and protocol content. Retention is 7 days and 100 MB per log class. Raw packages require a fresh explicit export choice."),
            text("DiagnosticsDialog.confirmTitle", "Start Full Trace?"),
            JOptionPane.OK_CANCEL_OPTION,
            JOptionPane.WARNING_MESSAGE);
    return choice == JOptionPane.OK_OPTION;
  }

  private static void openFolder(Path directory) {
    try {
      if (Desktop.isDesktopSupported()) {
        FilesCreate(directory);
        Desktop.getDesktop().open(directory.toFile());
      }
    } catch (IOException ignored) {
    }
  }

  private static void FilesCreate(Path directory) throws IOException {
    java.nio.file.Files.createDirectories(directory);
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
