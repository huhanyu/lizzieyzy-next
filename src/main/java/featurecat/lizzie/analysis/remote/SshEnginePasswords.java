package featurecat.lizzie.analysis.remote;

import featurecat.lizzie.logging.LogCategories;
import featurecat.lizzie.util.Utils;
import java.io.IOException;
import java.util.Locale;
import java.util.Optional;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Keeps the passwords of SSH remote engines in the operating system's credential store (macOS
 * Keychain, Windows DPAPI, Linux Secret Service) instead of the configuration file.
 *
 * <p>In memory and in older configuration files a password is the legacy {@link Utils#doEncrypt}
 * text, whose key ships with the source code and therefore protects nothing. Saved passwords are
 * now kept as {@code vault:<account>} references; the configuration only stores the account under
 * {@value #REFERENCE_KEY}. When no credential store is available the legacy form is kept, so a
 * password is never lost.
 */
public final class SshEnginePasswords {
  public static final String REFERENCE_KEY = "passwordRef";
  static final String VAULT_PREFIX = "vault:";
  static final String ENGINE_LIST_KEY = "engine-settings-list";
  static final String ANALYSIS_ENGINE_KEY = "analysis-engine-ssh-info";
  static final String CONTRIBUTE_ENGINE_KEY = "contribute-engine-ssh-info";

  private static final Logger LOG = LoggerFactory.getLogger(LogCategories.REMOTE);

  private SshEnginePasswords() {}

  /**
   * Stores a password typed into an engine settings dialog and returns the value to keep in the
   * engine settings: a credential-store reference, or the legacy form if no store is available.
   */
  public static String remember(String host, String port, String userName, String plaintext) {
    return remember(RemoteComputeConfig.credentialStore(), host, port, userName, plaintext);
  }

  static String remember(
      CredentialStore store, String host, String port, String userName, String plaintext) {
    if (plaintext == null || plaintext.isEmpty()) {
      return "";
    }
    String account = account(host, port, userName);
    if (store != null && store.isAvailable()) {
      try {
        store.write(CredentialStore.Kind.SSH_ENGINE_PASSWORD, account, plaintext);
        return VAULT_PREFIX + account;
      } catch (IOException | RuntimeException e) {
        LOG.warn(
            "Unable to save the SSH engine password for {} in {}; keeping the legacy form",
            account,
            store.backendName(),
            e);
      }
    }
    return Utils.doEncrypt(plaintext);
  }

  /** The plaintext password for a stored value; fails if a saved reference cannot be read. */
  public static String reveal(String stored) throws IOException {
    return reveal(RemoteComputeConfig.credentialStore(), stored);
  }

  static String reveal(CredentialStore store, String stored) throws IOException {
    if (stored == null || stored.isEmpty()) {
      return "";
    }
    if (!stored.startsWith(VAULT_PREFIX)) {
      return decryptLegacy(stored);
    }
    String account = stored.substring(VAULT_PREFIX.length());
    if (store == null || !store.isAvailable()) {
      throw new IOException("The system credential store is unavailable.");
    }
    Optional<String> secret = store.read(CredentialStore.Kind.SSH_ENGINE_PASSWORD, account);
    if (secret.isEmpty()) {
      throw new IOException("No saved SSH password for " + account + " in " + store.backendName());
    }
    return secret.get();
  }

  /** Like {@link #reveal(String)}, but an unreadable password is shown as empty. */
  public static String revealForDisplay(String stored) {
    try {
      return reveal(stored);
    } catch (IOException | RuntimeException e) {
      LOG.warn("Unable to read a saved SSH engine password", e);
      return "";
    }
  }

  /** Reads the stored password value of one SSH engine settings object. */
  public static String readFrom(JSONObject settings) {
    if (settings == null) {
      return "";
    }
    String reference = settings.optString(REFERENCE_KEY, "").trim();
    if (!reference.isEmpty()) {
      return VAULT_PREFIX + reference;
    }
    return settings.optString("password", "");
  }

  /** Writes the stored password value of one SSH engine settings object. */
  public static void writeTo(JSONObject settings, String stored) {
    String value = stored == null ? "" : stored;
    if (value.startsWith(VAULT_PREFIX)) {
      settings.put(REFERENCE_KEY, value.substring(VAULT_PREFIX.length()));
      settings.put("password", "");
    } else {
      settings.remove(REFERENCE_KEY);
      settings.put("password", value);
    }
  }

  /**
   * Moves legacy passwords of every SSH engine in {@code leelazConfig} into the credential store.
   * Returns whether the configuration changed and should be saved.
   */
  public static boolean migrateLegacyPasswords(JSONObject leelazConfig) {
    return migrateLegacyPasswords(leelazConfig, RemoteComputeConfig.credentialStore());
  }

  static boolean migrateLegacyPasswords(JSONObject leelazConfig, CredentialStore store) {
    if (leelazConfig == null || store == null || !store.isAvailable()) {
      return false;
    }
    boolean changed = false;
    JSONArray engines = leelazConfig.optJSONArray(ENGINE_LIST_KEY);
    if (engines != null) {
      for (int i = 0; i < engines.length(); i++) {
        changed |= migrate(engines.optJSONObject(i), store);
      }
    }
    changed |= migrate(leelazConfig.optJSONObject(ANALYSIS_ENGINE_KEY), store);
    changed |= migrate(leelazConfig.optJSONObject(CONTRIBUTE_ENGINE_KEY), store);
    return changed;
  }

  private static boolean migrate(JSONObject settings, CredentialStore store) {
    if (settings == null || !settings.optString(REFERENCE_KEY, "").isBlank()) {
      return false;
    }
    String legacy = settings.optString("password", "");
    if (legacy.isEmpty()) {
      return false;
    }
    String plaintext;
    try {
      plaintext = decryptLegacy(legacy);
    } catch (IOException e) {
      LOG.warn("Skipping an SSH engine password that cannot be decoded", e);
      return false;
    }
    String stored =
        remember(
            store,
            settings.optString("ip", ""),
            settings.optString("port", ""),
            settings.optString("userName", ""),
            plaintext);
    if (!stored.startsWith(VAULT_PREFIX)) {
      return false;
    }
    writeTo(settings, stored);
    LOG.info(
        "Moved a saved SSH engine password into {} ({})",
        store.backendName(),
        stored.substring(VAULT_PREFIX.length()));
    return true;
  }

  private static String decryptLegacy(String legacy) throws IOException {
    try {
      String plaintext = Utils.doDecrypt(legacy);
      if (plaintext == null) {
        throw new IOException("The saved SSH password cannot be decoded.");
      }
      return plaintext;
    } catch (RuntimeException e) {
      throw new IOException("The saved SSH password cannot be decoded.", e);
    }
  }

  /** The credential-store account of a server login, for example {@code go@gpu.example:2222}. */
  static String account(String host, String port, String userName) {
    String normalizedHost = host == null ? "" : host.trim().toLowerCase(Locale.ROOT);
    String normalizedUser = userName == null ? "" : userName.trim();
    String normalizedPort = port == null || port.isBlank() ? "22" : port.trim();
    return normalizedUser + "@" + normalizedHost + ":" + normalizedPort;
  }
}
