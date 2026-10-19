#!/usr/bin/env python3
"""Regression tests for the pinned JCEF release-bundle preparation helper."""

from __future__ import annotations

import importlib.util
import hashlib
import io
import json
from pathlib import Path
import tempfile
import unittest
from unittest import mock


SCRIPT_PATH = Path(__file__).with_name("prepare_bundled_jcef.py")
SPEC = importlib.util.spec_from_file_location("prepare_bundled_jcef", SCRIPT_PATH)
if SPEC is None or SPEC.loader is None:
    raise RuntimeError(f"Unable to load {SCRIPT_PATH}")
JCEF = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(JCEF)


def re_jcef_tags(text: str) -> list[str]:
    import re

    return re.findall(r"jcef-[0-9a-f]{7}\+cef-[0-9.]+\+g[0-9a-f]{7}\+chromium-[0-9.]+", text)


class PrepareBundledJcefTest(unittest.TestCase):
    def test_every_release_pin_matches_the_prepared_bundle(self) -> None:
        root = SCRIPT_PATH.parent.parent
        tag = JCEF.JCEF_RELEASE_TAG
        jcefmaven_version = tag.split("+cef-", 1)[1].split("+", 1)[0]
        pom = (root / "pom.xml").read_text(encoding="utf-8")
        self.assertIn(
            "<artifactId>jcefmaven</artifactId>\n\t\t\t<version>" + jcefmaven_version + "</version>",
            pom,
        )
        pinned_files = [
            "src/main/java/featurecat/lizzie/gui/BrowserFrame.java",
            "scripts/package_macos_dmg.sh",
            "scripts/package_windows_exe.sh",
            ".github/workflows/build-macos-amd64-release.yml",
            ".github/workflows/build-macos-arm64-release.yml",
            ".github/workflows/build-windows-release.yml",
        ]
        for relative in pinned_files:
            text = (root / relative).read_text(encoding="utf-8")
            self.assertIn(tag, text, relative)
            for other in set(re_jcef_tags(text)) - {tag}:
                self.fail(f"{relative} pins a different JCEF release: {other}")
        pinned_checksums = {
            "scripts/package_macos_dmg.sh": ["macosx-arm64", "macosx-amd64"],
            "scripts/package_windows_exe.sh": ["windows-amd64"],
            ".github/workflows/build-macos-arm64-release.yml": ["macosx-arm64"],
            ".github/workflows/build-macos-amd64-release.yml": ["macosx-amd64"],
            ".github/workflows/build-windows-release.yml": ["windows-amd64"],
        }
        for relative, platforms in pinned_checksums.items():
            text = (root / relative).read_text(encoding="utf-8")
            for platform in platforms:
                self.assertIn(JCEF.PLATFORM_PACKAGES[platform]["sha256"], text, relative)

    def create_bundle(
        self,
        root: Path,
        package_platform: str = "windows-amd64",
        metadata_platform: str | None = None,
    ) -> None:
        for relative_name in JCEF.PLATFORM_PACKAGES[package_platform]["required"]:
            path = root / relative_name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(b"fixture")
        (root / "build_meta.json").write_text(
            json.dumps(
                {
                    "release_tag": JCEF.JCEF_RELEASE_TAG,
                    "platform": metadata_platform or package_platform,
                }
            ),
            encoding="utf-8",
        )

    def test_validate_bundle_rejects_wrong_platform(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory)
            # Use the non-executable fixture layout here. Some hardened Windows hosts quarantine a
            # directory that imitates the complete Chromium DLL/EXE layout, before the metadata
            # assertion under test can run.
            self.create_bundle(
                root,
                package_platform="macosx-arm64",
                metadata_platform="macosx-amd64",
            )

            with self.assertRaisesRegex(SystemExit, "JCEF platform mismatch"):
                JCEF.validate_bundle(root, "macosx-arm64")

    def test_windows_locale_trim_keeps_supported_languages_only(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory)
            locales = root / "locales"
            locales.mkdir()
            for name in JCEF.SUPPORTED_WINDOWS_LOCALES | {"fr.pak", "de.pak"}:
                (locales / name).write_bytes(name.encode("utf-8"))

            stats = JCEF.trim_optional_locales(root, "windows-amd64")

            self.assertEqual(2, stats["removedFiles"])
            self.assertEqual(len(b"fr.pak") + len(b"de.pak"), stats["removedBytes"])
            self.assertEqual(len(JCEF.SUPPORTED_WINDOWS_LOCALES), stats["retainedFiles"])
            self.assertEqual(
                JCEF.SUPPORTED_WINDOWS_LOCALES,
                {path.name for path in locales.iterdir()},
            )

    def test_macos_locale_trim_keeps_supported_languages_only(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory)
            resources = root / "Chromium Embedded Framework.framework" / "Resources"
            resources.mkdir(parents=True)
            for name in JCEF.SUPPORTED_MACOS_LOCALES | {"de.lproj", "fr.lproj"}:
                locale = resources / name
                locale.mkdir()
                (locale / "locale.pak").write_bytes(name.encode("utf-8"))

            stats = JCEF.trim_optional_locales(root, "macosx-arm64")

            self.assertEqual(2, stats["removedFiles"])
            self.assertEqual(len(b"de.lproj") + len(b"fr.lproj"), stats["removedBytes"])
            self.assertEqual(len(JCEF.SUPPORTED_MACOS_LOCALES), stats["retainedFiles"])
            self.assertEqual(
                JCEF.SUPPORTED_MACOS_LOCALES,
                {path.name for path in resources.iterdir()},
            )

    def test_macos_bundles_are_pinned_for_both_chip_families(self) -> None:
        self.assertEqual(
            "jcef-natives-macosx-arm64",
            JCEF.PLATFORM_PACKAGES["macosx-arm64"]["artifact"],
        )
        self.assertEqual(
            "685b27b3dc7f6899a7d4a3a3d58978c95c36ebd1afd571c7027a66fe6f1252a0",
            JCEF.PLATFORM_PACKAGES["macosx-arm64"]["sha256"],
        )
        self.assertEqual(
            "jcef-natives-macosx-amd64",
            JCEF.PLATFORM_PACKAGES["macosx-amd64"]["artifact"],
        )
        self.assertEqual(
            "0a49f85ebc8f942fae81bc0120950ce83a629696352338f000856ee72580908a",
            JCEF.PLATFORM_PACKAGES["macosx-amd64"]["sha256"],
        )

    def test_validate_macos_bundle_accepts_required_runtime(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory)
            self.create_bundle(root, package_platform="macosx-arm64")

            JCEF.validate_bundle(root, "macosx-arm64")

    def test_download_retries_a_truncated_checksum_mismatch(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            target = Path(temporary_directory) / "jcef.jar"
            complete_payload = b"complete-jcef-fixture"
            expected_sha256 = hashlib.sha256(complete_payload).hexdigest()

            with (
                mock.patch.object(
                    JCEF.urllib.request,
                    "urlopen",
                    side_effect=[
                        io.BytesIO(b"truncated"),
                        io.BytesIO(complete_payload),
                    ],
                ) as urlopen,
                mock.patch.object(JCEF.time, "sleep"),
            ):
                JCEF.download_with_retries(
                    "https://example.invalid/jcef.jar",
                    target,
                    attempts=2,
                    expected_sha256=expected_sha256,
                )

            self.assertEqual(2, urlopen.call_count)
            self.assertEqual(complete_payload, target.read_bytes())
            self.assertFalse(target.with_suffix(".jar.tmp").exists())


if __name__ == "__main__":
    unittest.main()
