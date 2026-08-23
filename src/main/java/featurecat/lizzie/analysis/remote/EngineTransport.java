package featurecat.lizzie.analysis.remote;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public interface EngineTransport extends AutoCloseable {
  void start() throws IOException;

  InputStream stdout();

  OutputStream stdin();

  InputStream stderr();

  boolean isOpen();

  default void setUnresponsiveListener(Runnable listener) {}

  default void markAnalysisProgressAccepted(long totalPlayouts) {}

  /** True when the transport ended deliberately so its owner can rebuild a fresh session. */
  default boolean isRecoveryRequested() {
    return false;
  }

  String description();

  /**
   * Immediately tears down the physical transport without writing an application-level shutdown
   * command. Implementations must make this operation idempotent.
   */
  void abort();

  @Override
  void close();
}
