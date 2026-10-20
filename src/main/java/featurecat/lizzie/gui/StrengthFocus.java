package featurecat.lizzie.gui;

import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import javax.swing.JComponent;

/**
 * Shows a control's focus ring only when the keyboard moved the focus there, like a browser's
 * {@code :focus-visible}: the control that holds the focus when the dialog opens, or after a click,
 * shows no ring until Tab or an arrow key moves the focus.
 */
final class StrengthFocus extends FocusAdapter {
  private static final String KEYBOARD = "lizzie.strength.keyboardFocus";
  private static final StrengthFocus LISTENER = new StrengthFocus();

  private StrengthFocus() {}

  static void install(JComponent component) {
    component.addFocusListener(LISTENER);
  }

  /** Whether {@code component} has the focus and should show it with a ring. */
  static boolean visible(JComponent component) {
    return component.isFocusOwner() && byKeyboard(component);
  }

  /** Whether the keyboard brought the focus to {@code component} the last time it gained it. */
  static boolean byKeyboard(JComponent component) {
    return Boolean.TRUE.equals(component.getClientProperty(KEYBOARD));
  }

  @Override
  public void focusGained(FocusEvent event) {
    if (event.getComponent() instanceof JComponent component) {
      component.putClientProperty(KEYBOARD, isKeyboard(event.getCause()));
      component.repaint();
    }
  }

  @Override
  public void focusLost(FocusEvent event) {
    if (event.getComponent() instanceof JComponent component) {
      component.putClientProperty(KEYBOARD, false);
      component.repaint();
    }
  }

  private static boolean isKeyboard(FocusEvent.Cause cause) {
    return cause == FocusEvent.Cause.TRAVERSAL
        || cause == FocusEvent.Cause.TRAVERSAL_FORWARD
        || cause == FocusEvent.Cause.TRAVERSAL_BACKWARD
        || cause == FocusEvent.Cause.TRAVERSAL_UP
        || cause == FocusEvent.Cause.TRAVERSAL_DOWN;
  }
}
