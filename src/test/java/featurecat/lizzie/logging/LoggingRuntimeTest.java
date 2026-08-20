package featurecat.lizzie.logging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.LoggingEvent;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import java.util.ServiceLoader;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;
import org.slf4j.spi.SLF4JServiceProvider;

class LoggingRuntimeTest {
  @TempDir Path tempDir;

  @AfterEach
  void tearDown() {
    LoggingRuntime.resetForTests();
  }

  @Test
  void bootstrapWritesStartupEventToAppLog() throws Exception {
    LoggingRuntime runtime = start();

    runtime.awaitIdle();
    String appLog = read("logs/app.log");
    assertTrue(appLog.contains("application log session started"));
    assertTrue(appLog.contains(runtime.applicationLogSessionId()));
    assertTrue(Files.isRegularFile(tempDir.resolve("logs/crash.log")));
    assertFalse(Files.exists(tempDir.resolve("logs/engine-trace.log")));
  }

  @Test
  void repeatedInitializeReusesRuntimeWithoutDuplicateStartupEvents() throws Exception {
    LoggingRuntime first = start();
    LoggingRuntime second =
        LoggingRuntime.initialize(new WorkDirectoryResolution(tempDir, List.of()), testLimits());
    first.awaitIdle();

    assertEquals(first, second);
    String appLog = read("logs/app.log");
    assertEquals(1, count(appLog, "application log session started"));
  }

  @Test
  void discoversExactlyOneSlf4jProvider() {
    AtomicInteger providers = new AtomicInteger();
    ServiceLoader.load(SLF4JServiceProvider.class).forEach(provider -> providers.incrementAndGet());
    assertEquals(1, providers.get());
  }

  @Test
  void diagnosticsEnableSelectedModuleImmediately() throws Exception {
    LoggingRuntime runtime = start();
    org.slf4j.Logger engine = LoggerFactory.getLogger(LogCategories.ENGINE);
    engine.debug("hidden-before-diagnostics");
    runtime.applySettings(LoggingSettings.defaults().withDiagnosticsEnabled(true));
    engine.debug("visible-after-diagnostics");
    runtime.awaitIdle();

    String appLog = read("logs/app.log");
    assertFalse(appLog.contains("hidden-before-diagnostics"));
    assertTrue(appLog.contains("visible-after-diagnostics"));
  }

  @Test
  void persistFailureRestoresPreviousRuntimePlan() throws Exception {
    LoggingRuntime runtime = start();
    LoggingSettings enabled = LoggingSettings.defaults().withDiagnosticsEnabled(true);
    assertThrows(
        IllegalStateException.class,
        () ->
            runtime.applySettings(
                enabled,
                settings -> {
                  throw new IOException("disk full");
                }));
    assertFalse(runtime.settings().diagnosticsEnabled());
    LoggerFactory.getLogger(LogCategories.ENGINE).debug("should-remain-hidden");
    runtime.awaitIdle();
    assertFalse(read("logs/app.log").contains("should-remain-hidden"));
  }

  @Test
  void fullTraceStartStopWritesDistinctSessionsToSelectedStreamsOnly() throws Exception {
    LoggingRuntime runtime = start();
    runtime.applySettings(
        LoggingSettings.defaults().withPreferredTraceScopes(EnumSet.of(TraceScope.ENGINE_GTP)));
    runtime.startFullTrace();
    String first = runtime.currentTraceSessionId();
    LoggerFactory.getLogger(LogCategories.ENGINE_TRACE).info("raw-gtp-1");
    runtime.stopFullTrace();
    runtime.startFullTrace();
    String second = runtime.currentTraceSessionId();
    LoggerFactory.getLogger(LogCategories.ENGINE_TRACE).info("raw-gtp-2");
    runtime.awaitIdle();
    runtime.stopFullTrace();
    runtime.awaitIdle();

    assertNotEquals(first, second);
    String trace = read("logs/engine-trace.log");
    assertTrue(trace.contains("Full Trace session started"), trace);
    assertTrue(trace.contains("Full Trace session stopped"), trace);
    assertTrue(trace.contains(first), "missing first=" + first + " in " + trace);
    assertTrue(trace.contains(second), "missing second=" + second + " in " + trace);
    assertTrue(trace.contains("raw-gtp-1"));
    assertFalse(Files.exists(tempDir.resolve("logs/readboard-trace.log")));
    assertFalse(read("logs/app.log").contains("raw-gtp-1"));
  }

