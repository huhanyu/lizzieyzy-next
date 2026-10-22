package featurecat.lizzie.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class KataGoEngineManifestTest {
  private static final String DIGEST = "ab".repeat(32);
  private static final String EARLIER_BUILD =
      "Manifest schema: 2\n"
          + "KataGo release: next-2026-09-01.1\n"
          + "Asset ID: windows-nvidia\n"
          + "Asset: katago-windows-nvidia.zip\n"
          + "Asset SHA-256: "
          + "00".repeat(32)
          + "\nExecutable SHA-256: "
          + DIGEST
          + "\nBackend: nvidia\n"
          + "Origin: project-source-build\n"
          + "Source commit: "
          + "1".repeat(40)
          + "\nZlib linkage: static\n";

  @TempDir Path folder;

  @Test
  void anEarlierProjectBuildWithItsDigestIsAStaticZlibBuild() throws Exception {
    Map<String, String> manifest = read(EARLIER_BUILD);

    assertTrue(
        KataGoEngineManifest.describesProjectStaticZlibBuild(
            manifest, "nvidia", DIGEST.toUpperCase()));
  }

  @Test
  void anotherOriginLinkageBackendOrDigestIsNot() throws Exception {
    assertFalse(described(EARLIER_BUILD.replace("project-source-build", "official-release")));
    assertFalse(described(EARLIER_BUILD.replace("Zlib linkage: static", "Zlib linkage: dynamic")));
    assertFalse(
        KataGoEngineManifest.describesProjectStaticZlibBuild(
            read(EARLIER_BUILD), "nvidia-tensorrt", DIGEST));
    assertFalse(
        KataGoEngineManifest.describesProjectStaticZlibBuild(
            read(EARLIER_BUILD), "nvidia", "cd".repeat(32)));
    assertFalse(KataGoEngineManifest.describesProjectStaticZlibBuild(null, "nvidia", DIGEST));
  }

  @Test
  void aMalformedManifestReadsAsNull() throws Exception {
    assertNull(read(EARLIER_BUILD + "Origin: project-source-build\n"), "a repeated key");
    assertNull(read(EARLIER_BUILD + "Signed by: someone\n"), "an unknown key");
    assertNull(read(EARLIER_BUILD + "HumanSL companion: katago-humansl.exe\n"), "no digest");
    assertNull(read("Manifest schema 2\n"), "no separator");
    assertEquals("nvidia", read(EARLIER_BUILD.replace("\n", "\r\n")).get("Backend"));
  }

  private boolean described(String text) throws Exception {
    return KataGoEngineManifest.describesProjectStaticZlibBuild(read(text), "nvidia", DIGEST);
  }

  private Map<String, String> read(String text) throws Exception {
    Path manifest = folder.resolve("lizzieyzy-next-katago-engine-manifest.txt");
    Files.writeString(manifest, text);
    return KataGoEngineManifest.readStrict(manifest);
  }
}
