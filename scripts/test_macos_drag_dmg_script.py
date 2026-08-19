#!/usr/bin/env python3

import os
from pathlib import Path
import plistlib
import shutil
import subprocess
import tempfile
import textwrap
import unittest


ROOT = Path(__file__).resolve().parents[1]


class MacosDragDmgScriptTest(unittest.TestCase):
    @staticmethod
    def script_text(name: str) -> str:
        return (ROOT / "scripts" / name).read_text(encoding="utf-8")

    @staticmethod
    def shell_function(script: str, name: str) -> str:
        start = script.index(f"{name}() {{")
        end = script.index("\n}\n", start) + len("\n}\n")
        return script[start:end]

    def require_posix_bash(self) -> str:
        if os.name == "nt":
            self.skipTest("Executable shell fault injection runs on POSIX CI")
        bash = shutil.which("bash")
        if bash is None:
            self.skipTest("bash is unavailable")
        if shutil.which("python3") is None:
            self.skipTest("python3 is unavailable to parse hdiutil plists")
        return bash

    @staticmethod
    def write_executable(path: Path, content: str) -> None:
        path.write_text(textwrap.dedent(content).lstrip(), encoding="utf-8")
        path.chmod(0o700)

    def test_writable_image_creation_is_retried_with_diagnostics(self) -> None:
        script = self.script_text("create_macos_drag_dmg.sh")
        self.assertIn("create_writable_dmg()", script)
        self.assertIn("for attempt in 1 2 3", script)
        self.assertIn("Creating writable DMG (attempt $attempt/3)", script)
        self.assertIn("Unable to create the writable DMG after 3 attempts.", script)
        self.assertIn("\ncreate_writable_dmg\n", script)
        self.assertNotIn("hdiutil create \\\n  -quiet", script)

    def test_writable_image_attach_is_retried_with_diagnostics(self) -> None:
        script = self.script_text("create_macos_drag_dmg.sh")
        self.assertIn("attach_writable_dmg()", script)
        self.assertIn("Attaching writable DMG (attempt $attempt/3)", script)
        self.assertIn("Writable DMG attach attempt $attempt/3 failed:", script)
        self.assertIn("Unable to attach the writable DMG after 3 attempts.", script)
        self.assertIn("-plist \\", script)
        self.assertIn('load_mount_metadata "$attach_plist" "$MOUNT_POINT_REAL"', script)
        self.assertIn('MOUNT_PARENT_DEVICE="$parsed_parent"', script)
        self.assertIn("Unable to prove that the failed attach left no mounted image", script)
        self.assertIn("\nattach_writable_dmg\n", script)

    def test_detach_is_exact_bounded_and_fail_closed(self) -> None:
        script = self.script_text("create_macos_drag_dmg.sh")
        detach = self.shell_function(script, "detach_mount")
        cleanup = self.shell_function(script, "cleanup")

        self.assertIn('detach_target="$MOUNT_DEVICE"', detach)
        self.assertIn('hdiutil detach "$detach_target"', detach)
        self.assertIn('hdiutil detach "$detach_target" -force', detach)
        self.assertIn("if wait_for_detach; then", detach)
        self.assertIn("Unable to detach the writable DMG; refusing to continue.", detach)
        self.assertNotIn("|| true", detach)
        self.assertIn("hdiutil info -plist", script)
        self.assertIn('stat "$MOUNT_DEVICE"', script)
        self.assertIn('stat "$MOUNT_PARENT_DEVICE"', script)
        self.assertIn("MOUNT_PARENT_FILESYSTEM", script)
        self.assertIn("stat -f '%d' \"$MOUNT_POINT\"", script)
        self.assertIn("local status=$?", cleanup)
        self.assertIn("trap - EXIT", cleanup)
        self.assertIn("if ! detach_mount; then", cleanup)
        self.assertIn('status="$UNSAFE_DETACH_EXIT"', cleanup)
        self.assertIn('exit "$status"', cleanup)

    def test_post_layout_stages_have_explicit_failure_diagnostics(self) -> None:
        script = self.script_text("create_macos_drag_dmg.sh")

        for expected in (
            "Applying Finder layout (attempt $attempt/3)",
            "Filesystem sync failed after Finder layout attempt $attempt/3.",
            "Renaming mounted DMG volume to $VOLUME_NAME",
            "Unable to rename the mounted DMG volume:",
            "Flushing mounted DMG changes before detach",
            "Filesystem sync failed before writable DMG detach.",
            "Converting writable DMG (attempt $attempt/3)",
            "Unable to convert the writable DMG after 3 attempts.",
        ):
            self.assertIn(expected, script)

        convert = self.shell_function(script, "convert_release_dmg")
        self.assertIn("for attempt in 1 2 3", convert)
        self.assertIn('hdiutil-convert-$attempt.log', convert)
        self.assertIn('rm -f "$TMP_OUTPUT"', convert)
        self.assertIn('[[ -s "$TMP_OUTPUT" ]]', convert)

    def run_drag_retry_fixture(
        self, succeed_at: int, failure_status: int = 1
    ) -> tuple[subprocess.CompletedProcess[str], int, bool, str]:
        bash = self.require_posix_bash()
        package_script = self.script_text("package_macos_dmg.sh")
        retry_function = self.shell_function(
            package_script, "create_drag_dmg_with_retry"
        )

        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            helper = root / "fake-drag-helper.sh"
            harness = root / "retry-harness.sh"
            attempts = root / "attempts.txt"
            source = root / "source"
            output = root / "release.dmg"
            source.mkdir()
            self.write_executable(
                helper,
                r"""
                #!/usr/bin/env bash
                set -euo pipefail
                count=0
                if [[ -f "$FAKE_ATTEMPT_FILE" ]]; then
                  count="$(cat "$FAKE_ATTEMPT_FILE")"
                fi
                count=$((count + 1))
                printf '%s\n' "$count" >"$FAKE_ATTEMPT_FILE"
                if [[ "$count" -gt 1 && -e "$3" ]]; then
                  echo "stale output survived into retry $count" >&2
                  exit 97
                fi
                printf 'partial-%s\n' "$count" >"$3"
                if [[ "$count" -lt "$FAKE_SUCCEED_AT" ]]; then
                  exit "$FAKE_FAILURE_STATUS"
                fi
                printf 'complete-%s\n' "$count" >"$3"
                """,
            )
            self.write_executable(
                harness,
                f"""
                #!/usr/bin/env bash
                set -euo pipefail
                DRAG_DMG_SCRIPT="$1"
                sleep() {{ :; }}
                {retry_function}
                create_drag_dmg_with_retry "Test Volume" "$2" "$3" "Intel"
                """,
            )
            environment = os.environ.copy()
            environment["FAKE_ATTEMPT_FILE"] = str(attempts)
            environment["FAKE_SUCCEED_AT"] = str(succeed_at)
            environment["FAKE_FAILURE_STATUS"] = str(failure_status)
            result = subprocess.run(
                [bash, str(harness), str(helper), str(source), str(output)],
                cwd=root,
                env=environment,
                text=True,
                capture_output=True,
                check=False,
            )
            attempt_count = int(attempts.read_text(encoding="utf-8").strip())
            output_exists = output.exists()
            output_text = output.read_text(encoding="utf-8") if output_exists else ""
            return result, attempt_count, output_exists, output_text

    def test_initial_drag_dmg_retries_fresh_after_partial_failure(self) -> None:
        result, attempts, output_exists, output_text = self.run_drag_retry_fixture(2)

        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual(2, attempts)
        self.assertTrue(output_exists)
        self.assertEqual("complete-2\n", output_text)
        self.assertIn("retrying with a fresh work directory", result.stderr)
        self.assertNotIn("stale output survived", result.stderr)

    def test_initial_drag_dmg_retries_nonstandard_helper_failure(self) -> None:
        result, attempts, output_exists, output_text = self.run_drag_retry_fixture(
            2, failure_status=42
        )

        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual(2, attempts)
        self.assertTrue(output_exists)
        self.assertEqual("complete-2\n", output_text)

    def test_initial_drag_dmg_exhaustion_removes_partial_output_and_fails(self) -> None:
        result, attempts, output_exists, _output_text = self.run_drag_retry_fixture(99)

        self.assertNotEqual(0, result.returncode)
        self.assertEqual(3, attempts)
        self.assertFalse(output_exists)
        self.assertIn(
            "Unable to create the drag-install DMG after 3 fresh attempts.",
            result.stderr,
        )

    def test_initial_drag_dmg_does_not_retry_unsafe_cleanup_failure(self) -> None:
        result, attempts, output_exists, _output_text = self.run_drag_retry_fixture(
            99, failure_status=70
        )

        self.assertEqual(70, result.returncode)
        self.assertEqual(1, attempts)
        self.assertFalse(output_exists)
        self.assertIn("could not safely detach", result.stderr)

    def test_convert_retries_partial_and_empty_outputs(self) -> None:
        bash = self.require_posix_bash()
        script = self.script_text("create_macos_drag_dmg.sh")
        convert = self.shell_function(script, "convert_release_dmg")

        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            fake_bin = root / "bin"
            fake_bin.mkdir()
            attempts = root / "convert-attempts.txt"
            work = root / "work"
            work.mkdir()
            source = root / "source.dmg"
            source.write_bytes(b"source")
            output = work / "release.dmg"
            self.write_executable(
                fake_bin / "hdiutil",
                r"""
                #!/usr/bin/env bash
                set -euo pipefail
                count=0
                if [[ -f "$FAKE_ATTEMPT_FILE" ]]; then
                  count="$(cat "$FAKE_ATTEMPT_FILE")"
                fi
                count=$((count + 1))
                printf '%s\n' "$count" >"$FAKE_ATTEMPT_FILE"
                output=""
                while [[ "$#" -gt 0 ]]; do
                  if [[ "$1" == "-o" ]]; then
                    shift
                    output="$1"
                  fi
                  shift
                done
                if [[ "$count" -gt 1 && -e "$output" ]]; then
                  echo "stale convert output survived into attempt $count" >&2
                  exit 97
                fi
                if [[ "$count" -eq 1 ]]; then
                  printf 'partial\n' >"$output"
                  echo "injected convert failure" >&2
                  exit 9
                fi
                if [[ "$count" -eq 2 ]]; then
                  : >"$output"
                  exit 0
                fi
                printf 'complete\n' >"$output"
                """,
            )
            harness = root / "convert-harness.sh"
            self.write_executable(
                harness,
                f"""
                #!/usr/bin/env bash
                set -euo pipefail
                WORK_DIR="$1"
                RW_DMG="$2"
                TMP_OUTPUT="$3"
                sleep() {{ :; }}
                {convert}
                convert_release_dmg
                [[ "$(cat "$TMP_OUTPUT")" == "complete" ]]
                """,
            )
            environment = os.environ.copy()
            environment["PATH"] = f"{fake_bin}{os.pathsep}{environment['PATH']}"
            environment["FAKE_ATTEMPT_FILE"] = str(attempts)
            result = subprocess.run(
                [bash, str(harness), str(work), str(source), str(output)],
                cwd=root,
                env=environment,
                text=True,
                capture_output=True,
                check=False,
            )

            self.assertEqual(0, result.returncode, result.stderr)
            self.assertEqual("3", attempts.read_text(encoding="utf-8").strip())
            self.assertEqual(b"complete\n", output.read_bytes())
            self.assertIn("attempt 1/3 failed", result.stderr)
            self.assertIn("success without a non-empty image", result.stderr)

    def test_detach_requires_parent_filesystem_restoration(self) -> None:
        bash = self.require_posix_bash()
        script = self.script_text("create_macos_drag_dmg.sh")
        device_is_active = self.shell_function(script, "device_is_active")

        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            fake_bin = root / "bin"
            fake_bin.mkdir()
            work = root / "work"
            mount_point = root / "mount"
            work.mkdir()
            mount_point.mkdir()
            empty_plist = root / "empty.plist"
            with empty_plist.open("wb") as stream:
                plistlib.dump({"images": []}, stream)
            self.write_executable(
                fake_bin / "hdiutil",
                "#!/bin/sh\ncat \"$FAKE_INFO_PLIST\"\n",
            )
            self.write_executable(
                fake_bin / "stat",
                r"""
                #!/usr/bin/env bash
                set -euo pipefail
                if [[ "$1" == "-f" && "$2" == "%d" ]]; then
                  printf '%s\n' "$FAKE_MOUNT_FILESYSTEM"
                  exit 0
                fi
                exit 1
                """,
            )
            harness = root / "filesystem-harness.sh"
            self.write_executable(
                harness,
                f"""
                #!/usr/bin/env bash
                set -euo pipefail
                WORK_DIR="$1"
                MOUNT_POINT="$2"
                MOUNT_DEVICE="/dev/disk41s1"
                MOUNT_PARENT_DEVICE="/dev/disk41"
                MOUNT_PARENT_FILESYSTEM="100"
                {device_is_active}
                FAKE_MOUNT_FILESYSTEM="200"
                export FAKE_MOUNT_FILESYSTEM
                device_is_active
                FAKE_MOUNT_FILESYSTEM="100"
                export FAKE_MOUNT_FILESYSTEM
                if device_is_active; then
                  echo "parent filesystem restoration was not recognized" >&2
                  exit 90
                fi
                """,
            )
            environment = os.environ.copy()
            environment["PATH"] = f"{fake_bin}{os.pathsep}{environment['PATH']}"
            environment["FAKE_INFO_PLIST"] = str(empty_plist)
            result = subprocess.run(
                [bash, str(harness), str(work), str(mount_point)],
                cwd=root,
                env=environment,
                text=True,
                capture_output=True,
                check=False,
            )

            self.assertEqual(0, result.returncode, result.stderr)

    def test_exit_cleanup_returns_unsafe_status_and_retains_workdir(self) -> None:
        bash = self.require_posix_bash()
        script = self.script_text("create_macos_drag_dmg.sh")
        cleanup = self.shell_function(script, "cleanup")

        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            work = root / "work"
            mount_point = work / "mount"
            mount_point.mkdir(parents=True)
            harness = root / "cleanup-harness.sh"
            self.write_executable(
                harness,
                f"""
                #!/usr/bin/env bash
                set -euo pipefail
                WORK_DIR="$1"
                MOUNT_POINT="$2"
                MOUNTED=1
                UNSAFE_DETACH_EXIT=70
                detach_mount() {{ return 1; }}
                {cleanup}
                trap cleanup EXIT
                exit 23
                """,
            )
            result = subprocess.run(
                [bash, str(harness), str(work), str(mount_point)],
                cwd=root,
                text=True,
                capture_output=True,
                check=False,
            )

            self.assertEqual(70, result.returncode, result.stderr)
            self.assertTrue(work.is_dir())
            self.assertIn("retaining", result.stderr)

    def test_exit_cleanup_preserves_original_status_after_safe_detach(self) -> None:
        bash = self.require_posix_bash()
        script = self.script_text("create_macos_drag_dmg.sh")
        cleanup = self.shell_function(script, "cleanup")

        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            work = root / "work"
            mount_point = work / "mount"
            mount_point.mkdir(parents=True)
            harness = root / "cleanup-harness.sh"
            self.write_executable(
                harness,
                f"""
                #!/usr/bin/env bash
                set -euo pipefail
                WORK_DIR="$1"
                MOUNT_POINT="$2"
                MOUNTED=1
                UNSAFE_DETACH_EXIT=70
                detach_mount() {{ MOUNTED=0; return 0; }}
                {cleanup}
                trap cleanup EXIT
                exit 23
                """,
            )
            result = subprocess.run(
                [bash, str(harness), str(work), str(mount_point)],
                cwd=root,
                text=True,
                capture_output=True,
                check=False,
            )

            self.assertEqual(23, result.returncode, result.stderr)
            self.assertFalse(work.exists())

    def test_detach_never_trusts_exit_zero_while_exact_device_remains(self) -> None:
        bash = self.require_posix_bash()
        script = self.script_text("create_macos_drag_dmg.sh")
        functions = "\n".join(
            self.shell_function(script, name)
            for name in ("device_is_active", "wait_for_detach", "detach_mount")
        )

        for detach_exit in (0, 1):
            with self.subTest(detach_exit=detach_exit), tempfile.TemporaryDirectory() as directory:
                root = Path(directory)
                fake_bin = root / "bin"
                fake_bin.mkdir()
                calls = root / "detach-calls.txt"
                info_plist = root / "hdiutil-info.plist"
                work = root / "work"
                mount_point = root / "mount"
                work.mkdir()
                mount_point.mkdir()
                with info_plist.open("wb") as stream:
                    plistlib.dump(
                        {
                            "images": [
                                {
                                    "system-entities": [
                                        {"dev-entry": "/dev/disk41"},
                                        {
                                            "dev-entry": "/dev/disk41s1",
                                            "mount-point": str(mount_point),
                                        },
                                    ]
                                }
                            ]
                        },
                        stream,
                    )
                self.write_executable(
                    fake_bin / "hdiutil",
                    r"""
                    #!/usr/bin/env bash
                    set -euo pipefail
                    if [[ "$1" == "info" ]]; then
                      cat "$FAKE_INFO_PLIST"
                      exit 0
                    fi
                    printf '%s\n' "$*" >>"$FAKE_DETACH_CALLS"
                    exit "$FAKE_DETACH_EXIT"
                    """,
                )
                harness = root / "detach-harness.sh"
                self.write_executable(
                    harness,
                    f"""
                    #!/usr/bin/env bash
                    set -euo pipefail
                    WORK_DIR="$1"
                    MOUNT_POINT="$2"
                    MOUNT_POINT_REAL="$2"
                    MOUNT_DEVICE="/dev/disk41s1"
                    MOUNT_PARENT_DEVICE="/dev/disk41"
                    MOUNTED_AT="$2"
                    MOUNT_PARENT_FILESYSTEM="100"
                    MOUNTED=1
                    sleep() {{ :; }}
                    {functions}
                    if detach_mount; then
                      echo "detach unexpectedly succeeded" >&2
                      exit 90
                    fi
                    [[ "$MOUNTED" -eq 1 ]]
                    """,
                )
                environment = os.environ.copy()
                environment["PATH"] = f"{fake_bin}{os.pathsep}{environment['PATH']}"
                environment["FAKE_INFO_PLIST"] = str(info_plist)
                environment["FAKE_DETACH_CALLS"] = str(calls)
                environment["FAKE_DETACH_EXIT"] = str(detach_exit)
                result = subprocess.run(
                    [bash, str(harness), str(work), str(mount_point)],
                    cwd=root,
                    env=environment,
                    text=True,
                    capture_output=True,
                    check=False,
                )
                self.assertEqual(0, result.returncode, result.stderr)
                detach_calls = calls.read_text(encoding="utf-8").splitlines()
                self.assertEqual(6, len(detach_calls))
                self.assertEqual("detach /dev/disk41s1 -force", detach_calls[-1])
                self.assertIn(
                    "Unable to detach the writable DMG; refusing to continue.",
                    result.stderr,
                )


if __name__ == "__main__":
    unittest.main()
