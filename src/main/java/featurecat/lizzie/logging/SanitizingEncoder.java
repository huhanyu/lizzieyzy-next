package featurecat.lizzie.logging;

import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.encoder.EncoderBase;
import java.nio.charset.StandardCharsets;

final class SanitizingEncoder extends EncoderBase<ILoggingEvent> {
  private final PatternLayoutEncoder delegate = new PatternLayoutEncoder();
  private PersistenceSanitizer sanitizer = new PersistenceSanitizer();

  void setPattern(String pattern) {
    delegate.setPattern(pattern);
  }

  void setSanitizer(PersistenceSanitizer sanitizer) {
    this.sanitizer = sanitizer == null ? new PersistenceSanitizer() : sanitizer;
  }

  @Override
  public void start() {
    delegate.setContext(context);
    delegate.setCharset(StandardCharsets.UTF_8);
    delegate.start();
    super.start();
  }

  @Override
  public void stop() {
    super.stop();
    delegate.stop();
  }

  @Override
  public byte[] headerBytes() {
    return delegate.headerBytes();
  }

  @Override
  public byte[] footerBytes() {
    return delegate.footerBytes();
  }

  @Override
  public byte[] encode(ILoggingEvent event) {
    try {
      byte[] encoded = delegate.encode(event);
      String formatted = new String(encoded, StandardCharsets.UTF_8);
      return sanitizer.sanitize(formatted).getBytes(StandardCharsets.UTF_8);
    } catch (RuntimeException e) {
      return (PersistenceSanitizer.FAILURE_MARKER + System.lineSeparator())
          .getBytes(StandardCharsets.UTF_8);
    }
  }
}
