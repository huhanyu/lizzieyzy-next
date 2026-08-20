package featurecat.lizzie.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.rolling.RollingFileAppender;
import ch.qos.logback.core.rolling.SizeAndTimeBasedRollingPolicy;
import ch.qos.logback.core.util.FileSize;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.LoggerFactory;

public final class LoggingRuntime {
  private static final String PATTERN =
      "%d{yyyy-MM-dd HH:mm:ss.SSS} %-5level [%logger] session=%X{lizzie.appSession} %msg%n%ex";
  private static final String STDERR_PREFIX = "LizzieYzy logging: ";
  private static final Object LOCK = new Object();
  private static LoggingRuntime instance;

  private final Path workDirectory;
  private final Path logsDirectory;
  private final LoggingLimits limits;
  private final String applicationLogSessionId;
  private final LoggerContext context;
  private final PersistenceSanitizer sanitizer;
  private final Map<LogStream, StreamState> streams = new EnumMap<>(LogStream.class);
  private final Map<LogStream, BoundedAsyncAppender> appenders = new EnumMap<>(LogStream.class);
  private final Set<String> issuedEngineIds = ConcurrentHashMap.newKeySet();
  private final AtomicLong engineSequence = new AtomicLong();
  private final AtomicLong commandSequence = new AtomicLong();
  private final AtomicBoolean persistenceEnabled = new AtomicBoolean();
  private final Set<String> stderrNotices = ConcurrentHashMap.newKeySet();
  private final PrintStream stderr;
  private volatile LoggingSettings settings = LoggingSettings.defaults();
  private volatile String traceSessionId;
  private volatile Set<TraceScope> activeTraceScopes = EnumSet.noneOf(TraceScope.class);
  private volatile boolean shutdown;

  private LoggingRuntime(
      Path workDirectory, LoggingLimits limits, LoggerContext context, PrintStream stderr) {
    this.workDirectory = workDirectory;
    this.logsDirectory = workDirectory.resolve("logs");
    this.limits = limits;
    this.context = context;
    this.stderr = stderr;
    this.applicationLogSessionId = UUID.randomUUID().toString();
    this.sanitizer = new PersistenceSanitizer();
    for (LogStream stream : LogStream.values()) {
      streams.put(stream, new StreamState(stream));
    }
  }

  public static LoggingRuntime initialize(WorkDirectoryResolution resolution) {
    return initialize(resolution, LoggingLimits.production());
  }

  public static LoggingRuntime initialize(
      WorkDirectoryResolution resolution, LoggingLimits limits) {
    Objects.requireNonNull(resolution, "resolution");
    Objects.requireNonNull(limits, "limits");
    synchronized (LOCK) {
      if (instance != null && !instance.shutdown) {
        return instance;
      }
      LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
      LoggingRuntime runtime =
          new LoggingRuntime(resolution.directory(), limits, context, System.err);
      runtime.bootstrap(resolution);
      instance = runtime;
      return runtime;
    }
  }

  public static Optional<LoggingRuntime> current() {
    synchronized (LOCK) {
      return Optional.ofNullable(instance);
    }
  }

  static void resetForTests() {
    synchronized (LOCK) {
      if (instance != null) {
        instance.shutdown();
        instance = null;
      }
      LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
      context.reset();
      if (!context.isStarted()) {
        context.start();
      }
    }
  }

  public String applicationLogSessionId() {
    return applicationLogSessionId;
  }

  public Path logsDirectory() {
    return logsDirectory;
  }

  public LoggingSettings settings() {
    return settings;
  }

  public String currentTraceSessionId() {
    return traceSessionId;
  }

  public boolean fullTraceActive() {
    return traceSessionId != null;
  }

  public String newEngineIdentity() {
    String identity;
    do {
      identity =
          "eng-"
              + Long.toUnsignedString(engineSequence.incrementAndGet(), 16)
              + "-"
              + Long.toUnsignedString(ThreadLocalRandom.current().nextLong(), 16);
    } while (!issuedEngineIds.add(identity));
    return identity;
  }

