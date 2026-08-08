package featurecat.lizzie.teacher;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.util.Arrays;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.UIManager;

/** Modal settings editor; network discovery runs outside the EDT. */
final class TeacherSettingsDialog extends JDialog {
  private final TeacherSettings settings;
  private final JTextField baseUrlField = new JTextField(34);
  private final JPasswordField apiKeyField = new JPasswordField(28);
  private final char passwordEchoChar = apiKeyField.getEchoChar();
  private final JComboBox<String> modelBox = new JComboBox<>();
  private final JCheckBox showApiKey =
      new JCheckBox(TeacherStrings.get("Teacher.settings.showKey", "Show API key"));
  private final JCheckBox rememberApiKey =
      new JCheckBox(TeacherStrings.get("Teacher.settings.rememberKey", "Remember securely"));
  private final JLabel status = new JLabel(" ");
  private final JButton refreshModels =
      new JButton(TeacherStrings.get("Teacher.settings.refreshModels", "Refresh models"));
  private final JButton saveButton =
      new JButton(TeacherStrings.get("Teacher.settings.save", "Save"));
  private final JButton cancelButton =
      new JButton(TeacherStrings.get("Teacher.settings.cancel", "Cancel"));
  private boolean saved;

  static boolean show(Component parent, TeacherSettings settings) {
    Window owner = parent == null ? null : SwingUtilities.getWindowAncestor(parent);
    TeacherSettingsDialog dialog = new TeacherSettingsDialog(owner, settings);
    dialog.setLocationRelativeTo(parent);
    dialog.setVisible(true);
    return dialog.saved;
  }

  private TeacherSettingsDialog(Window owner, TeacherSettings settings) {
    super(
        owner,
        TeacherStrings.get("Teacher.settings.title", "AI commentary settings"),
        Dialog.ModalityType.APPLICATION_MODAL);
    this.settings = settings;
    setDefaultCloseOperation(DISPOSE_ON_CLOSE);
    setContentPane(buildContent());
    loadValues();
    pack();
    setMinimumSize(new Dimension(Math.max(560, getWidth()), getHeight()));
    getRootPane()
        .registerKeyboardAction(
            event -> {
              if (cancelButton.isEnabled()) {
                dispose();
              }
            },
            KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
            JComponent.WHEN_IN_FOCUSED_WINDOW);
  }

  private JPanel buildContent() {
    JPanel form = new JPanel(new GridBagLayout());
    form.setBorder(BorderFactory.createEmptyBorder(18, 20, 10, 20));
    GridBagConstraints constraints = new GridBagConstraints();
    constraints.insets = new Insets(5, 4, 5, 4);
    constraints.anchor = GridBagConstraints.WEST;
    constraints.fill = GridBagConstraints.HORIZONTAL;

    JLabel baseUrlLabel =
        new JLabel(TeacherStrings.get("Teacher.settings.baseUrl", "API base URL"));
    JLabel apiKeyLabel = new JLabel(TeacherStrings.get("Teacher.settings.apiKey", "API key"));
    JLabel modelLabel = new JLabel(TeacherStrings.get("Teacher.settings.model", "Model"));
    baseUrlLabel.setLabelFor(baseUrlField);
    apiKeyLabel.setLabelFor(apiKeyField);
    modelLabel.setLabelFor(modelBox);

    addRow(form, constraints, 0, baseUrlLabel, baseUrlField);

    JPanel keyRow = new JPanel(new BorderLayout(8, 0));
    keyRow.add(apiKeyField, BorderLayout.CENTER);
    keyRow.add(showApiKey, BorderLayout.EAST);
    addRow(form, constraints, 1, apiKeyLabel, keyRow);

    modelBox.setEditable(true);
    JPanel modelRow = new JPanel(new BorderLayout(8, 0));
    modelRow.add(modelBox, BorderLayout.CENTER);
    modelRow.add(refreshModels, BorderLayout.EAST);
    addRow(form, constraints, 2, modelLabel, modelRow);

    constraints.gridx = 1;
    constraints.gridy = 3;
    constraints.weightx = 1.0;
    form.add(rememberApiKey, constraints);

    JTextArea privacy =
        note(
            TeacherStrings.get(
                    "Teacher.settings.privacy",
                    "The API key is never written to the normal configuration file.")
                + "\n"
                + TeacherStrings.get(
                    "Teacher.settings.dataNotice",
                    "Only the selected KataGo analysis summary and your question are sent to this API; the complete SGF is not uploaded."));
    constraints.gridy = 4;
    form.add(privacy, constraints);

    constraints.gridy = 5;
    form.add(status, constraints);

    JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
    buttons.add(cancelButton);
    buttons.add(saveButton);

    showApiKey.addActionListener(
        event -> apiKeyField.setEchoChar(showApiKey.isSelected() ? (char) 0 : passwordEchoChar));
    refreshModels.addActionListener(event -> refreshModels());
    cancelButton.addActionListener(event -> dispose());
    saveButton.addActionListener(event -> save());
    getRootPane().setDefaultButton(saveButton);

    JPanel content = new JPanel(new BorderLayout());
    content.add(form, BorderLayout.CENTER);
    content.add(buttons, BorderLayout.SOUTH);
    return content;
  }

  private static JTextArea note(String text) {
    JTextArea note = new JTextArea(text, 3, 34);
    note.setEditable(false);
    note.setFocusable(false);
    note.setLineWrap(true);
    note.setWrapStyleWord(true);
    note.setOpaque(false);
    note.setBorder(null);
    note.setFont(UIManager.getFont("Label.font"));
    note.setForeground(UIManager.getColor("Label.disabledForeground"));
    return note;
  }

