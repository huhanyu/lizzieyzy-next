#!/usr/bin/env python3
"""Keep the architecture refactor moving in one direction.

scripts/architecture_budget.json records upper bounds for the things the refactor is shrinking:

- ``max_lines``: line counts of the largest classes (new logic belongs in focused classes);
- ``max_static_references``: references to the global mutable ``Lizzie.*`` fields in main code;
- ``max_cross_package_references``: main-code references from one package to another
  (for example the analysis/engine layer reaching into Swing UI classes).
- ``max_unlisted_file_lines``: the size limit for every Java file not listed in ``max_lines``.

The check fails when a value goes above its budget. When a change makes code smaller, run
``python3 scripts/check_architecture_budget.py --tighten`` and commit the lowered budget.
Raising a budget is a deliberate edit that reviewers can see; prefer extracting code instead.
"""

from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[1]
BUDGET_FILE = REPO_ROOT / "scripts" / "architecture_budget.json"
MAIN_JAVA = Path("src/main/java")
PACKAGE_ROOT = MAIN_JAVA / "featurecat" / "lizzie"


def count_lines(path: Path) -> int:
    if not path.is_file():
        return 0
    with path.open("rb") as handle:
        return sum(1 for _ in handle)


def main_java_files(root: Path) -> list[Path]:
    base = root / MAIN_JAVA
    if not base.is_dir():
        return []
    return sorted(base.rglob("*.java"))


def strip_comments_and_strings(source: str) -> str:
    """Drop comments and string literals so references in prose or text do not count."""
    pattern = re.compile(
        r'//[^\n]*|/\*.*?\*/|"""(?:\\.|[^\\])*?"""|"(?:\\.|[^"\\\n])*"|\'(?:\\.|[^\'\\\n])*\'',
        re.S,
    )
    return pattern.sub(lambda match: "" if match.group(0).startswith("/") else '""', source)


def static_reference_counts(root: Path, names: list[str]) -> dict[str, int]:
    counts = {name: 0 for name in names}
    patterns = {name: re.compile(r"\b" + re.escape(name) + r"\b") for name in names}
    for path in main_java_files(root):
        code = strip_comments_and_strings(path.read_text(encoding="utf-8", errors="replace"))
        for name, pattern in patterns.items():
            counts[name] += len(pattern.findall(code))
    return counts


def cross_package_counts(root: Path, rules: list[str]) -> dict[str, int]:
    """Count `from -> to` references, e.g. `featurecat.lizzie.analysis -> featurecat.lizzie.gui`.

    A reference is an import of, or a fully qualified use of, any class in the target package
    from a file in the source package (sub-packages included on both sides).
    """
    counts = {rule: 0 for rule in rules}
    for rule in rules:
        source_package, target_package = (part.strip() for part in rule.split("->"))
        source_dir = root / MAIN_JAVA / Path(*source_package.split("."))
        target_pattern = re.compile(r"\b" + re.escape(target_package) + r"\.[A-Za-z_]")
        if not source_dir.is_dir():
            continue
        for path in sorted(source_dir.rglob("*.java")):
            text = path.read_text(encoding="utf-8", errors="replace")
            for line in text.splitlines():
                if line.startswith("package "):
                    continue
                if line.startswith("import "):
                    if target_pattern.search(line):
                        counts[rule] += 1
                    continue
            code = strip_comments_and_strings(text)
            body = "\n".join(
                line for line in code.splitlines()
                if not line.startswith("import ") and not line.startswith("package ")
            )
            counts[rule] += len(target_pattern.findall(body))
    return counts


def measure(root: Path, budget: dict) -> dict:
    max_lines = budget.get("max_lines", {})
    return {
        "max_lines": {path: count_lines(root / path) for path in max_lines},
        "max_static_references": static_reference_counts(
            root, list(budget.get("max_static_references", {}))
        ),
        "max_cross_package_references": cross_package_counts(
            root, list(budget.get("max_cross_package_references", {}))
        ),
    }


def unlisted_oversized_files(root: Path, budget: dict) -> list[tuple[str, int]]:
    limit = int(budget.get("max_unlisted_file_lines", 0))
    if limit <= 0:
        return []
    listed = set(budget.get("max_lines", {}))
    oversized = []
    for path in main_java_files(root):
        relative = path.relative_to(root).as_posix()
        if relative in listed:
            continue
        lines = count_lines(path)
        if lines > limit:
            oversized.append((relative, lines))
    return oversized


def compare(budget: dict, current: dict) -> tuple[list[str], list[str]]:
    failures: list[str] = []
    improvements: list[str] = []
    for section in ("max_lines", "max_static_references", "max_cross_package_references"):
        for key, allowed in budget.get(section, {}).items():
            value = current[section].get(key, 0)
            if value > allowed:
                failures.append(f"{section}: {key} is {value}, budget {allowed} (+{value - allowed})")
            elif value < allowed:
                improvements.append(f"{section}: {key} is {value}, budget {allowed}")
    return failures, improvements


def tighten(budget: dict, current: dict) -> dict:
    tightened = json.loads(json.dumps(budget))
    for section in ("max_lines", "max_static_references", "max_cross_package_references"):
        for key, allowed in tightened.get(section, {}).items():
            tightened[section][key] = min(allowed, current[section].get(key, 0))
    return tightened


def run(root: Path, budget_file: Path, apply_tighten: bool) -> int:
    budget = json.loads(budget_file.read_text(encoding="utf-8"))
    current = measure(root, budget)
    failures, improvements = compare(budget, current)
    for relative, lines in unlisted_oversized_files(root, budget):
        failures.append(
            f"max_unlisted_file_lines: {relative} has {lines} lines, limit "
            f"{budget['max_unlisted_file_lines']}; split it, or list it in max_lines with a reason"
        )
    if apply_tighten:
        tightened = tighten(budget, current)
        if tightened != budget:
            budget_file.write_text(json.dumps(tightened, indent=2) + "\n", encoding="utf-8")
            print(f"Lowered the architecture budget in {budget_file.relative_to(root)}.")
        budget = tightened
        improvements = []
    if failures:
        print("Architecture budget exceeded:")
        for failure in failures:
            print(f"  - {failure}")
        print(
            "Move new logic into focused classes instead of growing these hot spots, or raise the "
            "budget in scripts/architecture_budget.json with a reason reviewers can check."
        )
        return 1
    print("Architecture budget check passed.")
    if improvements:
        print("These values went down; run with --tighten to lock in the improvement:")
        for improvement in improvements:
            print(f"  - {improvement}")
    return 0


def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--tighten", action="store_true", help="Lower budgets to current values")
    parser.add_argument("--root", default=str(REPO_ROOT), help=argparse.SUPPRESS)
    parser.add_argument("--budget", default=None, help=argparse.SUPPRESS)
    return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    args = parse_args(argv)
    root = Path(args.root).resolve()
    budget_file = Path(args.budget).resolve() if args.budget else root / BUDGET_FILE.relative_to(REPO_ROOT)
    return run(root, budget_file, args.tighten)


if __name__ == "__main__":
    sys.exit(main())
