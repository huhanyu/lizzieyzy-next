package featurecat.lizzie.analysis.remote;

import featurecat.lizzie.util.NetworkProxy;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ZhiziApiClient {
  public static final URI DEFAULT_BASE_URI = URI.create("https://www.zhizigo.com");
  private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(20);
  private static final long DEFAULT_CODE_COOLDOWN_SECONDS = 60L;
  private static final Logger LOGGER = LoggerFactory.getLogger(ZhiziApiClient.class);

  private final URI baseUri;
  private final HttpClient httpClient;

  public ZhiziApiClient() throws IOException {
    this(
        DEFAULT_BASE_URI,
        NetworkProxy.configure(HttpClient.newBuilder()).connectTimeout(REQUEST_TIMEOUT).build());
  }

  public ZhiziApiClient(URI baseUri, HttpClient httpClient) {
    this.baseUri = baseUri;
    this.httpClient = httpClient;
  }

  public String login(String identifier, String password) throws IOException, InterruptedException {
    JSONObject body = identifierBody(identifier);
    body.put("password", password == null ? "" : password);
    return extractToken(
        post(
            "/api/cluster/account/login",
            body,
            "",
            ZhiziApiException.Operation.LOGIN,
            false),
        ZhiziApiException.Operation.LOGIN);
  }

  public String fastLogin(String identifier, String verificationCode)
      throws IOException, InterruptedException {
    JSONObject body = identifierBody(identifier);
    body.put("verificationCode", verificationCode == null ? "" : verificationCode);
    return extractToken(
        post(
            "/api/cluster/account/fast-login",
            body,
            "",
            ZhiziApiException.Operation.FAST_LOGIN,
            false),
        ZhiziApiException.Operation.FAST_LOGIN);
  }

  public void sendCode(String identifier) throws IOException, InterruptedException {
    sendCode(identifier, VerificationPurpose.FAST_LOGIN);
  }

  public CodeDelivery sendCode(String identifier, VerificationPurpose purpose)
      throws IOException, InterruptedException {
    JSONObject body = identifierBody(identifier);
    body.put("type", (purpose == null ? VerificationPurpose.FAST_LOGIN : purpose).apiValue);
    JsonResponse response =
        postResponse(
            "/api/cluster/account/send-code",
            body,
            "",
            ZhiziApiException.Operation.SEND_CODE,
            true);
    long cooldown = response.retryAfterSeconds;
    return new CodeDelivery(cooldown > 0 ? cooldown : DEFAULT_CODE_COOLDOWN_SECONDS);
  }

  public String resetPassword(String identifier, String verificationCode, String newPassword)
      throws IOException, InterruptedException {
    if (newPassword == null || newPassword.length() < 8) {
      throw new ZhiziApiException(
          400,
          "password_too_short",
          "",
          0,
          false,
          ZhiziApiException.Operation.RESET_PASSWORD);
    }
    JSONObject body = identifierBody(identifier);
    body.put("verificationCode", verificationCode == null ? "" : verificationCode);
    body.put("password", newPassword);
    return extractToken(
        post(
            "/api/cluster/account/reset-password",
            body,
            "",
            ZhiziApiException.Operation.RESET_PASSWORD,
            false),
        ZhiziApiException.Operation.RESET_PASSWORD);
  }

  public SocketToken fetchSocketioToken(String accountToken, String args)
      throws IOException, InterruptedException {
    JSONObject body = new JSONObject();
    body.put(
        "args",
        args == null || args.trim().isEmpty() ? RemoteComputeConfig.DEFAULT_ZHIZI_ARGS : args);
    JSONObject response =
        post(
            "/api/cluster/account/fetch-socketio-token",
            body,
            accountToken,
            ZhiziApiException.Operation.FETCH_SOCKET_TOKEN,
            false);
    String token = response.optString("token", "");
    String socketIOURL = response.optString("socketIOURL", "");
    if (token.isEmpty() || socketIOURL.isEmpty()) {
      throw invalidResponse(ZhiziApiException.Operation.FETCH_SOCKET_TOKEN);
    }
    return new SocketToken(token, socketIOURL);
  }

  public ConnectAccount fetchConnectAccount(String accountToken)
      throws IOException, InterruptedException {
    JSONObject response =
        get(
            "/api/cluster/account/connectAccount/fetch",
            accountToken,
            ZhiziApiException.Operation.FETCH_CONNECT_ACCOUNT);
    String username = response.optString("connectUsername", "").trim();
    String password = response.optString("connectPassword", "");
    if (username.isEmpty() || password.isEmpty()) {
      throw invalidResponse(ZhiziApiException.Operation.FETCH_CONNECT_ACCOUNT);
    }
    return new ConnectAccount(username, password);
  }

  private JSONObject post(
      String path,
      JSONObject body,
      String bearerToken,
      ZhiziApiException.Operation operation,
      boolean allowEmpty)
      throws IOException, InterruptedException {
    return postResponse(path, body, bearerToken, operation, allowEmpty).body;
  }

  private JsonResponse postResponse(
      String path,
      JSONObject body,
      String bearerToken,
      ZhiziApiException.Operation operation,
      boolean allowEmpty)
      throws IOException, InterruptedException {
    HttpRequest.Builder builder =
        HttpRequest.newBuilder(baseUri.resolve(path))
            .timeout(REQUEST_TIMEOUT)
            .header("Content-Type", "application/json")
            .version(HttpClient.Version.HTTP_1_1)
            .header("Accept", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body.toString()));
    addAuthorization(builder, bearerToken);
    return sendJson(builder.build(), operation, allowEmpty);
  }

  private JSONObject get(
      String path, String bearerToken, ZhiziApiException.Operation operation)
      throws IOException, InterruptedException {
    HttpRequest.Builder builder =
        HttpRequest.newBuilder(baseUri.resolve(path))
            .timeout(REQUEST_TIMEOUT)
            .header("Accept", "application/json")
            .version(HttpClient.Version.HTTP_1_1)
            .GET();
    addAuthorization(builder, bearerToken);
    return sendJson(builder.build(), operation, false).body;
  }

  private JsonResponse sendJson(
      HttpRequest request, ZhiziApiException.Operation operation, boolean allowEmpty)
      throws IOException, InterruptedException {
    HttpResponse<String> response;
    try {
      response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    } catch (IOException firstFailure) {
      if (!operation.isIdempotent()) {
        throw networkException(operation, firstFailure);
      }
      Thread.sleep(650L);
      try {
        response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      } catch (IOException retryFailure) {
        throw networkException(operation, retryFailure);
      }
    }

    int status = response.statusCode();
    String responseBody = response.body() == null ? "" : response.body();
    long retryAfter = parseRetryAfter(response);
    String requestId = requestId(response);
    if (status < 200 || status >= 300) {
      String errorKey = errorKey(responseBody, status, operation);
      boolean retryable =
          operation.isIdempotent()
              && (status == 408 || status == 425 || status == 429 || status >= 500);
      LOGGER.warn(
          "Zhizi request failed: operation={}, status={}, key={}, requestId={}",
          operation,
          status,
          errorKey,
          requestId);
      throw new ZhiziApiException(
          status, errorKey, requestId, retryAfter, retryable, operation);
    }
    if (responseBody.trim().isEmpty()) {
      if (!allowEmpty) {
        throw invalidResponse(operation);
      }
      return new JsonResponse(new JSONObject(), retryAfter);
    }
    try {
      return new JsonResponse(new JSONObject(responseBody), retryAfter);
    } catch (JSONException invalidJson) {
      throw new ZhiziApiException(
          status, "invalid_response", requestId, retryAfter, false, operation, invalidJson);
    }
  }

  private static void addAuthorization(HttpRequest.Builder builder, String bearerToken) {
    if (bearerToken != null && !bearerToken.trim().isEmpty()) {
      builder.header("Authorization", "Bearer " + bearerToken.trim());
    }
  }

  private static JSONObject identifierBody(String identifier) {
    String trimmed = identifier == null ? "" : identifier.trim();
    JSONObject body = new JSONObject();
    if (trimmed.contains("@")) {
      body.put("email", trimmed);
    } else {
      body.put("phone", trimmed);
    }
    return body;
  }

  private static String extractToken(JSONObject response, ZhiziApiException.Operation operation)
      throws ZhiziApiException {
    String token = response.optString("token", "");
    if (token.isEmpty()) {
      throw invalidResponse(operation);
    }
    return token;
  }

  private static String errorKey(
      String responseBody, int status, ZhiziApiException.Operation operation) {
    if (responseBody != null && !responseBody.isBlank()) {
      try {
        String key = new JSONObject(responseBody).optString("key", "").trim();
        if (!key.isEmpty()) {
          return key;
        }
      } catch (JSONException ignored) {
        // Some official failures, notably 401, are plain text rather than JSON.
      }
    }
    if (status != 401) {
      return "unknown_error";
    }
    if (operation == ZhiziApiException.Operation.LOGIN) {
      return "invalid_credentials";
    }
    if (operation == ZhiziApiException.Operation.FAST_LOGIN
        || operation == ZhiziApiException.Operation.RESET_PASSWORD) {
      return "invalid_verification_code";
    }
    return "unauthorized";
  }

  private static String requestId(HttpResponse<?> response) {
    for (String name : new String[] {"x-request-id", "x-correlation-id", "traceparent"}) {
      String value = response.headers().firstValue(name).orElse("").trim();
      if (!value.isEmpty()) {
        return value;
      }
    }
    return "";
  }

  private static long parseRetryAfter(HttpResponse<?> response) {
    String value = response.headers().firstValue("Retry-After").orElse("").trim();
    if (value.isEmpty()) {
      return 0L;
    }
    try {
      return Math.max(0L, Math.min(600L, Long.parseLong(value)));
    } catch (NumberFormatException ignored) {
      try {
        long seconds =
            Duration.between(
                    ZonedDateTime.now(),
                    ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME))
                .getSeconds();
        return Math.max(0L, Math.min(600L, seconds));
      } catch (DateTimeParseException ignoredDate) {
        return 0L;
      }
    }
  }

  private static ZhiziApiException invalidResponse(ZhiziApiException.Operation operation) {
    return new ZhiziApiException(200, "invalid_response", "", 0, false, operation);
  }

  private static ZhiziApiException networkException(
      ZhiziApiException.Operation operation, IOException cause) {
    return new ZhiziApiException(
        0, "network_error", "", 0, operation.isIdempotent(), operation, cause);
  }

  public enum VerificationPurpose {
    FAST_LOGIN("fast_login"),
    RESET_PASSWORD("reset_password");

    private final String apiValue;

    VerificationPurpose(String apiValue) {
      this.apiValue = apiValue;
    }
  }

  public static final class CodeDelivery {
    public final long retryAfterSeconds;

    public CodeDelivery(long retryAfterSeconds) {
      this.retryAfterSeconds = Math.max(0L, Math.min(600L, retryAfterSeconds));
    }
  }

  public static final class SocketToken {
    public final String token;
    public final String socketIOURL;

    public SocketToken(String token, String socketIOURL) {
      this.token = token;
      this.socketIOURL = socketIOURL;
    }
  }

  public static final class ConnectAccount {
    public final String username;
    public final String password;

    public ConnectAccount(String username, String password) {
      this.username = username == null ? "" : username;
      this.password = password == null ? "" : password;
    }
  }

  private static final class JsonResponse {
    private final JSONObject body;
    private final long retryAfterSeconds;

    private JsonResponse(JSONObject body, long retryAfterSeconds) {
      this.body = body;
      this.retryAfterSeconds = retryAfterSeconds;
    }
  }
}
