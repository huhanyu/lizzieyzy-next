package featurecat.lizzie.gui;

import featurecat.lizzie.analysis.HumanSlStrengthPreparation;
import featurecat.lizzie.analysis.PlayerStrengthEstimator;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.Window;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.Locale;
import java.util.ResourceBundle;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.Timer;
import javax.swing.WindowConstants;
import javax.swing.filechooser.FileNameExtensionFilter;

/**
 * The player-strength dialog. It shows the dashboard for the current game, rebuilds it in place
 * when the model changes or HumanSL data arrives, and opens the detail view and the model import.
 */
final class StrengthDialog implements StrengthDashboard.Actions {
  private static final int REFRESH_MILLIS = 700;

  /** What the dialog needs from the main window. */
  interface Host {
    /** The estimate of the current game with the active model. */
    PlayerStrengthEstimator.Report report();

    PlayerStrengthEstimator.StrengthModel activeModel();

    void setActiveModel(PlayerStrengthEstimator.StrengthModel model);

    PlayerStrengthEstimator.StrengthModel[] models();

    PlayerStrengthEstimator.StrengthModel importModel(Path path) throws IOException;

    HumanSlStrengthPreparation.Status preparationStatus();

    /** A value that changes whenever the analysis or the HumanSL data behind the estimate does. */
    long preparationStamp();

    boolean backgroundEnabled();

    void setBackgroundEnabled(boolean enabled) throws IOException;

    /** Prepares the HumanSL data of the whole game now, showing its own progress. */
    void prepareNow();
  }

  private final Host host;
  private final StrengthStyle style;
  private final ResourceBundle bundle;
  private final StrengthDashboard dashboard;
  private Component window;
  private long shownStamp = Long.MIN_VALUE;

  StrengthDialog(Host host, StrengthStyle style, ResourceBundle bundle) {
    this.host = host;
    this.style = style;
    this.bundle = bundle;
    dashboard =
        new StrengthDashboard(
            host.report(), host.models(), host.backgroundEnabled(), style, bundle, this);
    showStatus();
  }

  /** Opens the dialog over {@code owner} and returns when it is closed. */
  static void show(Window owner, Host host, StrengthStyle style, ResourceBundle bundle) {
    StrengthDialog strength = new StrengthDialog(host, style, bundle);
    JDialog dialog =
        new JDialog(
            owner,
            bundle.getString("PlayerStrengthEstimate.title"),
            Dialog.ModalityType.APPLICATION_MODAL);
    strength.window = dialog;
    dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
    dialog.setContentPane(strength.dashboard);
    place(dialog, owner, style.px(1120), style.px(760), style.px(960), style.px(660));
    AccessibilitySupport.applyToTree(dialog.getContentPane());
    AccessibilitySupport.installEscapeAction(dialog.getRootPane(), dialog, dialog::dispose);
    Timer timer = new Timer(REFRESH_MILLIS, event -> strength.refresh());
    dialog.addWindowListener(
        new WindowAdapter() {
          @Override
          public void windowClosed(WindowEvent event) {
            timer.stop();
          }
        });
    timer.start();
    dialog.setVisible(true);
  }

  private static void place(
      JDialog dialog, Window owner, int width, int height, int minWidth, int minHeight) {
    dialog.setMinimumSize(new Dimension(minWidth, minHeight));
    dialog.setSize(width, height);
    dialog.setLocationRelativeTo(owner);
    LizzieFrame.constrainWindowToAvailableWorkArea(dialog);
  }

  StrengthDashboard dashboard() {
    return dashboard;
  }

  /**
   * Updates the HumanSL status and, while the HumanSL model is active, shows a new estimate once
   * the data behind it has changed. The model menu is left alone while it is open.
   */
  void refresh() {
    if (window != null && !window.isDisplayable()) {
      return;
    }
    showStatus();
    if (!host.activeModel().isHumanSl40() || dashboard.isPickerOpen()) {
      return;
    }
    long stamp = host.preparationStamp();
    if (stamp == shownStamp) {
      return;
    }
    shownStamp = stamp;
    dashboard.showReport(host.report(), host.models());
  }

  private void showStatus() {
    HumanSlStrengthPreparation.Status status = host.preparationStatus();
    dashboard.showStatus(statusText(status, bundle), tone(status.key()));
  }

  /** The footer text for a HumanSL preparation status. */
  static String statusText(HumanSlStrengthPreparation.Status status, ResourceBundle bundle) {
    String key = status.key();
    if (key.equals("HumanSL40.needEngine")) {
      key = "HumanSL40.backgroundNeedEngine";
    } else if (key.equals("HumanSL40.failed")
        || key.equals("HumanSL40.invalidModel")
        || key.equals("HumanSL40.invalidResponse")) {
      key = "HumanSL40.backgroundFailed";
    }
    return MessageFormat.format(bundle.getString(key), status.completed(), status.total());
  }

