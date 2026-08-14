package featurecat.lizzie.gui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.swing.JLabel;
import javax.swing.UIManager;
import org.junit.jupiter.api.Test;

public class MessageThemeTest {
  @Test
  void messageDialogInstallsTheSharedEscapeCloseAction() throws Exception {
    String source =
        Files.readString(
            Path.of("src/main/java/featurecat/lizzie/gui/Message.java"), StandardCharsets.UTF_8);

    assertTrue(
        source.contains(
            "AccessibilitySupport.installEscapeAction(getRootPane(), this, this::closeAndDispose)"),
        "blocking message dialogs must remain dismissible from the keyboard.");
  }

  @Test
  void darkLookAndFeelBackgroundFallsBackToReadableMessageColors() {
    Color previousPanel = UIManager.getColor("Panel.background");
    Color previousLabel = UIManager.getColor("Label.foreground");
    try {
      UIManager.put("Panel.background", Color.BLACK);
      UIManager.put("Label.foreground", Color.BLACK);
      JLabel label = new JLabel();

      MessageTheme.apply(label);

      assertFalse(Color.BLACK.equals(label.getBackground()));
    } finally {
      UIManager.put("Panel.background", previousPanel);
      UIManager.put("Label.foreground", previousLabel);
    }
  }
}
