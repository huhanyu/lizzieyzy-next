package featurecat.lizzie.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.UnsynchronizedAppenderBase;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

final class BoundedAsyncAppender extends UnsynchronizedAppenderBase<ILoggingEvent> {
  private final LogStream stream;
  private final BlockingQueue<ILoggingEvent> queue;
  private final boolean dropInfoFirst;
  private final int discardingThreshold;
  private final AtomicLong dropped = new AtomicLong();
  private final AtomicBoolean accepting = new AtomicBoolean(true);
  private final AtomicBoolean workerFailed = new AtomicBoolean();
  private volatile Appender<ILoggingEvent> nested;
  private volatile LoggingRuntime runtime;
  private volatile CountDownLatch gate;
  private Thread worker;

  BoundedAsyncAppender(LogStream stream, int capacity, boolean dropInfoFirst) {
    this.stream = stream;
    this.queue = new ArrayBlockingQueue<>(Math.max(1, capacity));
    this.dropInfoFirst = dropInfoFirst;
    this.discardingThreshold = Math.max(1, capacity / 4);
  }

  void setNested(Appender<ILoggingEvent> nested) {
    this.nested = nested;
  }

  void setRuntime(LoggingRuntime runtime) {
    this.runtime = runtime;
  }

  void setGate(CountDownLatch gate) {
    this.gate = gate;
  }

  LogStream stream() {
    return stream;
  }

  long droppedCount() {
    return dropped.get();
  }

  int queuedCount() {
    return queue.size();
  }

  void stopAccepting() {
    accepting.set(false);
  }

  @Override
  public void start() {
    if (nested == null) {
      addError("Nested appender is required");
      return;
    }
    if (!nested.isStarted()) {
      nested.start();
    }
    worker = new Thread(this::drain, "lizzie-log-" + stream.name().toLowerCase());
    worker.setDaemon(true);
    super.start();
    worker.start();
  }

  @Override
  public void stop() {
    accepting.set(false);
    super.stop();
    if (worker != null) {
      worker.interrupt();
    }
  }

  long shutdown(long deadlineNanos) {
    accepting.set(false);
    if (worker != null) {
      long remaining = deadlineNanos - System.nanoTime();
      if (remaining > 0) {
        try {
          worker.join(TimeUnit.NANOSECONDS.toMillis(remaining) + 1);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        }
      }
      if (worker.isAlive()) {
        worker.interrupt();
      }
    }
    if (nested != null && nested.isStarted()) {
      nested.stop();
    }
    super.stop();
    return queue.size();
  }

  @Override
  protected void append(ILoggingEvent event) {
    if (!accepting.get() || event == null) {
      if (event != null) {
        dropped.incrementAndGet();
        notifyDrop();
      }
      return;
    }
    event.prepareForDeferredProcessing();
    if (dropInfoFirst
        && queue.remainingCapacity() <= discardingThreshold
        && event.getLevel().toInt() < Level.WARN_INT) {
      dropped.incrementAndGet();
      notifyDrop();
      return;
    }
    if (!queue.offer(event)) {
      dropped.incrementAndGet();
      notifyDrop();
    }
  }

  private void drain() {
    while (true) {
      try {
        ILoggingEvent event = queue.poll(50, TimeUnit.MILLISECONDS);
        if (event == null) {
          if (!accepting.get()) {
            break;
          }
          continue;
        }
        CountDownLatch currentGate = gate;
        if (currentGate != null) {
          currentGate.await();
        }
        nested.doAppend(event);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        break;
      } catch (RuntimeException e) {
        if (workerFailed.compareAndSet(false, true) && runtime != null) {
          runtime.recordFailure(stream, e.getClass().getSimpleName() + ": " + e.getMessage());
        }
      }
    }
  }

  private void notifyDrop() {
    LoggingRuntime current = runtime;
    if (current != null) {
      current.recordDrop(stream);
    }
  }
}
