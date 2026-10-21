package featurecat.lizzie.gui;

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.KeyStroke;

/**
 * The keyboard shortcut a menu label carries in trailing parentheses, such as "保存(Ctrl+S)" or "Pass
 * (P)", so the top menus can show it as a right-aligned hint and the label alone on the left.
 *
 * <p>Only an exact key, optionally with modifiers, counts. Explanations such as "(保留原棋盘)" or
 * "（推荐）", mouse gestures such as "(Alt+鼠标圈选)" and misspelled modifiers stay part of the label, so
 * nothing is ever cut from a label that is not a shortcut.
 */
final class MenuShortcutHint {
  /** A label and its shortcut, or the whole text and {@code null}. */
  record Split(String label, String hint) {}

  /** Engine entries ("[1] KataGo") and recent files ("1: game.sgf") show names users chose. */
  private static final Pattern USER_CONTENT = Pattern.compile("^(?:\\[\\d+\\]|\\d+: )");

  private static final Pattern TRAILING =
      Pattern.compile("^(.*?)\\s*[(（]([^()（）]+)[)）]\\s*$", Pattern.DOTALL);
  private static final String MODIFIER = "(?:ctrl|shift|alt|cmd|command|option|meta)";
  private static final String KEY =
      "(?:[a-z0-9]|f(?:[1-9]|1[0-2])|home|end|delete|del|enter|space|tab|esc|backspace|insert"
          + "|pgup|pgdn|pageup|pagedown|[←→↑↓,.\\[\\]/\\\\=-]|空格|逗号|逗號|回车|回車)";
  private static final Pattern KEYS =
      Pattern.compile("(?:" + MODIFIER + "\\s*\\+\\s*)*" + KEY, Pattern.CASE_INSENSITIVE);

  private MenuShortcutHint() {}

  static Split split(String text) {
    if (text == null) {
      return new Split("", null);
    }
    if (USER_CONTENT.matcher(text).find()) {
      return new Split(text, null);
    }
    Matcher trailing = TRAILING.matcher(text);
    if (!trailing.matches() || trailing.group(1).isBlank()) {
      return new Split(text, null);
    }
    String keys = trailing.group(2).trim();
    if (!KEYS.matcher(keys).matches()) {
      return new Split(text, null);
    }
    return new Split(trailing.group(1), normalize(keys));
  }

  /** An accelerator in the same notation as the labels' shortcuts, such as "Ctrl+Shift+B". */
  static String format(KeyStroke stroke) {
    if (stroke == null) {
      return null;
    }
    int modifiers = stroke.getModifiers();
    StringBuilder text = new StringBuilder();
    if ((modifiers & InputEvent.CTRL_DOWN_MASK) != 0) text.append("Ctrl+");
    if ((modifiers & InputEvent.SHIFT_DOWN_MASK) != 0) text.append("Shift+");
    if ((modifiers & InputEvent.ALT_DOWN_MASK) != 0) text.append("Alt+");
    if ((modifiers & InputEvent.META_DOWN_MASK) != 0) text.append("Cmd+");
    return text.append(KeyEvent.getKeyText(stroke.getKeyCode())).toString();
  }

  /** "ALT+R", "Alt + O" and "ctrl+s" read as "Alt+R", "Alt+O" and "Ctrl+S". */
  private static String normalize(String keys) {
    String[] parts = keys.split("\\s*\\+\\s*");
    StringBuilder text = new StringBuilder();
    for (int i = 0; i < parts.length; i++) {
      String part = parts[i];
      if (i > 0) text.append('+');
      if (i < parts.length - 1 || part.length() == 1) {
        text.append(part.toUpperCase(Locale.ROOT).charAt(0));
        text.append(i < parts.length - 1 ? part.substring(1).toLowerCase(Locale.ROOT) : "");
      } else {
        text.append(part);
      }
    }
    return text.toString();
  }
}
