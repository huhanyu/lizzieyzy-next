package featurecat.lizzie.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class KataGoAssetCatalogTest {
  @Test
  void pinsKataGo1181AndB11AsTheOnlyBundledDefault() {
    KataGoAssetCatalog catalog = KataGoAssetCatalog.get();
    KataGoAssetCatalog.Model model = catalog.defaultModel();

    assertEquals("1.18.1", catalog.katagoVersion());
    assertEquals("v1.18.1", catalog.katagoReleaseTag());
    assertEquals("b11c768h12nbt3tflrs-fson-silu.bin.gz", model.fileName());
    assertEquals(211_660_960L, model.sizeBytes());
    assertEquals(
        "1881600caab9e9d85a3dd6a019e9b8e7d2c237b5f984e13ed49a8645be3077c6",
        model.sha256());
    assertTrue(model.bundled());
    assertFalse(catalog.model("b10-balanced").bundled());
  }

  @Test
  void keepsWindowsCuda128UnifiedAndLinuxCuda121() {
    KataGoAssetCatalog catalog = KataGoAssetCatalog.get();

    assertEquals("cuda12.8-cudnn9", catalog.asset("windows-nvidia").runtimeProfile());
    assertEquals(64, catalog.asset("windows-nvidia").executableSha256().length());
    assertEquals("cuda12.1-cudnn9", catalog.asset("linux-nvidia").runtimeProfile());
    assertTrue(catalog.asset("windows-nvidia").assetName().contains("cuda12.8"));
    assertTrue(catalog.asset("linux-nvidia").assetName().contains("cuda12.1"));
  }

  @Test
  void exposesExperimentalWindowsBackendsWithTrustedHashes() {
    KataGoAssetCatalog catalog = KataGoAssetCatalog.get();

    for (String id :
        new String[] {
          "windows-directml",
          "windows-openvino",
          "windows-rocm-gfx103x",
          "windows-rocm-gfx110x",
          "windows-rocm-gfx1151",
          "windows-rocm-gfx120x"
        }) {
      KataGoAssetCatalog.Asset asset = catalog.asset(id);
      assertEquals("experimental", asset.releaseTier());
      assertEquals(64, asset.sha256().length());
      assertTrue(catalog.assetDownloadUrl(asset).endsWith(asset.assetName()));
    }
  }
}
