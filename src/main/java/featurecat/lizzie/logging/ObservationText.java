package featurecat.lizzie.logging;

/** Bounds opt-in raw observation text without splitting UTF-16 surrogate pairs. */
public final class ObservationText {
  public static final int RAW_EVENT_MAX_UTF8_BYTES = 16 * 1024;
  public static final int RAW_EVENT_MAX_LINES = 64;
  private static final String TRUNCATION_MARKER = " [truncated]";

  private ObservationText() {}

  public static String boundedRawEvent(String value) {
    return boundedUtf8(value, RAW_EVENT_MAX_UTF8_BYTES, RAW_EVENT_MAX_LINES);
  }

  public static String boundedUtf8(String value, int maxUtf8Bytes, int maxLines) {
    if (value == null || value.isEmpty()) {
      return value;
    }
    if (maxUtf8Bytes <= 0 || maxLines <= 0) {
      throw new IllegalArgumentException("Raw observation bounds must be positive");
    }
    if (!exceedsBounds(value, maxUtf8Bytes, maxLines)) {
      return value;
    }

    int markerBytes = TRUNCATION_MARKER.length();
    if (maxUtf8Bytes <= markerBytes) {
      return TRUNCATION_MARKER.substring(0, maxUtf8Bytes);
    }
    int contentBudget = maxUtf8Bytes - markerBytes;
    StringBuilder bounded = new StringBuilder(Math.min(value.length(), contentBudget));
    int usedBytes = 0;
    int lines = 1;
    boolean previousCarriageReturn = false;
    for (int offset = 0; offset < value.length(); ) {
      int codePoint = value.codePointAt(offset);
      int characters = Character.charCount(codePoint);
      int bytes = utf8Length(codePoint, value, offset, characters);
      boolean lineBreak = codePoint == '\r' || (codePoint == '\n' && !previousCarriageReturn);
      if (usedBytes + bytes > contentBudget || (lineBreak && lines >= maxLines)) {
        break;
      }
      bounded.appendCodePoint(codePoint);
      usedBytes += bytes;
      if (lineBreak) {
        lines++;
      }
      previousCarriageReturn = codePoint == '\r';
      offset += characters;
    }
    bounded.append(TRUNCATION_MARKER);
    return bounded.toString();
  }

  private static boolean exceedsBounds(String value, int maxUtf8Bytes, int maxLines) {
    long bytes = 0;
    int lines = 1;
    boolean previousCarriageReturn = false;
    for (int offset = 0; offset < value.length(); ) {
      int codePoint = value.codePointAt(offset);
      int characters = Character.charCount(codePoint);
      bytes += utf8Length(codePoint, value, offset, characters);
      if (bytes > maxUtf8Bytes) {
        return true;
      }
      if (codePoint == '\r' || (codePoint == '\n' && !previousCarriageReturn)) {
        lines++;
        if (lines > maxLines) {
          return true;
        }
      }
      previousCarriageReturn = codePoint == '\r';
      offset += characters;
    }
    return false;
  }

  private static int utf8Length(int codePoint, String value, int offset, int characters) {
    if (characters == 1 && Character.isSurrogate(value.charAt(offset))) {
      // String#getBytes(UTF_8) replaces an unpaired surrogate with one ASCII question mark.
      return 1;
    }
    if (codePoint <= 0x7f) {
      return 1;
    }
    if (codePoint <= 0x7ff) {
      return 2;
    }
    return codePoint <= 0xffff ? 3 : 4;
  }
}
