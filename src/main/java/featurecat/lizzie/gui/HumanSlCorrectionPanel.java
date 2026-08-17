package featurecat.lizzie.gui;

import featurecat.lizzie.Lizzie;
import featurecat.lizzie.training.HumanMoveDecision;
import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.text.MessageFormat;
import java.util.ResourceBundle;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;

/** Non-modal coaching card shown only in live-correction mode. */
public final class HumanSlCorrectionPanel extends HumanSlTrainingStyle.RoundedPanel {
  private static final long serialVersionUID = 1L;

  private final ResourceBundle resources = Lizzie.resourceBundle;
  private final JLabel moveLabel = new JFontLabel();
  private final JLabel detailLabel = new JFontLabel();
  private final JFontButton retryButton = new JFontButton();
  private final JFontButton continueButton = new JFontButton();
  private HumanSlGameController controller;
  private HumanMoveDecision decision;

  public HumanSlCorrectionPanel() {
    super(HumanSlTrainingStyle.CARD, HumanSlTrainingStyle.BORDER, 18);
    setName("humanSlCorrectionCard");
    setLayout(new BorderLayout(0, 14));
    setBorder(BorderFactory.createEmptyBorder(18, 18, 16, 18));
    setVisible(false);
    add(buildCopy(), BorderLayout.CENTER);
    add(buildActions(), BorderLayout.SOUTH);
    AccessibilitySupport.applyToTree(this);
  }

  public void showDecision(HumanSlGameController owner, HumanMoveDecision value) {
    controller = owner;
    decision = value;
    String moveText =
        MessageFormat.format(
            text("HumanSlTraining.correction.move", "You played {0}. Try {1}."),
            value.actualMove,
            value.commonHumanMove);
    moveLabel.setText(moveText);
    moveLabel.setFont(font(moveText, Font.BOLD, 14));
    String detailText = detail(value);
    detailLabel.setText(detailText);
    detailLabel.setFont(font(detailText, Font.PLAIN, 12));
    setVisible(true);
    retryButton.requestFocusInWindow();
  }

  public void dismiss(HumanSlGameController owner) {
    if (owner == null || controller == owner) {
      setVisible(false);
      controller = null;
      decision = null;
    }
  }

  private JPanel buildCopy() {
    JPanel copy = new JPanel(new GridBagLayout());
    copy.setOpaque(false);
    GridBagConstraints c = new GridBagConstraints();
    c.gridx = 0;
    c.gridy = 0;
    c.weightx = 1.0;
    c.fill = GridBagConstraints.HORIZONTAL;
    c.anchor = GridBagConstraints.WEST;
    String titleText = text("HumanSlTraining.correction.title", "Coach suggestion");
    JLabel title = new JFontLabel(titleText);
    title.setForeground(HumanSlTrainingStyle.TEXT);
    title.setFont(font(titleText, Font.BOLD, 18));
    copy.add(title, c);
    c.gridy = 1;
    c.insets = new Insets(10, 0, 0, 0);
    moveLabel.setForeground(HumanSlTrainingStyle.TEXT);
    moveLabel.setFont(font("", Font.BOLD, 14));
    copy.add(moveLabel, c);
    c.gridy = 2;
    c.insets = new Insets(6, 0, 0, 0);
    detailLabel.setForeground(HumanSlTrainingStyle.MUTED);
    detailLabel.setFont(font("", Font.PLAIN, 12));
    copy.add(detailLabel, c);
    return copy;
  }

  private JPanel buildActions() {
    JPanel actions = new JPanel(new GridBagLayout());
    actions.setOpaque(false);
    GridBagConstraints c = new GridBagConstraints();
    c.gridy = 0;
    c.weightx = 1.0;
    c.fill = GridBagConstraints.HORIZONTAL;
    c.gridx = 0;
    retryButton.setText(text("HumanSlTraining.correction.retry", "Replay move"));
    HumanSlTrainingStyle.stylePrimary(retryButton);
    retryButton.addActionListener(
        event -> {
          HumanSlGameController active = controller;
          HumanMoveDecision selected = decision;
          dismiss(active);
          if (active != null && selected != null) {
            active.retryHumanMove(selected);
          }
        });
    actions.add(retryButton, c);
    c.gridx = 1;
    c.insets = new Insets(0, 8, 0, 0);
    continueButton.setText(text("HumanSlTraining.correction.continue", "Continue game"));
    HumanSlTrainingStyle.styleSecondary(continueButton);
    continueButton.addActionListener(
        event -> {
          HumanSlGameController active = controller;
          dismiss(active);
          if (active != null) {
            active.continueAfterCorrection();
          }
        });
    actions.add(continueButton, c);
    return actions;
  }

  private String detail(HumanMoveDecision value) {
    if (Double.isFinite(value.scoreLoss)) {
      return MessageFormat.format(
          text("HumanSlTraining.correction.scoreLoss", "Estimated loss: {0} points"),
          String.format(java.util.Locale.US, "%.1f", value.scoreLoss));
    }
    if (Double.isFinite(value.winrateLoss)) {
      return MessageFormat.format(
          text("HumanSlTraining.correction.winrateLoss", "Winrate change: {0}%"),
          String.format(java.util.Locale.US, "%.1f", value.winrateLoss * 100.0));
    }
    return text("HumanSlTraining.correction.hint", "This move is less common for the selected style.");
  }

  private Font font(String value, int style, int size) {
    return HumanSlTrainingStyle.fontForText(value, style, size);
  }

  private String text(String key, String fallback) {
    try {
      return resources.getString(key);
    } catch (Exception ignored) {
      return fallback;
    }
  }
}
