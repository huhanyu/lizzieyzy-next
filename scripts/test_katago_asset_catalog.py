import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

import katago_asset_catalog


class KataGoAssetCatalogTest(unittest.TestCase):
    def test_catalog_is_valid_and_b11_is_default(self):
        catalog = katago_asset_catalog.load_catalog(katago_asset_catalog.DEFAULT_CATALOG)
        default_model = catalog["models"][catalog["defaultModelId"]]

        self.assertEqual("1.18.1", catalog["katagoVersion"])
        self.assertEqual("b11c768h12nbt3tflrs-fson-silu.bin.gz", default_model["fileName"])
        self.assertEqual(211660960, default_model["sizeBytes"])
        self.assertTrue(default_model["bundled"])

    def test_cli_reads_a_scalar_path(self):
        completed = subprocess.run(
            [
                sys.executable,
                str(Path(katago_asset_catalog.__file__)),
                "get",
                "assets.windows-nvidia.runtimeProfile",
            ],
            check=True,
            capture_output=True,
            text=True,
        )

        self.assertEqual("cuda12.8-cudnn9", completed.stdout.strip())

    def test_validation_rejects_unpinned_asset(self):
        catalog = katago_asset_catalog.load_catalog(katago_asset_catalog.DEFAULT_CATALOG)
        catalog["assets"]["windows-cpu"]["sha256"] = "missing"
        with tempfile.TemporaryDirectory() as temp_dir:
            path = Path(temp_dir) / "catalog.json"
            path.write_text(json.dumps(catalog), encoding="utf-8")

            with self.assertRaisesRegex(ValueError, "invalid sha256"):
                katago_asset_catalog.load_catalog(path)

    def test_validation_requires_unified_nvidia_executable_digest(self):
        catalog = katago_asset_catalog.load_catalog(katago_asset_catalog.DEFAULT_CATALOG)
        del catalog["assets"]["windows-nvidia"]["executableSha256"]
        with tempfile.TemporaryDirectory() as temp_dir:
            path = Path(temp_dir) / "catalog.json"
            path.write_text(json.dumps(catalog), encoding="utf-8")

            with self.assertRaisesRegex(ValueError, "requires executableSha256"):
                katago_asset_catalog.load_catalog(path)


if __name__ == "__main__":
    unittest.main()
