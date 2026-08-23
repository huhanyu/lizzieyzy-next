package featurecat.lizzie.analysis;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class ReadBoardLoggingProtocol {
  public static final String CAPABILITY_COMMAND = "readboardLoggingV1";
  public static final String SET_COMMAND = "readboardLoggingSet";
  public static final String OBSERVED_COMMAND = "readboardLoggingObserved";
  public static final String ARG_LOG_DIR = "--log-dir";
  public static final String ARG_HOST_SESSION_ID = "--host-session-id";
  public static final String ARG_LOGGING_CONTRACT = "--logging-contract";
  public static final String ARG_DIAGNOSTICS = "--diagnostics";
  public static final String ARG_CAPTURE = "--capture";
  public static final String CONTRACT_VERSION = "1";
  public static final String ON = "on";
  public static final String OFF = "off";
  public static final String UNKNOWN = "unknown";
  public static final String HEALTHY = "healthy";
  public static final String DEGRADED = "degraded";
  public static final String UNAVAILABLE = "unavailable";
  public static final String REASON_APPLIED = "applied";
  public static final String REASON_LEGACY_HELPER = "legacy-helper";
  public static final String REASON_CAPABILITY_TIMEOUT = "capability-timeout";
  public static final String REASON_PATH_UNAVAILABLE = "path-unavailable";
  public static final String REASON_WRITER_FAULT = "writer-fault";
  public static final String REASON_INVALID_REQUEST = "invalid-request";
  public static final String PRIVACY_SAFE = "safe";
  public static final String PRIVACY_LOCAL_PATH = "localPath";
  public static final String PRIVACY_LOCAL_URL = "localUrl";
  public static final String PRIVACY_USER_TEXT = "userText";
  public static final String PRIVACY_SESSION_ID = "sessionId";
  public static final String PRIVACY_SECRET = "secret";


  public enum Toggle {
    OFF,
    ON,
    UNKNOWN
  }

  public enum Persistence {
    HEALTHY,
    DEGRADED,
    UNAVAILABLE
  }

  public enum Reason {
    APPLIED,
    LEGACY_HELPER,
    CAPABILITY_TIMEOUT,
    PATH_UNAVAILABLE,
    WRITER_FAULT,
    INVALID_REQUEST
  }

  public enum Privacy {
    SAFE,
    LOCAL_PATH,
    LOCAL_URL,
    USER_TEXT,
    SESSION_ID,
    SECRET
  }


  public static final class Capability {
    public final String processSessionId;
    public final Toggle diagnostics;
    public final Toggle capture;
    public final Toggle trace;
    public final Persistence persistence;
    public final int dropCount;

    public Capability(
        String processSessionId,
        Toggle diagnostics,
        Toggle capture,
        Toggle trace,
        Persistence persistence,
        int dropCount) {
      this.processSessionId = processSessionId;
      this.diagnostics = diagnostics;
      this.capture = capture;
      this.trace = trace;
      this.persistence = persistence;
      this.dropCount = dropCount;
    }
  }

  public static final class SetRequest {
    public final String requestId;
    public final Toggle diagnostics;
    public final Toggle capture;
    public final Toggle trace;

    public SetRequest(String requestId, Toggle diagnostics, Toggle capture, Toggle trace) {
      this.requestId = requestId;
      this.diagnostics = diagnostics;
      this.capture = capture;
      this.trace = trace;
    }
  }

  public static final class Observed {
    public final String requestId;
    public final String processSessionId;
    public final Toggle diagnostics;
    public final Toggle capture;
    public final Toggle trace;
    public final Persistence persistence;
    public final int dropCount;
    public final Reason reason;

    public Observed(
        String requestId,
        String processSessionId,
        Toggle diagnostics,
        Toggle capture,
        Toggle trace,
        Persistence persistence,
        int dropCount,
        Reason reason) {
      this.requestId = requestId;
      this.processSessionId = processSessionId;
      this.diagnostics = diagnostics;
      this.capture = capture;
      this.trace = trace;
      this.persistence = persistence;
      this.dropCount = dropCount;
      this.reason = reason;
    }
  }

  public static final class RequestGate {
    private String latestRequestId;

    public void noteRequest(String requestId) {
      if (isOpaqueId(requestId)) {
        latestRequestId = requestId;
      }
    }

    public String latestRequestId() {
      return latestRequestId;
    }

    public boolean acceptObserved(Observed observed) {
      return observed != null && Objects.equals(latestRequestId, observed.requestId);
    }
  }

  private ReadBoardLoggingProtocol() {}

  public static boolean isControlLine(String rawLine) {
    String command = readCommand(rawLine);
    return CAPABILITY_COMMAND.equals(command)
        || SET_COMMAND.equals(command)
        || OBSERVED_COMMAND.equals(command);
  }

  public static boolean isOpaqueId(String value) {
    if (value == null || value.isEmpty()) {
      return false;
    }
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      boolean allowed =
          (c >= 'A' && c <= 'Z')
              || (c >= 'a' && c <= 'z')
              || (c >= '0' && c <= '9')
              || c == '-'
              || c == '_';
      if (!allowed) {
        return false;
      }
    }
    return true;
  }

  public static boolean isAbsoluteLogDirectory(String path) {
    if (path == null || path.trim().isEmpty()) {
      return false;
    }
    if (path.length() >= 4
        && isAsciiLetter(path.charAt(0))
        && path.charAt(1) == ':'
        && (path.charAt(2) == '\\' || path.charAt(2) == '/')
        && path.charAt(3) != '\\'
        && path.charAt(3) != '/') {
      return true;
    }
    if (!path.startsWith("\\\\") || path.startsWith("\\\\?\\")) {
      return false;
    }
    int serverEnd = indexOfSeparator(path, 2);
    if (serverEnd <= 2) {
      return false;
    }
    int shareStart = serverEnd + 1;
    if (shareStart >= path.length()) {
      return false;
    }
    int shareEnd = indexOfSeparator(path, shareStart);
    int shareLength = (shareEnd < 0 ? path.length() : shareEnd) - shareStart;
    return shareLength > 0;
  }

  private static int indexOfSeparator(String path, int start) {
    int slash = path.indexOf('\\', start);
    int forward = path.indexOf('/', start);
    if (slash < 0) {
      return forward;
    }
    if (forward < 0) {
      return slash;
    }
    return Math.min(slash, forward);
  }

  public static List<String> appendLaunchArguments(
      List<String> positional,
      String logDir,
      String hostSessionId,
      boolean diagnostics,
      boolean capture) {
    if (positional == null || positional.size() < 7) {
      throw new IllegalArgumentException("positional launch arguments");
    }
    if (!isAbsoluteLogDirectory(logDir) || !isOpaqueId(hostSessionId)) {
      throw new IllegalArgumentException("invalid-request");
    }
    List<String> args = new ArrayList<String>(positional.subList(0, 7));
    args.add(ARG_LOG_DIR);
    args.add(logDir);
    args.add(ARG_HOST_SESSION_ID);
    args.add(hostSessionId);
    args.add(ARG_LOGGING_CONTRACT);
    args.add(CONTRACT_VERSION);
    args.add(ARG_DIAGNOSTICS);
    args.add(diagnostics ? ON : OFF);
    args.add(ARG_CAPTURE);
    args.add(capture ? ON : OFF);
    return Collections.unmodifiableList(args);
  }

  public static Capability tryParseCapability(String rawLine) {
    String[] fields = splitFields(rawLine, 7);
    if (fields == null || !CAPABILITY_COMMAND.equals(fields[0]) || !isOpaqueId(fields[1])) {
      return null;
    }
    Toggle diagnostics = parseDeterminateToggle(fields[2]);
    Toggle capture = parseDeterminateToggle(fields[3]);
    Toggle trace = parseDeterminateToggle(fields[4]);
    Persistence persistence = parsePersistence(fields[5]);
    Integer dropCount = parseDropCount(fields[6]);
    if (diagnostics == null
        || capture == null
        || trace == null
        || persistence == null
        || dropCount == null) {
      return null;
    }
    return new Capability(fields[1], diagnostics, capture, trace, persistence, dropCount);
  }

  public static SetRequest tryParseSet(String rawLine) {
    String[] fields = splitFields(rawLine, 5);
    if (fields == null || !SET_COMMAND.equals(fields[0]) || !isOpaqueId(fields[1])) {
      return null;
    }
    Toggle diagnostics = parseDeterminateToggle(fields[2]);
    Toggle capture = parseDeterminateToggle(fields[3]);
    Toggle trace = parseDeterminateToggle(fields[4]);
    if (diagnostics == null || capture == null || trace == null) {
      return null;
    }
    return new SetRequest(fields[1], diagnostics, capture, trace);
  }

  public static Observed tryParseObserved(String rawLine) {
    String[] fields = splitFields(rawLine, 9);
    if (fields == null
        || !OBSERVED_COMMAND.equals(fields[0])
        || !isOpaqueId(fields[1])
        || !isOpaqueId(fields[2])) {
      return null;
    }
    Toggle diagnostics = parseObservedToggle(fields[3]);
    Toggle capture = parseObservedToggle(fields[4]);
    Toggle trace = parseObservedToggle(fields[5]);
    Persistence persistence = parsePersistence(fields[6]);
    Integer dropCount = parseDropCount(fields[7]);
    Reason reason = parseReason(fields[8]);
    if (diagnostics == null
        || capture == null
        || trace == null
        || persistence == null
        || dropCount == null
        || reason == null) {
      return null;
    }
    return new Observed(
        fields[1], fields[2], diagnostics, capture, trace, persistence, dropCount, reason);
  }

  public static String formatCapability(Capability message) {
    Objects.requireNonNull(message, "message");
    return join(
        CAPABILITY_COMMAND,
        message.processSessionId,
        formatDeterminateToggle(message.diagnostics),
        formatDeterminateToggle(message.capture),
        formatDeterminateToggle(message.trace),
        formatPersistence(message.persistence),
        formatDropCount(message.dropCount));
  }

  public static String formatSet(SetRequest message) {
    Objects.requireNonNull(message, "message");
    return join(
        SET_COMMAND,
        message.requestId,
        formatDeterminateToggle(message.diagnostics),
        formatDeterminateToggle(message.capture),
        formatDeterminateToggle(message.trace));
  }

  public static String formatObserved(Observed message) {
    Objects.requireNonNull(message, "message");
    return join(
        OBSERVED_COMMAND,
        message.requestId,
        message.processSessionId,
        formatObservedToggle(message.diagnostics),
        formatObservedToggle(message.capture),
        formatObservedToggle(message.trace),
        formatPersistence(message.persistence),
        formatDropCount(message.dropCount),
        formatReason(message.reason));
  }

  public static Persistence worstPersistence(
      Persistence app, Persistence trace, Persistence crash, Persistence capture) {
    Persistence worst = app;
    if (ordinal(trace) > ordinal(worst)) {
      worst = trace;
    }
    if (ordinal(crash) > ordinal(worst)) {
      worst = crash;
    }
    if (ordinal(capture) > ordinal(worst)) {
      worst = capture;
    }
    return worst;
  }

  public static int combineDropCount(int runtimeDrops, int traceDrops) {
    if (runtimeDrops < 0) {
      runtimeDrops = 0;
    }
    if (traceDrops < 0) {
      traceDrops = 0;
    }
    return runtimeDrops + traceDrops;
  }

  public static Privacy tryParsePrivacy(String token) {
    if (PRIVACY_SAFE.equals(token)) {
      return Privacy.SAFE;
    }
    if (PRIVACY_LOCAL_PATH.equals(token)) {
      return Privacy.LOCAL_PATH;
    }
    if (PRIVACY_LOCAL_URL.equals(token)) {
      return Privacy.LOCAL_URL;
    }
    if (PRIVACY_USER_TEXT.equals(token)) {
      return Privacy.USER_TEXT;
    }
    if (PRIVACY_SESSION_ID.equals(token)) {
      return Privacy.SESSION_ID;
    }
    if (PRIVACY_SECRET.equals(token)) {
      return Privacy.SECRET;
    }
    return null;
  }

  public static String formatPrivacy(Privacy privacy) {
    if (privacy == Privacy.LOCAL_PATH) {
      return PRIVACY_LOCAL_PATH;
    }
    if (privacy == Privacy.LOCAL_URL) {
      return PRIVACY_LOCAL_URL;
    }
    if (privacy == Privacy.USER_TEXT) {
      return PRIVACY_USER_TEXT;
    }
    if (privacy == Privacy.SESSION_ID) {
      return PRIVACY_SESSION_ID;
    }
    if (privacy == Privacy.SECRET) {
      return PRIVACY_SECRET;
    }
    return PRIVACY_SAFE;
  }


  public static Observed applySetIndependently(
      SetRequest request,
      String processSessionId,
      Persistence app,
      Persistence trace,
      Persistence crash,
      Persistence capture,
      int runtimeDrops,
      int traceDrops,
      Reason reason) {
    Objects.requireNonNull(request, "request");
    return new Observed(
        request.requestId,
        processSessionId,
        request.diagnostics,
        request.capture,
        request.trace,
        worstPersistence(app, trace, crash, capture),
        combineDropCount(runtimeDrops, traceDrops),
        reason);
  }

  private static String readCommand(String rawLine) {
    if (rawLine == null) {
      return null;
    }
    String trimmed = rawLine.replace("\r", "").replace("\n", "").trim();
    if (trimmed.isEmpty()) {
      return null;
    }
    int space = trimmed.indexOf(' ');
    return space < 0 ? trimmed : trimmed.substring(0, space);
  }

  private static String[] splitFields(String rawLine, int expectedCount) {
    if (rawLine == null) {
      return null;
    }
    String trimmed = rawLine.replace("\r", "").replace("\n", "").trim();
    if (trimmed.isEmpty()) {
      return null;
    }
    String[] parts = trimmed.split(" ");
    if (parts.length != expectedCount) {
      return null;
    }
    for (String part : parts) {
      if (part.isEmpty()) {
        return null;
      }
    }
    return parts;
  }

  private static Toggle parseDeterminateToggle(String token) {
    if (ON.equals(token)) {
      return Toggle.ON;
    }
    if (OFF.equals(token)) {
      return Toggle.OFF;
    }
    return null;
  }

  private static Toggle parseObservedToggle(String token) {
    if (UNKNOWN.equals(token)) {
      return Toggle.UNKNOWN;
    }
    return parseDeterminateToggle(token);
  }

  private static Persistence parsePersistence(String token) {
    if (HEALTHY.equals(token)) {
      return Persistence.HEALTHY;
    }
    if (DEGRADED.equals(token)) {
      return Persistence.DEGRADED;
    }
    if (UNAVAILABLE.equals(token)) {
      return Persistence.UNAVAILABLE;
    }
    return null;
  }

  private static Reason parseReason(String token) {
    if (REASON_APPLIED.equals(token)) {
      return Reason.APPLIED;
    }
    if (REASON_LEGACY_HELPER.equals(token)) {
      return Reason.LEGACY_HELPER;
    }
    if (REASON_CAPABILITY_TIMEOUT.equals(token)) {
      return Reason.CAPABILITY_TIMEOUT;
    }
    if (REASON_PATH_UNAVAILABLE.equals(token)) {
      return Reason.PATH_UNAVAILABLE;
    }
    if (REASON_WRITER_FAULT.equals(token)) {
      return Reason.WRITER_FAULT;
    }
    if (REASON_INVALID_REQUEST.equals(token)) {
      return Reason.INVALID_REQUEST;
    }
    return null;
  }

  private static Integer parseDropCount(String token) {
    if (token == null || token.isEmpty()) {
      return null;
    }
    for (int i = 0; i < token.length(); i++) {
      char c = token.charAt(i);
      if (c < '0' || c > '9') {
        return null;
      }
    }
    try {
      return Integer.valueOf(token);
    } catch (NumberFormatException ignored) {
      return null;
    }
  }

  private static String formatDeterminateToggle(Toggle toggle) {
    return toggle == Toggle.ON ? ON : OFF;
  }

  private static String formatObservedToggle(Toggle toggle) {
    return toggle == Toggle.UNKNOWN ? UNKNOWN : formatDeterminateToggle(toggle);
  }

  private static String formatPersistence(Persistence persistence) {
    if (persistence == Persistence.HEALTHY) {
      return HEALTHY;
    }
    if (persistence == Persistence.DEGRADED) {
      return DEGRADED;
    }
    return UNAVAILABLE;
  }

  private static String formatDropCount(int dropCount) {
    return Integer.toString(Math.max(dropCount, 0));
  }

  private static String formatReason(Reason reason) {
    if (reason == Reason.LEGACY_HELPER) {
      return REASON_LEGACY_HELPER;
    }
    if (reason == Reason.CAPABILITY_TIMEOUT) {
      return REASON_CAPABILITY_TIMEOUT;
    }
    if (reason == Reason.PATH_UNAVAILABLE) {
      return REASON_PATH_UNAVAILABLE;
    }
    if (reason == Reason.WRITER_FAULT) {
      return REASON_WRITER_FAULT;
    }
    if (reason == Reason.INVALID_REQUEST) {
      return REASON_INVALID_REQUEST;
    }
    return REASON_APPLIED;
  }

  private static int ordinal(Persistence persistence) {
    return persistence == null ? -1 : persistence.ordinal();
  }

  private static String join(String... parts) {
    StringBuilder builder = new StringBuilder();
    for (int i = 0; i < parts.length; i++) {
      if (i > 0) {
        builder.append(' ');
      }
      builder.append(parts[i]);
    }
    return builder.toString();
  }

  private static boolean isAsciiLetter(char value) {
    return (value >= 'A' && value <= 'Z') || (value >= 'a' && value <= 'z');
  }
}