  static StrengthDashboard.Tone tone(String key) {
    switch (key) {
      case "HumanSL40.backgroundReady":
        return StrengthDashboard.Tone.READY;
      case "HumanSL40.preparing":
      case "HumanSL40.progress":
        return StrengthDashboard.Tone.WORKING;
      case "HumanSL40.backgroundIdle":
      case "HumanSL40.backgroundPaused":
      case "HumanSL40.backgroundOff":
      case "HumanSL40.cancelled":
        return StrengthDashboard.Tone.IDLE;
      default:
        return StrengthDashboard.Tone.PROBLEM;
    }
  }

  private void showEstimate() {
    shownStamp = Long.MIN_VALUE;
    dashboard.showReport(host.report(), host.models());
  }

  @Override
  public void chooseModel(PlayerStrengthEstimator.StrengthModel model) {
    if (model.equals(host.activeModel()) && !model.isHumanSl40()) {
      return;
    }
    host.setActiveModel(model);
    showEstimate();
  }

  @Override
  public void importModel() {
    JFileChooser chooser = new JFileChooser();
    chooser.setDialogTitle(bundle.getString("PlayerStrengthEstimate.importModel.title"));
    chooser.setFileFilter(
        new FileNameExtensionFilter("XGBoost strength model (*.json, *.zip)", "json", "zip"));
    if (chooser.showOpenDialog(window) != JFileChooser.APPROVE_OPTION) {
      return;
    }
    try {
      host.setActiveModel(host.importModel(chooser.getSelectedFile().toPath()));
      showEstimate();
    } catch (IOException error) {
      JOptionPane.showMessageDialog(
          window,
          String.format(
              Locale.US,
              bundle.getString("PlayerStrengthEstimate.importModel.error"),
              error.getMessage()),
          bundle.getString("PlayerStrengthEstimate.importModel.title"),
          JOptionPane.ERROR_MESSAGE);
    }
  }

  @Override
  public void showDetails(int view) {
    PlayerStrengthEstimator.Report report = dashboard.report();
    boolean match = view == StrengthDashboard.MATCH;
    Window owner = window instanceof Window shown ? shown : null;
    JDialog details =
        new JDialog(
            owner,
            bundle.getString("PlayerStrengthEstimate.title")
                + " - "
                + bundle.getString("PlayerStrengthEstimate.detailData"),
            Dialog.ModalityType.DOCUMENT_MODAL);
    details.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
    details.setContentPane(detailPanel(report, match, style, bundle));
    details.pack();
    int height = details.getHeight();
    place(
        details, owner, Math.max(details.getWidth(), style.px(900)), height, style.px(720), height);
    AccessibilitySupport.applyToTree(details.getContentPane());
    AccessibilitySupport.installEscapeAction(details.getRootPane(), details, details::dispose);
    details.setVisible(true);
  }

  /** The content of the detail dialog: the match chart or the table of every figure. */
  static JComponent detailPanel(
      PlayerStrengthEstimator.Report report,
      boolean match,
      StrengthStyle style,
      ResourceBundle bundle) {
    String blackName = bundle.getString("SidebarHeader.black");
    String whiteName = bundle.getString("SidebarHeader.white");
    JPanel panel = new JPanel(new BorderLayout(0, style.px(14)));
    panel.setBackground(style.background);
    panel.setBorder(
        BorderFactory.createEmptyBorder(style.px(22), style.px(24), style.px(20), style.px(24)));
    panel.add(
        match
            ? new StrengthSegmentChart(report, new String[] {blackName, whiteName}, bundle, style)
            : new StrengthDetailTable(report, blackName, whiteName, bundle, style),
        BorderLayout.CENTER);
    String note =
        match
            ? "PlayerStrengthEstimate.match.reviewNote"
            : report.model.isHumanSl40()
                ? "HumanSL40.reviewNote"
                : "PlayerStrengthEstimate.reviewOnlyNote";
    panel.add(new StrengthNote(bundle.getString(note), false, style), BorderLayout.SOUTH);
    return panel;
  }

  @Override
  public boolean setBackgroundEnabled(boolean enabled) {
    try {
      host.setBackgroundEnabled(enabled);
    } catch (IOException error) {
      JOptionPane.showMessageDialog(
          window,
          error.getMessage(),
          bundle.getString("PlayerStrengthEstimate.title"),
          JOptionPane.ERROR_MESSAGE);
    }
    showStatus();
    return host.backgroundEnabled();
  }

  @Override
  public void prepareNow() {
    host.prepareNow();
    showEstimate();
  }
}
