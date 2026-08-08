package featurecat.lizzie.teacher;

import featurecat.lizzie.Lizzie;
import java.text.MessageFormat;
import java.util.Locale;
import java.util.MissingResourceException;

final class TeacherStrings {
  private TeacherStrings() {}

  static String get(String key, String fallback) {
    try {
      if (Lizzie.resourceBundle != null && Lizzie.resourceBundle.containsKey(key)) {
        return Lizzie.resourceBundle.getString(key);
      }
    } catch (MissingResourceException ignored) {
    }
    return fallback;
  }

  static String format(String key, String fallback, Object... arguments) {
    return MessageFormat.format(get(key, fallback), arguments);
  }

  static Locale locale() {
    try {
      if (Lizzie.resourceBundle != null && Lizzie.resourceBundle.getLocale() != null) {
        return Lizzie.resourceBundle.getLocale();
      }
    } catch (RuntimeException ignored) {
    }
    return Locale.getDefault();
  }
}
