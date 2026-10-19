package featurecat.lizzie.logging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

class StandardErrorCaptureTest {
  @TempDir Path tempDir;

  @AfterEach
  void cleanUp() {
    StandardErrorCapture.uninstall();
    LoggingRuntime.resetForTests();
  }

  @Test
  void everyLineReachesTheLogAndTheOriginalStream() {
    ByteArrayOutputStream console = new ByteArrayOutputStream();
    ListAppender<ILoggingEvent> events = attachedListAppender("test.stderr.lines");
    PrintStream err = capture(console, "test.stderr.lines");

    err.print("first line\nsecond ");
    err.println("line");
    err.println();

    String newline = System.lineSeparator();
    assertEquals(
        "first line\nsecond line" + newline + newline, console.toString(StandardCharsets.UTF_8));
    assertEquals(List.of("stderr: first line", "stderr: second line"), formatted(events));
  }

  @Test
  void printStackTraceIsLoggedLineByLine() {
    ListAppender<ILoggingEvent> events = attachedListAppender("test.stderr.trace");
    PrintStream err = capture(new ByteArrayOutputStream(), "test.stderr.trace");

    new IllegalStateException("stderr-canary").printStackTrace(err);

    List<String> lines = formatted(events);
    assertTrue(lines.get(0).contains("IllegalStateException: stderr-canary"), lines.toString());
    assertTrue(lines.stream().anyMatch(line -> line.contains("\tat ")), lines.toString());
  }

  @Test
  void loggingThatWritesToStderrIsNotFedBackIntoTheLog() {
    ByteArrayOutputStream console = new ByteArrayOutputStream();
    Logger logger = (Logger) LoggerFactory.getLogger("test.stderr.reentrant");
    PrintStream[] err = new PrintStream[1];
    ListAppender<ILoggingEvent> echoing =
        new ListAppender<>() {
          @Override
          protected void append(ILoggingEvent event) {
            super.append(event);
            err[0].println("appender echo: " + event.getFormattedMessage());
          }
        };
    echoing.start();
    logger.addAppender(echoing);
    err[0] = capture(console, "test.stderr.reentrant");

    err[0].println("original");

    assertEquals(List.of("stderr: original"), formatted(echoing));
    assertTrue(
        console.toString(StandardCharsets.UTF_8).contains("appender echo: stderr: original"));
  }

  @Test
  void installedCaptureWritesStackTracesIntoAppLogAndUninstallRestoresStderr() throws Exception {
    LoggingRuntime runtime =
        LoggingRuntime.initialize(
            new WorkDirectoryResolution(tempDir, List.of()),
            new LoggingLimits(64, 32, 32, 32, 7, 1_000_000, 256_000));
    PrintStream before = System.err;

    StandardErrorCapture.install();
    StandardErrorCapture.install();
    new IllegalStateException("app-log-canary").printStackTrace();
    StandardErrorCapture.uninstall();

    assertSame(before, System.err);
    runtime.awaitIdle();
    String app = Files.readString(tempDir.resolve("logs/app.log"));
    assertTrue(app.contains("app-log-canary"), app);
  }

  private static PrintStream capture(ByteArrayOutputStream console, String loggerName) {
    return new PrintStream(
        new StandardErrorCapture(
            new PrintStream(console, true, StandardCharsets.UTF_8),
            LoggerFactory.getLogger(loggerName),
            StandardCharsets.UTF_8),
        true,
        StandardCharsets.UTF_8);
  }

  private static ListAppender<ILoggingEvent> attachedListAppender(String loggerName) {
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    ((Logger) LoggerFactory.getLogger(loggerName)).addAppender(appender);
    return appender;
  }

  private static List<String> formatted(ListAppender<ILoggingEvent> appender) {
    return appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
  }
}
