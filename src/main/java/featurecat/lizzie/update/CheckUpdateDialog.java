package featurecat.lizzie.update;

import featurecat.lizzie.Config;
import featurecat.lizzie.Lizzie;
import featurecat.lizzie.gui.JFontButton;
import featurecat.lizzie.gui.JFontLabel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JCheckBox;
import java.util.function.Consumer;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTextArea;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

/** 检查更新页: current version, 更新通道, 更新源, and Check. Opening the page does not use the network. */
public final class CheckUpdateDialog extends JDialog {
  private static final Color PAGE_BACKGROUND = new Color(246, 247, 249);
  private static final Color MUTED_TEXT = new Color(90, 96, 104);

  private final JRadioButton stableButton =
      channelRadio(UpdateText.tr("WindowsUpdate.channel.stable", "正式", "Official"));
  private final JRadioButton betaButton =
      channelRadio(UpdateText.tr("WindowsUpdate.channel.beta", "测试", "Test"));
  private final JRadioButton officialSourceButton =
      channelRadio(UpdateText.tr("WindowsUpdate.source.official", "官网", "Official site"));
  private final JRadioButton githubSourceButton =
      channelRadio(UpdateText.tr("WindowsUpdate.source.github", "GitHub", "GitHub"));
  private final JFontButton checkButton =
      new JFontButton(UpdateText.tr("WindowsUpdate.btnCheck", "检查更新", "Check update"));
  private final JTextArea resultLabel = new JTextArea(4, 36);
  private final JFontButton offerButton = new JFontButton(
      UpdateText.tr("WindowsUpdate.viewOffer", "查看更新", "View update"));
  private final JFontButton cancelButton = new JFontButton(
      UpdateText.tr("WindowsUpdate.btnCancel", "取消", "Cancel"));
  private final JFontButton pauseButton = new JFontButton();
  private final JFontButton retryButton = new JFontButton(
      UpdateText.tr("WindowsUpdate.btnResume", "继续", "Resume"));
  private final JCheckBox autoCheck = new JCheckBox("", UpdatePreferences.autoCheck());
  private final JCheckBox autoDownload = new JCheckBox("", UpdatePreferences.autoDownload());
  private final UpdateCheckCoordinator coordinator;
  private final Consumer<UpdateCheckCoordinator.State> observer = this::showState;

  CheckUpdateDialog(Component parent, UpdateCheckCoordinator coordinator) {
    super(parent instanceof java.awt.Window ? (java.awt.Window) parent
            : parent == null ? null : SwingUtilities.getWindowAncestor(parent),
        UpdateText.tr("WindowsUpdate.page.title", "检查更新", "Check update"),
        ModalityType.MODELESS);
    this.coordinator = coordinator;
    buildUi(parent);
    coordinator.subscribe(observer);
  }

  @Override
  public void dispose() {
    coordinator.unsubscribe(observer);
    super.dispose();
  }

  private void buildUi(Component parent) {
    setDefaultCloseOperation(DISPOSE_ON_CLOSE);
    JPanel root = new JPanel(new BorderLayout(0, 16));
    root.setBorder(BorderFactory.createEmptyBorder(18, 20, 16, 20));
    root.setBackground(PAGE_BACKGROUND);
    setContentPane(root);

    root.add(buildHeader(), BorderLayout.NORTH);
    root.add(buildForm(), BorderLayout.CENTER);
    root.add(buildFooter(), BorderLayout.SOUTH);

    pack();
    setMinimumSize(new Dimension(Math.max(460, getWidth()), getHeight()));
    setLocationRelativeTo(parent == null ? Lizzie.frame : parent);
  }

