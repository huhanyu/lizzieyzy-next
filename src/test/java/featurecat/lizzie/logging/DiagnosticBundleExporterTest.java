package featurecat.lizzie.logging;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import featurecat.lizzie.analysis.ReadBoardLoggingControl;
import featurecat.lizzie.analysis.ReadBoardLoggingProtocol;
import featurecat.lizzie.analysis.ReadBoardLoggingSnapshot;
import featurecat.lizzie.analysis.SyncDiagnosticsExportSnapshot;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Instant;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

class DiagnosticBundleExporterTest {
  private static final String PROCESS_SESSION = "dGVzdFByb2Nlc3NJRA";
  private static final String OLD_PROCESS_SESSION = "b2xkUHJvY2Vzc1Nlc3Npb24";
  private static final String HOST_SESSION = "dGVzdEhvc3RTZXNzaW9u";
  private static final String CANARY_PASSWORD = "CANARY_PASSWORD_7f3a";
  private static final String CANARY_TOKEN = "CANARY_TOKEN_9c2b";
  private static final String CANARY_COOKIE = "CANARY_COOKIE_4d11";
  private static final String CANARY_MACHINE_KEY = "CANARY_MACHINEKEY_88aa";
  private static final String CANARY_CREDENTIAL = "CANARY_CREDENTIAL_12ef";
  private static final byte[] PIXEL_PNG =
      new byte[] {
        (byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 0x01, 0x02, 0x03, 0x04
      };

  @TempDir Path tempDir;

  @AfterEach
  void tearDown() {
    LoggingRuntime.resetForTests();
  }

  @Test
  void defaultExportWritesAtomicPackageWithManifestAndSafeSources() throws Exception {
    LoggingRuntime runtime = start();
    LoggerFactory.getLogger(LogCategories.APP).info("app-evidence");
    LoggerFactory.getLogger(LogCategories.CRASH).error("crash-evidence");
    runtime.awaitIdle();

    JSONObject config = new JSONObject();
    JSONObject ui = new JSONObject();
    ui.put("board-size", 19);
    ui.put("unknown-support-key", "SHOULD_OMIT");
    ui.put("password", "CANARY_PASSWORD_EXPORT");
    config.put("ui", ui);
    JSONObject leelaz = new JSONObject();
    leelaz.put("command", "C:\\\\Users\\\\alice\\\\katago.exe gtp -model secret.bin");
    config.put("leelaz", leelaz);

    Path diagnostics = DiagnosticBundleExporter.defaultOutputDirectory(tempDir);
    Path zip =
        new DiagnosticBundleExporter(diagnostics)
            .export(
                new DiagnosticBundleRequest(
                    runtime,
                    EnumSet.noneOf(TraceScope.class),
                    config,
                    emptySnapshot(),
                    "next-dev"));

    assertTrue(zip.startsWith(diagnostics), zip.toString());
    assertTrue(zip.getFileName().toString().startsWith("lizzie-diagnostics-"));
    assertTrue(zip.getFileName().toString().endsWith(".zip"));
    assertFalse(Files.exists(Path.of(zip + ".partial")));
    try (var stream = Files.list(diagnostics)) {
      assertEquals(1, stream.filter(path -> path.toString().endsWith(".zip")).count());
    }

    Map<String, byte[]> entries = unzipEntries(zip);
    Set<String> names = entries.keySet();
    assertTrue(names.contains("manifest.json"));
    assertTrue(names.contains("logs/lizzie/app.log"));
    assertTrue(names.contains("logs/lizzie/crash.log"));
    assertTrue(names.contains("snapshots/config.json"));
    assertTrue(names.contains("snapshots/environment.txt"));
    assertTrue(names.contains("snapshots/summary.txt"));
    assertTrue(names.contains("snapshots/sync-context.json"));
    assertTrue(names.contains("snapshots/versions.json"));
    assertFalse(names.contains("app.log"));
    assertFalse(names.contains("crash.log"));
    assertFalse(names.contains("logs/lizzie/engine-trace.log"));
    assertFalse(names.contains("logs/readboard/trace.log"));
    assertFalse(names.stream().anyMatch(name -> name.endsWith(".zip")));

    JSONObject manifest = manifest(entries);
    assertEquals(runtime.applicationLogSessionId(), manifest.getString("applicationSession"));
    assertTrue(manifest.has("traceSession"));
    assertTrue(manifest.isNull("traceSession"));
    assertEquals("next-dev", manifest.getString("appVersion"));
    assertEquals(ExportSanitizer.VERSION, manifest.getString("sanitizerVersion"));
    assertFalse(manifest.getBoolean("fullTraceActive"));
    assertTrue(manifest.getBoolean("diagnosticsEnabled"));

    JSONObject app = source(manifest, "lizzie-app");
    assertSource(app, true, true, "included", "logs/lizzie/");
    assertEquals(24, app.getInt("windowHours"));
    assertEquals(50L * 1024 * 1024, app.getLong("capBytes"));
    JSONObject crash = source(manifest, "lizzie-crash");
    assertSource(crash, true, true, "included", "logs/lizzie/");
    assertEquals(10L * 1024 * 1024, crash.getLong("capBytes"));
    assertSource(source(manifest, "lizzie-engine-trace"), false, false, "omitted", "logs/lizzie/");
    assertEquals("not-requested", source(manifest, "lizzie-engine-trace").getString("reason"));
    assertSource(source(manifest, "readboard-trace"), false, false, "omitted", "logs/readboard/");
    assertSource(
        source(manifest, "readboard-capture"), true, false, "omitted", "diagnostics/readboard-capture/");
    assertEquals("helper-not-started", source(manifest, "readboard-capture").getString("reason"));

    assertTrue(text(entries, "logs/lizzie/app.log").contains("app-evidence"));
    assertTrue(text(entries, "logs/lizzie/crash.log").contains("crash-evidence"));
    JSONObject exportedConfig = new JSONObject(text(entries, "snapshots/config.json"));
    assertEquals(19, exportedConfig.getJSONObject("ui").getInt("board-size"));
    assertFalse(exportedConfig.getJSONObject("ui").has("unknown-support-key"));
    assertFalse(exportedConfig.getJSONObject("ui").has("password"));
    JSONObject engine = exportedConfig.getJSONObject("leelaz");
    assertEquals("katago", engine.getString("kind"));
    assertEquals("katago.exe", engine.getString("executable"));
    assertFalse(engine.has("command"));
    JSONObject versions = new JSONObject(text(entries, "snapshots/versions.json"));
    assertEquals("next-dev", versions.getString("host"));
    assertNoCanaries(entries, "CANARY_PASSWORD_EXPORT", "C:\\Users\\alice");
  }

  @Test
  void missingCrashSourceStillPublishesPackage() throws Exception {
    LoggingRuntime runtime = start();
    runtime.awaitIdle();
    runtime.shutdown();
    Files.deleteIfExists(tempDir.resolve("logs/crash.log"));

    Path zip = exportDefault(runtime);
    Map<String, byte[]> entries = unzipEntries(zip);
    JSONObject crash = source(manifest(entries), "lizzie-crash");
    assertEquals("failed", crash.getString("status"));
    assertEquals("missing", crash.getString("reason"));
    assertTrue(crash.getBoolean("failed"));
    assertFalse(crash.getBoolean("included"));
    assertTrue(entries.containsKey("manifest.json"));
    assertTrue(entries.containsKey("logs/lizzie/app.log"));
    assertFalse(entries.containsKey("logs/lizzie/crash.log"));
    assertFalse(entries.containsKey("crash.log"));
  }

  @Test
  void cancellationBeforePublicationLeavesNoZip() throws Exception {
    LoggingRuntime runtime = start();
    runtime.awaitIdle();
    Path diagnostics = DiagnosticBundleExporter.defaultOutputDirectory(tempDir);
    assertThrows(
        IOException.class,
        () ->
            new DiagnosticBundleExporter(diagnostics)
                .export(request(runtime, EnumSet.noneOf(TraceScope.class)), () -> true));
    if (Files.isDirectory(diagnostics)) {
      try (var stream = Files.list(diagnostics)) {
        assertEquals(0, stream.count());
      }
    }
  }

  @Test
  void secondExportUsesCollisionSafeNameAndDoesNotNestPriorZip() throws Exception {
    LoggingRuntime runtime = start();
    runtime.awaitIdle();
    Path first = exportDefault(runtime);
    Path second = exportDefault(runtime);
    assertNotEquals(first, second);
    Map<String, byte[]> entries = unzipEntries(second);
    assertFalse(entries.keySet().stream().anyMatch(name -> name.endsWith(".zip")));
  }

  @Test
  void exportSanitizerRemovesCredentialPathAndUrlCanariesFromLogs() throws Exception {
    LoggingRuntime runtime = start();
    LoggerFactory.getLogger(LogCategories.APP)
        .error(
            "login password={} path={} url={}",
            CANARY_PASSWORD,
            "/home/dev/lizzieyzy-next/config.txt",
            "https://example.test/status");
    runtime.awaitIdle();

    Map<String, byte[]> entries = unzipEntries(exportDefault(runtime));
    String all = joinedText(entries);
    assertFalse(all.contains(CANARY_PASSWORD), all);
    assertFalse(all.contains("/home/dev/lizzieyzy-next/config.txt"), all);
    assertFalse(all.contains("https://example.test/status"), all);
    assertTrue(all.contains("/home/<user>") || all.contains("<redacted-path>"), all);
    assertTrue(all.contains("<redacted-url>") || all.contains("<redacted"), all);
  }

  @Test
  void exportOmitsOriginalSessionKeysFromEveryZipEntryIncludingManifest() throws Exception {
    LoggingRuntime runtime = start();
    LoggerFactory.getLogger(LogCategories.APP).info("yike session=live-room:186538");
    runtime.awaitIdle();

    Map<String, byte[]> entries = unzipEntries(exportDefault(runtime));
    assertNoCanaries(entries, "live-room:186538", "live-room\\u003a186538");
    String all = joinedText(entries);
    assertTrue(all.contains("live-room#1"), all);
    JSONObject manifest = manifest(entries);
    assertEquals("live-room#1", manifest.getJSONArray("aliases").getString(0));
  }

  @Test
  void rawOptInCopiesCurrentTraceSessionOnly() throws Exception {
    LoggingRuntime runtime = start();
    runtime.startFullTrace(EnumSet.of(TraceScope.ENGINE_GTP));
    String firstSession = runtime.currentTraceSessionId();
    LoggerFactory.getLogger(LogCategories.ENGINE_TRACE).info("raw-first session={}", firstSession);
    runtime.awaitIdle();
    runtime.stopFullTrace();
    runtime.startFullTrace(EnumSet.of(TraceScope.ENGINE_GTP));
    String secondSession = runtime.currentTraceSessionId();
    LoggerFactory.getLogger(LogCategories.ENGINE_TRACE).info("raw-second session={}", secondSession);
    runtime.awaitIdle();

    Path zip =
        new DiagnosticBundleExporter(DiagnosticBundleExporter.defaultOutputDirectory(tempDir))
            .export(request(runtime, EnumSet.of(TraceScope.ENGINE_GTP)));
    Map<String, byte[]> entries = unzipEntries(zip);
    assertTrue(entries.containsKey("logs/lizzie/engine-trace.log"));
    assertFalse(entries.containsKey("engine-trace.log"));
    String trace = text(entries, "logs/lizzie/engine-trace.log");
    assertTrue(trace.contains("raw-second"), trace);
    assertFalse(trace.contains("raw-first"), trace);
    JSONObject sources = manifest(entries).getJSONObject("sources");
    assertEquals("included", sources.getJSONObject("lizzie-engine-trace").getString("status"));
  }

  @Test
  void rawCapTruncatesCompleteRecordsWithoutPausingActiveTrace() throws Exception {
    LoggingRuntime runtime = start();
    runtime.startFullTrace(EnumSet.of(TraceScope.ENGINE_GTP));
    org.slf4j.Logger trace = LoggerFactory.getLogger(LogCategories.ENGINE_TRACE);
    for (int i = 0; i < 40; i++) {
      trace.info("raw-line-{}-xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx", i);
    }
    runtime.awaitIdle();
    DiagnosticBundleLimits limits = new DiagnosticBundleLimits(24, 1024, 24, 1024, 200, 200);
    Path zip =
        new DiagnosticBundleExporter(
                DiagnosticBundleExporter.defaultOutputDirectory(tempDir), limits)
            .export(request(runtime, EnumSet.of(TraceScope.ENGINE_GTP)));
    Map<String, byte[]> entries = unzipEntries(zip);
    JSONObject engine = source(manifest(entries), "lizzie-engine-trace");
    assertEquals("truncated", engine.getString("status"));
    assertTrue(engine.getBoolean("truncated"));
    assertTrue(entries.get("logs/lizzie/engine-trace.log").length <= 200 + 80);
    trace.info("post-export-still-live");
    runtime.awaitIdle();
    assertTrue(
        Files.readString(tempDir.resolve("logs/engine-trace.log")).contains("post-export-still-live"));
  }

  @Test
  void appWindowOmitsLinesOlderThan24Hours() throws Exception {
    LoggingRuntime runtime = start();
    runtime.awaitIdle();
    Path appLog = tempDir.resolve("logs/app.log");
    Files.writeString(
        appLog,
        "2020-01-01 00:00:00.000 INFO  [lizzie.app] old-evidence\n",
        java.nio.file.StandardOpenOption.APPEND);
    Map<String, byte[]> entries = unzipEntries(exportDefault(runtime));
    assertFalse(text(entries, "logs/lizzie/app.log").contains("old-evidence"));
  }

  @Test
  void estimateIsPositiveBeforeExport() throws Exception {
    LoggingRuntime runtime = start();
    runtime.awaitIdle();
    long estimate =
        new DiagnosticBundleExporter(DiagnosticBundleExporter.defaultOutputDirectory(tempDir))
            .estimateUncompressedBytes(request(runtime, EnumSet.noneOf(TraceScope.class)));
    assertTrue(estimate > 0);
  }

  @Test
  void dualProcessAppAndCrashUseSeparateNamespacesWindowsAndCaps() throws Exception {
    LoggingRuntime runtime = start();
    LoggerFactory.getLogger(LogCategories.APP).info("host-app-now");
    runtime.awaitIdle();
    Path rb = readBoardRoot(runtime);
    Files.createDirectories(rb);
    Files.writeString(
        rb.resolve("app.log"),
        jsonl("2020-01-01T00:00:00.000Z", "app", PROCESS_SESSION, "rb-old", null)
            + jsonl(Instant.now().toString(), "app", PROCESS_SESSION, "rb-app-now", null));
    Files.writeString(
        rb.resolve("crash.log"),
        jsonl(Instant.now().toString(), "crash", PROCESS_SESSION, "rb-crash-now", null));

    Map<String, byte[]> windowed =
        unzipEntries(
            new DiagnosticBundleExporter(DiagnosticBundleExporter.defaultOutputDirectory(tempDir))
                .export(request(runtime, helperSnapshot(PROCESS_SESSION, false, false))));
    assertTrue(windowed.containsKey("logs/lizzie/app.log"));
    assertTrue(windowed.containsKey("logs/readboard/app.log"));
    assertTrue(windowed.containsKey("logs/readboard/crash.log"));
    assertFalse(text(windowed, "logs/readboard/app.log").contains("rb-old"));
    assertTrue(text(windowed, "logs/readboard/app.log").contains("rb-app-now"));
    assertTrue(text(windowed, "logs/readboard/crash.log").contains("rb-crash-now"));
    assertEquals("logs/lizzie/", source(manifest(windowed), "lizzie-app").getString("namespace"));
    assertEquals("logs/readboard/", source(manifest(windowed), "readboard-app").getString("namespace"));

    StringBuilder padding = new StringBuilder();
    for (int i = 0; i < 80; i++) {
      padding.append("pad-").append(i).append('\n');
    }
    Files.writeString(tempDir.resolve("logs/app.log"), padding.toString(), java.nio.file.StandardOpenOption.APPEND);
    Files.writeString(rb.resolve("app.log"), padding.toString(), java.nio.file.StandardOpenOption.APPEND);
    DiagnosticBundleLimits limits = new DiagnosticBundleLimits(24, 40, 24, 1024, 1024, 1024);
    Map<String, byte[]> capped =
        unzipEntries(
            new DiagnosticBundleExporter(
                    DiagnosticBundleExporter.defaultOutputDirectory(tempDir), limits)
                .export(request(runtime, helperSnapshot(PROCESS_SESSION, false, false))));
    assertEquals("truncated", source(manifest(capped), "lizzie-app").getString("status"));
    assertEquals("truncated", source(manifest(capped), "readboard-app").getString("status"));
    assertTrue(capped.get("logs/lizzie/app.log").length <= 40 + 80);
    assertTrue(capped.get("logs/readboard/app.log").length <= 40 + 80);
  }

  @Test
  void defaultExportExcludesEveryFullTraceSource() throws Exception {
    LoggingRuntime runtime = start();
    runtime.startFullTrace(EnumSet.allOf(TraceScope.class));
    LoggerFactory.getLogger(LogCategories.ENGINE_TRACE).info("host-engine-live");
    runtime.awaitIdle();
    Files.createDirectories(readBoardRoot(runtime));
    Files.writeString(
        readBoardRoot(runtime).resolve("trace.log"),
        jsonl(Instant.now().toString(), "trace", PROCESS_SESSION, "rb-trace-live", null));

    Map<String, byte[]> entries =
        unzipEntries(
            new DiagnosticBundleExporter(DiagnosticBundleExporter.defaultOutputDirectory(tempDir))
                .export(request(runtime, helperSnapshot(PROCESS_SESSION, false, true))));
    Set<String> names = entries.keySet();
    assertFalse(names.contains("logs/lizzie/engine-trace.log"));
    assertFalse(names.contains("logs/lizzie/readboard-trace.log"));
    assertFalse(names.contains("logs/lizzie/network-trace.log"));
    assertFalse(names.contains("logs/readboard/trace.log"));
    JSONObject manifest = manifest(entries);
    assertEquals("omitted", source(manifest, "lizzie-engine-trace").getString("status"));
    assertEquals("omitted", source(manifest, "lizzie-readboard-trace").getString("status"));
    assertEquals("omitted", source(manifest, "lizzie-network-trace").getString("status"));
    assertEquals("omitted", source(manifest, "readboard-trace").getString("status"));
    assertEquals("not-requested", source(manifest, "readboard-trace").getString("reason"));
  }

  @Test
  void fullTraceOptInKeepsCurrentHostAndProcessSessionsAndDoesNotSubstituteOld() throws Exception {
    LoggingRuntime runtime = start();
    runtime.startFullTrace(EnumSet.of(TraceScope.ENGINE_GTP));
    String first = runtime.currentTraceSessionId();
    LoggerFactory.getLogger(LogCategories.ENGINE_TRACE).info("host-old session={}", first);
    runtime.awaitIdle();
    runtime.stopFullTrace();
    Files.createDirectories(readBoardRoot(runtime));
    Files.writeString(
        readBoardRoot(runtime).resolve("trace.log"),
        jsonl(Instant.now().toString(), "trace", OLD_PROCESS_SESSION, "rb-old-trace", null)
            + jsonl(Instant.now().toString(), "trace", PROCESS_SESSION, "rb-current-trace", null));

    Map<String, byte[]> missingCurrent =
        unzipEntries(
            new DiagnosticBundleExporter(DiagnosticBundleExporter.defaultOutputDirectory(tempDir))
                .export(
                    request(
                        runtime,
                        EnumSet.of(TraceScope.ENGINE_GTP),
                        true,
                        true,
                        helperSnapshot(PROCESS_SESSION, false, true))));
    assertFalse(missingCurrent.containsKey("logs/lizzie/engine-trace.log"));
    assertEquals(
        "no-active-session",
        source(manifest(missingCurrent), "lizzie-engine-trace").getString("reason"));
    assertEquals("omitted", source(manifest(missingCurrent), "lizzie-engine-trace").getString("status"));
    assertTrue(text(missingCurrent, "logs/readboard/trace.log").contains("rb-current-trace"));
    assertFalse(text(missingCurrent, "logs/readboard/trace.log").contains("rb-old-trace"));

    runtime.startFullTrace(EnumSet.of(TraceScope.ENGINE_GTP));
    String current = runtime.currentTraceSessionId();
    LoggerFactory.getLogger(LogCategories.ENGINE_TRACE).info("host-current session={}", current);
    runtime.awaitIdle();
    Map<String, byte[]> included =
        unzipEntries(
            new DiagnosticBundleExporter(DiagnosticBundleExporter.defaultOutputDirectory(tempDir))
                .export(
                    request(
                        runtime,
                        EnumSet.of(TraceScope.ENGINE_GTP),
                        true,
                        true,
                        helperSnapshot(PROCESS_SESSION, false, true))));
    String hostTrace = text(included, "logs/lizzie/engine-trace.log");
    assertTrue(hostTrace.contains("host-current"), hostTrace);
    assertFalse(hostTrace.contains("host-old"), hostTrace);
    assertTrue(text(included, "logs/readboard/trace.log").contains("rb-current-trace"));
    assertFalse(text(included, "logs/readboard/trace.log").contains("rb-old-trace"));
  }

  @Test
  void captureCollectsCompleteCurrentEventDirectoryAndLeavesActiveWriterAlone() throws Exception {
    LoggingRuntime runtime = start();
    Path capture = readBoardRoot(runtime).resolve("capture");
    writeCaptureEvent(
        capture,
        "20260821-170300-123-0001-recognition-success",
        PROCESS_SESSION,
        "current-event",
        "/home/dev/capture-current.png",
        "nickname=AliceFox token=" + CANARY_TOKEN);
    writeCaptureEvent(
        capture,
        "20260820-170300-123-0001-recognition-success",
        OLD_PROCESS_SESSION,
        "old-event",
        "/home/dev/capture-old.png",
        "old-session-text");
    Path incomplete = capture.resolve("20260821-180000-000-0002-recognition-success");
    Files.createDirectories(incomplete);
    Files.write(incomplete.resolve("frame.png"), PIXEL_PNG);
    Files.writeString(capture.resolve("debug.log"), "root debug path=/home/dev/capture-debug.png\n");

    Path zip =
        new DiagnosticBundleExporter(DiagnosticBundleExporter.defaultOutputDirectory(tempDir))
            .export(
                request(
                    runtime,
                    EnumSet.noneOf(TraceScope.class),
                    false,
                    true,
                    helperSnapshot(PROCESS_SESSION, true, false)));
    Map<String, byte[]> entries = unzipEntries(zip);
    String prefix =
        "diagnostics/readboard-capture/20260821-170300-123-0001-recognition-success/";
    assertTrue(entries.containsKey(prefix + "frame.png"));
    assertTrue(entries.containsKey(prefix + "metadata.json"));
    assertTrue(entries.containsKey(prefix + "recognition.txt"));
    assertTrue(entries.containsKey("diagnostics/readboard-capture/debug.log"));
    assertArrayEquals(PIXEL_PNG, entries.get(prefix + "frame.png"));
    assertFalse(
        entries.keySet().stream()
            .anyMatch(name -> name.contains("20260820-170300-123-0001-recognition-success")));
    assertFalse(
        entries.keySet().stream()
            .anyMatch(name -> name.contains("20260821-180000-000-0002-recognition-success")));
    String metadata = text(entries, prefix + "metadata.json");
    assertTrue(metadata.contains("current-event"), metadata);
    assertFalse(metadata.contains("/home/dev/capture-current.png"), metadata);
    assertFalse(text(entries, prefix + "recognition.txt").contains(CANARY_TOKEN));
    assertEquals("included", source(manifest(entries), "readboard-capture").getString("status"));
    assertTrue(Files.exists(incomplete.resolve("frame.png")));
  }

  @Test
  void captureTruncatesOnCompleteEventDirectoryBoundary() throws Exception {
    LoggingRuntime runtime = start();
    Path capture = readBoardRoot(runtime).resolve("capture");
    writeCaptureEvent(
        capture, "20260821-170300-123-0001-first", PROCESS_SESSION, "first", "/tmp/a.png", "first-text");
    writeCaptureEvent(
        capture,
        "20260821-170301-123-0002-second",
        PROCESS_SESSION,
        "second",
        "/tmp/b.png",
        "second-text");
    long newestBytes = directorySize(capture.resolve("20260821-170301-123-0002-second"));
    DiagnosticBundleLimits limits =
        new DiagnosticBundleLimits(24, 1024, 24, 1024, 1024, newestBytes);
    Map<String, byte[]> entries =
        unzipEntries(
            new DiagnosticBundleExporter(
                    DiagnosticBundleExporter.defaultOutputDirectory(tempDir), limits)
                .export(
                    request(
                        runtime,
                        EnumSet.noneOf(TraceScope.class),
                        false,
                        true,
                        helperSnapshot(PROCESS_SESSION, true, false))));
    JSONObject captureSource = source(manifest(entries), "readboard-capture");
    assertEquals("truncated", captureSource.getString("status"));
    assertTrue(captureSource.getBoolean("truncated"));
    boolean first =
        entries.keySet().stream().anyMatch(name -> name.contains("20260821-170300-123-0001-first"));
    boolean second =
        entries.keySet().stream().anyMatch(name -> name.contains("20260821-170301-123-0002-second"));
    assertFalse(first);
    assertTrue(second);
    assertTrue(captureSource.getLong("bytes") <= newestBytes);
  }

  @Test
  void untaggedCaptureEventsAreBoundToCurrentProcessObservationTime() throws Exception {
    LoggingRuntime runtime = start();
    ReadBoardLoggingSnapshot helper = helperSnapshot(PROCESS_SESSION, true, false);
    Path capture = readBoardRoot(runtime).resolve("capture");
    writeCaptureEvent(
        capture,
        "20200101-000000-000-0001-old-untagged",
        null,
        Instant.parse("2020-01-01T00:00:00Z"),
        "old-untagged",
        "/tmp/old.png",
        "old-untagged-text");
    writeCaptureEvent(
        capture,
        "20260821-170300-123-0001-current-untagged",
        null,
        Instant.now(),
        "current-untagged",
        "/tmp/now.png",
        "current-untagged-text");
    Map<String, byte[]> entries =
        unzipEntries(
            new DiagnosticBundleExporter(DiagnosticBundleExporter.defaultOutputDirectory(tempDir))
                .export(
                    request(
                        runtime, EnumSet.noneOf(TraceScope.class), false, true, helper)));
    assertTrue(
        entries.keySet().stream()
            .anyMatch(name -> name.contains("20260821-170300-123-0001-current-untagged")));
    assertFalse(
        entries.keySet().stream()
            .anyMatch(name -> name.contains("20200101-000000-000-0001-old-untagged")));
  }

  @Test
  void manifestRecordsHelperMissingAndUnreadableSourcesWithoutFailingZip() throws Exception {
    LoggingRuntime runtime = start();
    Path rb = readBoardRoot(runtime);
    Files.createDirectories(rb);
    Path unreadable = rb.resolve("app.log");
    Files.writeString(unreadable, jsonl(Instant.now().toString(), "app", PROCESS_SESSION, "hidden", null));
    Files.setPosixFilePermissions(unreadable, Set.of());

    Map<String, byte[]> detached = unzipEntries(exportDefault(runtime));
    JSONObject detachedManifest = manifest(detached);
    assertEquals("helper-not-started", source(detachedManifest, "readboard-capture").getString("reason"));
    assertEquals("omitted", source(detachedManifest, "readboard-capture").getString("status"));
    assertTrue(detached.containsKey("manifest.json"));

    ReadBoardLoggingSnapshot started = helperSnapshot(PROCESS_SESSION, true, false);
    Map<String, byte[]> helperOn =
        unzipEntries(
            new DiagnosticBundleExporter(DiagnosticBundleExporter.defaultOutputDirectory(tempDir))
                .export(
                    request(
                        runtime, EnumSet.noneOf(TraceScope.class), false, true, started)));
    JSONObject helperManifest = manifest(helperOn);
    JSONObject rbApp = source(helperManifest, "readboard-app");
    assertEquals("failed", rbApp.getString("status"));
    assertTrue(rbApp.getBoolean("failed"));
    assertFalse(rbApp.getBoolean("included"));
    assertTrue(rbApp.getString("reason").equals("unreadable") || rbApp.getString("reason").equals("missing"));
    assertEquals("missing", source(helperManifest, "readboard-crash").getString("reason"));
    assertEquals("failed", source(helperManifest, "readboard-crash").getString("status"));
    assertEquals("no-current-session", source(helperManifest, "readboard-capture").getString("reason"));
    assertEquals("omitted", source(helperManifest, "readboard-capture").getString("status"));
    assertTrue(helperOn.containsKey("manifest.json"));
    try {
      Files.setPosixFilePermissions(unreadable, Set.of(PosixFilePermission.OWNER_READ));
    } catch (IOException ignored) {
    }
  }

  @Test
  void typedJsonlAndCaptureShareAliasPolicyAndDropSecretCanaries() throws Exception {
    LoggingRuntime runtime = start();
    JSONObject fields = new JSONObject();
    fields.put("path", tagged("/home/dev/secret-config.txt", "localPath"));
    fields.put("url", tagged("https://example.test/board", "localUrl"));
    fields.put("nickname", tagged("AliceFox", "userText"));
    fields.put("token", tagged(CANARY_TOKEN, "secret"));
    fields.put("cookie", tagged(CANARY_COOKIE, "secret"));
    fields.put("authorization", tagged(CANARY_CREDENTIAL, "secret"));
    fields.put("machineKey", tagged(CANARY_MACHINE_KEY, "secret"));
    Files.createDirectories(readBoardRoot(runtime));
    Files.writeString(
        readBoardRoot(runtime).resolve("app.log"),
        jsonl(Instant.now().toString(), "app", PROCESS_SESSION, "rb-tagged", fields));
    writeCaptureEvent(
        readBoardRoot(runtime).resolve("capture"),
        "20260821-170300-123-0001-recognition-success",
        PROCESS_SESSION,
        "capture-tagged",
        "/home/dev/secret-config.txt",
        "nickname=AliceFox url=https://example.test/board token=" + CANARY_TOKEN);

    Map<String, byte[]> entries =
        unzipEntries(
            new DiagnosticBundleExporter(DiagnosticBundleExporter.defaultOutputDirectory(tempDir))
                .export(
                    request(
                        runtime,
                        EnumSet.noneOf(TraceScope.class),
                        false,
                        true,
                        helperSnapshot(PROCESS_SESSION, true, false))));
    String app = text(entries, "logs/readboard/app.log");
    assertFalse(app.contains("/home/dev/secret-config.txt"), app);
    assertFalse(app.contains("https://example.test/board"), app);
    assertFalse(app.contains("AliceFox"), app);
    assertFalse(app.contains(PROCESS_SESSION), app);
    assertFalse(app.contains(CANARY_TOKEN), app);
    assertTrue(app.contains("path#1") || app.contains("<redacted-path>") || app.contains("/home/<user>"), app);
    assertTrue(app.contains("url#1") || app.contains("<redacted-url>"), app);
    assertTrue(app.contains("nickname#1") || app.contains("<redacted"), app);
    String metadata =
        text(
            entries,
            "diagnostics/readboard-capture/20260821-170300-123-0001-recognition-success/metadata.json");
    String recognition =
        text(
            entries,
            "diagnostics/readboard-capture/20260821-170300-123-0001-recognition-success/recognition.txt");
    assertFalse(metadata.contains("/home/dev/secret-config.txt"), metadata);
    assertFalse(recognition.contains(CANARY_TOKEN), recognition);
    assertFalse(recognition.contains("AliceFox"), recognition);
    assertNoCanaries(
        entries,
        CANARY_PASSWORD,
        CANARY_TOKEN,
        CANARY_COOKIE,
        CANARY_MACHINE_KEY,
        CANARY_CREDENTIAL,
        PROCESS_SESSION,
        "/home/dev/secret-config.txt",
        "AliceFox");
  }

  @Test
  void previousBundleInsideLogsIsNotRecursivelyIncluded() throws Exception {
    LoggingRuntime runtime = start();
    runtime.awaitIdle();
    Files.write(
        runtime.logsDirectory().resolve("lizzie-diagnostics-old.zip"),
        "old-bundle".getBytes(StandardCharsets.UTF_8));
    Files.createDirectories(readBoardRoot(runtime).resolve("capture"));
    Files.write(
        readBoardRoot(runtime).resolve("capture").resolve("old-export.zip"),
        "nested".getBytes(StandardCharsets.UTF_8));
    Map<String, byte[]> entries = unzipEntries(exportDefault(runtime));
    assertFalse(entries.keySet().stream().anyMatch(name -> name.endsWith(".zip")));
    assertFalse(joinedText(entries).contains("old-bundle"));
  }

  private LoggingRuntime start() {
    LoggingRuntime.resetForTests();
    return LoggingRuntime.initialize(
        new WorkDirectoryResolution(tempDir, java.util.List.of()),
        new LoggingLimits(64, 32, 32, 32, 7, 1_000_000, 256_000));
  }

  private Path exportDefault(LoggingRuntime runtime) throws IOException {
    return new DiagnosticBundleExporter(DiagnosticBundleExporter.defaultOutputDirectory(tempDir))
        .export(request(runtime, EnumSet.noneOf(TraceScope.class)));
  }

  private static DiagnosticBundleRequest request(
      LoggingRuntime runtime, Set<TraceScope> rawScopes) {
    return new DiagnosticBundleRequest(runtime, rawScopes, new JSONObject(), emptySnapshot(), "next-dev");
  }

  private static DiagnosticBundleRequest request(
      LoggingRuntime runtime, ReadBoardLoggingSnapshot helper) {
    return request(runtime, EnumSet.noneOf(TraceScope.class), false, true, helper);
  }

  private static DiagnosticBundleRequest request(
      LoggingRuntime runtime,
      Set<TraceScope> rawScopes,
      boolean includeReadBoardTrace,
      boolean includeCapture,
      ReadBoardLoggingSnapshot helper) {
    return new DiagnosticBundleRequest(
        runtime,
        rawScopes,
        includeReadBoardTrace,
        includeCapture,
        new JSONObject(),
        emptySnapshot(),
        helper,
        "next-dev",
        "rb-test");
  }

  private static ReadBoardLoggingSnapshot helperSnapshot(
      String processSession, boolean capture, boolean trace) {
    ReadBoardLoggingControl control =
        new ReadBoardLoggingControl(
            new ReadBoardLoggingControl.Desired(false, capture, trace), true);
    control.onCapability(
        ReadBoardLoggingProtocol.tryParseCapability(
            "readboardLoggingV1 "
                + processSession
                + " off "
                + (capture ? "on" : "off")
                + " "
                + (trace ? "on" : "off")
                + " healthy 0"));
    return control.snapshot();
  }

  private Path readBoardRoot(LoggingRuntime runtime) {
    return runtime.logsDirectory().resolve("readboard");
  }

  private static void writeCaptureEvent(
      Path captureRoot,
      String eventDirectory,
      String processSession,
      String eventName,
      String capturePath,
      String recognition)
      throws IOException {
    writeCaptureEvent(
        captureRoot,
        eventDirectory,
        processSession,
        Instant.now(),
        eventName,
        capturePath,
        recognition);
  }

  private static void writeCaptureEvent(
      Path captureRoot,
      String eventDirectory,
      String processSession,
      Instant timestamp,
      String eventName,
      String capturePath,
      String recognition)
      throws IOException {
    Path event = captureRoot.resolve(eventDirectory);
    Files.createDirectories(event);
    Files.write(event.resolve("frame.png"), PIXEL_PNG);
    JSONObject metadata = new JSONObject();
    metadata.put("EventName", eventName);
    metadata.put("TimestampUtc", timestamp.toString());
    if (processSession != null && !processSession.isEmpty()) {
      metadata.put("processSessionId", processSession);
    }
    metadata.put("CapturePath", capturePath);
    Files.writeString(event.resolve("metadata.json"), metadata.toString());
    Files.writeString(event.resolve("recognition.txt"), recognition);
    Files.writeString(event.resolve("debug.log"), "event debug " + recognition + "\n");
  }

  private static String jsonl(
      String ts, String stream, String processSession, String message, JSONObject fields) {
    JSONObject line = new JSONObject();
    line.put("ts", ts);
    line.put("level", "INFO");
    line.put("stream", stream);
    line.put("eventId", "test.event");
    line.put("module", "test");
    line.put("hostSessionId", tagged(HOST_SESSION, "sessionId"));
    line.put("processSessionId", tagged(processSession, "sessionId"));
    JSONObject resolved = fields == null ? new JSONObject() : fields;
    resolved.put("message", tagged(message, "safe"));
    line.put("fields", resolved);
    return line.toString() + "\n";
  }

  private static JSONObject tagged(Object value, String privacy) {
    JSONObject field = new JSONObject();
    field.put("value", value);
    field.put("privacy", privacy);
    return field;
  }


  private static long directorySize(Path directory) throws IOException {
    long total = 0;
    try (var stream = Files.walk(directory)) {
      for (Path path : (Iterable<Path>) stream::iterator) {
        if (Files.isRegularFile(path)) {
          total += Files.size(path);
        }
      }
    }
    return total;
  }

  private static SyncDiagnosticsExportSnapshot emptySnapshot() {
    return new SyncDiagnosticsExportSnapshot(
        1L, null, java.util.List.of(), java.util.List.of(), java.util.List.of(), null);
  }

  private static Map<String, byte[]> unzipEntries(Path zip) throws IOException {
    Map<String, byte[]> entries = new LinkedHashMap<>();
    try (ZipInputStream input = new ZipInputStream(Files.newInputStream(zip))) {
      ZipEntry entry;
      while ((entry = input.getNextEntry()) != null) {
        entries.put(entry.getName(), input.readAllBytes());
      }
    }
    return entries;
  }

  private static String text(Map<String, byte[]> entries, String name) {
    byte[] bytes = entries.get(name);
    return bytes == null ? "" : new String(bytes, StandardCharsets.UTF_8);
  }

  private static String joinedText(Map<String, byte[]> entries) {
    StringBuilder all = new StringBuilder();
    for (byte[] bytes : entries.values()) {
      all.append(new String(bytes, StandardCharsets.ISO_8859_1)).append('\n');
    }
    return all.toString();
  }

  private static JSONObject manifest(Map<String, byte[]> entries) {
    return new JSONObject(text(entries, "manifest.json"));
  }

  private static JSONObject source(JSONObject manifest, String name) {
    return manifest.getJSONObject("sources").getJSONObject(name);
  }

  private static void assertSource(
      JSONObject source, boolean requested, boolean included, String status, String namespace) {
    assertEquals(requested, source.getBoolean("requested"), source.toString());
    assertEquals(included, source.getBoolean("included"), source.toString());
    assertEquals(status, source.getString("status"), source.toString());
    assertEquals(namespace, source.getString("namespace"), source.toString());
  }

  private static void assertNoCanaries(Map<String, byte[]> entries, String... canaries) {
    for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
      String utf8 = new String(entry.getValue(), StandardCharsets.UTF_8);
      String latin1 = new String(entry.getValue(), StandardCharsets.ISO_8859_1);
      for (String canary : canaries) {
        assertFalse(utf8.contains(canary), entry.getKey() + " leaked " + canary);
        assertFalse(latin1.contains(canary), entry.getKey() + " leaked " + canary);
      }
    }
  }

}
