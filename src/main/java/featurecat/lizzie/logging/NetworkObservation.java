package featurecat.lizzie.logging;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class NetworkObservation {
  static final int MAX_REQUEST_ID_CHARACTERS = 96;
  private static final int HASHED_REQUEST_ID_HEX_CHARACTERS = 24;
  private static final Logger NETWORK = LoggerFactory.getLogger(LogCategories.NETWORK);
  private static final Logger REMOTE = LoggerFactory.getLogger(LogCategories.REMOTE);
  private static final Logger TRACE = LoggerFactory.getLogger(LogCategories.NETWORK_TRACE);

  private NetworkObservation() {}

  public static boolean diagnosticsEnabled() {
    return initialized() && (NETWORK.isDebugEnabled() || REMOTE.isDebugEnabled());
  }

  public static boolean traceEnabled() {
    return initialized()
        && LoggingRuntime.current().filter(LoggingRuntime::fullTraceActive).isPresent()
        && TRACE.isInfoEnabled();
  }

  public static String newRequestIdentity() {
    return LoggingRuntime.current()
        .filter(runtime -> !runtime.isShutdown())
        .map(LoggingRuntime::newRequestIdentity)
        .orElse("req-none");
  }

  /** Preserves already-safe correlation IDs and irreversibly bounds all other external values. */
  public static String normalizeExternalRequestIdentity(String requestId) {
    if (requestId == null || requestId.isEmpty()) {
      return "";
    }
    if (requestId.length() <= MAX_REQUEST_ID_CHARACTERS && isSafeRequestIdentity(requestId)) {
      return requestId;
    }
    return "req-ext-" + sha256Prefix(requestId);
  }

  public static void recordNetwork(
      String method,
      String host,
      NetworkEndpointCategory category,
      Integer status,
      long latencyMs,
      String outcome,
      String requestId) {
    record(NETWORK, "network", method, host, category, status, latencyMs, outcome, requestId);
  }

  public static void recordRemote(
      String method,
      String host,
      NetworkEndpointCategory category,
      Integer status,
      long latencyMs,
      String outcome,
      String requestId) {
    record(REMOTE, "remote", method, host, category, status, latencyMs, outcome, requestId);
  }

  public static void tracePayload(
      NetworkEndpointCategory category, String direction, Supplier<String> payload) {
    if (category == null || category.prohibitsBodies() || payload == null) {
      return;
    }
    if (!traceEnabled()) {
      return;
    }
    String text = payload.get();
    if (text == null) {
      return;
    }
    inContext(
        null,
        () ->
            TRACE.info(
                "network raw direction={} payload={}",
                direction == null || direction.isEmpty() ? "unknown" : direction,
                ObservationText.boundedRawEvent(text)));
  }

  public static void inContext(String requestId, Runnable action) {
    if (action == null) {
      return;
    }
    String trace = LoggingRuntime.current().map(LoggingRuntime::currentTraceSessionId).orElse(null);
    String safeRequestId = normalizeExternalRequestIdentity(requestId);
    try (CorrelationContext.Scope scope =
        CorrelationContext.openScope().installRequest(safeRequestId)) {
      if (trace != null) {
        scope.installTraceSession(trace);
      }
      action.run();
    }
  }

  private static void record(
      Logger logger,
      String kind,
      String method,
      String host,
      NetworkEndpointCategory category,
      Integer status,
      long latencyMs,
      String outcome,
      String requestId) {
    if (!initialized()) {
      return;
    }
    String safeOutcome = outcome == null || outcome.isEmpty() ? "unknown" : outcome;
    boolean failed = "failed".equals(safeOutcome) || "error".equals(safeOutcome);
    if (failed) {
      if (!logger.isWarnEnabled()) {
        return;
      }
    } else if (!(initialized() && logger.isDebugEnabled())) {
      return;
    }
    String safeMethod = method == null || method.isEmpty() ? "UNKNOWN" : method;
    String safeHost = safeHost(host);
    String safeCategory =
        category == null ? NetworkEndpointCategory.OTHER.wireName() : category.wireName();
    Object safeStatus = status == null ? "-" : status;
    inContext(
        requestId,
        () -> {
          if (failed) {
            logger.warn(
                "{} event=http method={} host={} category={} status={} latencyMs={} outcome={}",
                kind,
                safeMethod,
                safeHost,
                safeCategory,
                safeStatus,
                latencyMs,
                safeOutcome);
          } else {
            logger.debug(
                "{} event=http method={} host={} category={} status={} latencyMs={} outcome={}",
                kind,
                safeMethod,
                safeHost,
                safeCategory,
                safeStatus,
                latencyMs,
                safeOutcome);
          }
        });
  }

  static String safeHost(String host) {
    if (host == null || host.isEmpty()) {
      return "unknown";
    }
    if (!host.contains("://") && host.indexOf('/') < 0 && host.indexOf('?') < 0) {
      return host;
    }
    try {
      URI uri = host.contains("://") ? URI.create(host) : URI.create("https://" + host);
      String parsed = uri.getHost();
      return parsed == null || parsed.isEmpty() ? "unknown" : parsed;
    } catch (RuntimeException ignored) {
      return "unknown";
    }
  }

  private static boolean isSafeRequestIdentity(String value) {
    for (int index = 0; index < value.length(); index++) {
      char character = value.charAt(index);
      boolean safe =
          character >= 'A' && character <= 'Z'
              || character >= 'a' && character <= 'z'
              || character >= '0' && character <= '9'
              || character == '-'
              || character == '_'
              || character == '.'
              || character == ':';
      if (!safe) {
        return false;
      }
    }
    return true;
  }

  private static String sha256Prefix(String value) {
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
      StringBuilder encoded = new StringBuilder(HASHED_REQUEST_ID_HEX_CHARACTERS);
      for (int index = 0;
          index < digest.length && encoded.length() < HASHED_REQUEST_ID_HEX_CHARACTERS;
          index++) {
        int current = digest[index] & 0xff;
        encoded.append(Character.forDigit(current >>> 4, 16));
        encoded.append(Character.forDigit(current & 0x0f, 16));
      }
      return encoded.toString();
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException("SHA-256 is unavailable", impossible);
    }
  }

  private static boolean initialized() {
    return LoggingRuntime.current().filter(runtime -> !runtime.isShutdown()).isPresent();
  }
}
