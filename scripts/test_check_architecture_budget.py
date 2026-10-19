#!/usr/bin/env python3
"""Tests for the architecture budget ratchet."""

from __future__ import annotations

import contextlib
import importlib.util
import io
import json
from pathlib import Path
import tempfile
import unittest

SCRIPT_PATH = Path(__file__).with_name("check_architecture_budget.py")
SPEC = importlib.util.spec_from_file_location("check_architecture_budget", SCRIPT_PATH)
if SPEC is None or SPEC.loader is None:
    raise RuntimeError(f"Unable to load {SCRIPT_PATH}")
BUDGET = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(BUDGET)

LEELAZ = "src/main/java/featurecat/lizzie/analysis/Leelaz.java"
FRAME = "src/main/java/featurecat/lizzie/gui/LizzieFrame.java"


class ArchitectureBudgetTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temporary = tempfile.TemporaryDirectory()
        self.root = Path(self.temporary.name)
        self.write(LEELAZ, "package featurecat.lizzie.analysis;\n"
                   "import featurecat.lizzie.gui.LizzieFrame;\n"
                   "class Leelaz {\n"
                   "  void a() { Lizzie.config.x = 1; Lizzie.frame.refresh(); }\n"
                   "}\n")
        self.write(FRAME, "package featurecat.lizzie.gui;\nclass LizzieFrame {}\n")
        self.budget_file = self.root / "scripts" / "architecture_budget.json"
        self.write_budget({
            "max_unlisted_file_lines": 10,
            "max_lines": {LEELAZ: 5},
            "max_static_references": {"Lizzie.config": 1, "Lizzie.frame": 1},
            "max_cross_package_references": {
                "featurecat.lizzie.analysis -> featurecat.lizzie.gui": 1
            },
        })

    def tearDown(self) -> None:
        self.temporary.cleanup()

    def write(self, relative: str, text: str) -> None:
        path = self.root / relative
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(text, encoding="utf-8")

    def write_budget(self, budget: dict) -> None:
        self.budget_file.parent.mkdir(parents=True, exist_ok=True)
        self.budget_file.write_text(json.dumps(budget), encoding="utf-8")

    def run_check(self, tighten: bool = False) -> tuple[int, str]:
        output = io.StringIO()
        with contextlib.redirect_stdout(output):
            code = BUDGET.run(self.root, self.budget_file, tighten)
        return code, output.getvalue()

    def test_passes_at_the_budget(self) -> None:
        code, output = self.run_check()
        self.assertEqual(0, code, output)

    def test_fails_when_a_listed_class_grows(self) -> None:
        self.write(LEELAZ, (self.root / LEELAZ).read_text(encoding="utf-8") + "// more\n")
        code, output = self.run_check()
        self.assertEqual(1, code)
        self.assertIn("Leelaz.java is 6, budget 5 (+1)", output)

    def test_fails_when_global_state_references_grow_but_ignores_text(self) -> None:
        self.write(
            "src/main/java/featurecat/lizzie/rules/Board.java",
            "class Board {\n"
            "  // Lizzie.config in a comment\n"
            "  String s = \"Lizzie.config in a string\";\n"
            "}\n",
        )
        self.assertEqual(0, self.run_check()[0])
        self.write(
            "src/main/java/featurecat/lizzie/rules/Board.java",
            "class Board { int x = Lizzie.config.y; }\n",
        )
        code, output = self.run_check()
        self.assertEqual(1, code)
        self.assertIn("Lizzie.config is 2, budget 1", output)

    def test_fails_when_an_unlisted_file_becomes_a_new_hot_spot(self) -> None:
        self.write(
            "src/main/java/featurecat/lizzie/gui/Huge.java", "class Huge {}\n" + "\n" * 11
        )
        code, output = self.run_check()
        self.assertEqual(1, code)
        self.assertIn("Huge.java has 12 lines, limit 10", output)

    def test_counts_cross_package_imports_and_qualified_uses(self) -> None:
        self.write(
            "src/main/java/featurecat/lizzie/analysis/Other.java",
            "package featurecat.lizzie.analysis;\n"
            "class Other { featurecat.lizzie.gui.Menu menu; }\n",
        )
        code, output = self.run_check()
        self.assertEqual(1, code)
        self.assertIn("featurecat.lizzie.analysis -> featurecat.lizzie.gui is 2, budget 1", output)

    def test_tighten_locks_in_improvements_only(self) -> None:
        self.write_budget({
            "max_unlisted_file_lines": 10,
            "max_lines": {LEELAZ: 50},
            "max_static_references": {"Lizzie.config": 9, "Lizzie.frame": 1},
            "max_cross_package_references": {},
        })
        code, output = self.run_check()
        self.assertEqual(0, code)
        self.assertIn("run with --tighten", output)

        code, _ = self.run_check(tighten=True)
        self.assertEqual(0, code)
        tightened = json.loads(self.budget_file.read_text(encoding="utf-8"))
        self.assertEqual(5, tightened["max_lines"][LEELAZ])
        self.assertEqual(1, tightened["max_static_references"]["Lizzie.config"])
        self.assertEqual(10, tightened["max_unlisted_file_lines"])

    def test_repository_budget_matches_the_current_tree(self) -> None:
        output = io.StringIO()
        with contextlib.redirect_stdout(output):
            code = BUDGET.run(BUDGET.REPO_ROOT, BUDGET.BUDGET_FILE, False)
        self.assertEqual(0, code, output.getvalue())


if __name__ == "__main__":
    unittest.main()
