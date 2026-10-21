package featurecat.lizzie;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConfigBottomToolbarDefaultsTest {
  @TempDir Path tempDir;

  @Test
  void syncButtonIsOffTheBottomBarUntilChosen() throws Exception {
    Config config = ConfigTestHelper.createBootstrapped(workDir("fresh"));

    assertFalse(config.liveButton, "Sync has its own window menu");
  }

  @Test
  void syncButtonStaysForUsersWhoTurnedItOn() throws Exception {
    Path workDir = workDir("chosen");
    JSONObject stored = new JSONObject().put("ui", new JSONObject().put("liveButton", true));
    Files.writeString(workDir.resolve("config.txt"), stored.toString(2), StandardCharsets.UTF_8);

    Config config = ConfigTestHelper.createBootstrapped(workDir);

    assertTrue(config.liveButton);
  }

  private Path workDir(String name) throws Exception {
    Path workDir = Files.createDirectories(tempDir.resolve(name));
    Files.createDirectories(workDir.resolve("save"));
    return workDir;
  }
}
