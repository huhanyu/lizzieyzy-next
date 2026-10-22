package featurecat.lizzie.util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The manifest the project writes beside each KataGo build it packages or installs
 * (lizzieyzy-next-katago-engine-manifest.txt): where the build came from and the digests to check
 * it by.
 */
final class KataGoEngineManifest {
  private KataGoEngineManifest() {}

  /**
   * The manifest's fields, or null when it is malformed: a line without "key: value", an unknown or
   * repeated key, or a HumanSL companion without its digest.
   */
  static Map<String, String> readStrict(Path manifestPath) throws IOException {
    Map<String, String> values = new LinkedHashMap<>();
    String[] lines =
        Files.readString(manifestPath, StandardCharsets.UTF_8).replace("\r", "").split("\n", -1);
    for (int index = 0; index < lines.length; index++) {
      String line = lines[index];
      if (line.isEmpty() && index == lines.length - 1) {
        continue;
      }
      int separator = line.indexOf(": ");
      if (separator <= 0 || separator == line.length() - 2) {
        return null;
      }
      String key = line.substring(0, separator);
      String value = line.substring(separator + 2);
      if (!isAllowedKey(key) || values.putIfAbsent(key, value) != null) {
        return null;
      }
    }
    boolean hasCompanion = values.containsKey("HumanSL companion");
    if (hasCompanion != values.containsKey("HumanSL companion SHA-256")) {
      return null;
    }
    return values;
  }

  /**
   * Whether the manifest describes one of the project's static-zlib builds for {@code backend}
   * whose executable has the given digest. Unlike the catalog check for the current release, any
   * earlier project build counts: after an update of the program alone, the installed engine is an
   * earlier build of the same kind and still needs no zlib DLL.
   */
  static boolean describesProjectStaticZlibBuild(
      Map<String, String> manifest, String backend, String executableSha256) {
    String recorded = manifest == null ? null : manifest.get("Executable SHA-256");
    return recorded != null
        && "2".equals(manifest.get("Manifest schema"))
        && "project-source-build".equals(manifest.get("Origin"))
        && "static".equals(manifest.get("Zlib linkage"))
        && backend != null
        && backend.equals(manifest.get("Backend"))
        && recorded.equalsIgnoreCase(executableSha256);
  }

  private static boolean isAllowedKey(String key) {
    return switch (key) {
      case "Manifest schema",
          "KataGo release",
          "Asset ID",
          "Asset",
          "Asset SHA-256",
          "Executable SHA-256",
          "Backend",
          "Origin",
          "Source commit",
          "Zlib linkage",
          "HumanSL companion",
          "HumanSL companion SHA-256" ->
          true;
      default -> false;
    };
  }
}