  private static void addRow(
      JPanel form, GridBagConstraints constraints, int row, JLabel label, Component component) {
    constraints.gridy = row;
    constraints.gridx = 0;
    constraints.weightx = 0.0;
    form.add(label, constraints);
    constraints.gridx = 1;
    constraints.weightx = 1.0;
    form.add(component, constraints);
  }

  private void loadValues() {
    setInputsEnabled(false);
    cancelButton.setEnabled(true);
    status.setText(
        TeacherStrings.get("Teacher.status.loadingSettings", "Loading secure settings..."));
    new SwingWorker<LoadedValues, Void>() {
      @Override
      protected LoadedValues doInBackground() throws Exception {
        TeacherSettings.Snapshot snapshot = settings.load();
        return new LoadedValues(snapshot, settings.apiKey().orElse(""));
      }

      @Override
      protected void done() {
        if (!isDisplayable()) {
          return;
        }
        try {
          LoadedValues loaded = get();
          TeacherSettings.Snapshot snapshot = loaded.snapshot;
          baseUrlField.setText(snapshot.baseUrl);
          modelBox.addItem(snapshot.model);
          modelBox.setSelectedItem(snapshot.model);
          rememberApiKey.setSelected(snapshot.rememberApiKey);
          apiKeyField.setText(loaded.apiKey);
          if (!snapshot.secureStorageAvailable) {
            rememberApiKey.setToolTipText(
                TeacherStrings.get(
                    "Teacher.settings.storageUnavailable",
                    "System credential storage is unavailable; the key will be session-only."));
          }
          setInputsEnabled(true);
          rememberApiKey.setEnabled(snapshot.secureStorageAvailable);
          status.setText(" ");
        } catch (Exception error) {
          setInputsEnabled(true);
          rememberApiKey.setEnabled(false);
          status.setText(localError(error));
        }
      }
    }.execute();
  }

  private void refreshModels() {
    char[] key = apiKeyField.getPassword();
    String baseUrl = baseUrlField.getText();
    String selectedModel = selectedModel();
    if (key.length == 0) {
      status.setText(TeacherStrings.get("Teacher.settings.enterKey", "Enter an API key first."));
      return;
    }
    refreshModels.setEnabled(false);
    status.setText(TeacherStrings.get("Teacher.settings.loadingModels", "Loading models..."));
    char[] keyCopy = key.clone();
    Arrays.fill(key, '\0');
    new SwingWorker<List<String>, Void>() {
      @Override
      protected List<String> doInBackground() throws Exception {
        try {
          return new TeacherLlmClient(baseUrl, new String(keyCopy), selectedModel).listModels();
        } finally {
          Arrays.fill(keyCopy, '\0');
        }
      }

      @Override
      protected void done() {
        refreshModels.setEnabled(true);
        try {
          List<String> models = get();
          Object previous = modelBox.getEditor().getItem();
          modelBox.removeAllItems();
          for (String model : models) {
            modelBox.addItem(model);
          }
          if (previous != null && !previous.toString().isBlank()) {
            modelBox.setSelectedItem(previous.toString());
          }
          status.setText(
              TeacherStrings.format(
                  "Teacher.settings.modelsLoaded", "Loaded {0} models.", models.size()));
        } catch (Exception error) {
          status.setText(localError(error));
        }
      }
    }.execute();
  }

  private void save() {
    char[] key = apiKeyField.getPassword();
    String requestedBaseUrl = baseUrlField.getText();
    String requestedModel = selectedModel();
    boolean requestedRemember = rememberApiKey.isSelected();
    setInputsEnabled(false);
    cancelButton.setEnabled(false);
    setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
    status.setText(TeacherStrings.get("Teacher.settings.saving", "Saving securely..."));
    new SwingWorker<TeacherSettings.Snapshot, Void>() {
      @Override
      protected TeacherSettings.Snapshot doInBackground() throws Exception {
        return settings.save(requestedBaseUrl, requestedModel, key, requestedRemember);
      }

      @Override
      protected void done() {
        Arrays.fill(key, '\0');
        try {
          get();
          saved = true;
          dispose();
        } catch (Exception error) {
          setDefaultCloseOperation(DISPOSE_ON_CLOSE);
          setInputsEnabled(true);
          cancelButton.setEnabled(true);
          status.setText(localError(error));
        }
      }
    }.execute();
  }

  private void setInputsEnabled(boolean enabled) {
    baseUrlField.setEnabled(enabled);
    apiKeyField.setEnabled(enabled);
    modelBox.setEnabled(enabled);
    showApiKey.setEnabled(enabled);
    rememberApiKey.setEnabled(enabled);
    refreshModels.setEnabled(enabled);
    saveButton.setEnabled(enabled);
  }

  private String selectedModel() {
    Object value = modelBox.getEditor().getItem();
    return value == null ? "" : value.toString().trim();
  }

  private static String localError(Throwable error) {
    Throwable cause = error;
    while (cause.getCause() != null) {
      cause = cause.getCause();
    }
    String message = cause.getMessage();
    return message == null || message.isBlank()
        ? TeacherStrings.get("Teacher.error.generic", "The operation failed.")
        : message;
  }

  private static final class LoadedValues {
    private final TeacherSettings.Snapshot snapshot;
    private final String apiKey;

    private LoadedValues(TeacherSettings.Snapshot snapshot, String apiKey) {
      this.snapshot = snapshot;
      this.apiKey = apiKey;
    }
  }
}
