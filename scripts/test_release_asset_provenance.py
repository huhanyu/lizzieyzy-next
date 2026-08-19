#!/usr/bin/env python3
"""Tests for run-bound release asset checksum provenance."""

from __future__ import annotations

import json
from pathlib import Path
import tempfile
import unittest

from scripts import release_asset_provenance as provenance


DATE_TAG = "2026-08-19"
RELEASE_TAG = f"next-{DATE_TAG}.1"
TARGET_SHA = "a" * 40


class ReleaseAssetProvenanceTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temporary_directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary_directory.cleanup)
        self.release_dir = Path(self.temporary_directory.name)
        for index, name in enumerate(
            provenance.expected_asset_names("linux", DATE_TAG), 1
        ):
            (self.release_dir / name).write_bytes(f"asset-{index}".encode("ascii"))

    def payload(self) -> dict[str, object]:
        return provenance.build_provenance(
            self.release_dir,
            "linux",
            DATE_TAG,
            RELEASE_TAG,
            TARGET_SHA,
            123,
            2,
        )

    def validate(self, payload: object) -> dict[str, dict[str, object]]:
        return provenance.validate_provenance(
            payload,
            platform="linux",
            date_tag=DATE_TAG,
            release_tag=RELEASE_TAG,
            target_sha=TARGET_SHA,
            run_id=123,
            run_attempt=2,
        )

    def test_builds_and_validates_exact_sorted_inventory(self) -> None:
        payload = self.payload()
        records = self.validate(json.loads(json.dumps(payload)))

        self.assertEqual(
            list(provenance.expected_asset_names("linux", DATE_TAG)),
            list(records),
        )
        self.assertTrue(all(record["sizeBytes"] > 0 for record in records.values()))

    def test_rejects_missing_empty_or_changed_local_asset(self) -> None:
        name = provenance.expected_asset_names("linux", DATE_TAG)[0]
        (self.release_dir / name).unlink()
        with self.assertRaisesRegex(provenance.ProvenanceError, "Missing"):
            self.payload()

        (self.release_dir / name).write_bytes(b"")
        with self.assertRaisesRegex(provenance.ProvenanceError, "empty"):
            self.payload()

    def test_rejects_wrong_run_sha_tag_or_attempt(self) -> None:
        for field, value, message in (
            ("targetSha", "b" * 40, "targetSha"),
            ("releaseTag", "next-2026-08-19.2", "releaseTag"),
            ("workflowRunId", 124, "workflowRunId"),
            ("workflowRunAttempt", 1, "workflowRunAttempt"),
        ):
            with self.subTest(field=field):
                payload = self.payload()
                payload[field] = value
                with self.assertRaisesRegex(provenance.ProvenanceError, message):
                    self.validate(payload)

    def test_rejects_missing_extra_duplicate_or_reordered_assets(self) -> None:
        mutations = []
        missing = self.payload()
        assert isinstance(missing["assets"], list)
        missing["assets"].pop()
        mutations.append(missing)

        extra = self.payload()
        assert isinstance(extra["assets"], list)
        extra["assets"].append(
            {"name": "unexpected.zip", "sizeBytes": 1, "sha256": "0" * 64}
        )
        mutations.append(extra)

        duplicate = self.payload()
        assert isinstance(duplicate["assets"], list)
        duplicate["assets"].append(dict(duplicate["assets"][0]))
        mutations.append(duplicate)

        reordered = self.payload()
        assert isinstance(reordered["assets"], list)
        reordered["assets"].reverse()
        mutations.append(reordered)

        for payload in mutations:
            with self.subTest(assets=payload["assets"]):
                with self.assertRaises(provenance.ProvenanceError):
                    self.validate(payload)

    def test_rejects_non_positive_size_or_malformed_digest(self) -> None:
        for field, value, message in (
            ("sizeBytes", 0, "positive integer"),
            ("sha256", "0" * 63, "sha256"),
        ):
            with self.subTest(field=field):
                payload = self.payload()
                assets = payload["assets"]
                assert isinstance(assets, list) and isinstance(assets[0], dict)
                assets[0][field] = value
                with self.assertRaisesRegex(provenance.ProvenanceError, message):
                    self.validate(payload)

    def test_artifact_name_is_bound_to_platform_and_attempt(self) -> None:
        self.assertEqual(
            "release-asset-provenance-linux-attempt-3",
            provenance.artifact_name("linux", 3),
        )


if __name__ == "__main__":
    unittest.main()
