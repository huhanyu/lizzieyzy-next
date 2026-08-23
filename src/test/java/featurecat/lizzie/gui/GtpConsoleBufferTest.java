package featurecat.lizzie.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import featurecat.lizzie.logging.ObservationText;
import featurecat.lizzie.util.DocType;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class GtpConsoleBufferTest {
  @Test
  void dropsOldestWhenFullAndNeverExceedsCapacity() {
    GtpConsoleBuffer buffer = new GtpConsoleBuffer();
    for (int i = 0; i < GtpConsoleBuffer.CAPACITY + 25; i++) {
      buffer.offer(doc("line-" + i));
    }
    assertEquals(GtpConsoleBuffer.CAPACITY, buffer.size());
    assertEquals("line-25", buffer.poll().content);
  }

  @Test
  void offerReturnsWhileConsumerIsStalled() throws Exception {
    GtpConsoleBuffer buffer = new GtpConsoleBuffer();
    CountDownLatch started = new CountDownLatch(1);
    CountDownLatch hold = new CountDownLatch(1);
    AtomicBoolean producerFinished = new AtomicBoolean();
    Thread consumer =
        new Thread(
            () -> {
              started.countDown();
              try {
                hold.await(2, TimeUnit.SECONDS);
              } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
              }
              buffer.poll();
            });
    consumer.start();
    assertTrue(started.await(1, TimeUnit.SECONDS));
    long began = System.nanoTime();
    buffer.offer(doc("producer"));
    producerFinished.set(true);
    assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - began) < 250);
    hold.countDown();
    consumer.join(1000L);
    assertTrue(producerFinished.get());
  }

  @Test
  void boundsEachConsoleEventByUtf8BytesAndLines() {
    GtpConsoleBuffer buffer = new GtpConsoleBuffer();

    buffer.offer(doc("棋😀".repeat(10_000)));

    String retained = buffer.poll().content;
    assertTrue(
        retained.getBytes(StandardCharsets.UTF_8).length
            <= ObservationText.RAW_EVENT_MAX_UTF8_BYTES,
        Integer.toString(retained.getBytes(StandardCharsets.UTF_8).length));
    assertTrue(retained.endsWith(" [truncated]"), retained);

    buffer.offer(doc("line\r\n".repeat(ObservationText.RAW_EVENT_MAX_LINES + 20)));
    String lineBounded = buffer.poll().content;
    assertTrue(lineBounded.lines().count() <= ObservationText.RAW_EVENT_MAX_LINES, lineBounded);
    assertTrue(lineBounded.endsWith(" [truncated]"), lineBounded);
  }

  private static DocType doc(String content) {
    DocType type = new DocType();
    type.content = content;
    return type;
  }
}
