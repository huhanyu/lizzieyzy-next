package featurecat.lizzie.fox;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

/** Fox AI 1.05 ASCII framing. A consumer only receives complete, validated frames. */
public final class FoxProtocol {
  private static final int MAX_FRAME = 65536;
  private final ByteArrayOutputStream pending = new ByteArrayOutputStream();

  public void accept(byte[] bytes, int count, Consumer<String> consumer) {
    for (int i = 0; i < count; i++) {
      int value = bytes[i] & 255;
      if (value > 127 || pending.size() >= MAX_FRAME) {
        pending.reset();
        throw new IllegalArgumentException("Invalid Fox ASCII frame");
      }
      pending.write(value);
      if (value == '\n') {
        byte[] frame = pending.toByteArray();
        pending.reset();
        consumer.accept(decode(frame));
      }
    }
  }

  public static String decode(byte[] frame) {
    int n = frame.length;
    if (n < 7
        || frame[0] != '$'
        || frame[n - 2] != '\r'
        || frame[n - 1] != '\n'
        || frame[n - 5] != '*') {
      throw new IllegalArgumentException("Invalid Fox frame boundary");
    }
    int xor = 0;
    for (int i = 1; i < n - 5; i++) {
      if (frame[i] < 32 || frame[i] > 126 || frame[i] == '*' || frame[i] == '$') {
        throw new IllegalArgumentException("Invalid Fox frame body");
      }
      xor ^= frame[i];
    }
    int hi = Character.digit((char) frame[n - 4], 16);
    int lo = Character.digit((char) frame[n - 3], 16);
    if (hi < 0 || lo < 0 || xor != (hi << 4 | lo)) {
      throw new IllegalArgumentException("Invalid Fox checksum");
    }
    return new String(frame, 1, n - 6, StandardCharsets.US_ASCII);
  }

  public static byte[] encode(String body) {
    int xor = 0;
    for (int i = 0; i < body.length(); i++) {
      char c = body.charAt(i);
      if (c < 32 || c > 126 || c == '*' || c == '$') {
        throw new IllegalArgumentException("Invalid Fox frame body");
      }
      xor ^= c;
    }
    return ("$" + body + "*" + String.format(java.util.Locale.ROOT, "%02X", xor) + "\r\n")
        .getBytes(StandardCharsets.US_ASCII);
  }
}
