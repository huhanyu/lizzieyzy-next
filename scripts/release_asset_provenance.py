#!/usr/bin/env python3
"""Create and validate run-bound SHA-256 provenance for release assets."""

from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import re
import sys


SCHEMA_VERSION = 1
PROVENANCE_FILENAME = "release-asset-provenance.json"
PLATFORM_ASSET_SUFFIXES: dict[str, tuple[str, ...]] = {
    "windows": (
        "windows64.opencl.installer.exe",
        "windows64.opencl.portable.zip",
        "windows64.nvidia.installer.exe",
        "windows64.nvidia.portable.zip",
        "windows64.nvidia50.cuda.installer.exe",
        "windows64.nvidia50.cuda.portable.zip",
        "windows64.with-katago.installer.exe",
        "windows64.with-katago.portable.zip",
        "windows64.without.engine.installer.exe",
        "windows64.without.engine.portable.zip",
        "windows64.core-update.zip",
        "windows64.nvidia.tensorrt.portable.README.txt",
        "windows64.nvidia.tensorrt.portable.manifest.json",
        "windows64.nvidia.tensorrt.portable.sha256.txt",
        "windows64.nvidia.tensorrt.portable.7z.001",
        "windows64.nvidia.tensorrt.portable.7z.002",
    ),
    "linux": (
        "linux64.with-katago.zip",
        "linux64.opencl.zip",
        "linux64.nvidia.zip",
    ),
    "mac-amd64": ("mac-intel.with-katago.dmg",),
    "mac-arm64": ("mac-apple-silicon.with-katago.dmg",),
}


class ProvenanceError(RuntimeError):
    """Release asset provenance is missing, ambiguous, or inconsistent."""


def require(condition: bool, message: str) -> None:
    if not condition:
        raise ProvenanceError(message)


def _positive_integer(value: object, field: str) -> int:
    require(type(value) is int and int(value) > 0, f"{field} must be a positive integer")
    return int(value)


def validate_identity(
    platform: str,
    date_tag: str,
    release_tag: str,
    target_sha: str,
    run_id: int,
    run_attempt: int,
) -> None:
    require(platform in PLATFORM_ASSET_SUFFIXES, f"Unsupported release platform: {platform}")
    require(
        re.fullmatch(r"\d{4}-\d{2}-\d{2}", date_tag) is not None,
        "dateTag must use YYYY-MM-DD",
    )
    require(
        re.fullmatch(rf"next-{re.escape(date_tag)}\.[1-9][0-9]*", release_tag)
        is not None,
        "releaseTag must exactly match dateTag and use a positive serial",
    )
    require(
        re.fullmatch(r"[0-9a-f]{40}", target_sha) is not None,
        "targetSha must be a full lowercase commit SHA",
    )
    _positive_integer(run_id, "workflowRunId")
    _positive_integer(run_attempt, "workflowRunAttempt")


def expected_asset_names(platform: str, date_tag: str) -> tuple[str, ...]:
    require(platform in PLATFORM_ASSET_SUFFIXES, f"Unsupported release platform: {platform}")
    names = [f"{date_tag}-{suffix}" for suffix in PLATFORM_ASSET_SUFFIXES[platform]]
    if platform == "windows":
        names.append("lizzieyzy-next-update-manifest.json")
    return tuple(sorted(names))


def artifact_name(platform: str, run_attempt: int) -> str:
    require(platform in PLATFORM_ASSET_SUFFIXES, f"Unsupported release platform: {platform}")
    _positive_integer(run_attempt, "workflowRunAttempt")
    return f"release-asset-provenance-{platform}-attempt-{run_attempt}"


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    try:
        with path.open("rb") as handle:
            for chunk in iter(lambda: handle.read(1024 * 1024), b""):
                digest.update(chunk)
    except OSError as exc:
        raise ProvenanceError(f"Unable to hash release asset {path.name}: {exc}") from exc
    return digest.hexdigest()


