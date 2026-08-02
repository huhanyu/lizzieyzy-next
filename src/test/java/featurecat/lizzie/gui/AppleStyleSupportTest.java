package featurecat.lizzie.gui;

import static org.junit.jupiter.api.Assertions.assertSame;

import javax.swing.JButton;
import javax.swing.plaf.ButtonUI;
import javax.swing.plaf.basic.BasicButtonUI;
import org.junit.jupiter.api.Test;

class AppleStyleSupportTest {
  @Test
  void globalThemeRefreshPreservesPurposeBuiltButtonUi() {
    JButton button = new JButton("Remote compute");
    ButtonUI customUi = new BasicButtonUI();
    button.setUI(customUi);
    AppleStyleSupport.preserveCustomButtonStyle(button);

    AppleStyleSupport.installButtonStyle(button);

    assertSame(customUi, button.getUI());
  }
}