  public String newCommandIdentity() {
    return "cmd-" + Long.toUnsignedString(commandSequence.incrementAndGet(), 16);
  }

  public void applySettings(LoggingSettings newSettings) {
    applySettings(newSettings, null);
  }

  public void applySettings(LoggingSettings newSettings, LoggingSettingsPersister persister) {
    Objects.requireNonNull(newSettings, "newSettings");
    LoggingSettings previous = settings;
    synchronized (this) {
      try {
        applyPlan(newSettings);
        if (persister != null) {
          persister.save(newSettings);
        }
      } catch (RuntimeException | IOException e) {
        applyPlan(previous);
        if (e instanceof RuntimeException) {
          throw (RuntimeException) e;
        }
        throw new IllegalStateException("Failed to persist logging settings", e);
      }
    }
  }

  public void startFullTrace() {
    startFullTrace(settings.preferredTraceScopes());
  }

  public synchronized void startFullTrace(Set<TraceScope> scopes) {
    if (shutdown) {
      return;
    }
    Set<TraceScope> selected =
        scopes == null || scopes.isEmpty()
            ? EnumSet.noneOf(TraceScope.class)
            : EnumSet.copyOf(scopes);
    if (traceSessionId != null) {
      stopFullTrace();
    }
    traceSessionId = "trace-" + UUID.randomUUID();
    activeTraceScopes = selected;
    CorrelationContext.installTraceSession(traceSessionId);
    for (TraceScope scope : selected) {
      startTraceStream(scope);
      org.slf4j.LoggerFactory.getLogger(scope.loggerName())
          .info("Full Trace session started session={} scope={}", traceSessionId, scope.wireName());
    }
  }

  public synchronized void stopFullTrace() {
    String session = traceSessionId;
    if (session == null) {
      return;
    }
    for (TraceScope scope : activeTraceScopes) {
      org.slf4j.LoggerFactory.getLogger(scope.loggerName())
          .info("Full Trace session stopped session={} scope={}", session, scope.wireName());
    }
    awaitIdle(50);
    for (TraceScope scope : activeTraceScopes) {
      stopTraceStream(scope);
    }
    activeTraceScopes = EnumSet.noneOf(TraceScope.class);
    traceSessionId = null;
    CorrelationContext.clearTraceSession();
  }

  public LoggingStatus status() {
    List<LoggingStatus.StreamStatus> snapshot = new ArrayList<>();
    for (StreamState state : streams.values()) {
      snapshot.add(state.snapshot());
    }
    return new LoggingStatus(persistenceEnabled.get(), snapshot);
  }

  public ShutdownReport shutdown() {
    synchronized (this) {
      if (shutdown) {
        return new ShutdownReport(Map.of());
      }
      shutdown = true;
      long deadline = System.nanoTime() + LoggingLimits.SHUTDOWN_BUDGET_NANOS;
      for (TraceScope scope : activeTraceScopes) {
        BoundedAsyncAppender appender = appenders.get(scope.stream());
        if (appender != null) {
          appender.stopAccepting();
        }
      }
      Map<LogStream, Long> unwritten = new EnumMap<>(LogStream.class);
      BoundedAsyncAppender app = appenders.get(LogStream.APP);
      BoundedAsyncAppender crash = appenders.get(LogStream.CRASH);
      if (app != null) {
        unwritten.put(LogStream.APP, app.shutdown(deadline));
      }
      if (crash != null) {
        unwritten.put(LogStream.CRASH, crash.shutdown(deadline));
      }
      for (TraceScope scope : TraceScope.values()) {
        BoundedAsyncAppender appender = appenders.get(scope.stream());
        if (appender != null) {
          unwritten.put(scope.stream(), appender.shutdown(deadline));
        }
      }
      long remaining = 0;
      for (Long count : unwritten.values()) {
        remaining += count;
      }
      if (remaining > 0) {
        notice("shutdown", "unwritten events=" + remaining);
      }
      try {
        context.stop();
      } catch (RuntimeException ignored) {
      }
      CorrelationContext.clearAsync();
      return new ShutdownReport(unwritten);
    }
  }

