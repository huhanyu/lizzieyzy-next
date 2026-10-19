package featurecat.lizzie.logging;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.Charset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Copies every line written to {@code System.err} into app.log, while the original stream still
 * receives every byte.
 *
 * <p>Hundreds of legacy catch blocks report failures with {@code printStackTrace()}. A packaged
 * desktop app has no console, so without this those stack traces never reach app.log or the
 * diagnostic bundle that users attach to bug reports.
 */
public final class StandardErrorCapture extends OutputStream {
  static final String LOGGER_NAME = LogCategories.APP + ".stderr";
  private static final int MAX_LINE_BYTES = 16 * 1024;
  private static final ThreadLocal<Boolean> FORWARDING = ThreadLocal.withInitial(() -> false);
  private static final Object INSTALL_LOCK = new Object();
  private static PrintStream previous;
  private static PrintStream installed;

  private final PrintStream original;
  private final Logger logger;
  private final Charset charset;
  private final ByteArrayOutputStream line = new ByteArrayOutputStream();

  StandardErrorCapture(PrintStream original, Logger logger, Charset charset) {
    this.original = original;
    this.logger = logger;
    this.charset = charset;
  }

  /** Replaces {@code System.err} with a stream that also logs each line. Idempotent. */
  public static void install() {
    synchronized (INSTALL_LOCK) {
      if (installed != null) {
        return;
      }
      previous = System.err;
      Charset charset = stderrCharset();
      installed =
          new PrintStream(
              new StandardErrorCapture(previous, LoggerFactory.getLogger(LOGGER_NAME), charset),
              true,
              charset);
      System.setErr(installed);
    }
  }

  /** The encoding the JDK uses for {@code System.err}, so console output is not re-encoded. */
  static Charset stderrCharset() {
    String name = System.getProperty("sun.stderr.encoding");
    if (name != null && !name.isBlank()) {
      try {
        return Charset.forName(name);
      } catch (IllegalArgumentException unsupported) {
        // Fall through to the default, as the JDK does.
      }
    }
    return Charset.defaultCharset();
  }

  /** Restores the stream that was active before {@link #install()}. */
  public static void uninstall() {
    synchronized (INSTALL_LOCK) {
      if (installed == null) {
        return;
      }
      installed.flush();
      if (System.err == installed) {
        System.setErr(previous);
      }
      installed = null;
      previous = null;
    }
  }

  @Override
  public synchronized void write(int b) {
    original.write(b);
    append(b);
  }

  @Override
  public synchronized void write(byte[] bytes, int offset, int length) {
    original.write(bytes, offset, length);
    for (int i = offset; i < offset + length; i++) {
      append(bytes[i]);
    }
  }

  @Override
  public synchronized void flush() {
    original.flush();
  }

  private void append(int b) {
    if (b == '\n') {
      forwardLine();
    } else if (b != '\r' && line.size() < MAX_LINE_BYTES) {
      line.write(b);
    }
  }

  private void forwardLine() {
    String text = line.toString(charset).stripTrailing();
    line.reset();
    if (text.isEmpty() || FORWARDING.get() || isLoggingThread()) {
      // Never feed the logging pipeline's own error output back into it.
      return;
    }
    FORWARDING.set(true);
    try {
      logger.warn("stderr: {}", text);
    } catch (RuntimeException ignored) {
      // Logging must never break the code that printed to stderr.
    } finally {
      FORWARDING.set(false);
    }
  }

  private static boolean isLoggingThread() {
    String name = Thread.currentThread().getName();
    return name.startsWith("AsyncAppender-") || name.startsWith("logback-");
  }
}
