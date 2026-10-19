package featurecat.lizzie;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.nio.file.Path;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConfigRetiredShareAccountTest {
  @TempDir Path tempDir;

  @Test
  void plaintextLizzieyzyCnAccountAndShareSettingsAreDropped() throws Exception {
    Config config = ConfigTestHelper.createForTests(tempDir);
    JSONObject ui =
        new JSONObject()
            .put("up-load-user", "player")
            .put("up-load-passwd", "plaintext-password")
            .put("share-label-1", "pro")
            .put("share-label-5", "custom")
            .put("share-public", false)
            .put("share", true)
            .put("coords", true);
    config.uiConfig = ui;

    config.forgetRetiredLizzieyzyCnAccount();

    assertFalse(ui.has("up-load-user"));
    assertFalse(ui.has("up-load-passwd"));
    assertFalse(ui.toString().contains("plaintext-password"));
    assertFalse(ui.has("share-label-1"));
    assertFalse(ui.has("share-label-5"));
    assertFalse(ui.has("share-public"));
    assertFalse(ui.has("share"));
    assertEquals(true, ui.get("coords"), "unrelated settings are kept");
  }
}