def build_provenance(
    release_dir: Path,
    platform: str,
    date_tag: str,
    release_tag: str,
    target_sha: str,
    run_id: int,
    run_attempt: int,
) -> dict[str, object]:
    validate_identity(platform, date_tag, release_tag, target_sha, run_id, run_attempt)
    assets: list[dict[str, object]] = []
    for name in expected_asset_names(platform, date_tag):
        path = release_dir / name
        require(path.is_file(), f"Missing release asset for provenance: {name}")
        size = path.stat().st_size
        require(size > 0, f"Release asset is empty: {name}")
        assets.append(
            {
                "name": name,
                "sizeBytes": size,
                "sha256": sha256_file(path),
            }
        )
    return {
        "schemaVersion": SCHEMA_VERSION,
        "platform": platform,
        "dateTag": date_tag,
        "releaseTag": release_tag,
        "targetSha": target_sha,
        "workflowRunId": run_id,
        "workflowRunAttempt": run_attempt,
        "assets": assets,
    }


def validate_provenance(
    payload: object,
    *,
    platform: str,
    date_tag: str,
    release_tag: str,
    target_sha: str,
    run_id: int,
    run_attempt: int,
) -> dict[str, dict[str, object]]:
    validate_identity(platform, date_tag, release_tag, target_sha, run_id, run_attempt)
    require(isinstance(payload, dict), "Provenance manifest must contain a JSON object")
    assert isinstance(payload, dict)
    expected_fields = {
        "schemaVersion",
        "platform",
        "dateTag",
        "releaseTag",
        "targetSha",
        "workflowRunId",
        "workflowRunAttempt",
        "assets",
    }
    require(
        set(payload) == expected_fields,
        "Provenance manifest fields do not exactly match the schema",
    )
    require(payload.get("schemaVersion") == SCHEMA_VERSION, "Unsupported provenance schema")
    require(payload.get("platform") == platform, "Provenance platform does not match the run")
    require(payload.get("dateTag") == date_tag, "Provenance dateTag does not match")
    require(payload.get("releaseTag") == release_tag, "Provenance releaseTag does not match")
    require(payload.get("targetSha") == target_sha, "Provenance targetSha does not match")
    require(payload.get("workflowRunId") == run_id, "Provenance workflowRunId does not match")
    require(
        payload.get("workflowRunAttempt") == run_attempt,
        "Provenance workflowRunAttempt does not match",
    )

    assets = payload.get("assets")
    require(isinstance(assets, list), "Provenance assets must be a list")
    assert isinstance(assets, list)
    records: dict[str, dict[str, object]] = {}
    ordered_names: list[str] = []
    for item in assets:
        require(isinstance(item, dict), "Every provenance asset must be an object")
        assert isinstance(item, dict)
        require(
            set(item) == {"name", "sizeBytes", "sha256"},
            "Provenance asset fields do not exactly match the schema",
        )
        name = item.get("name")
        require(isinstance(name, str) and bool(name), "Provenance asset name is invalid")
        assert isinstance(name, str)
        require(name not in records, f"Duplicate provenance asset: {name}")
        size = _positive_integer(item.get("sizeBytes"), f"sizeBytes for {name}")
        digest = item.get("sha256")
        require(
            isinstance(digest, str) and re.fullmatch(r"[0-9a-f]{64}", digest) is not None,
            f"sha256 for {name} is invalid",
        )
        records[name] = {"name": name, "sizeBytes": size, "sha256": digest}
        ordered_names.append(name)

    expected_names = list(expected_asset_names(platform, date_tag))
    require(ordered_names == expected_names, "Provenance asset inventory is not exact and sorted")
    return records


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--release-dir", required=True, type=Path)
    parser.add_argument("--platform", required=True, choices=tuple(PLATFORM_ASSET_SUFFIXES))
    parser.add_argument("--date-tag", required=True)
    parser.add_argument("--release-tag", required=True)
    parser.add_argument("--target-sha", required=True)
    parser.add_argument("--run-id", required=True, type=int)
    parser.add_argument("--run-attempt", required=True, type=int)
    parser.add_argument("--output", required=True, type=Path)
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    try:
        payload = build_provenance(
            args.release_dir,
            args.platform,
            args.date_tag,
            args.release_tag,
            args.target_sha,
            args.run_id,
            args.run_attempt,
        )
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(
            json.dumps(payload, ensure_ascii=False, indent=2) + "\n",
            encoding="utf-8",
        )
    except (OSError, ProvenanceError) as exc:
        print(f"Release asset provenance failed: {exc}", file=sys.stderr)
        return 1
    print(f"Wrote run-bound release asset provenance: {args.output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