  private JPanel buildHeader() {
    JPanel header = new JPanel(new BorderLayout(0, 4));
    header.setOpaque(false);

    JFontLabel versionLabel =
        new JFontLabel(UpdateText.tr("WindowsUpdate.currentVersion", "当前版本", "Current version"));
    versionLabel.setForeground(mutedText());

    JFontLabel versionValue = new JFontLabel(displayVersion());
    versionValue.setFont(versionValue.getFont().deriveFont(Font.BOLD, 16f));

    header.add(versionLabel, BorderLayout.NORTH);
    header.add(versionValue, BorderLayout.CENTER);
    return header;
  }

  private JPanel buildForm() {
    JPanel form = new JPanel(new GridBagLayout());
    form.setOpaque(false);

    JFontLabel channelLabel =
        new JFontLabel(UpdateText.tr("WindowsUpdate.page.channel", "更新通道", "Update channel"));
    channelLabel.setLabelFor(stableButton);

    UpdateChannel current = UpdateChannel.current();
    stableButton.setSelected(current == UpdateChannel.STABLE);
    betaButton.setSelected(current == UpdateChannel.BETA);
    ButtonGroup group = new ButtonGroup();
    group.add(stableButton);
    group.add(betaButton);
    stableButton.addActionListener(
        e -> {
          persistSelection(UpdateChannel.STABLE, UpdateSource.current(), coordinator);
          refreshSourceState();
        });
    betaButton.addActionListener(
        e -> {
          persistSelection(UpdateChannel.BETA, selectedSource(), coordinator);
          refreshSourceState();
        });

    JPanel channelRow = new JPanel();
    channelRow.setOpaque(false);
    channelRow.setLayout(new BoxLayout(channelRow, BoxLayout.X_AXIS));
    stableButton.setAlignmentY(Component.CENTER_ALIGNMENT);
    betaButton.setAlignmentY(Component.CENTER_ALIGNMENT);
    channelRow.add(stableButton);
    channelRow.add(Box.createHorizontalStrut(12));
    channelRow.add(betaButton);

    GridBagConstraints constraints = new GridBagConstraints();
    constraints.gridx = 0;
    constraints.gridy = 0;
    constraints.anchor = GridBagConstraints.WEST;
    constraints.insets = new Insets(0, 0, 8, 0);
    form.add(channelLabel, constraints);

    constraints.gridy = 1;
    constraints.fill = GridBagConstraints.HORIZONTAL;
    constraints.weightx = 1;
    constraints.insets = new Insets(0, 0, 14, 0);
    form.add(channelRow, constraints);

    JFontLabel sourceLabel =
        new JFontLabel(UpdateText.tr("WindowsUpdate.page.source", "更新源", "Update source"));
    sourceLabel.setLabelFor(officialSourceButton);

    ButtonGroup sourceGroup = new ButtonGroup();
    sourceGroup.add(officialSourceButton);
    sourceGroup.add(githubSourceButton);

    refreshSourceState();
    officialSourceButton.addActionListener(
        e -> persistSelection(selectedChannel(), UpdateSource.OFFICIAL_SITE, coordinator));
    githubSourceButton.addActionListener(
        e -> persistSelection(selectedChannel(), UpdateSource.GITHUB, coordinator));

    JPanel sourceRow = new JPanel();
    sourceRow.setOpaque(false);
    sourceRow.setLayout(new BoxLayout(sourceRow, BoxLayout.X_AXIS));
    officialSourceButton.setAlignmentY(Component.CENTER_ALIGNMENT);
    githubSourceButton.setAlignmentY(Component.CENTER_ALIGNMENT);
    sourceRow.add(officialSourceButton);
    sourceRow.add(Box.createHorizontalStrut(12));
    sourceRow.add(githubSourceButton);

    constraints.gridy = 2;
    constraints.fill = GridBagConstraints.NONE;
    constraints.weightx = 0;
    constraints.insets = new Insets(0, 0, 8, 0);
    form.add(sourceLabel, constraints);

    constraints.gridy = 3;
    constraints.fill = GridBagConstraints.HORIZONTAL;
    constraints.weightx = 1;
    constraints.insets = new Insets(0, 0, 14, 0);
    form.add(sourceRow, constraints);

    autoCheck.setOpaque(false);
    autoCheck.setFont(stableButton.getFont());
    autoDownload.setOpaque(false);
    autoDownload.setFont(stableButton.getFont());
    autoDownload.setEnabled(autoCheck.isSelected());
    refreshAutoLabels(autoCheck, autoDownload);
    autoDownload.addActionListener(e -> {
      UpdatePreferences.persistAutoDownload(autoDownload.isSelected(), coordinator);
      refreshAutoLabels(autoCheck, autoDownload);
    });
    autoCheck.addActionListener(e -> {
      UpdatePreferences.persistAutoCheck(autoCheck.isSelected(), coordinator);
      autoDownload.setEnabled(autoCheck.isSelected());
      autoDownload.setSelected(UpdatePreferences.autoDownload());
      refreshAutoLabels(autoCheck, autoDownload);
    });
    constraints.gridy = 4;
    form.add(autoCheck, constraints);
    constraints.gridy = 5;
    form.add(autoDownload, constraints);

    resultLabel.setForeground(mutedText());
    resultLabel.setFont(stableButton.getFont());
    resultLabel.setOpaque(false);
    resultLabel.setEditable(false);
    resultLabel.setFocusable(false);
    resultLabel.setLineWrap(true);
    resultLabel.setWrapStyleWord(true);
    constraints.gridy = 6;
    constraints.insets = new Insets(0, 0, 0, 0);
    form.add(resultLabel, constraints);
    return form;
  }

