package featurecat.lizzie.teacher;

import java.time.Instant;
import java.util.Optional;

/** Adds an independently replaceable AI commentary block without touching the user's comment. */
public final class TeacherCommentCodec {
  static final String BEGIN = "[LizzieYzy AI Commentary BEGIN]";
  static final String END = "[LizzieYzy AI Commentary END]";
  private static final int MAX_COMMENTARY_CHARACTERS = 100_000;

  private TeacherCommentCodec() {}

  public static String upsert(String originalComment, String commentary, String model) {
    String body = escapeMarkers(normalize(commentary)).trim();
    if (body.isEmpty()) {
      throw new IllegalArgumentException("AI commentary is empty.");
    }
    if (body.length() > MAX_COMMENTARY_CHARACTERS) {
      throw new IllegalArgumentException("AI commentary is too large for an SGF comment.");
    }
    String cleanOriginal = removeBlocks(originalComment).trim();
    String safeModel = escapeMarkers(normalize(model)).replace('\n', ' ').trim();
    StringBuilder block = new StringBuilder();
    block.append(BEGIN).append('\n');
    block.append("generatedAt=").append(Instant.now()).append('\n');
    if (!safeModel.isEmpty()) {
      block.append("model=").append(safeModel).append('\n');
    }
    block.append('\n').append(body).append('\n').append(END);
    return cleanOriginal.isEmpty() ? block.toString() : cleanOriginal + "\n\n" + block;
  }

  public static Optional<String> extract(String comment) {
    String source = normalize(comment);
    int searchFrom = source.length();
    while (searchFrom > 0) {
      int start = source.lastIndexOf(BEGIN, searchFrom - 1);
      if (start < 0) {
        return Optional.empty();
      }
      int end = source.indexOf(END, start + BEGIN.length());
      if (end >= 0) {
        String block = source.substring(start + BEGIN.length(), end).trim();
        int separator = block.indexOf("\n\n");
        String content = separator >= 0 ? block.substring(separator + 2).trim() : block;
        return content.isEmpty() ? Optional.empty() : Optional.of(content);
      }
      searchFrom = start;
    }
    return Optional.empty();
  }

  public static String removeBlocks(String comment) {
    String remaining = normalize(comment);
    int searchFrom = remaining.length();
    while (searchFrom > 0) {
      int start = remaining.lastIndexOf(BEGIN, searchFrom - 1);
      if (start < 0) {
        break;
      }
      int end = remaining.indexOf(END, start + BEGIN.length());
      if (end < 0) {
        searchFrom = start;
        continue;
      }
      int after = end + END.length();
      remaining = remaining.substring(0, start) + remaining.substring(after);
      searchFrom = start;
    }
    return remaining.replaceAll("[ \\t]+\\n", "\n").replaceAll("\\n{3,}", "\n\n");
  }

  private static String normalize(String value) {
    return value == null ? "" : value.replace("\r\n", "\n").replace('\r', '\n');
  }

  private static String escapeMarkers(String value) {
    return value
        .replace(BEGIN, "[LizzieYzy AI Commentary BEGIN escaped]")
        .replace(END, "[LizzieYzy AI Commentary END escaped]");
  }
}
