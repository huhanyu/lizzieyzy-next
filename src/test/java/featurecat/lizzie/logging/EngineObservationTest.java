package featurecat.lizzie.logging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

class EngineObservationTest {
  @TempDir Path tempDir;

  @AfterEach
  void tearDown() {
    LoggingRuntime.resetForTests();
  }

  @Test
  void withoutRuntimeDoesNotEmitEvenWhenLoggersAreEnabled() {
    LoggingRuntime.resetForTests();
    Logger engine = (Logger) LoggerFactory.getLogger(LogCategories.ENGINE);
    Logger gtp = (Logger) LoggerFactory.getLogger(LogCategories.GTP);
    Logger trace = (Logger) LoggerFactory.getLogger(LogCategories.ENGINE_TRACE);
    engine.setLevel(Level.DEBUG);
    gtp.setLevel(Level.DEBUG);
    trace.setLevel(Level.INFO);
    ListAppender<ILoggingEvent> engineEvents = attach(engine);
    ListAppender<ILoggingEvent> gtpEvents = attach(gtp);
    ListAppender<ILoggingEvent> traceEvents = attach(trace);

    assertFalse(EngineObservation.engineDiagnosticsEnabled());
    assertFalse(EngineObservation.gtpDiagnosticsEnabled());
    assertFalse(EngineObservation.traceEnabled());

    EngineObservation.recordStarted("eng-1", "MAIN_BOARD");
    EngineObservation.recordQueue("eng-1", 1, 1);
    EngineObservation.recordCommandSent("eng-1", "cmd-1", "play", 0, 1);
    EngineObservation.traceRawCommand("eng-1", "cmd-1", "play B D4");

    assertTrue(engineEvents.list.isEmpty(), engineEvents.list.toString());
    assertTrue(gtpEvents.list.isEmpty(), gtpEvents.list.toString());
    assertTrue(traceEvents.list.isEmpty(), traceEvents.list.toString());
  }

  @Test
  void initializedRuntimeWithoutFullTraceDoesNotEmitRawCommands() {
    LoggingRuntime runtime =
        LoggingRuntime.initialize(
            new WorkDirectoryResolution(tempDir, List.of()),
            new LoggingLimits(64, 32, 32, 32, 7, 1_000_000, 256_000));
    Logger trace = (Logger) LoggerFactory.getLogger(LogCategories.ENGINE_TRACE);
    ListAppender<ILoggingEvent> traceEvents = attach(trace);

    assertFalse(EngineObservation.traceEnabled());
    EngineObservation.traceRawCommand("eng-1", "cmd-1", "play B D4");
    runtime.awaitIdle();

    assertTrue(traceEvents.list.isEmpty(), traceEvents.list.toString());
  }

  @Test
  void rawTraceEventIsUtf8BoundedBeforeItReachesTheAppender() {
    LoggingRuntime runtime =
        LoggingRuntime.initialize(
            new WorkDirectoryResolution(tempDir, List.of()),
            new LoggingLimits(64, 32, 32, 32, 7, 1_000_000, 256_000));
    runtime.startFullTrace(EnumSet.of(TraceScope.ENGINE_GTP));
    Logger trace = (Logger) LoggerFactory.getLogger(LogCategories.ENGINE_TRACE);
    ListAppender<ILoggingEvent> traceEvents = attach(trace);

    EngineObservation.traceRawStream("eng-1", "cmd-1", "棋😀".repeat(10_000));

    assertEquals(1, traceEvents.list.size(), traceEvents.list.toString());
    String message = traceEvents.list.get(0).getFormattedMessage();
    String payload = message.substring(message.indexOf('=') + 1);
    assertTrue(
        payload.getBytes(StandardCharsets.UTF_8).length
            <= ObservationText.RAW_EVENT_MAX_UTF8_BYTES,
        Integer.toString(payload.getBytes(StandardCharsets.UTF_8).length));
    assertTrue(payload.endsWith(" [truncated]"), payload);
  }

  @Test
  void transportFailureUsesBoundedStructuredDimensions() {
    LoggingRuntime.initialize(
        new WorkDirectoryResolution(tempDir, List.of()),
        new LoggingLimits(64, 32, 32, 32, 7, 1_000_000, 256_000));
    Logger engine = (Logger) LoggerFactory.getLogger(LogCategories.ENGINE);
    ListAppender<ILoggingEvent> events = attach(engine);

    EngineObservation.recordTransportFailure(
        "eng-1", "stdout", "io-error", new java.io.IOException("secret raw payload"));

    assertEquals(1, events.list.size(), events.list.toString());
    String message = events.list.get(0).getFormattedMessage();
    assertTrue(message.contains("event=transport-failure"), message);
    assertTrue(message.contains("stream=stdout"), message);
    assertTrue(message.contains("reason=io-error"), message);
    assertTrue(message.contains("errorType=IOException"), message);
    assertFalse(message.contains("secret raw payload"), message);
  }

  private static ListAppender<ILoggingEvent> attach(Logger logger) {
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);
    return appender;
  }
}
