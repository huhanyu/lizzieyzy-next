package featurecat.lizzie.logging;

import featurecat.lizzie.analysis.ReadBoardLoggingProtocol;
import featurecat.lizzie.analysis.SyncDiagnosticsExportSanitizer;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public final class ExportSanitizer {
  public static final String VERSION = "export-1";

  private static final Pattern NICKNAME_PARAMETER =
      Pattern.compile("(?i)\\b(?:nickname|player|userName|username)\\b(?:\\s*[=:]\\s*)([^\\s,;]+)");

  private final PersistenceSanitizer persistence = new PersistenceSanitizer();
  private final SyncDiagnosticsExportSanitizer shareTime = new SyncDiagnosticsExportSanitizer();
  private final Map<String, String> typedAliases = new LinkedHashMap<>();
  private final Map<String, Integer> typedCounts = new LinkedHashMap<>();

  public String sanitize(String text) {
    if (text == null || text.isEmpty()) {
      return "";
    }
    String trimmed = text.trim();
    if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
      try {
        return sanitizeJsonObject(new JSONObject(trimmed)).toString();
      } catch (JSONException ignored) {
        // Untagged text still goes through the shared field sanitizer.
      }
    }
    return sanitizeText(text);
  }

  public String sanitizeText(String text) {
    if (text == null || text.isEmpty()) {
      return "";
    }
    String safe = shareTime.text(persistence.sanitize(text));
    safe = aliasNicknameParameters(safe);
    return applyKnownAliases(safe);
  }

  public JSONObject sanitizeJsonObject(JSONObject value) {
    Object sanitized = sanitizeJsonValue(value, null);
    return sanitized instanceof JSONObject object ? object : new JSONObject();
  }

  public SyncDiagnosticsExportSanitizer shareTime() {
    return shareTime;
  }

  public Map<String, String> aliases() {
    Map<String, String> merged = new LinkedHashMap<>(shareTime.aliases());
    merged.putAll(typedAliases);
    return merged;
  }

  public String alias(String kind, String value) {
    if (value == null || value.isEmpty()) {
      return "";
    }
    String existing = typedAliases.get(value);
    if (existing != null) {
      return existing;
    }
    int index = typedCounts.getOrDefault(kind, 0) + 1;
    typedCounts.put(kind, index);
    String alias = kind + "#" + index;
    typedAliases.put(value, alias);
    return alias;
  }

  private Object sanitizeJsonValue(Object value, String key) {
    if (value instanceof JSONObject object) {
      if (isTagged(object)) {
        return sanitizeTagged(object);
      }
      JSONObject sanitized = new JSONObject();
      for (String child : object.keySet()) {
        if (isSecretKey(child)) {
          continue;
        }
        Object rewritten = sanitizeJsonValue(object.get(child), child);
        if (rewritten != OMIT) {
          sanitized.put(child, rewritten);
        }
      }
      return sanitized;
    }
    if (value instanceof JSONArray array) {
      JSONArray sanitized = new JSONArray();
      for (int i = 0; i < array.length(); i++) {
        Object rewritten = sanitizeJsonValue(array.get(i), key);
        if (rewritten != OMIT) {
          sanitized.put(rewritten);
        }
      }
      return sanitized;
    }
    if (value instanceof String text) {
      return sanitizeUntaggedString(key, text);
    }
    return value;
  }

  private Object sanitizeTagged(JSONObject tagged) {
    String privacy = tagged.optString("privacy", "");
    Object raw = tagged.opt("value");
    if (ReadBoardLoggingProtocol.PRIVACY_SECRET.equals(privacy) || isSecretKey(privacy)) {
      return OMIT;
    }
    JSONObject rewritten = new JSONObject();
    rewritten.put("privacy", privacy);
    if (raw instanceof String text) {
      rewritten.put("value", aliasTaggedString(privacy, text));
    } else {
      rewritten.put("value", raw);
    }
    return rewritten;
  }

  private String aliasTaggedString(String privacy, String text) {
    if (ReadBoardLoggingProtocol.PRIVACY_LOCAL_PATH.equals(privacy)) {
      return alias("path", text);
    }
    if (ReadBoardLoggingProtocol.PRIVACY_LOCAL_URL.equals(privacy)) {
      return alias("url", text);
    }
    if (ReadBoardLoggingProtocol.PRIVACY_USER_TEXT.equals(privacy)) {
      return alias("nickname", text);
    }
    if (ReadBoardLoggingProtocol.PRIVACY_SESSION_ID.equals(privacy)) {
      return alias("session", text);
    }
    return sanitizeText(text);
  }

  private String sanitizeUntaggedString(String key, String text) {
    if (key != null) {
      String normalized = key.toLowerCase(Locale.ROOT);
      if (isSecretKey(normalized)) {
        return "<redacted>";
      }
      if (normalized.contains("session")) {
        return alias("session", text);
      }
      if (normalized.contains("path") || normalized.contains("file") || normalized.contains("dir")) {
        return alias("path", text);
      }
      if (normalized.contains("url")) {
        return alias("url", text);
      }
      if (normalized.contains("nickname") || normalized.equals("player")) {
        return alias("nickname", text);
      }
    }
    return sanitizeText(text);
  }

  private String aliasNicknameParameters(String text) {
    Matcher matcher = NICKNAME_PARAMETER.matcher(text);
    StringBuffer rewritten = new StringBuffer();
    while (matcher.find()) {
      matcher.appendReplacement(
          rewritten, Matcher.quoteReplacement(matcher.group().replace(matcher.group(1), alias("nickname", matcher.group(1)))));
    }
    matcher.appendTail(rewritten);
    return rewritten.toString();
  }

  private String applyKnownAliases(String text) {
    String rewritten = text;
    for (Map.Entry<String, String> alias : typedAliases.entrySet()) {
      if (!alias.getKey().isEmpty()) {
        rewritten = rewritten.replace(alias.getKey(), alias.getValue());
      }
    }
    return rewritten;
  }

  private static boolean isTagged(JSONObject object) {
    return object.has("value") && object.has("privacy");
  }

  private static boolean isSecretKey(String key) {
    if (key == null || key.isEmpty()) {
      return false;
    }
    String normalized = key.toLowerCase(Locale.ROOT).replace("-", "").replace("_", "");
    return normalized.contains("password")
        || normalized.contains("passwd")
        || normalized.contains("token")
        || normalized.contains("secret")
        || normalized.contains("cookie")
        || normalized.contains("authorization")
        || normalized.contains("credential")
        || normalized.contains("machinekey");
  }

  private static final Object OMIT = new Object();
}
