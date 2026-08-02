package featurecat.lizzie.analysis.remote;

import java.io.IOException;

/** A sanitized Zhizi API failure that is safe to route through UI and diagnostics. */
public final class ZhiziApiException extends IOException {
  public enum Operation {
    LOGIN(false),
    FAST_LOGIN(false),
    SEND_CODE(false),
    RESET_PASSWORD(false),
    FETCH_SOCKET_TOKEN(true),
    FETCH_CONNECT_ACCOUNT(true),
    FETCH_ACCOUNT(true),
    FETCH_BALANCE(true),
    FETCH_USAGE(true),
    FETCH_CREDITS(true),
    OTHER(false);

    private final boolean idempotent;

    Operation(boolean idempotent) {
      this.idempotent = idempotent;
    }

    public boolean isIdempotent() {
      return idempotent;
    }
  }

  private final int statusCode;
  private final String errorKey;
  private final String requestId;
  private final long retryAfterSeconds;
  private final boolean retryable;
  private final Operation operation;

  public ZhiziApiException(
      int statusCode,
      String errorKey,
      String requestId,
      long retryAfterSeconds,
      boolean retryable,
      Operation operation) {
    this(statusCode, errorKey, requestId, retryAfterSeconds, retryable, operation, null);
  }

  public ZhiziApiException(
      int statusCode,
      String errorKey,
      String requestId,
      long retryAfterSeconds,
      boolean retryable,
      Operation operation,
      Throwable cause) {
    super(buildSafeMessage(statusCode, errorKey, operation), cause);
    this.statusCode = Math.max(0, statusCode);
    this.errorKey = sanitize(errorKey, "unknown_error", 80);
    this.requestId = sanitize(requestId, "", 96);
    this.retryAfterSeconds = Math.max(0L, Math.min(600L, retryAfterSeconds));
    this.retryable = retryable;
    this.operation = operation == null ? Operation.OTHER : operation;
  }

  public int statusCode() {
    return statusCode;
  }

  public String errorKey() {
    return errorKey;
  }

  public String requestId() {
    return requestId;
  }

  public long retryAfterSeconds() {
    return retryAfterSeconds;
  }

  public boolean isRetryable() {
    return retryable;
  }

  public Operation operation() {
    return operation;
  }

  public boolean isUnauthorized() {
    return "unauthorized".equals(errorKey)
        || (statusCode == 401
            && (operation == Operation.FETCH_SOCKET_TOKEN
                || operation == Operation.FETCH_CONNECT_ACCOUNT
                || operation == Operation.FETCH_ACCOUNT
                || operation == Operation.FETCH_BALANCE
                || operation == Operation.FETCH_USAGE
                || operation == Operation.FETCH_CREDITS));
  }

  private static String buildSafeMessage(int statusCode, String errorKey, Operation operation) {
    String key = sanitize(errorKey, "unknown_error", 80);
    Operation safeOperation = operation == null ? Operation.OTHER : operation;
    return "Zhizi API "
        + safeOperation.name()
        + " failed (HTTP "
        + Math.max(0, statusCode)
        + ", key="
        + key
        + ")";
  }

  private static String sanitize(String value, String fallback, int maxLength) {
    String sanitized = value == null ? "" : value.replaceAll("[^A-Za-z0-9_.:-]", "").trim();
    if (sanitized.isEmpty()) {
      return fallback;
    }
    return sanitized.length() <= maxLength ? sanitized : sanitized.substring(0, maxLength);
  }
}
