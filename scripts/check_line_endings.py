#!/usr/bin/env python3
"""Validate tracked files against the repository's Git EOL attributes."""

from __future__ import annotations

import os
from pathlib import Path
import subprocess
import sys


def content_errors(path: str, data: bytes, expected_eol: str) -> list[str]:
    """Return deterministic EOL errors for one text file."""
    errors: list[str] = []
    if b"\0" in data:
        return [f"{path}: looks binary but is not marked binary in .gitattributes"]

    if expected_eol == "lf":
        if b"\r" in data:
            crlf_count = data.count(b"\r\n")
            bare_cr_count = data.replace(b"\r\n", b"").count(b"\r")
            errors.append(
                f"{path}: expected LF, found {crlf_count} CRLF and "
                f"{bare_cr_count} bare CR line ending(s)"
            )
        return errors

    if expected_eol == "crlf":
        remainder = data.replace(b"\r\n", b"")
        bare_lf_count = remainder.count(b"\n")
        bare_cr_count = remainder.count(b"\r")
        if bare_lf_count or bare_cr_count:
            errors.append(
                f"{path}: expected CRLF, found {bare_lf_count} bare LF and "
                f"{bare_cr_count} bare CR line ending(s)"
            )
        return errors

    return [f"{path}: unsupported eol attribute {expected_eol!r}"]


def tracked_attributes(repo_root: Path) -> dict[str, dict[str, str]]:
    tracked = subprocess.run(
        ["git", "-C", str(repo_root), "ls-files", "-z"],
        check=True,
        capture_output=True,
    ).stdout
    raw = subprocess.run(
        ["git", "-C", str(repo_root), "check-attr", "-z", "--stdin", "text", "eol"],
        input=tracked,
        check=True,
        capture_output=True,
    ).stdout

    fields = raw.split(b"\0")
    if fields and fields[-1] == b"":
        fields.pop()
    if len(fields) % 3:
        raise RuntimeError("unexpected git check-attr output")

    result: dict[str, dict[str, str]] = {}
    for index in range(0, len(fields), 3):
        path = os.fsdecode(fields[index])
        attribute = fields[index + 1].decode("ascii")
        value = fields[index + 2].decode("ascii")
        result.setdefault(path, {})[attribute] = value
    return result


def repository_errors(repo_root: Path) -> list[str]:
    errors: list[str] = []
    for relative_path, attributes in sorted(tracked_attributes(repo_root).items()):
        if attributes.get("text") == "unset":
            continue
        expected_eol = attributes.get("eol")
        if expected_eol not in {"lf", "crlf"}:
            errors.append(
                f"{relative_path}: tracked text file has no explicit LF/CRLF policy"
            )
            continue
        path = repo_root / relative_path
        try:
            data = path.read_bytes()
        except FileNotFoundError:
            errors.append(f"{relative_path}: tracked file is missing from the working tree")
            continue
        errors.extend(content_errors(relative_path, data, expected_eol))
    return errors


def main() -> int:
    repo_root = Path(__file__).resolve().parents[1]
    errors = repository_errors(repo_root)
    if errors:
        print("Line-ending policy violations:", file=sys.stderr)
        for error in errors:
            print(f"  - {error}", file=sys.stderr)
        return 1
    print("Line-ending policy check passed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
