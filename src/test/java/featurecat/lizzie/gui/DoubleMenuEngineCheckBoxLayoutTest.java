package featurecat.lizzie.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import featurecat.lizzie.Lizzie;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import javax.swing.JButton;
import javax.swing.JPanel;
import org.junit.jupiter.api.Test;

class DoubleMenuEngineCheckBoxLayoutTest {
  @Test
  void wrnAndPdaUseKomiStyleLabelColonAndAdjacentField() {
    for (ResourceBundle bundle :
        List.of(
            Lizzie.resourceBundle,
            ResourceBundle.getBundle("l10n.DisplayStrings", Locale.SIMPLIFIED_CHINESE))) {
      assertKomiStyleGroup(bundle.getString("Menu.separateLblWrn"));
      assertKomiStyleGroup(bundle.getString("Menu.separateLblPda"));
    }
  }

  private static void assertKomiStyleGroup(String rawLabel) {
    JFontCheckBox enable = new JFontCheckBox();
    JFontLabel label = new JFontLabel();
    JFontTextField field = new JFontTextField();

    JPanel panel = Menu.attachDoubleMenuLabeledField(enable, label, field, rawLabel);

    assertEquals("", enable.getText());
    assertTrue(
        label.getText().endsWith(":") || label.getText().endsWith("："),
        () -> "label should end with colon: " + label.getText());
    assertTrue(label.getText().startsWith(rawLabel.replaceAll("[:：]+$", "")));
    assertEquals(3, panel.getComponentCount());
    assertFalse(containsSpinner(panel));
    assertTrue(
        field.getX() <= label.getX() + label.getWidth(),
        () -> "field x=" + field.getX() + " should sit against label " + label.getBounds());
    assertTrue(
        field.getX() >= label.getX() + label.getWidth() - 8,
        () -> "field x=" + field.getX() + " too far from label " + label.getBounds());
  }

  private static boolean containsSpinner(JPanel panel) {
    for (var component : panel.getComponents()) {
      if (component instanceof JButton) {
        return true;
      }
    }
    return false;
  }
}
