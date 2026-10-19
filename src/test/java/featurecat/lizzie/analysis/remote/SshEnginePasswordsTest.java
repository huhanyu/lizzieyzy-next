package featurecat.lizzie.analysis.remote;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import featurecat.lizzie.util.Utils;
import java.io.IOException;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

class SshEnginePasswordsTest {
  @Test
  void rememberedPasswordLivesInTheCredentialStoreNotInTheSettings() throws IOException {
    MemoryStore store = new MemoryStore(true);

    String stored = SshEnginePasswords.remember(store, "GPU.example", "2222", "go", "s3cret");

    assertEquals("vault:go@gpu.example:2222", stored);
    assertEquals("s3cret", store.secret("go@gpu.example:2222"));
    assertEquals("s3cret", SshEnginePasswords.reveal(store, stored));

    JSONObject settings = new JSONObject();
    SshEnginePasswords.writeTo(settings, stored);
    assertEquals("go@gpu.example:2222", settings.getString(SshEnginePasswords.REFERENCE_KEY));
    assertEquals("", settings.getString("password"));
    assertFalse(settings.toString().contains("s3cret"));
    assertEquals(stored, SshEnginePasswords.readFrom(settings));
  }

  @Test
  void withoutACredentialStoreThePasswordIsKeptInTheLegacyForm() throws IOException {
    MemoryStore store = new MemoryStore(false);

    String stored = SshEnginePasswords.remember(store, "gpu.example", "", "go", "s3cret");

    assertEquals(Utils.doEncrypt("s3cret"), stored);
    assertEquals("s3cret", SshEnginePasswords.reveal(store, stored));
    JSONObject settings = new JSONObject().put(SshEnginePasswords.REFERENCE_KEY, "stale");
    SshEnginePasswords.writeTo(settings, stored);
    assertFalse(settings.has(SshEnginePasswords.REFERENCE_KEY));
    assertEquals(stored, settings.getString("password"));
  }

  @Test
  void emptyPasswordStaysEmpty() throws IOException {
    MemoryStore store = new MemoryStore(true);
    assertEquals("", SshEnginePasswords.remember(store, "h", "22", "u", ""));
    assertEquals("", SshEnginePasswords.reveal(store, ""));
    assertEquals("", SshEnginePasswords.readFrom(new JSONObject()));
    assertTrue(store.isEmpty());
  }

  @Test
  void missingSavedPasswordIsAnErrorRatherThanAnEmptyLogin() {
    MemoryStore store = new MemoryStore(true);
    assertThrows(
        IOException.class, () -> SshEnginePasswords.reveal(store, "vault:go@gpu.example:22"));
    assertThrows(
        IOException.class,
        () -> SshEnginePasswords.reveal(new MemoryStore(false), "vault:go@gpu.example:22"));
  }

  @Test
  void migrationMovesEveryLegacyPasswordOutOfTheConfiguration() throws IOException {
    MemoryStore store = new MemoryStore(true);
    JSONObject leelaz = new JSONObject();
    leelaz.put(
        SshEnginePasswords.ENGINE_LIST_KEY,
        new JSONArray()
            .put(sshEngine("10.0.0.5", "22", "katago", Utils.doEncrypt("first")))
            .put(new JSONObject().put("command", "katago gtp").put("password", ""))
            .put(sshEngine("gpu.example", "2222", "go", Utils.doEncrypt("second"))));
    leelaz.put(
        SshEnginePasswords.ANALYSIS_ENGINE_KEY,
        sshEngine("analysis.example", "", "go", Utils.doEncrypt("third")));
    leelaz.put(
        SshEnginePasswords.CONTRIBUTE_ENGINE_KEY,
        sshEngine("contribute.example", "22", "go", Utils.doEncrypt("fourth")));

    assertTrue(SshEnginePasswords.migrateLegacyPasswords(leelaz, store));

    for (String plaintext : new String[] {"first", "second", "third", "fourth"}) {
      assertFalse(leelaz.toString().contains(Utils.doEncrypt(plaintext)), plaintext);
    }
    JSONObject first = leelaz.getJSONArray(SshEnginePasswords.ENGINE_LIST_KEY).getJSONObject(0);
    assertEquals("first", SshEnginePasswords.reveal(store, SshEnginePasswords.readFrom(first)));
    JSONObject analysis = leelaz.getJSONObject(SshEnginePasswords.ANALYSIS_ENGINE_KEY);
    assertEquals("go@analysis.example:22", analysis.getString(SshEnginePasswords.REFERENCE_KEY));
    assertEquals("fourth", store.secret("go@contribute.example:22"));
    assertFalse(
        leelaz
            .getJSONArray(SshEnginePasswords.ENGINE_LIST_KEY)
            .getJSONObject(1)
            .has(SshEnginePasswords.REFERENCE_KEY),
        "engines without a password are left alone");

    assertFalse(SshEnginePasswords.migrateLegacyPasswords(leelaz, store), "migration is one-time");
  }

  @Test
  void migrationKeepsLegacyPasswordsWhenNoCredentialStoreIsAvailable() {
    String legacy = Utils.doEncrypt("first");
    JSONObject leelaz =
        new JSONObject()
            .put(
                SshEnginePasswords.ENGINE_LIST_KEY,
                new JSONArray().put(sshEngine("10.0.0.5", "22", "katago", legacy)));

    assertFalse(SshEnginePasswords.migrateLegacyPasswords(leelaz, new MemoryStore(false)));
    assertEquals(
        legacy,
        leelaz.getJSONArray(SshEnginePasswords.ENGINE_LIST_KEY).getJSONObject(0).get("password"));
  }

  @Test
  void undecodableLegacyValueIsSkipped() {
    JSONObject leelaz =
        new JSONObject()
            .put(
                SshEnginePasswords.ANALYSIS_ENGINE_KEY,
                sshEngine("10.0.0.5", "22", "katago", "not-base64-aes"));
    MemoryStore store = new MemoryStore(true);
    assertFalse(SshEnginePasswords.migrateLegacyPasswords(leelaz, store));
    assertTrue(store.isEmpty());
  }

  private static JSONObject sshEngine(String ip, String port, String user, String password) {
    return new JSONObject()
        .put("useJavaSSH", true)
        .put("ip", ip)
        .put("port", port)
        .put("userName", user)
        .put("password", password);
  }

  private static final class MemoryStore implements CredentialStore {
    private final boolean available;
    private final Map<Kind, Map<String, String>> secrets = new EnumMap<>(Kind.class);

    private MemoryStore(boolean available) {
      this.available = available;
    }

    String secret(String account) {
      return secrets.getOrDefault(Kind.SSH_ENGINE_PASSWORD, Map.of()).get(account);
    }

    boolean isEmpty() {
      return secrets.values().stream().allMatch(Map::isEmpty);
    }

    @Override
    public String backendName() {
      return "memory";
    }

    @Override
    public boolean isAvailable() {
      return available;
    }

    @Override
    public Optional<String> read(Kind kind, String account) {
      return Optional.ofNullable(secrets.getOrDefault(kind, Map.of()).get(account));
    }

    @Override
    public void write(Kind kind, String account, String secret) throws IOException {
      if (!available) {
        throw new IOException("unavailable");
      }
      secrets.computeIfAbsent(kind, ignored -> new HashMap<>()).put(account, secret);
    }

    @Override
    public void delete(Kind kind, String account) {
      Map<String, String> values = secrets.get(kind);
      if (values != null) {
        values.remove(account);
      }
    }
  }
}
