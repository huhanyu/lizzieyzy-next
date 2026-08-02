package featurecat.lizzie.analysis;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SyncDiagnosticsExportSanitizerTest {
  @Test
  void redactsRemoteComputeCredentialsInCommonDiagnosticFormats() {
    String sanitized =
        new SyncDiagnosticsExportSanitizer()
            .text(
                "{\"password\":\"plain-password\",\"connectPassword\":\"socket-password\","
                    + "\"zhizi-account-token\":\"account-token-value\","
                    + "\"zz-socketio-token\":\"socket-token-value\"} "
                    + "Authorization: Bearer bearer-token-value password=query-password");

    for (String secret :
        new String[] {
          "plain-password",
          "socket-password",
          "account-token-value",
          "socket-token-value",
          "bearer-token-value",
          "query-password"
        }) {
      assertFalse(sanitized.contains(secret), secret);
    }
    assertTrue(sanitized.contains("<redacted-credential>"));
  }
}