  @Test
  void restartDoesNotActivatePersistedTracePreference() throws Exception {
    Path work = tempDir;
    LoggingRuntime runtime = start();
    runtime.applySettings(
        LoggingSettings.defaults()
            .withPreferredTraceScopes(EnumSet.of(TraceScope.NETWORK_WEBSOCKET)));
    runtime.shutdown();
    LoggingRuntime.resetForTests();
    LoggingRuntime restarted =
        LoggingRuntime.initialize(new WorkDirectoryResolution(work, List.of()), testLimits());
    restarted.applySettings(
        LoggingSettings.defaults()
            .withPreferredTraceScopes(EnumSet.of(TraceScope.NETWORK_WEBSOCKET)));

    assertFalse(restarted.fullTraceActive());
    assertFalse(Files.exists(work.resolve("logs/network-trace.log")));
  }

  @Test
  void rollingCreatesArchiveSegmentsAndKeepsActiveName() throws Exception {
    LoggingRuntime.resetForTests();
    LoggingRuntime runtime =
        LoggingRuntime.initialize(
            new WorkDirectoryResolution(tempDir, List.of()),
            new LoggingLimits(64, 32, 32, 32, 7, 4000, 400));
    org.slf4j.Logger app = LoggerFactory.getLogger(LogCategories.APP);
    for (int i = 0; i < 40; i++) {
      app.info("rolling-payload-{}", "x".repeat(80) + i);
    }
    runtime.awaitIdle(80);
    assertTrue(Files.isRegularFile(tempDir.resolve("logs/app.log")));
    try (var stream = Files.list(tempDir.resolve("logs/archive"))) {
      assertTrue(stream.anyMatch(path -> path.getFileName().toString().startsWith("app.")));
    }
  }

  @Test
  void saturatedTraceQueueDoesNotBlockAppOrProducer() throws Exception {
    LoggingRuntime runtime =
        LoggingRuntime.initialize(
            new WorkDirectoryResolution(tempDir, List.of()),
            new LoggingLimits(32, 4, 4, 4, 7, 10000, 1000));
    runtime.startFullTrace(EnumSet.of(TraceScope.ENGINE_GTP));
    CountDownLatch gate = new CountDownLatch(1);
    runtime.blockPersistenceForTests(LogStream.ENGINE_TRACE, gate);
    org.slf4j.Logger trace = LoggerFactory.getLogger(LogCategories.ENGINE_TRACE);
    long started = System.nanoTime();
    for (int i = 0; i < 64; i++) {
      trace.info("flood-{}", i);
    }
    long elapsedNanos = System.nanoTime() - started;
    LoggerFactory.getLogger(LogCategories.APP).error("app-while-trace-blocked");
    runtime.awaitIdle();
    gate.countDown();

    assertTrue(elapsedNanos < TimeUnit.SECONDS.toNanos(1));
    assertTrue(read("logs/app.log").contains("app-while-trace-blocked"));
    assertTrue(runtime.status().stream(LogStream.ENGINE_TRACE).orElseThrow().droppedCount() > 0);
  }

  @Test
  void sanitizerRemovesCredentialCanariesAndKeepsOrdinaryPaths() throws Exception {
    LoggingRuntime runtime = start();
    LoggerFactory.getLogger(LogCategories.APP)
        .error(
            "login password={} path={} url={}",
            "CANARY_PASSWORD_7f3a",
            "/home/dev/lizzieyzy-next/config.txt",
            "https://example.test/status");
    runtime.awaitIdle();

    String appLog = read("logs/app.log");
    assertFalse(appLog.contains("CANARY_PASSWORD_7f3a"));
    assertTrue(appLog.contains("/home/dev/lizzieyzy-next/config.txt"));
    assertTrue(appLog.contains("https://example.test/status"));
  }

