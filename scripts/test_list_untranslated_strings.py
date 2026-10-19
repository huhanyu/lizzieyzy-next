#!/usr/bin/env python3
"""Tests for the untranslated-string report and its baseline check."""

from __future__ import annotations

import contextlib
import importlib.util
import io
import json
from pathlib import Path
import tempfile
import unittest

SCRIPT_PATH = Path(__file__).with_name("list_untranslated_strings.py")
SPEC = importlib.util.spec_from_file_location("list_untranslated_strings", SCRIPT_PATH)
if SPEC is None or SPEC.loader is None:
    raise RuntimeError(f"Unable to load {SCRIPT_PATH}")
UNTRANSLATED = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(UNTRANSLATED)


class UntranslatedStringsTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temporary = tempfile.TemporaryDirectory()
        self.root = Path(self.temporary.name)
        self.l10n = self.root / "src/main/resources/l10n"
        self.l10n.mkdir(parents=True)
        (self.root / "scripts").mkdir()
        self.write("DisplayStrings.properties", "A.title=Open log folder\nA.url=URL\nMenu.introduction.fileName=readme_en.pdf\n")
        self.write("DisplayStrings_en_US.properties", "A.title=Open log folder\nA.url=URL\nMenu.introduction.fileName=readme_en.pdf\n")
        self.write(
            "DisplayStrings_ja_JP.properties",
            "A.title=\\u30ed\\u30b0\\u30d5\\u30a9\\u30eb\\u30c0\\u30fc\\u3092\\u958b\\u304f\n"
            "A.url=URL\nMenu.introduction.fileName=readme_en.pdf\n",
        )
        self.write("DisplayStrings_ko.properties", "A.title=Open log folder\nA.url=URL\nMenu.introduction.fileName=readme_en.pdf\n")

    def tearDown(self) -> None:
        self.temporary.cleanup()

    def write(self, name: str, text: str) -> None:
        (self.l10n / name).write_text(text, encoding="utf-8")

    def run_main(self, *args: str) -> tuple[int, str]:
        output = io.StringIO()
        with contextlib.redirect_stdout(output):
            code = UNTRANSLATED.main([*args, "--root", str(self.root)])
        return code, output.getvalue()

    def test_reports_english_text_but_not_escaped_translations_or_kept_values(self) -> None:
        report = UNTRANSLATED.untranslated(self.root)
        self.assertEqual([], report["ja_JP"])
        self.assertEqual([("A.title", "Open log folder")], report["ko"])

    def test_check_fails_only_when_a_locale_gets_worse(self) -> None:
        self.assertEqual(0, self.run_main("--update-baseline")[0])
        self.assertEqual({"ja_JP": 0, "ko": 1}, json.loads((self.root / "scripts/untranslated_baseline.json").read_text()))
        self.assertEqual(0, self.run_main("--check")[0])

        self.write("DisplayStrings.properties", "A.title=Open log folder\nA.new=Start Full Logs\nA.url=URL\nMenu.introduction.fileName=readme_en.pdf\n")
        self.write(
            "DisplayStrings_ja_JP.properties",
            "A.title=\\u30ed\\u30b0\nA.new=Start Full Logs\nA.url=URL\nMenu.introduction.fileName=readme_en.pdf\n",
        )
        code, output = self.run_main("--check")
        self.assertEqual(1, code)
        self.assertIn("ja_JP: 1 > 0", output)
        self.assertIn("A.new = Start Full Logs", output)

    def test_repository_bundles_are_within_the_baseline(self) -> None:
        output = io.StringIO()
        with contextlib.redirect_stdout(output):
            code = UNTRANSLATED.main(["--check"])
        self.assertEqual(0, code, output.getvalue())


if __name__ == "__main__":
    unittest.main()
