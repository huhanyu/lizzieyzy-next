#!/usr/bin/env python3
"""Validate generated release notes before any workflow may edit a release."""

from __future__ import annotations

import argparse
from datetime import date
from pathlib import Path
import re
import sys

try:
    from scripts import publish_release_request as publisher
except ModuleNotFoundError:  # Direct execution: python scripts/validate_release_notes.py
    import publish_release_request as publisher  # type: ignore[no-redef]


MAX_NOTES_BYTES = 2 * 1024 * 1024
PROTECTED_AUDITED_TAGS = frozenset({"next-2026-08-19.1"})


class NotesValidationError(RuntimeError):
    """Generated release notes are unsafe or do not match the release identity."""


def require(condition: bool, message: str) -> None:
    if not condition:
        raise NotesValidationError(message)


def validate_generated_release_notes(
    body: str,
    *,
    date_tag: str,
    release_tag: str,
    repository: str,
) -> None:
    try:
        parsed_date = date.fromisoformat(date_tag)
    except ValueError as exc:
        raise NotesValidationError(f"Invalid release date: {date_tag}") from exc
    require(parsed_date.isoformat() == date_tag, f"Invalid release date: {date_tag}")
    match = publisher.TAG_PATTERN.fullmatch(release_tag)
    require(match is not None, "release_tag must use next-YYYY-MM-DD.N")
    assert match is not None
    require(match.group(1) == date_tag, "release_tag must exactly match date_tag")
    require(
        not match.group(2).startswith("0"),
        "release serial must be a positive integer without leading zeros",
    )
    require(
        re.fullmatch(r"[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+", repository) is not None,
        "repository must use owner/name format",
    )
    require(
        release_tag not in PROTECTED_AUDITED_TAGS,
        f"Refusing to regenerate manually audited release notes for {release_tag}",
    )
    require(release_tag in body, "Generated release notes do not contain release_tag")
    try:
        publisher.validate_no_unresolved_note_markers(body)
        publisher.validate_direct_download_tables(
            body,
            date_tag,
            release_tag,
            repository,
        )
    except publisher.PublishError as exc:
        raise NotesValidationError(str(exc)) from exc


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--notes-file", required=True, type=Path)
    parser.add_argument("--date-tag", required=True)
    parser.add_argument("--release-tag", required=True)
    parser.add_argument("--repository", required=True)
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    try:
        if not args.notes_file.is_file():
            raise NotesValidationError(
                f"Generated release notes file is missing: {args.notes_file}"
            )
        size = args.notes_file.stat().st_size
        if size <= 0 or size > MAX_NOTES_BYTES:
            raise NotesValidationError(
                f"Generated release notes size must be between 1 and {MAX_NOTES_BYTES} bytes"
            )
        body = args.notes_file.read_text(encoding="utf-8")
        validate_generated_release_notes(
            body,
            date_tag=args.date_tag,
            release_tag=args.release_tag,
            repository=args.repository,
        )
    except (OSError, UnicodeError, NotesValidationError) as exc:
        print(f"Generated release notes validation failed: {exc}", file=sys.stderr)
        return 1
    print(f"Validated generated release notes for {args.release_tag}", flush=True)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
