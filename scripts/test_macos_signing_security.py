#!/usr/bin/env python3
"""Windows-runnable static security tests for the macOS signing transaction."""

from __future__ import annotations

from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[1]
SIGN_SCRIPT = ROOT / "scripts" / "sign_macos_release.sh"


class MacOSSigningSecurityTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.script = SIGN_SCRIPT.read_text(encoding="utf-8")

    def test_cleanup_trap_precedes_all_keychain_and_certificate_creation(self) -> None:
        trap = self.script.index("trap cleanup EXIT")
        self.assertLess(trap, self.script.index("security create-keychain"))
        self.assertLess(trap, self.script.index('cert_path="$(mktemp'))
        self.assertIn("trap 'exit 129' HUP", self.script)
        self.assertIn("trap 'exit 130' INT", self.script)
        self.assertIn("trap 'exit 143' TERM", self.script)

    def test_keychain_and_sensitive_files_are_unique_and_restrictive(self) -> None:
        self.assertNotIn('keychain="lizzieyzy-sign.keychain-db"', self.script)
        self.assertNotIn("mktemp -t", self.script)
        self.assertNotIn(").p12", self.script)
        self.assertIn("lizzieyzy-keychain.XXXXXXXX", self.script)
        self.assertIn('keychain="$keychain_dir/signing.keychain-db"', self.script)
        self.assertIn('chmod 700 "$keychain_dir"', self.script)
        self.assertIn("lizzieyzy-cert.XXXXXXXX", self.script)
        self.assertIn('chmod 600 "$cert_path"', self.script)

    def test_partial_secrets_fail_closed_and_all_absent_explicitly_skip(self) -> None:
        for name in (
            "APPLE_CERT_P12",
            "APPLE_ID",
            "APPLE_APP_PASSWORD",
            "APPLE_TEAM_ID",
        ):
            self.assertIn(name, self.script)
        self.assertIn('if [[ "$configured_secret_count" -eq 0 ]]', self.script)
        self.assertIn("credentials are fully absent; skipping", self.script)
        self.assertIn('if [[ "$configured_secret_count" -ne', self.script)
        self.assertIn("credentials are only partially configured", self.script)

    def test_cleanup_removes_every_sensitive_resource_on_early_failure(self) -> None:
        cleanup_start = self.script.index("cleanup() {")
        cleanup_end = self.script.index("trap cleanup EXIT", cleanup_start)
        cleanup = self.script[cleanup_start:cleanup_end]
        self.assertIn('rm -f -- "$cert_path"', cleanup)
        self.assertIn('security delete-keychain "$keychain"', cleanup)
        self.assertIn('rm -rf -- "$keychain_dir"', cleanup)
        self.assertIn('rm -rf -- "$work_dir"', cleanup)
        self.assertIn('hdiutil detach "$mounted_target" -force', cleanup)

    def test_gatekeeper_failure_keeps_original_dmg_and_runs_cleanup(self) -> None:
        assessment = self.script.index("if ! spctl --assess")
        replacement = self.script.index('mv "$signed_dmg" "$dmg"', assessment)
        guarded_tail = self.script[assessment:replacement]
        self.assertNotIn("|| true", guarded_tail)
        self.assertIn("refusing to replace the original DMG", guarded_tail)
        self.assertIn("exit 1", guarded_tail)
        self.assertLess(assessment, replacement)

        cleanup_start = self.script.index("cleanup() {")
        cleanup_end = self.script.index("trap cleanup EXIT", cleanup_start)
        cleanup = self.script[cleanup_start:cleanup_end]
        self.assertIn('rm -rf -- "$work_dir"', cleanup)
        self.assertIn('rm -f -- "$cert_path"', cleanup)
        self.assertIn('security delete-keychain "$keychain"', cleanup)

    def test_signing_failure_blocks_both_macos_release_uploads(self) -> None:
        for workflow_name in (
            "build-macos-amd64-release.yml",
            "build-macos-arm64-release.yml",
        ):
            with self.subTest(workflow=workflow_name):
                workflow = (ROOT / ".github" / "workflows" / workflow_name).read_text(
                    encoding="utf-8"
                )
                sign_step = workflow.index("sign_macos_release_with_retry.sh")
                release_upload = workflow.index("gh release upload")
                self.assertLess(sign_step, release_upload)
                nearby = workflow[max(0, sign_step - 250): sign_step + 250]
                self.assertNotIn("continue-on-error", nearby)

    def test_documentation_matches_fail_closed_secret_and_upload_behavior(self) -> None:
        documentation = (ROOT / "docs" / "MACOS_SIGNING.md").read_text(
            encoding="utf-8"
        )
        self.assertIn("四个必需 secret 全部未配置", documentation)
        self.assertIn("部分配置会 fail closed", documentation)
        self.assertIn("上传 Release asset 之前终止", documentation)
        self.assertNotIn("这步失败不会阻断 artifact 上传", documentation)
        self.assertIn("逐层执行", documentation)
        self.assertIn("`--deep` 只用于签名后的递归验证", documentation)


if __name__ == "__main__":
    unittest.main()