  /** A checked option lacking this run's authority is pending; disabling takes effect immediately. */
  private void refreshAutoLabels(JCheckBox autoCheck, JCheckBox autoDownload) {
    autoCheck.setText(autoCheck.isSelected() && !coordinator.automaticCheckActive()
        ? UpdateText.tr("WindowsUpdate.autoCheckPending", "启动时自动检查更新（下次启动生效）",
            "Check for updates at startup (from next launch)")
        : UpdateText.tr("WindowsUpdate.autoCheck", "启动时自动检查更新",
            "Check for updates at startup"));
    autoDownload.setText(autoDownload.isSelected() && !coordinator.automaticDownloadActive()
        ? UpdateText.tr("WindowsUpdate.autoDownloadPending", "自动下载更新（下次启动生效）",
            "Download updates automatically (from next launch)")
        : UpdateText.tr("WindowsUpdate.autoDownload", "自动下载更新",
            "Download updates automatically"));
    if (isDisplayable()) {
      Dimension preferred = getPreferredSize();
      if (preferred.width > getWidth() || preferred.height > getHeight()) {
        setSize(Math.max(getWidth(), preferred.width), Math.max(getHeight(), preferred.height));
      }
      validate();
    }
  }

  private JPanel buildFooter() {
    JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
    footer.setOpaque(false);
    checkButton.addActionListener(e -> startCheck());
    offerButton.addActionListener(e -> coordinator.openOffer());
    cancelButton.addActionListener(e -> coordinator.cancelDownload());
    pauseButton.addActionListener(e -> coordinator.togglePause());
    retryButton.addActionListener(e -> coordinator.retryDownload());
    footer.add(cancelButton);
    footer.add(pauseButton);
    footer.add(retryButton);
    footer.add(offerButton);
    JFontButton later = new JFontButton(UpdateText.tr("WindowsUpdate.later", "稍后", "Later"));
    later.addActionListener(e -> dispose());
    footer.add(later);
    footer.add(checkButton);
    getRootPane().setDefaultButton(checkButton);
    return footer;
  }

  static void persistSelection(UpdateChannel channel, UpdateSource source) {
    persistSelection(channel, source, WindowsUpdateController.coordinator());
  }

  private static void persistSelection(UpdateChannel channel, UpdateSource source,
      UpdateCheckCoordinator coordinator) {
    boolean changed = UpdateChannel.current() != channel
        || (channel != UpdateChannel.BETA && UpdateSource.current() != source);
    UpdateChannel.persist(channel);
    if (channel != UpdateChannel.BETA) {
      UpdateSource.persist(source);
    }
    if (changed) coordinator.invalidateAutomatic();
  }

