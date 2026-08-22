package featurecat.lizzie.logging;

import featurecat.lizzie.analysis.ReadBoardLoggingProtocol;
import featurecat.lizzie.analysis.ReadBoardLoggingSnapshot;
import featurecat.lizzie.analysis.SyncDiagnosticsExportSnapshot;
import featurecat.lizzie.analysis.SyncDiagnosticsExporter;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class DiagnosticBundleExporter {
  public static final long APP_WINDOW_HOURS = 24;
  public static final long APP_CAP_BYTES = 50L * 1024 * 1024;
  public static final long CRASH_WINDOW_HOURS = 24;
  public static final long CRASH_CAP_BYTES = 10L * 1024 * 1024;
  public static final long RAW_CAP_BYTES = 50L * 1024 * 1024;
  public static final String NS_LIZZIE = "logs/lizzie/";
  public static final String NS_READBOARD = "logs/readboard/";
  public static final String NS_CAPTURE = "diagnostics/readboard-capture/";
  public static final String NS_SNAPSHOTS = "snapshots/";

  private static final Logger LOG = LoggerFactory.getLogger(LogCategories.DIAGNOSTICS);
  private static final DateTimeFormatter FILE_TIMESTAMP =
      DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneId.systemDefault());
  private static final DateTimeFormatter LOG_TIMESTAMP =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

  private final Path outputDirectory;
  private final DiagnosticBundleLimits limits;

  public DiagnosticBundleExporter(Path outputDirectory) {
    this(outputDirectory, DiagnosticBundleLimits.production());
  }

  public DiagnosticBundleExporter(Path outputDirectory, DiagnosticBundleLimits limits) {
    this.outputDirectory = Objects.requireNonNull(outputDirectory, "outputDirectory");
    this.limits = Objects.requireNonNull(limits, "limits");
  }

  public static Path defaultOutputDirectory(Path workDirectory) {
    return Objects.requireNonNull(workDirectory, "workDirectory").resolve("diagnostics");
  }

  public long estimateUncompressedBytes(DiagnosticBundleRequest request) throws IOException {
    Objects.requireNonNull(request, "request");
    long total = 0;
    Path logs = request.runtime().logsDirectory();
    total += estimateLogBytes(logs, "app", limits.appCapBytes());
    total += estimateLogBytes(logs, "crash", limits.crashCapBytes());
    Path readboard = logs.resolve("readboard");
    total += estimateLogBytes(readboard, "app", limits.appCapBytes());
    total += estimateLogBytes(readboard, "crash", limits.crashCapBytes());
    if (!request.rawScopes().isEmpty() && request.runtime().currentTraceSessionId() != null) {
      for (TraceScope scope : request.rawScopes()) {
        total += estimateLogBytes(logs, stem(scope.fileName()), limits.rawCapBytes());
      }
    }
    if (request.includeReadBoardTrace() && hasProcessSession(request)) {
      total += estimateLogBytes(readboard, "trace", limits.rawCapBytes());
    }
    if (request.includeCapture() && hasProcessSession(request)) {
      total += Math.min(directorySize(readboard.resolve("capture")), limits.captureCapBytes());
    }
    total += 64 * 1024;
    return total;
  }

  private static long estimateLogBytes(Path logsDirectory, String stem, long cap) throws IOException {
    long total = 0;
    for (Path file : listLogFiles(logsDirectory, stem)) {
      total += Files.size(file);
      if (total >= cap) {
        return cap;
      }
    }
    return total;
  }

  public Path export(DiagnosticBundleRequest request) throws IOException {
    return export(request, () -> false);
  }

  public Path export(DiagnosticBundleRequest request, BooleanSupplier cancelled) throws IOException {
    Objects.requireNonNull(request, "request");
    BooleanSupplier cancel = cancelled == null ? () -> false : cancelled;
    Files.createDirectories(outputDirectory);
    Instant captureTime = Instant.now();
    Path published = uniqueZipPath(captureTime);
    Path temporary = outputDirectory.resolve(published.getFileName().toString() + ".partial");
    ExportSanitizer sanitizer = new ExportSanitizer();
    JSONObject sources = new JSONObject();
    try {
      try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(temporary))) {
        Path hostLogs = request.runtime().logsDirectory();
        Path readboardLogs = hostLogs.resolve("readboard");
        String hostSession = request.runtime().applicationLogSessionId();
        String processSession = processSession(request);
        String processAlias =
            processSession == null ? null : sanitizer.alias("session", processSession);

        copyLogSource(
            out,
            NS_LIZZIE + "app.log",
            "lizzie-app",
            NS_LIZZIE,
            hostLogs,
            "app",
            captureTime.minusSeconds(limits.appWindowHours() * 3600),
            limits.appWindowHours(),
            limits.appCapBytes(),
            sanitizer,
            sources,
            null,
            false,
            true,
            hostSession,
            "no-active-session");
        throwIfCancelled(cancel, temporary);
        copyLogSource(
            out,
            NS_LIZZIE + "crash.log",
            "lizzie-crash",
            NS_LIZZIE,
            hostLogs,
            "crash",
            captureTime.minusSeconds(limits.crashWindowHours() * 3600),
            limits.crashWindowHours(),
            limits.crashCapBytes(),
            sanitizer,
            sources,
            null,
            false,
            true,
            hostSession,
            "no-active-session");
        throwIfCancelled(cancel, temporary);
        copyHostTraces(out, request, sanitizer, sources, hostSession, cancel, temporary);
        throwIfCancelled(cancel, temporary);
        copyLogSource(
            out,
            NS_READBOARD + "app.log",
            "readboard-app",
            NS_READBOARD,
            readboardLogs,
            "app",
            captureTime.minusSeconds(limits.appWindowHours() * 3600),
            limits.appWindowHours(),
            limits.appCapBytes(),
            sanitizer,
            sources,
            null,
            true,
            true,
            processAlias,
            "no-current-session");
        throwIfCancelled(cancel, temporary);
        copyLogSource(
            out,
            NS_READBOARD + "crash.log",
            "readboard-crash",
            NS_READBOARD,
            readboardLogs,
            "crash",
            captureTime.minusSeconds(limits.crashWindowHours() * 3600),
            limits.crashWindowHours(),
            limits.crashCapBytes(),
            sanitizer,
            sources,
            null,
            true,
            true,
            processAlias,
            "no-current-session");
        throwIfCancelled(cancel, temporary);
        copyReadBoardTrace(out, request, sanitizer, sources, processSession, processAlias, cancel, temporary);
        throwIfCancelled(cancel, temporary);
        copyCapture(out, request, sanitizer, sources, processSession, processAlias, cancel, temporary);
        throwIfCancelled(cancel, temporary);
        writeSnapshots(out, request, sanitizer, sources, hostSession, captureTime);
        throwIfCancelled(cancel, temporary);
        writeTextEntry(
            out,
            "manifest.json",
            renderManifest(request, captureTime, sanitizer, sources, processAlias).toString(2));
      }
      throwIfCancelled(cancel, temporary);
      try {
        Files.move(temporary, published, StandardCopyOption.ATOMIC_MOVE);
      } catch (AtomicMoveNotSupportedException e) {
        Files.deleteIfExists(temporary);
        throw new IOException("atomic publication is required", e);
      }
      LOG.info("diagnostic package published file={}", published.getFileName());
      return published;
    } catch (IOException e) {
      Files.deleteIfExists(temporary);
      throw e;
    }
  }

  private void copyHostTraces(
      ZipOutputStream out,
      DiagnosticBundleRequest request,
      ExportSanitizer sanitizer,
      JSONObject sources,
      String hostSession,
      BooleanSupplier cancel,
      Path temporary)
      throws IOException {
    Set<TraceScope> rawScopes = request.rawScopes();
    for (TraceScope scope : TraceScope.values()) {
      String sourceName = hostTraceSourceName(scope);
      if (!rawScopes.contains(scope)) {
        sources.put(
            sourceName,
            sourceRecord(
                false,
                "omitted",
                0,
                0,
                limits.rawCapBytes(),
                NS_LIZZIE,
                hostSession,
                "not-requested",
                false));
        continue;
      }
      throwIfCancelled(cancel, temporary);
      String session = request.runtime().currentTraceSessionId();
      if (session == null) {
        sources.put(
            sourceName,
            sourceRecord(
                true,
                "omitted",
                0,
                0,
                limits.rawCapBytes(),
                NS_LIZZIE,
                hostSession,
                "no-active-session",
                false));
        continue;
      }
      copyLogSource(
          out,
          NS_LIZZIE + scope.fileName(),
          sourceName,
          NS_LIZZIE,
          request.runtime().logsDirectory(),
          stem(scope.fileName()),
          Instant.EPOCH,
          0,
          limits.rawCapBytes(),
          sanitizer,
          sources,
          session,
          false,
          true,
          hostSession,
          "no-active-session");
    }
  }

  private void copyReadBoardTrace(
      ZipOutputStream out,
      DiagnosticBundleRequest request,
      ExportSanitizer sanitizer,
      JSONObject sources,
      String processSession,
      String processAlias,
      BooleanSupplier cancel,
      Path temporary)
      throws IOException {
    if (!request.includeReadBoardTrace()) {
      sources.put(
          "readboard-trace",
          sourceRecord(
              false,
              "omitted",
              0,
              0,
              limits.rawCapBytes(),
              NS_READBOARD,
              processAlias,
              "not-requested",
              false));
      return;
    }
    if (!request.readBoardLogging().attached() || processSession == null) {
      sources.put(
          "readboard-trace",
          sourceRecord(
              true,
              "omitted",
              0,
              0,
              limits.rawCapBytes(),
              NS_READBOARD,
              processAlias,
              request.readBoardLogging().attached() ? "no-current-session" : "helper-not-started",
              false));
      return;
    }
    throwIfCancelled(cancel, temporary);
    copyLogSource(
        out,
        NS_READBOARD + "trace.log",
        "readboard-trace",
        NS_READBOARD,
        request.runtime().logsDirectory().resolve("readboard"),
        "trace",
        Instant.EPOCH,
        0,
        limits.rawCapBytes(),
        sanitizer,
        sources,
        processSession,
        true,
        true,
        processAlias,
        "no-current-session");
  }

  private void copyCapture(
      ZipOutputStream out,
      DiagnosticBundleRequest request,
      ExportSanitizer sanitizer,
      JSONObject sources,
      String processSession,
      String processAlias,
      BooleanSupplier cancel,
      Path temporary)
      throws IOException {
    if (!request.includeCapture()) {
      sources.put(
          "readboard-capture",
          sourceRecord(
              false,
              "omitted",
              0,
              0,
              limits.captureCapBytes(),
              NS_CAPTURE,
              processAlias,
              "not-requested",
              false));
      return;
    }
    if (!request.readBoardLogging().attached() || processSession == null) {
      sources.put(
          "readboard-capture",
          sourceRecord(
              true,
              "omitted",
              0,
              0,
              limits.captureCapBytes(),
              NS_CAPTURE,
              processAlias,
              request.readBoardLogging().attached() ? "no-current-session" : "helper-not-started",
              false));
      return;
    }
    throwIfCancelled(cancel, temporary);
    Path captureRoot = request.runtime().logsDirectory().resolve("readboard").resolve("capture");
    if (!Files.isDirectory(captureRoot)) {
      sources.put(
          "readboard-capture",
          sourceRecord(
              true,
              "omitted",
              0,
              0,
              limits.captureCapBytes(),
              NS_CAPTURE,
              processAlias,
              "no-current-session",
              false));
      return;
    }
    if (!Files.isReadable(captureRoot)) {
      sources.put(
          "readboard-capture",
          sourceRecord(
              true,
              "failed",
              0,
              0,
              limits.captureCapBytes(),
              NS_CAPTURE,
              processAlias,
              "unreadable",
              false));
      return;
    }
    try {
      List<Path> events =
          listCurrentCaptureEvents(
              captureRoot, processSession, request.readBoardLogging().processSessionObservedAt());
      events.sort(Comparator.comparing((Path path) -> path.getFileName().toString()).reversed());
      long remaining = limits.captureCapBytes();
      long written = 0;
      int includedEvents = 0;
      boolean truncated = false;
      String boundary = "";
      for (Path event : events) {
        throwIfCancelled(cancel, temporary);
        long size = directorySize(event);
        if (size > remaining) {
          truncated = true;
          break;
        }
        writeCaptureEvent(out, event, sanitizer);
        written += size;
        remaining -= size;
        includedEvents++;
        if (boundary.isEmpty()) {
          boundary = event.getFileName().toString();
        }
      }
      Path debugLog = captureRoot.resolve("debug.log");
      if (Files.isRegularFile(debugLog) && !debugLog.getFileName().toString().endsWith(".zip")) {
        long size = Files.size(debugLog);
        if (size <= remaining) {
          writeSanitizedTextEntry(out, NS_CAPTURE + "debug.log", debugLog, sanitizer);
          written += size;
        } else if (size > 0) {
          truncated = true;
        }
      }
      if (includedEvents == 0 && !truncated) {
        sources.put(
            "readboard-capture",
            sourceRecord(
                true,
                "omitted",
                0,
                0,
                limits.captureCapBytes(),
                NS_CAPTURE,
                processAlias,
                "no-current-session",
                false));
        return;
      }
      JSONObject source =
          sourceRecord(
              true,
              truncated ? "truncated" : "included",
              written,
              0,
              limits.captureCapBytes(),
              NS_CAPTURE,
              processAlias,
              truncated ? "cap" : "",
              truncated);
      if (!boundary.isEmpty()) {
        source.put("boundary", boundary);
      }
      sources.put("readboard-capture", source);
    } catch (IOException e) {
      sources.put(
          "readboard-capture",
          sourceRecord(
              true,
              "failed",
              0,
              0,
              limits.captureCapBytes(),
              NS_CAPTURE,
              processAlias,
              failureReason(e),
              false));
    }
  }

  private void writeSnapshots(
      ZipOutputStream out,
      DiagnosticBundleRequest request,
      ExportSanitizer sanitizer,
      JSONObject sources,
      String hostSession,
      Instant captureTime)
      throws IOException {
    JSONObject projected = ConfigExportProjection.project(request.config());
    writeTextEntry(
        out, NS_SNAPSHOTS + "config.json", sanitizer.sanitizeJsonObject(projected).toString(2));
    JSONObject versions = new JSONObject();
    versions.put("host", request.appVersion());
    versions.put("readboard", request.readBoardVersion());
    writeTextEntry(out, NS_SNAPSHOTS + "versions.json", versions.toString(2));
    writeTextEntry(
        out,
        NS_SNAPSHOTS + "readboard-observed.json",
        renderObserved(request.readBoardLogging(), sanitizer).toString(2));
    SyncDiagnosticsExportSnapshot snapshot =
        request.snapshot() == null
            ? new SyncDiagnosticsExportSnapshot(
                captureTime.toEpochMilli(), null, null, null, null, null)
            : request.snapshot();
    SyncDiagnosticsExporter.writeSnapshotEntries(out, snapshot, sanitizer.shareTime(), NS_SNAPSHOTS);
    sources.put(
        "snapshots",
        sourceRecord(true, "included", 0, 0, 0, NS_SNAPSHOTS, hostSession, "", false));
  }

  private void copyLogSource(
      ZipOutputStream out,
      String entryName,
      String sourceName,
      String namespace,
      Path logsDirectory,
      String stem,
      Instant cutoff,
      long windowHours,
      long capBytes,
      ExportSanitizer sanitizer,
      JSONObject sources,
      String requiredSession,
      boolean jsonlSession,
      boolean requested,
      String sessionForManifest,
      String emptyReason)
      throws IOException {
    Path chrono = null;
    Path trimmed = null;
    try {
      List<Path> files = listLogFiles(logsDirectory, stem);
      if (files.isEmpty()) {
        sources.put(
            sourceName,
            sourceRecord(
                requested,
                "failed",
                0,
                windowHours,
                capBytes,
                namespace,
                sessionForManifest,
                "missing",
                false));
        return;
      }
      chrono = Files.createTempFile(outputDirectory, sourceName, ".src");
      try (BufferedWriter writer = Files.newBufferedWriter(chrono, StandardCharsets.UTF_8)) {
        for (int i = files.size() - 1; i >= 0; i--) {
          Path file = files.get(i);
          if (!Files.isReadable(file)) {
            throw new AccessDeniedException(file.toString());
          }
          try (BufferedReader reader = openLogReader(file)) {
            String line;
            while ((line = reader.readLine()) != null) {
              if (!inWindow(line, cutoff)
                  || !matchesRequiredSession(line, requiredSession, jsonlSession)) {
                continue;
              }
              String sanitized = sanitizer.sanitize(line);
              writer.write(sanitized);
              if (!sanitized.endsWith("\n")) {
                writer.write('\n');
              }
            }
          }
        }
      }
      long size = Files.size(chrono);
      if (size == 0 && requiredSession != null) {
        sources.put(
            sourceName,
            sourceRecord(
                requested,
                "omitted",
                0,
                windowHours,
                capBytes,
                namespace,
                sessionForManifest,
                emptyReason,
                false));
        return;
      }
      Path publishedSource = chrono;
      boolean truncated = size > capBytes;
      if (truncated) {
        trimmed = Files.createTempFile(outputDirectory, sourceName, ".tail");
        copyCompleteTail(chrono, trimmed, capBytes);
        publishedSource = trimmed;
        size = Files.size(trimmed);
      }
      writeFileEntry(out, entryName, publishedSource);
      sources.put(
          sourceName,
          sourceRecord(
              requested,
              truncated ? "truncated" : "included",
              size,
              windowHours,
              capBytes,
              namespace,
              sessionForManifest,
              truncated ? "cap" : "",
              truncated));
    } catch (IOException e) {
      sources.put(
          sourceName,
          sourceRecord(
              requested,
              "failed",
              0,
              windowHours,
              capBytes,
              namespace,
              sessionForManifest,
              failureReason(e),
              false));
    } finally {
      if (chrono != null) {
        Files.deleteIfExists(chrono);
      }
      if (trimmed != null) {
        Files.deleteIfExists(trimmed);
      }
    }
  }

  private static BufferedReader openLogReader(Path file) throws IOException {
    InputStream raw = Files.newInputStream(file);
    InputStream input =
        file.getFileName().toString().endsWith(".gz") ? new GZIPInputStream(raw) : raw;
    return new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8));
  }

  private static void copyCompleteTail(Path source, Path dest, long capBytes) throws IOException {
    long size = Files.size(source);
    long start = size <= capBytes ? 0L : size - capBytes;
    if (start > 0) {
      try (FileChannel channel = FileChannel.open(source, StandardOpenOption.READ)) {
        ByteBuffer one = ByteBuffer.allocate(1);
        while (start < size) {
          channel.position(start);
          one.clear();
          if (channel.read(one) < 1) {
            break;
          }
          one.flip();
          start++;
          if (one.get() == '\n') {
            break;
          }
        }
      }
    }
    try (InputStream input = Files.newInputStream(source);
        OutputStream output = Files.newOutputStream(dest)) {
      input.skipNBytes(start);
      input.transferTo(output);
    }
  }

  private static void writeFileEntry(ZipOutputStream out, String name, Path file) throws IOException {
    out.putNextEntry(new ZipEntry(name));
    try (InputStream input = Files.newInputStream(file)) {
      input.transferTo(out);
    }
    out.closeEntry();
  }

  private static List<Path> listLogFiles(Path logsDirectory, String stem) throws IOException {
    List<Path> files = new ArrayList<>();
    if (logsDirectory == null || !Files.isDirectory(logsDirectory)) {
      return files;
    }
    Path active = logsDirectory.resolve(stem + ".log");
    if (Files.isRegularFile(active)) {
      files.add(active);
    }
    Path archive = logsDirectory.resolve("archive");
    if (Files.isDirectory(archive)) {
      try (DirectoryStream<Path> stream = Files.newDirectoryStream(archive, stem + ".*.log.gz")) {
        for (Path path : stream) {
          if (!path.getFileName().toString().endsWith(".zip")) {
            files.add(path);
          }
        }
      }
    }
    files.sort(Comparator.comparing((Path path) -> path.getFileName().toString()).reversed());
    files.sort(Comparator.comparing(DiagnosticBundleExporter::lastModified).reversed());
    return files;
  }

  private static Instant lastModified(Path path) {
    try {
      return Files.getLastModifiedTime(path).toInstant();
    } catch (IOException e) {
      return Instant.EPOCH;
    }
  }

  private static boolean inWindow(String line, Instant cutoff) {
    Instant parsed = parseTimestamp(line, cutoff);
    if (parsed == null) {
      return true;
    }
    return !parsed.isBefore(cutoff);
  }

  private static Instant parseTimestamp(String line, Instant cutoff) {
    if (cutoff == null || cutoff.equals(Instant.EPOCH)) {
      return Instant.EPOCH;
    }
    String trimmed = line == null ? "" : line.trim();
    if (trimmed.startsWith("{")) {
      try {
        String ts = new JSONObject(trimmed).optString("ts", "");
        if (!ts.isEmpty()) {
          return Instant.parse(ts);
        }
      } catch (RuntimeException ignored) {
        return null;
      }
    }
    if (line == null || line.length() < 23) {
      return null;
    }
    try {
      LocalDateTime parsed = LocalDateTime.parse(line.substring(0, 23), LOG_TIMESTAMP);
      return parsed.atZone(ZoneId.systemDefault()).toInstant();
    } catch (DateTimeParseException e) {
      return null;
    }
  }

  private static boolean matchesRequiredSession(
      String line, String requiredSession, boolean jsonlSession) {
    if (requiredSession == null || requiredSession.isEmpty()) {
      return true;
    }
    if (jsonlSession) {
      String found = extractProcessSession(line);
      if (found != null) {
        return requiredSession.equals(found);
      }
    }
    return line.contains(requiredSession);
  }

  private static String extractProcessSession(String line) {
    try {
      JSONObject json = new JSONObject(line);
      if (!json.has("processSessionId")) {
        return null;
      }
      Object value = json.get("processSessionId");
      if (value instanceof JSONObject tagged) {
        return tagged.optString("value", null);
      }
      return String.valueOf(value);
    } catch (JSONException e) {
      return null;
    }
  }

  private JSONObject renderManifest(
      DiagnosticBundleRequest request,
      Instant captureTime,
      ExportSanitizer sanitizer,
      JSONObject sources,
      String processAlias) {
    LoggingRuntime runtime = request.runtime();
    LoggingSettings settings = runtime.settings();
    JSONObject manifest = new JSONObject();
    manifest.put("applicationSession", runtime.applicationLogSessionId());
    if (runtime.currentTraceSessionId() == null) {
      manifest.put("traceSession", JSONObject.NULL);
    } else {
      manifest.put("traceSession", runtime.currentTraceSessionId());
    }
    if (processAlias == null) {
      manifest.put("processSession", JSONObject.NULL);
    } else {
      manifest.put("processSession", processAlias);
    }
    manifest.put("captureTime", captureTime.toString());
    manifest.put("appVersion", request.appVersion());
    manifest.put("sanitizerVersion", ExportSanitizer.VERSION);
    manifest.put("diagnosticsEnabled", settings.diagnosticsEnabled());
    manifest.put("fullTraceActive", runtime.fullTraceActive());
    manifest.put("diagnosticModules", wireNames(settings.diagnosticModules()));
    manifest.put("preferredTraceScopes", wireScopeNames(settings.preferredTraceScopes()));
    manifest.put("activeTraceScopes", wireScopeNames(runtime.activeTraceScopes()));
    JSONArray aliases = new JSONArray();
    for (String alias : sanitizer.aliases().values()) {
      aliases.put(alias);
    }
    manifest.put("aliases", aliases);
    manifest.put("sources", sources);
    return manifest;
  }

  private static JSONObject renderObserved(
      ReadBoardLoggingSnapshot snapshot, ExportSanitizer sanitizer) {
    JSONObject json = new JSONObject();
    json.put("attached", snapshot.attached());
    json.put("contractLaunch", snapshot.contractLaunch());
    json.put("status", snapshot.status().name());
    if (snapshot.processSessionId() == null || snapshot.processSessionId().isEmpty()) {
      json.put("processSessionId", JSONObject.NULL);
    } else {
      json.put("processSessionId", sanitizer.alias("session", snapshot.processSessionId()));
    }
    json.put("capabilityKnown", snapshot.capabilityKnown());
    json.put("diagnosticsDesired", snapshot.desired().diagnostics);
    json.put("captureDesired", snapshot.desired().capture);
    json.put("traceDesired", snapshot.desired().trace);
    json.put("observedDiagnostics", token(snapshot.observedDiagnostics()));
    json.put("observedCapture", token(snapshot.observedCapture()));
    json.put("observedTrace", token(snapshot.observedTrace()));
    json.put(
        "persistence",
        snapshot.persistence() == null
            ? JSONObject.NULL
            : snapshot.persistence().name().toLowerCase(Locale.ROOT).replace('_', '-'));
    json.put("dropCount", snapshot.dropCount());
    json.put(
        "reason",
        snapshot.reason() == null
            ? JSONObject.NULL
            : snapshot.reason().name().toLowerCase(Locale.ROOT).replace('_', '-'));
    json.put("captureSummary", sanitizer.sanitizeText(snapshot.captureSummary()));
    return json;
  }

  private static String token(ReadBoardLoggingProtocol.Toggle toggle) {
    return toggle == null ? "unknown" : toggle.name().toLowerCase(Locale.ROOT);
  }

  private static JSONArray wireNames(Set<DiagnosticModule> modules) {
    JSONArray array = new JSONArray();
    for (DiagnosticModule module : modules) {
      array.put(module.wireName());
    }
    return array;
  }

  private static JSONArray wireScopeNames(Set<TraceScope> scopes) {
    JSONArray array = new JSONArray();
    for (TraceScope scope : scopes) {
      array.put(scope.wireName());
    }
    return array;
  }

  private Path uniqueZipPath(Instant captureTime) throws IOException {
    String stamp = FILE_TIMESTAMP.format(captureTime);
    Path candidate = outputDirectory.resolve("lizzie-diagnostics-" + stamp + ".zip");
    int suffix = 2;
    while (Files.exists(candidate)) {
      candidate = outputDirectory.resolve("lizzie-diagnostics-" + stamp + "-" + suffix + ".zip");
      suffix++;
    }
    return candidate;
  }

  private static void writeTextEntry(ZipOutputStream out, String name, String text) throws IOException {
    out.putNextEntry(new ZipEntry(name));
    out.write(text.getBytes(StandardCharsets.UTF_8));
    out.closeEntry();
  }

  private static JSONObject sourceRecord(
      boolean requested,
      String status,
      long bytes,
      long windowHours,
      long capBytes,
      String namespace,
      String session,
      String reason,
      boolean truncated) {
    JSONObject json = new JSONObject();
    json.put("requested", requested);
    json.put("included", "included".equals(status) || "truncated".equals(status));
    json.put("status", status);
    json.put("bytes", bytes);
    json.put("omitted", "omitted".equals(status));
    json.put("failed", "failed".equals(status));
    json.put("truncated", truncated || "truncated".equals(status));
    json.put("reason", reason == null ? "" : reason);
    if (windowHours > 0) {
      json.put("windowHours", windowHours);
    }
    json.put("capBytes", capBytes);
    json.put("namespace", namespace);
    if (session != null && !session.isEmpty()) {
      json.put("session", session);
    }
    return json;
  }

  private static String hostTraceSourceName(TraceScope scope) {
    switch (scope) {
      case ENGINE_GTP:
        return "lizzie-engine-trace";
      case READBOARD_YIKE:
        return "lizzie-readboard-trace";
      case NETWORK_WEBSOCKET:
        return "lizzie-network-trace";
      default:
        return "lizzie-" + scope.wireName();
    }
  }

  private static String stem(String fileName) {
    return fileName.endsWith(".log") ? fileName.substring(0, fileName.length() - 4) : fileName;
  }

  private static void throwIfCancelled(BooleanSupplier cancelled, Path temporary) throws IOException {
    if (cancelled.getAsBoolean()) {
      Files.deleteIfExists(temporary);
      throw new IOException("diagnostic export cancelled");
    }
  }

  private static boolean hasProcessSession(DiagnosticBundleRequest request) {
    return processSession(request) != null;
  }

  private static String processSession(DiagnosticBundleRequest request) {
    String processSession = request.readBoardLogging().processSessionId();
    if (processSession == null || processSession.isEmpty()) {
      return null;
    }
    return processSession;
  }

  private static String failureReason(IOException e) {
    if (e instanceof AccessDeniedException || "unreadable".equals(e.getMessage())) {
      return "unreadable";
    }
    if (e instanceof java.nio.file.NoSuchFileException) {
      return "missing";
    }
    return "unreadable";
  }

  private static List<Path> listCurrentCaptureEvents(
      Path captureRoot, String processSession, Instant sessionObservedAt) throws IOException {
    List<Path> events = new ArrayList<>();
    try (DirectoryStream<Path> stream = Files.newDirectoryStream(captureRoot)) {
      for (Path path : stream) {
        if (!Files.isDirectory(path) || path.getFileName().toString().endsWith(".zip")) {
          continue;
        }
        Path metadata = path.resolve("metadata.json");
        if (!Files.isRegularFile(metadata)) {
          continue;
        }
        if (captureEventMatchesSession(metadata, processSession, sessionObservedAt)) {
          events.add(path);
        }
      }
    }
    return events;
  }

  private static boolean captureEventMatchesSession(
      Path metadata, String processSession, Instant sessionObservedAt) throws IOException {
    try {
      JSONObject json = new JSONObject(Files.readString(metadata));
      String stamped = extractMetadataSession(json);
      if (stamped != null && !stamped.isEmpty()) {
        return processSession.equals(stamped);
      }
      Instant eventTime = parseCaptureTimestamp(json);
      if (eventTime == null || sessionObservedAt == null) {
        return false;
      }
      return !eventTime.isBefore(sessionObservedAt);
    } catch (JSONException e) {
      return false;
    }
  }

  private static Instant parseCaptureTimestamp(JSONObject json) {
    String raw = json.optString("TimestampUtc", json.optString("timestampUtc", ""));
    if (raw == null || raw.isEmpty()) {
      return null;
    }
    try {
      return Instant.parse(raw);
    } catch (RuntimeException e) {
      return null;
    }
  }

  private static String extractMetadataSession(JSONObject json) {
    if (json.has("processSessionId")) {
      Object value = json.get("processSessionId");
      if (value instanceof JSONObject tagged) {
        return tagged.optString("value", null);
      }
      return String.valueOf(value);
    }
    if (json.has("ProcessSessionId")) {
      return json.optString("ProcessSessionId", null);
    }
    return null;
  }

  private static void writeCaptureEvent(
      ZipOutputStream out, Path event, ExportSanitizer sanitizer)
      throws IOException {
    try (DirectoryStream<Path> stream = Files.newDirectoryStream(event)) {
      for (Path file : stream) {
        if (!Files.isRegularFile(file) || file.getFileName().toString().endsWith(".zip")) {
          continue;
        }
        String relative = NS_CAPTURE + event.getFileName() + "/" + file.getFileName();
        if (isPng(file.getFileName().toString())) {
          writeFileEntry(out, relative, file);
        } else {
          writeSanitizedTextEntry(out, relative, file, sanitizer);
        }
      }
    }
  }

  private static long writeSanitizedTextEntry(
      ZipOutputStream out, String name, Path file, ExportSanitizer sanitizer) throws IOException {
    String raw = Files.readString(file, StandardCharsets.UTF_8);
    String sanitized = file.getFileName().toString().endsWith(".json")
        ? sanitizer.sanitize(raw)
        : sanitizer.sanitizeText(raw);
    byte[] bytes = sanitized.getBytes(StandardCharsets.UTF_8);
    out.putNextEntry(new ZipEntry(name));
    out.write(bytes);
    out.closeEntry();
    return bytes.length;
  }

  private static boolean isPng(String fileName) {
    return fileName.toLowerCase(Locale.ROOT).endsWith(".png");
  }

  private static long directorySize(Path directory) throws IOException {
    if (directory == null || !Files.isDirectory(directory)) {
      return 0;
    }
    long total = 0;
    try (var stream = Files.walk(directory)) {
      for (Path path : (Iterable<Path>) stream::iterator) {
        if (Files.isRegularFile(path) && !path.getFileName().toString().endsWith(".zip")) {
          total += Files.size(path);
        }
      }
    }
    return total;
  }
}