  @Test
  void sanitizerFailureOmitsOriginalEvent() {
    SanitizingEncoder encoder = new SanitizingEncoder();
    LoggerContext context = new LoggerContext();
    encoder.setContext(context);
    encoder.setPattern("%msg%n");
    encoder.setSanitizer(
        new PersistenceSanitizer() {
          @Override
          public String sanitize(String text) {
            throw new IllegalStateException("boom");
          }
        });
    encoder.start();
    LoggingEvent event = new LoggingEvent();
    event.setLoggerName(LogCategories.APP);
    event.setLevel(Level.INFO);
    event.setMessage("secret CANARY_SHOULD_NOT_APPEAR");
    String encoded = new String(encoder.encode(event), StandardCharsets.UTF_8);
    assertEquals(PersistenceSanitizer.FAILURE_MARKER + System.lineSeparator(), encoded);
  }

  @Test
  void engineAndCommandIdentitiesAreNotReused() {
    LoggingRuntime runtime = start();
    String firstEngine = runtime.newEngineIdentity();
    String secondEngine = runtime.newEngineIdentity();
    assertNotEquals(firstEngine, secondEngine);
    assertNotEquals(runtime.newCommandIdentity(), runtime.newCommandIdentity());
  }

  @Test
  void idleShutdownDoesNotBurnThreeSecondBudget() {
    LoggingRuntime runtime = start();
    runtime.awaitIdle();
    long started = System.nanoTime();
    runtime.shutdown();
    assertTrue(System.nanoTime() - started < TimeUnit.SECONDS.toNanos(1));
  }

  @Test
  void shutdownFlushesAppAndReportsUnwrittenWhenBlocked() throws Exception {
    LoggingRuntime runtime = start();
    CountDownLatch gate = new CountDownLatch(1);
    runtime.blockPersistenceForTests(LogStream.APP, gate);
    LoggerFactory.getLogger(LogCategories.APP).error("late-event");
    ShutdownReport report = runtime.shutdown();
    gate.countDown();
    assertTrue(report.unwritten(LogStream.APP) >= 0);
  }

  @Test
  void isolatedJvmProviderSmokeWritesAppLog() throws Exception {
    Path shaded = findShadedJar();
    Assumptions.assumeTrue(shaded != null, "shaded artifact is required for provider smoke");
    Path work = Files.createTempDirectory(tempDir, "smoke");
    String java =
        Path.of(
                System.getProperty("java.home"),
                "bin",
                System.getProperty("os.name", "").startsWith("Windows") ? "java.exe" : "java")
            .toString();
    Process process =
        new ProcessBuilder(
                java,
                "-cp",
                shaded.toString(),
                LoggingProviderSmoke.class.getName(),
                work.toString())
            .redirectErrorStream(true)
            .start();
    String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    assertEquals(0, process.waitFor(), output);
    assertTrue(Files.readString(work.resolve("logs/app.log")).contains("provider-smoke"));
  }

  private LoggingRuntime start() {
    LoggingRuntime.resetForTests();
    return LoggingRuntime.initialize(new WorkDirectoryResolution(tempDir, List.of()), testLimits());
  }

  private static LoggingLimits testLimits() {
    return new LoggingLimits(64, 32, 32, 32, 7, 1_000_000, 256_000);
  }

  private String read(String relative) throws IOException {
    return Files.readString(tempDir.resolve(relative));
  }

  private static int count(String text, String token) {
    int count = 0;
    int index = 0;
    while ((index = text.indexOf(token, index)) >= 0) {
      count++;
      index += token.length();
    }
    return count;
  }

  private static Path findShadedJar() {
    Path target = Path.of("target");
    if (!Files.isDirectory(target)) {
      return null;
    }
    try (var stream = Files.list(target)) {
      return stream
          .filter(path -> path.getFileName().toString().endsWith("-shaded.jar"))
          .findFirst()
          .orElse(null);
    } catch (IOException e) {
      return null;
    }
  }
}