  private void startCheck() {
    coordinator.checkManually(
        UpdateCheckSelection.of(selectedChannel(), selectedSource(), Lizzie.nextVersion));
  }

  private void showState(UpdateCheckCoordinator.State state) {
    refreshAutoLabels(autoCheck, autoDownload);
    checkButton.setEnabled(!coordinator.busy());
    offerButton.setVisible(state.hasOffer() && !state.downloading()
        && state.download().phase() != UpdateCheckCoordinator.DownloadPhase.CANCELLED
        && state.download().phase() != UpdateCheckCoordinator.DownloadPhase.FAILED);
    offerButton.setEnabled(!coordinator.busy());
    offerButton.setText(UpdateText.tr("WindowsUpdate.viewOffer", "查看更新", "View update"));
    cancelButton.setVisible(state.downloading());
    pauseButton.setVisible(state.downloading());
    pauseButton.setText(UpdateDownloadText.pauseAction(state));
    retryButton.setVisible(state.hasOffer() && (state.download().phase() == UpdateCheckCoordinator.DownloadPhase.CANCELLED
        || state.download().phase() == UpdateCheckCoordinator.DownloadPhase.FAILED));
    retryButton.setEnabled(!coordinator.busy());
    if (state.download().phase() != UpdateCheckCoordinator.DownloadPhase.NONE) {
      resultLabel.setText(UpdateDownloadText.status(state));
      return;
    }
    if (state.checking()) {
      resultLabel.setText(UpdateText.tr("WindowsUpdate.checking", "正在检查更新…", "Checking for updates…"));
      return;
    }
    if (state.result() == null) {
      resultLabel.setText("");
      return;
    }
    String message = state.hasOffer()
        ? UpdateText.tr("WindowsUpdate.available", "发现可用更新。", "An update is available.")
        : UpdateCheckFeedback.message(state.result(), state.selection().channel);
    String warning = UpdateCheckFeedback.warning(state.result());
    resultLabel.setText(warning.isEmpty() ? message : message + "\n" + warning);
  }

  private UpdateChannel selectedChannel() {
    return betaButton.isSelected() ? UpdateChannel.BETA : UpdateChannel.STABLE;
  }

  private void refreshSourceState() {
    boolean stable = selectedChannel() == UpdateChannel.STABLE;
    officialSourceButton.setEnabled(stable);
    githubSourceButton.setEnabled(stable);
    if (stable) {
      UpdateSource current = UpdateSource.current();
      officialSourceButton.setSelected(current != UpdateSource.GITHUB);
      githubSourceButton.setSelected(current == UpdateSource.GITHUB);
      return;
    }
    officialSourceButton.setSelected(false);
    githubSourceButton.setSelected(true);
  }

  private UpdateSource selectedSource() {
    return githubSourceButton.isSelected() ? UpdateSource.GITHUB : UpdateSource.OFFICIAL_SITE;
  }

  private static JRadioButton channelRadio(String text) {
    JRadioButton button = new JRadioButton(text);
    button.setFont(new Font(Config.sysDefaultFontName, Font.PLAIN, Config.frameFontSize));
    button.setOpaque(false);
    button.setContentAreaFilled(false);
    button.setFocusPainted(false);
    button.setBorderPainted(false);
    button.setBorder(BorderFactory.createEmptyBorder());
    button.setHorizontalAlignment(SwingConstants.LEFT);
    return button;
  }

  private static Color mutedText() {
    if (Lizzie.config != null && Lizzie.config.isAppleStyle) {
      return new Color(170, 176, 184);
    }
    return MUTED_TEXT;
  }

  private static String displayVersion() {
    return Lizzie.nextVersion == null || Lizzie.nextVersion.isBlank() ? "-" : Lizzie.nextVersion;
  }
}