  void recordDrop(LogStream stream) {
    StreamState state = streams.get(stream);
    if (state != null) {
      state.recordDrop();
    }
  }

  void recordFailure(LogStream stream, String reason) {
    StreamState state = streams.get(stream);
    if (state != null) {
      state.recordFailure(reason);
    }
    notice(stream.name(), reason);
  }

  void blockPersistenceForTests(LogStream stream, CountDownLatch gate) {
    BoundedAsyncAppender appender = appenders.get(stream);
    if (appender != null) {
      appender.setGate(gate);
    }
  }

  void awaitIdle() {
    awaitIdle(40);
  }

  void awaitIdle(int spins) {
    for (int i = 0; i < spins; i++) {
      boolean idle = true;
      for (BoundedAsyncAppender appender : appenders.values()) {
        if (appender != null && appender.queuedCount() > 0) {
          idle = false;
          break;
        }
      }
      if (idle) {
        try {
          Thread.sleep(20);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        }
        return;
      }
      try {
        Thread.sleep(10);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return;
      }
    }
  }

  private void bootstrap(WorkDirectoryResolution resolution) {
    try {
      context.reset();
      Logger root = context.getLogger(Logger.ROOT_LOGGER_NAME);
      root.setLevel(Level.WARN);
      root.setAdditive(true);
      configureProjectLoggers();
      CorrelationContext.installAppSession(applicationLogSessionId);
      Files.createDirectories(logsDirectory.resolve("archive"));
      persistenceEnabled.set(true);
      startFileStream(LogStream.APP, "app.log", true);
      startFileStream(LogStream.CRASH, "crash.log", true);
      attachCrashLogger();
      applyPlan(LoggingSettings.defaults());
      org.slf4j.Logger app = LoggerFactory.getLogger(LogCategories.APP);
      app.info(
          "application log session started id={} workDir={}",
          applicationLogSessionId,
          workDirectory);
      for (WorkDirectoryDiagnostic diagnostic : resolution.diagnostics()) {
        app.info("work-directory {}: {}", diagnostic.code(), diagnostic.message());
      }
    } catch (RuntimeException | IOException e) {
      persistenceEnabled.set(false);
      notice("bootstrap", e.getClass().getSimpleName() + ": " + e.getMessage());
    }
  }

  private void configureProjectLoggers() {
    for (String name :
        List.of(
            LogCategories.APP,
            LogCategories.CONFIG,
            LogCategories.ENGINE,
            LogCategories.GTP,
            LogCategories.READBOARD,
            LogCategories.NETWORK,
            LogCategories.REMOTE,
            LogCategories.SGF,
            LogCategories.UI,
            LogCategories.CRASH,
            LogCategories.DIAGNOSTICS)) {
      Logger logger = context.getLogger(name);
      logger.setLevel(Level.INFO);
      logger.setAdditive(true);
    }
    for (TraceScope scope : TraceScope.values()) {
      Logger logger = context.getLogger(scope.loggerName());
      logger.setLevel(Level.OFF);
      logger.setAdditive(false);
    }
  }

  private void applyPlan(LoggingSettings newSettings) {
    this.settings = newSettings;
    for (DiagnosticModule module : DiagnosticModule.values()) {
      Level level =
          newSettings.diagnosticsEnabled() && newSettings.diagnosticModules().contains(module)
              ? Level.DEBUG
              : Level.INFO;
      context.getLogger(module.loggerName()).setLevel(level);
      if (module == DiagnosticModule.NETWORK_REMOTE) {
        context.getLogger(LogCategories.REMOTE).setLevel(level);
      }
    }
  }

  private void attachCrashLogger() {
    Logger crash = context.getLogger(LogCategories.CRASH);
    BoundedAsyncAppender crashAppender = appenders.get(LogStream.CRASH);
    if (crashAppender != null) {
      crash.addAppender(crashAppender);
      crash.setAdditive(true);
    }
  }

