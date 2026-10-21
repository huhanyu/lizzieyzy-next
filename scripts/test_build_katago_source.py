#!/usr/bin/env python3

import copy
import hashlib
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

import build_katago_source
from build_katago_source import (
    SOURCE_COMMIT, TARGETS, apply_source_patches, build, check_host, check_release_receipts,
    check_source, configuration, file_record, macos_minimum_version,
)
from katago_source_targets import (
    SOURCE_PATCHES, TARGET_PATCHES, reported_revision, source_patch_records, source_revision,
)


class SourceBuildTest(unittest.TestCase):
    def receipts(self):
        return [
            {
                "schemaVersion": 1, "sourceRepository": "https://github.com/lightvector/KataGo",
                "target": target, "sourceCommit": SOURCE_COMMIT, "origin": "project-source-build",
                "backend": TARGETS[target][2],
                "executable": {"file": "katago.exe" if target.startswith("windows-") else "katago",
                               "sizeBytes": 1, "sha256": "a" * 64},
                "buildStatus": "PASS", "packagingStatus": "PASS", "dependencyAuditStatus": "PASS",
                "hardwareAcceptanceStatus": "PASS" if TARGETS[target][2] == "EIGEN" else "PENDING_HARDWARE",
                "hardwareAcceptanceReason": "CI runner has no corresponding GPU",
                "productionAbiAcceptanceStatus": "PASS",
                "sourcePatches": source_patch_records(target),
            }
            for target in TARGETS
        ]

    def test_exact_matrix(self):
        self.assertEqual(15, len(TARGETS))
        self.assertEqual(10, sum(key.startswith("windows-") for key in TARGETS))
        self.assertEqual(3, sum(key.startswith("linux-") for key in TARGETS))
        self.assertEqual(2, sum(key.startswith("macos-") for key in TARGETS))

    def test_file_record_uses_actual_size_and_digest(self):
        with tempfile.TemporaryDirectory() as root:
            path = Path(root) / "katago"
            path.write_bytes(b"abc")
            self.assertEqual({
                "file": "katago", "sizeBytes": 3,
                "sha256": "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            }, file_record(path, Path(root)))

    def test_no_auto_fetch_or_disabled_revision(self):
        for target in TARGETS:
            options = configuration(target, [])
            self.assertIn("-DNO_GIT_REVISION=0", options)
            self.assertIn("-DKATAGO_AUTO_FETCH_DEPS=OFF", options)

    def test_sdk_options_cannot_override_identity(self):
        for setting in ("NO_GIT_REVISION=1", "USE_BACKEND=DUMMY", "CMAKE_BUILD_TYPE=Debug"):
            with self.subTest(setting=setting), self.assertRaises(ValueError):
                configuration("windows-cpu", [setting])

    def test_macos_deployment_target_does_not_follow_build_host(self):
        for target in ("macos-arm64", "macos-amd64"):
            self.assertIn("-DCMAKE_OSX_DEPLOYMENT_TARGET=15.0", configuration(target, []))
            self.assertIn(
                f"-DCMAKE_Swift_FLAGS=-target {TARGETS[target][1]}-apple-macosx15.0 "
                "-Xlinker -headerpad_max_install_names",
                configuration(target, []),
            )
            with self.assertRaises(ValueError):
                configuration(target, ["CMAKE_OSX_DEPLOYMENT_TARGET=26.0"])
        self.assertEqual("15.0", macos_minimum_version("    minos 15.0\n      sdk 26.5\n"))

    def test_macos_actual_load_commands_must_keep_compatibility(self):
        for value in ("minos 26.0\n", "minos 15.1\n", "sdk 26.5\n",
                      "minos 15.0\nminos 15.0\n"):
            with self.subTest(value=value), self.assertRaises(ValueError):
                macos_minimum_version(value)

    def test_sdk_values_are_separate_arguments(self):
        value = "CMAKE_PREFIX_PATH=C:/Chinese path/sdk"
        self.assertIn("-D" + value, configuration("windows-cpu", [value]))

    def test_source_sha_is_mandatory(self):
        with patch("build_katago_source.checked", return_value="old"):
            with self.assertRaisesRegex(ValueError, "pinned"):
                check_source(Path("source"))

    def test_untracked_source_is_rejected(self):
        with patch("build_katago_source.checked", side_effect=[SOURCE_COMMIT, "?? cpp/overrides.h"]):
            with self.assertRaisesRegex(ValueError, "clean"):
                check_source(Path("source"))

    def test_wrong_os_cannot_claim_target(self):
        with patch("build_katago_source.platform.system", return_value="Darwin"):
            with self.assertRaises(ValueError):
                check_host("windows-cpu")

    def test_failed_build_cannot_reuse_stale_output(self):
        with tempfile.TemporaryDirectory() as root:
            path = Path(root)
            output = path / "build"
            output.mkdir()
            stale = output / "katago"
            stale.write_bytes(b"stale")
            with patch("build_katago_source.check_host"), patch("build_katago_source.check_source"):
                with patch("build_katago_source.cuda_sdk_tools.verify_sdk", return_value={"lockSha256": "a" * 64}):
                    with self.assertRaises(FileExistsError):
                        build(path / "source", output, "windows-nvidia", [], 2, windows_sdk=path / "sdk")
            self.assertEqual(b"stale", stale.read_bytes())

    def test_build_failure_records_failure_not_approval(self):
        with tempfile.TemporaryDirectory() as root:
            path = Path(root)
            with patch("build_katago_source.check_host"), patch("build_katago_source.check_source"):
                with patch("build_katago_source.subprocess.run", side_effect=RuntimeError("compile failed")), \
                        patch("build_katago_source.cuda_sdk_tools.verify_sdk", return_value={"lockSha256": "a" * 64}), \
                        patch("build_katago_source.file_record", return_value={"sha256": "a" * 64}):
                    with self.assertRaises(RuntimeError):
                        build(path / "source", path / "build", "windows-nvidia", [], 2, windows_sdk=path / "sdk")
            text = (path / "build/source-build.json").read_text()
            self.assertIn('"buildStatus": "FAIL"', text)
            self.assertIn('"packagingStatus": "NOT_RUN"', text)

    def test_macos_cannot_use_unverified_host_dependencies(self):
        with tempfile.TemporaryDirectory() as root:
            path = Path(root)
            with patch("build_katago_source.check_host"), patch("build_katago_source.check_source"):
                with self.assertRaisesRegex(ValueError, "verified pinned dependencies"):
                    build(path / "source", path / "build", "macos-arm64", [], 2)
            self.assertFalse((path / "build").exists())

    def test_missing_gpu_hardware_is_distinct_from_build_failure(self):
        check_release_receipts(self.receipts())

    def test_cpu_must_run_not_claim_missing_gpu(self):
        receipts = self.receipts()
        receipts[0]["hardwareAcceptanceStatus"] = "PENDING_HARDWARE"
        with self.assertRaisesRegex(ValueError, "CPU execution"):
            check_release_receipts(receipts)

    def test_pending_gpu_needs_reason(self):
        receipts = self.receipts()
        receipts[1]["hardwareAcceptanceReason"] = " "
        with self.assertRaisesRegex(ValueError, "explicit reason"):
            check_release_receipts(receipts)

    def test_binary_identity_and_backend_are_mandatory(self):
        for field, value in (("file", "../katago.exe"), ("sizeBytes", 0),
                             ("sizeBytes", True), ("sha256", "invalid")):
            receipts = self.receipts()
            receipts[0]["executable"][field] = value
            with self.subTest(field=field), self.assertRaisesRegex(ValueError, "executable identity"):
                check_release_receipts(receipts)
        receipts = self.receipts()
        receipts[0]["backend"] = "CUDA"
        with self.assertRaisesRegex(ValueError, "source identity"):
            check_release_receipts(receipts)

    def test_missing_target_rejects_release(self):
        with self.assertRaisesRegex(ValueError, "missing build targets"):
            check_release_receipts(self.receipts()[:-1])

    def test_linux_build_host_abi_is_not_production_compatibility(self):
        receipts = self.receipts()
        for receipt in receipts:
            if receipt["target"] == "linux-cpu":
                receipt["productionAbiAcceptanceStatus"] = "NOT_RUN"
        with self.assertRaisesRegex(ValueError, "Linux ABI compatibility"):
            check_release_receipts(receipts)

    def test_duplicate_target_rejects_release(self):
        receipts = self.receipts()
        with self.assertRaisesRegex(ValueError, "duplicate"):
            check_release_receipts(receipts + [receipts[0]])

    def test_actual_hardware_failure_cannot_be_relabelled_by_other_gates(self):
        receipts = self.receipts()
        receipts[0]["hardwareAcceptanceStatus"] = "FAIL"
        with self.assertRaisesRegex(ValueError, "actual failure"):
            check_release_receipts(receipts)

    def test_every_non_hardware_gate_is_required(self):
        for field in ("buildStatus", "packagingStatus", "dependencyAuditStatus"):
            receipts = self.receipts()
            receipts[0][field] = "NOT_RUN"
            with self.subTest(field=field), self.assertRaises(ValueError):
                check_release_receipts(receipts)

    def test_official_old_binary_cannot_fill_missing_self_build(self):
        for field, value in (("sourceCommit", "old"), ("origin", "official-release")):
            receipts = copy.deepcopy(self.receipts())
            receipts[0][field] = value
            with self.subTest(field=field), self.assertRaises(ValueError):
                check_release_receipts(receipts)


    def test_only_the_metal_builds_carry_the_fixed_batch_patch(self):
        for target in TARGETS:
            expected = ("metal-fixed-batch.patch",) if TARGETS[target][2] == "METAL" else ()
            self.assertEqual(expected, TARGET_PATCHES.get(target, ()), target)
        for name, sha256 in SOURCE_PATCHES.items():
            with (build_katago_source.PATCH_DIR / name).open("rb") as handle:
                self.assertEqual(sha256, hashlib.file_digest(handle, "sha256").hexdigest(), name)

    def test_metal_padding_leaves_a_configured_batch_size_dynamic(self):
        # The bundled analysis.cfg (KataGo's analysis_example.cfg) sets nnMaxBatchSize = 64, and
        # whole-game quick analysis runs it with two search threads. Padding every batch to 64 rows
        # made that analysis about 30 times slower on an M4 Pro, so only a batch size KataGo derives
        # itself (none in the config) is padded. Metal cannot run in CI, so this pins the reviewed
        # patch's rule; docs/KATAGO_SOURCE_BUILD.md has the hardware measurement.
        text = (build_katago_source.PATCH_DIR / "metal-fixed-batch.patch").read_text(encoding="utf-8")
        self.assertIn('+    return !cfg.contains("nnMaxBatchSize");', text)
        self.assertIn("+  context->padBatch = metalPadsBatch(cfg);", text)
        self.assertIn("+  return metalPadsBatch(cfg) ? NeuralNet::BatchPolicy::FixedShape", text)

    def test_a_patch_that_differs_from_its_pin_is_never_applied(self):
        with tempfile.TemporaryDirectory() as root:
            patches = Path(root)
            (patches / "metal-fixed-batch.patch").write_text("diff --git a/x b/x\n")
            with patch.object(build_katago_source, "PATCH_DIR", patches), \
                    patch("build_katago_source.checked") as git:
                with self.assertRaisesRegex(ValueError, "pinned SHA-256"):
                    apply_source_patches(Path("source"), "macos-arm64")
            git.assert_not_called()

    def test_patched_build_restores_the_pinned_checkout_even_when_compiling_fails(self):
        with tempfile.TemporaryDirectory() as root:
            path = Path(root)
            git = []
            with patch("build_katago_source.check_host"), patch("build_katago_source.check_source"), \
                    patch("build_katago_source.verify_sdk", return_value={"lockSha256": "a" * 64}), \
                    patch("build_katago_source.file_record", return_value={"sha256": "a" * 64}), \
                    patch("build_katago_source.sdk_environment", return_value={}), \
                    patch("build_katago_source.checked", side_effect=lambda *args, **kw: git.append(args) or ""), \
                    patch("build_katago_source.subprocess.run", side_effect=RuntimeError("compile failed")):
                with self.assertRaises(RuntimeError):
                    build(path / "source", path / "build", "macos-arm64", [], 2, macos_sdk=path / "sdk")
            patch_file = str(build_katago_source.PATCH_DIR / "metal-fixed-batch.patch")
            self.assertEqual([("git", "apply", "--check", patch_file), ("git", "apply", patch_file),
                              ("git", "apply", "--reverse", patch_file)], git)
            receipt = (path / "build/source-build.json").read_text()
            self.assertIn('"buildStatus": "FAIL"', receipt)
            self.assertIn('"file": "metal-fixed-batch.patch"', receipt)

    def test_unpatched_targets_never_touch_the_checkout(self):
        with patch("build_katago_source.checked") as git:
            self.assertEqual([], apply_source_patches(Path("source"), "windows-cpu"))
        git.assert_not_called()

    def test_reported_revision_must_match_exactly(self):
        version = "KataGo v1.18.2\nGit revision: {}\nCompile Time: now\n"
        self.assertEqual(SOURCE_COMMIT + "-dirty", source_revision("macos-arm64"))
        self.assertEqual(SOURCE_COMMIT, source_revision("linux-cpu"))
        self.assertEqual(SOURCE_COMMIT + "-dirty",
                         reported_revision(version.format(SOURCE_COMMIT + "-dirty")))
        # A dirty build can no longer pass as the clean pinned source, nor the reverse.
        self.assertNotEqual(source_revision("linux-cpu"),
                            reported_revision(version.format(SOURCE_COMMIT + "-dirty")))
        self.assertNotEqual(source_revision("macos-arm64"), reported_revision(version.format(SOURCE_COMMIT)))
        self.assertIsNone(reported_revision("KataGo v1.18.2\n"))

    def test_release_receipts_must_record_the_declared_patches(self):
        for patches in ([], [{"file": "metal-fixed-batch.patch", "sha256": "0" * 64}], None):
            receipts = self.receipts()
            for receipt in receipts:
                if receipt["target"] == "macos-arm64":
                    if patches is None:
                        del receipt["sourcePatches"]
                    else:
                        receipt["sourcePatches"] = patches
            with self.subTest(patches=patches), self.assertRaisesRegex(ValueError, "source identity"):
                check_release_receipts(receipts)
        receipts = self.receipts()
        receipts[0]["sourcePatches"] = source_patch_records("macos-arm64")
        with self.assertRaisesRegex(ValueError, "source identity"):
            check_release_receipts(receipts)


if __name__ == "__main__":
    unittest.main()
