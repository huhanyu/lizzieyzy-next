package featurecat.lizzie.analysis;

import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.Map;

/** In-memory observation only: dropping display lines never drops protocol responses. */
final class FoxGtpTranscript {
  static final int MAX_CHARS = 65536;
  private final ArrayDeque<String> pending = new ArrayDeque<>();
  private final Map<String, Long> requests = new LinkedHashMap<>();
  private long connection;
  private Long responseConnection;
  private int chars;

  synchronized void begin(long connection) {
    this.connection = connection;
    pending.clear();
    chars = 0;
    // Outstanding old responses retain their old connection, including across reconnect.
  }

  synchronized void sent(String command) {
    String id = command.split(" ", 2)[0];
    requests.put(id, connection);
    while (requests.size() > 512) requests.remove(requests.keySet().iterator().next());
    append("> ", command, connection);
  }

  synchronized void received(String line) {
    String text = line.trim();
    if (text.startsWith("=") || text.startsWith("?")) {
      String id = text.substring(1).split("\\s", 2)[0];
      responseConnection = requests.remove(id);
    }
    if (responseConnection != null) append("< ", line, responseConnection);
    if (text.isEmpty()) responseConnection = null;
  }

  private void append(String direction, String line, long sourceConnection) {
    if (sourceConnection != connection) return;
    String prefix = "[" + connection + "] " + direction;
    int limit = MAX_CHARS - prefix.length() - 2;
    String entry = prefix + (line.length() <= limit ? line : line.substring(0, limit) + "…") + "\n";
    while (chars + entry.length() > MAX_CHARS && !pending.isEmpty()) {
      chars -= pending.removeFirst().length();
    }
    pending.addLast(entry);
    chars += entry.length();
  }

  synchronized String drain() {
    if (pending.isEmpty()) return "";
    StringBuilder batch = new StringBuilder(chars);
    while (!pending.isEmpty()) batch.append(pending.removeFirst());
    chars = 0;
    return batch.toString();
  }
}