  private void startFileStream(LogStream stream, String fileName, boolean dropInfoFirst) {
    try {
      RollingFileAppender<ILoggingEvent> file = rollingAppender(stream, fileName);
      BoundedAsyncAppender async =
          new BoundedAsyncAppender(stream, limits.queueCapacity(stream), dropInfoFirst);
      async.setName(stream.name());
      async.setContext(context);
      async.setNested(file);
      async.setRuntime(this);
      async.start();
      appenders.put(stream, async);
      if (stream == LogStream.APP) {
        context.getLogger(Logger.ROOT_LOGGER_NAME).addAppender(async);
      }
    } catch (RuntimeException e) {
      persistenceEnabled.set(stream != LogStream.APP && persistenceEnabled.get());
      recordFailure(stream, e.getClass().getSimpleName() + ": " + e.getMessage());
    }
  }

  private void startTraceStream(TraceScope scope) {
    if (!appenders.containsKey(scope.stream())) {
      startFileStream(scope.stream(), scope.fileName(), false);
    }
    Logger logger = context.getLogger(scope.loggerName());
    BoundedAsyncAppender appender = appenders.get(scope.stream());
    if (appender != null) {
      if (!logger.isAttached(appender)) {
        logger.addAppender(appender);
      }
      logger.setLevel(Level.INFO);
      logger.setAdditive(false);
    }
  }

  private void stopTraceStream(TraceScope scope) {
    Logger logger = context.getLogger(scope.loggerName());
    logger.setLevel(Level.OFF);
    BoundedAsyncAppender appender = appenders.get(scope.stream());
    if (appender != null) {
      logger.detachAppender(appender);
    }
  }

  private RollingFileAppender<ILoggingEvent> rollingAppender(LogStream stream, String fileName) {
    Path active = logsDirectory.resolve(fileName);
    Path archive = logsDirectory.resolve("archive");
    RollingFileAppender<ILoggingEvent> file = new RollingFileAppender<>();
    file.setName(stream.name() + "-file");
    file.setContext(context);
    file.setFile(active.toString());
    file.setAppend(true);
    file.setImmediateFlush(true);
    SanitizingEncoder encoder = new SanitizingEncoder();
    encoder.setContext(context);
    encoder.setPattern(PATTERN);
    encoder.setSanitizer(sanitizer);
    encoder.start();
    file.setEncoder(encoder);
    SizeAndTimeBasedRollingPolicy<ILoggingEvent> policy = new SizeAndTimeBasedRollingPolicy<>();
    policy.setContext(context);
    policy.setParent(file);
    String stem =
        fileName.endsWith(".log") ? fileName.substring(0, fileName.length() - 4) : fileName;
    policy.setFileNamePattern(archive.resolve(stem + ".%d{yyyy-MM-dd}.%i.log.gz").toString());
    policy.setMaxHistory(limits.retentionDays());
    policy.setTotalSizeCap(new FileSize(limits.totalSizeCapBytes()));
    policy.setMaxFileSize(new FileSize(limits.activeFileSizeBytes()));
    policy.start();
    file.setRollingPolicy(policy);
    file.start();
    return file;
  }

  private void notice(String key, String message) {
    if (!stderrNotices.add(key)) {
      return;
    }
    stderr.println(STDERR_PREFIX + key + " " + message);
  }

  private static final class StreamState {
    private final LogStream stream;
    private final AtomicLong dropped = new AtomicLong();
    private volatile String reason;
    private volatile Instant firstOccurrence;
    private volatile Instant lastOccurrence;
    private volatile boolean recovered;

    private StreamState(LogStream stream) {
      this.stream = stream;
    }

    private void recordDrop() {
      dropped.incrementAndGet();
      Instant now = Instant.now();
      if (firstOccurrence == null) {
        firstOccurrence = now;
        reason = "queue saturation";
      }
      lastOccurrence = now;
      recovered = false;
    }

    private void recordFailure(String failureReason) {
      Instant now = Instant.now();
      if (firstOccurrence == null) {
        firstOccurrence = now;
      }
      lastOccurrence = now;
      reason = failureReason;
      recovered = false;
    }

    private LoggingStatus.StreamStatus snapshot() {
      return new LoggingStatus.StreamStatus(
          stream, reason, firstOccurrence, lastOccurrence, dropped.get(), recovered);
    }
  }
}
