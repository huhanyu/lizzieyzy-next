package featurecat.lizzie.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.util.List;
import javax.swing.JComponent;
import javax.swing.JToggleButton;
import org.junit.jupiter.api.Test;

class StrengthButtonTest {
  private static final StrengthStyle STYLE = new StrengthStyle(false, false, "Dialog", 12);
  private static final String THAI_DETAILS =
      "\u0e02\u0e49\u0e2d\u0e21\u0e39\u0e25\u0e42\u0e14\u0e22\u0e25\u0e30\u0e40\u0e2d\u0e35\u0e22\u0e14";

  @Test
  void buttonGrowsWithItsLocalizedText() {
    StrengthButton button = new StrengthButton(THAI_DETAILS, StrengthButton.Glyph.DETAILS, STYLE);
    int text = button.getFontMetrics(button.getFont()).stringWidth(THAI_DETAILS);
    assertTrue(button.getPreferredSize().width >= text + STYLE.px(14) * 2 + STYLE.px(14));
  }

  @Test
  void segmentsAreAsWideAsTheLongestLabel() {
    StrengthTabs tabs = new StrengthTabs(List.of("A", THAI_DETAILS, "B"), 0, index -> {}, STYLE);
    tabs.setSize(tabs.getPreferredSize());
    tabs.doLayout();
    JToggleButton longest = tabs.segment(1);
    int text = longest.getFontMetrics(longest.getFont()).stringWidth(THAI_DETAILS);
    for (int i = 0; i < 3; i++) {
      assertTrue(tabs.segment(i).getWidth() >= text + STYLE.px(24));
      assertEquals(tabs.segment(0).getWidth(), tabs.segment(i).getWidth());
    }
  }

  @Test
  void focusRingAppearsOnlyWhenTheKeyboardMovesTheFocus() {
    StrengthTabs tabs = new StrengthTabs(List.of("A", "B"), 0, index -> {}, STYLE);
    for (JComponent control :
        new JComponent[] {
          new StrengthButton("A", StrengthButton.Glyph.NONE, STYLE),
          new StrengthSwitch("A", false, STYLE),
          tabs.segment(0)
        }) {
      gain(control, FocusEvent.Cause.ACTIVATION);
      assertFalse(StrengthFocus.byKeyboard(control), "the dialog opening is not the keyboard");
      gain(control, FocusEvent.Cause.MOUSE_EVENT);
      assertFalse(StrengthFocus.byKeyboard(control), "a click shows no ring");
      gain(control, FocusEvent.Cause.TRAVERSAL_FORWARD);
      assertTrue(StrengthFocus.byKeyboard(control), "Tab shows the ring");
      for (FocusListener listener : control.getFocusListeners()) {
        listener.focusLost(new FocusEvent(control, FocusEvent.FOCUS_LOST));
      }
      assertFalse(StrengthFocus.byKeyboard(control));
    }
  }

  private static void gain(JComponent control, FocusEvent.Cause cause) {
    FocusEvent event = new FocusEvent(control, FocusEvent.FOCUS_GAINED, false, null, cause);
    for (FocusListener listener : control.getFocusListeners()) {
      listener.focusGained(event);
    }
  }
}
