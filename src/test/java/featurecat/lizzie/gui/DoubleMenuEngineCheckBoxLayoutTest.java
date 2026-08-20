package featurecat.lizzie.gui;

import static org.junit.jupiter.api.Assertions.assertTrue;

import featurecat.lizzie.Config;
import featurecat.lizzie.Lizzie;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Insets;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import javax.swing.AbstractButton;
import javax.swing.Icon;
import org.junit.jupiter.api.Test;

class DoubleMenuEngineCheckBoxLayoutTest {
  @Test
  void wrnAndPdaPreferredWidthCoversIconAndLabelAfterDoubleMenuRebuild() {
    for (ResourceBundle bundle :
        List.of(
            Lizzie.resourceBundle,
            ResourceBundle.getBundle("l10n.DisplayStrings", Locale.SIMPLIFIED_CHINESE))) {
      assertUnclippedAfterRebuild(bundle.getString("Menu.separateLblWrn"));
      assertUnclippedAfterRebuild(bundle.getString("Menu.separateLblPda"));
    }
  }

  private static void assertUnclippedAfterRebuild(String label) {
    JFontCheckBox checkBox = new JFontCheckBox();
    checkBox.setText(label);
    checkBox.setPreferredSize(new Dimension(20, Config.menuHeight - 3));

    Menu.lockDoubleMenuCheckBoxSize(checkBox);
    Menu.lockDoubleMenuCheckBoxSize(checkBox);

    int minimum = unclippedLabelWidth(checkBox);
    assertTrue(
        checkBox.getPreferredSize().width >= minimum,
        () ->
            "preferred width "
                + checkBox.getPreferredSize().width
                + " < unclipped "
                + minimum
                + " for "
                + label);
    assertTrue(
        checkBox.getMinimumSize().width >= minimum,
        () ->
            "minimum width "
                + checkBox.getMinimumSize().width
                + " < unclipped "
                + minimum
                + " for "
                + label);
  }

  private static int unclippedLabelWidth(AbstractButton button) {
    Insets insets = button.getInsets();
    Icon icon = button.getIcon();
    int iconWidth = icon == null ? 0 : icon.getIconWidth();
    FontMetrics metrics = button.getFontMetrics(button.getFont());
    return insets.left
        + iconWidth
        + button.getIconTextGap()
        + metrics.stringWidth(button.getText())
        + insets.right;
  }
}
