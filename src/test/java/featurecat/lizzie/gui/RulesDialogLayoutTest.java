package featurecat.lizzie.gui;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import featurecat.lizzie.Config;
import featurecat.lizzie.ConfigTestHelper;
import featurecat.lizzie.Lizzie;
import featurecat.lizzie.analysis.KataGoRules;
import java.awt.Component;
import java.awt.GraphicsEnvironment;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import javax.imageio.ImageIO;
import javax.swing.JDialog;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RulesDialogLayoutTest {
  @TempDir Path directory;

  @Test
  void bothEditorsFitTranslationsAndFontSizes() throws Exception {
    boolean headless = GraphicsEnvironment.isHeadless();
    assertFalse(Boolean.getBoolean("lizzie.desktop.required") && headless);
    assumeFalse(headless);
    SwingUtilities.invokeAndWait(() -> {
      var oldConfig = Lizzie.config;
      var oldBundle = Lizzie.resourceBundle;
      int oldFont = Config.frameFontSize;
      try {
        Lizzie.config = ConfigTestHelper.createForTests(directory);
        Lizzie.config.analysisSpecificRules = "";
        for (String language : List.of("zh-CN", "zh-TW", "en-US", "ja-JP", "ko", "th-TH")) {
          Lizzie.resourceBundle = ResourceBundle.getBundle("l10n.DisplayStrings",
              Locale.forLanguageTag(language));
          for (int size : List.of(12, 16, 20)) {
            Config.frameFontSize = size;
            SetKataRules rules = new SetKataRules(null, true, KataGoRules.parse("goe").orElseThrow());
            SetAnalysisRules analysis = new SetAnalysisRules();
            try {
              analysis.setModal(false);
              for (JDialog dialog : List.of(rules, analysis)) {
                dialog.setVisible(true);
                dialog.validate();
                Path output = Path.of(System.getProperty("lizzie.desktop.evidence.dir",
                    "target/desktop-smoke/probes"), "rules-dialogs");
                Files.createDirectories(output);
                BufferedImage image = new BufferedImage(dialog.getWidth(), dialog.getHeight(),
                    BufferedImage.TYPE_INT_RGB);
                var graphics = image.createGraphics();
                try { dialog.printAll(graphics); } finally { graphics.dispose(); }
                ImageIO.write(image, "png", output.resolve(dialog.getClass().getSimpleName()
                    + "-" + language + "-" + size + ".png").toFile());
                for (Component control : dialog.getContentPane().getComponents()) {
                  if (!control.isVisible()) continue;
                  assertTrue(control.getX() >= 0 && control.getY() >= 0
                      && control.getX() + control.getWidth() <= dialog.getContentPane().getWidth()
                      && control.getY() + control.getHeight() <= dialog.getContentPane().getHeight(),
                      language + "/" + size + " outside dialog: " + control);
                  assertTrue(control.getWidth() >= control.getPreferredSize().width
                      && control.getHeight() >= control.getPreferredSize().height,
                      language + "/" + size + " clipped text: " + control);
                }
              }
            } finally {
              rules.dispose();
              analysis.dispose();
            }
          }
        }
      } catch (Exception e) {
        throw new RuntimeException(e);
      } finally {
        Lizzie.config = oldConfig;
        Lizzie.resourceBundle = oldBundle;
        Config.frameFontSize = oldFont;
      }
    });
  }
}
