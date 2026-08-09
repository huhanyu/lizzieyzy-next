package featurecat.lizzie.teacher;

import featurecat.lizzie.Lizzie;
import java.text.MessageFormat;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

final class TeacherStrings {
  private static final ResourceBundle.Control NO_DEFAULT_LOCALE_FALLBACK =
      ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_DEFAULT);

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

  static String get(Locale locale, String key, String fallback) {
    try {
      ResourceBundle bundle =
          ResourceBundle.getBundle(
              "l10n.DisplayStrings",
              locale == null ? Locale.getDefault() : locale,
              NO_DEFAULT_LOCALE_FALLBACK);
      if (bundle.containsKey(key)) {
        return bundle.getString(key);
      }
    } catch (MissingResourceException | IllegalArgumentException ignored) {
    }
    return fallback;
  }

  static String format(Locale locale, String key, String fallback, Object... arguments) {
    MessageFormat formatter =
        new MessageFormat(
            get(locale, key, fallback), locale == null ? Locale.getDefault() : locale);
    return formatter.format(arguments);
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
