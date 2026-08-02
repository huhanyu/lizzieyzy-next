package featurecat.lizzie.analysis.remote;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ZhiziApiClientTest {
  private HttpServer server;
  private String lastPath;
  private String lastMethod;
  private String lastAuthorization;
  private JSONObject lastBody;
  private int responseStatus;
  private String responseBody;
  private final Map<String, String> responseHeaders = new LinkedHashMap<>();
  private int requestCount;

  @BeforeEach
  void setUp() throws IOException {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/", this::handle);
    server.start();
    responseStatus = 200;
    responseBody = null;
    responseHeaders.clear();
    requestCount = 0;
  }

  @AfterEach
  void tearDown() {
    if (server != null) {
      server.stop(0);
    }
  }

  @Test
  void passwordLoginUsesEmailBodyAndReturnsToken() throws Exception {
    ZhiziApiClient client = client();

    String token = client.login("player@example.com", "secret");

    assertEquals("account-token", token);
    assertEquals("/api/cluster/account/login", lastPath);
    assertEquals("player@example.com", lastBody.getString("email"));
    assertEquals("secret", lastBody.getString("password"));
  }

  @Test
  void fastLoginUsesPhoneBodyAndReturnsToken() throws Exception {
    ZhiziApiClient client = client();

    String token = client.fastLogin("13800138000", "123456");

    assertEquals("account-token", token);
    assertEquals("/api/cluster/account/fast-login", lastPath);
    assertEquals("13800138000", lastBody.getString("phone"));
    assertEquals("123456", lastBody.getString("verificationCode"));
  }

  @Test
  void fetchSocketTokenSendsBearerTokenAndArgs() throws Exception {
    ZhiziApiClient client = client();

    ZhiziApiClient.SocketToken token =
        client.fetchSocketioToken("account-token", RemoteComputeConfig.FASTER_ZHIZI_ARGS);

    assertEquals("socket-token", token.token);
    assertEquals("https://socket.example", token.socketIOURL);
    assertEquals("/api/cluster/account/fetch-socketio-token", lastPath);
    assertEquals("Bearer account-token", lastAuthorization);
    assertTrue(lastBody.getString("args").contains("--gpu-type 3x"));
  }

  @Test
  void fetchSocketTokenCanUseVipShareGpuType() throws Exception {
    ZhiziApiClient client = client();

    client.fetchSocketioToken("account-token", RemoteComputeConfig.VIP_ZHIZI_ARGS);

    assertEquals("/api/cluster/account/fetch-socketio-token", lastPath);
    assertTrue(lastBody.getString("args").contains("--gpu-type vip-share"));
  }

  @Test
  void fetchConnectAccountUsesBearerGetAndReturnsTransientCredentials() throws Exception {
    ZhiziApiClient client = client();

    ZhiziApiClient.ConnectAccount account = client.fetchConnectAccount("account-token");

    assertEquals("GET", lastMethod);
    assertEquals("/api/cluster/account/connectAccount/fetch", lastPath);
    assertEquals("Bearer account-token", lastAuthorization);
    assertEquals("zz-player@example.com", account.username);
    assertEquals("temporary-password", account.password);
  }

  @Test
  void sendCodeUsesOfficialPurposeAndHonorsRetryAfter() throws Exception {
    responseBody = "";
    responseHeaders.put("Retry-After", "45");

    ZhiziApiClient.CodeDelivery delivery =
        client().sendCode("13800138000", ZhiziApiClient.VerificationPurpose.RESET_PASSWORD);

    assertEquals("/api/cluster/account/send-code", lastPath);
    assertEquals("reset_password", lastBody.getString("type"));
    assertEquals(45L, delivery.retryAfterSeconds);
  }

  @Test
  void resetPasswordRotatesTokenAndValidatesMinimumLength() throws Exception {
    String token = client().resetPassword("player@example.com", "654321", "new-secret");

    assertEquals("account-token", token);
    assertEquals("/api/cluster/account/reset-password", lastPath);
    assertEquals("654321", lastBody.getString("verificationCode"));
    assertEquals("new-secret", lastBody.getString("password"));

    ZhiziApiException tooShort =
        assertThrows(
            ZhiziApiException.class,
            () -> client().resetPassword("player@example.com", "654321", "short"));
    assertEquals("password_too_short", tooShort.errorKey());
    assertEquals(1, requestCount, "local validation must not send a second request");
  }

  @Test
  void jsonErrorIsStructuredWithoutLeakingRawResponse() {
    responseStatus = 500;
    responseBody =
        "{\"statusCode\":500,\"key\":\"send_code_error\",\"secret\":\"do-not-leak\"}";
    responseHeaders.put("X-Request-Id", "request-42");

    ZhiziApiException failure =
        assertThrows(
            ZhiziApiException.class,
            () ->
                client()
                    .sendCode(
                        "13800138000", ZhiziApiClient.VerificationPurpose.FAST_LOGIN));

    assertEquals(500, failure.statusCode());
    assertEquals("send_code_error", failure.errorKey());
    assertEquals("request-42", failure.requestId());
    assertFalse(failure.isRetryable(), "send-code is a side-effecting request");
    assertFalse(failure.getMessage().contains("do-not-leak"));
    assertFalse(failure.getMessage().contains("statusCode"));
  }

  @Test
  void plainTextUnauthorizedIsClassifiedByOperation() {
    responseStatus = 401;
    responseBody = "Not Authorized: account details must not be echoed";

    ZhiziApiException loginFailure =
        assertThrows(
            ZhiziApiException.class, () -> client().login("player@example.com", "bad-password"));
    assertEquals("invalid_credentials", loginFailure.errorKey());
    assertFalse(loginFailure.isUnauthorized(), "bad credentials are not an expired saved session");
    assertFalse(loginFailure.getMessage().contains("account details"));

    ZhiziApiException tokenFailure =
        assertThrows(
            ZhiziApiException.class,
            () -> client().fetchSocketioToken("expired-token", "--gpu-type 1x"));
    assertEquals("unauthorized", tokenFailure.errorKey());
    assertTrue(tokenFailure.isUnauthorized());
  }

  @Test
  void unknownErrorKeyAndEmptyLoginResponseRemainSafe() {
    responseStatus = 409;
    responseBody = "{\"key\":\"future_server_error\"}";
    ZhiziApiException unknown =
        assertThrows(
            ZhiziApiException.class, () -> client().fastLogin("13800138000", "123456"));
    assertEquals("future_server_error", unknown.errorKey());
    assertFalse(unknown.isRetryable());

    responseStatus = 200;
    responseBody = "";
    ZhiziApiException empty =
        assertThrows(
            ZhiziApiException.class, () -> client().login("player@example.com", "password"));
    assertEquals("invalid_response", empty.errorKey());
  }

  @Test
  void retryAfterIsClampedToOfficialCodeLifetime() {
    responseStatus = 429;
    responseBody = "{\"key\":\"fast_login_too_frequent\"}";
    responseHeaders.put("Retry-After", "3600");

    ZhiziApiException failure =
        assertThrows(
            ZhiziApiException.class,
            () ->
                client()
                    .sendCode(
                        "13800138000", ZhiziApiClient.VerificationPurpose.FAST_LOGIN));

    assertEquals(600L, failure.retryAfterSeconds());
    assertFalse(failure.isRetryable());
    assertEquals(1, requestCount, "the client must not blindly retry send-code");
  }

  private ZhiziApiClient client() {
    URI baseUri = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
    return new ZhiziApiClient(baseUri, HttpClient.newHttpClient());
  }

  private void handle(HttpExchange exchange) throws IOException {
    requestCount++;
    lastPath = exchange.getRequestURI().getPath();
    lastMethod = exchange.getRequestMethod();
    lastAuthorization = exchange.getRequestHeaders().getFirst("Authorization");
    String request = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    lastBody = request.isBlank() ? new JSONObject() : new JSONObject(request);
    String payload = responseBody;
    if (payload == null) {
      JSONObject response = new JSONObject();
      if (lastPath.endsWith("/fetch-socketio-token")) {
        response.put("token", "socket-token");
        response.put("socketIOURL", "https://socket.example");
      } else if (lastPath.endsWith("/connectAccount/fetch")) {
        response.put("connectUsername", "zz-player@example.com");
        response.put("connectPassword", "temporary-password");
      } else if (lastPath.endsWith("/send-code")) {
        payload = "";
      } else {
        response.put("token", "account-token");
      }
      if (payload == null) {
        payload = response.toString();
      }
    }
    byte[] bytes = payload.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().add("Content-Type", "application/json");
    responseHeaders.forEach((name, value) -> exchange.getResponseHeaders().add(name, value));
    exchange.sendResponseHeaders(responseStatus, bytes.length);
    try (OutputStream out = exchange.getResponseBody()) {
      out.write(bytes);
    }
  }
}
