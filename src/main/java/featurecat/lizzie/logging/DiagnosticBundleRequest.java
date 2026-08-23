package featurecat.lizzie.logging;

import featurecat.lizzie.analysis.ReadBoardLoggingSnapshot;
import featurecat.lizzie.analysis.SyncDiagnosticsExportSnapshot;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import org.json.JSONObject;

public final class DiagnosticBundleRequest {
  private final LoggingRuntime runtime;
  private final Set<TraceScope> rawScopes;
  private final boolean includeReadBoardTrace;
  private final boolean includeCapture;
  private final JSONObject config;
  private final SyncDiagnosticsExportSnapshot snapshot;
  private final ReadBoardLoggingSnapshot readBoardLogging;
  private final String appVersion;
  private final String readBoardVersion;

  public DiagnosticBundleRequest(
      LoggingRuntime runtime,
      Set<TraceScope> rawScopes,
      JSONObject config,
      SyncDiagnosticsExportSnapshot snapshot,
      String appVersion) {
    this(
        runtime,
        rawScopes,
        false,
        true,
        config,
        snapshot,
        ReadBoardLoggingSnapshot.detached(),
        appVersion,
        "unknown");
  }

  public DiagnosticBundleRequest(
      LoggingRuntime runtime,
      Set<TraceScope> rawScopes,
      boolean includeReadBoardTrace,
      boolean includeCapture,
      JSONObject config,
      SyncDiagnosticsExportSnapshot snapshot,
      ReadBoardLoggingSnapshot readBoardLogging,
      String appVersion,
      String readBoardVersion) {
    this.runtime = Objects.requireNonNull(runtime, "runtime");
    this.rawScopes =
        rawScopes == null || rawScopes.isEmpty()
            ? EnumSet.noneOf(TraceScope.class)
            : EnumSet.copyOf(rawScopes);
    this.includeReadBoardTrace = includeReadBoardTrace;
    this.includeCapture = includeCapture;
    this.config = config == null ? new JSONObject() : new JSONObject(config.toString());
    this.snapshot = snapshot;
    this.readBoardLogging =
        readBoardLogging == null ? ReadBoardLoggingSnapshot.detached() : readBoardLogging;
    this.appVersion = appVersion == null ? "unknown" : appVersion;
    this.readBoardVersion = readBoardVersion == null ? "unknown" : readBoardVersion;
  }

  public LoggingRuntime runtime() {
    return runtime;
  }

  public Set<TraceScope> rawScopes() {
    return Collections.unmodifiableSet(rawScopes);
  }

  public boolean includeReadBoardTrace() {
    return includeReadBoardTrace;
  }

  public boolean includeCapture() {
    return includeCapture;
  }

  public JSONObject config() {
    return config;
  }

  public SyncDiagnosticsExportSnapshot snapshot() {
    return snapshot;
  }

  public ReadBoardLoggingSnapshot readBoardLogging() {
    return readBoardLogging;
  }

  public String appVersion() {
    return appVersion;
  }

  public String readBoardVersion() {
    return readBoardVersion;
  }
}
