"""Dependency-free source identity shared by builds, probes and the release publisher."""

from __future__ import annotations

import re

SOURCE_COMMIT = "47aadc08518b3e121f22539796c911002f699584"

TARGETS = {
    "windows-cpu": ("Windows", "x86_64", "EIGEN", False),
    "windows-opencl": ("Windows", "x86_64", "OPENCL", False),
    "windows-nvidia": ("Windows", "x86_64", "CUDA", False),
    "windows-tensorrt": ("Windows", "x86_64", "TENSORRT", False),
    "windows-directml": ("Windows", "x86_64", "ONNX", True),
    "windows-openvino": ("Windows", "x86_64", "ONNX", True),
    "windows-rocm-gfx103x": ("Windows", "x86_64", "ROCM", True),
    "windows-rocm-gfx110x": ("Windows", "x86_64", "ROCM", True),
    "windows-rocm-gfx1151": ("Windows", "x86_64", "ROCM", True),
    "windows-rocm-gfx120x": ("Windows", "x86_64", "ROCM", True),
    "linux-cpu": ("Linux", "x86_64", "EIGEN", False),
    "linux-opencl": ("Linux", "x86_64", "OPENCL", False),
    "linux-nvidia": ("Linux", "x86_64", "CUDA", False),
    "macos-arm64": ("Darwin", "arm64", "METAL", False),
    "macos-amd64": ("Darwin", "x86_64", "METAL", False),
}

# Reviewed changes applied on top of SOURCE_COMMIT, by file name under scripts/katago_patches, with
# the SHA-256 of each patch file. See docs/KATAGO_SOURCE_BUILD.md for why each one exists.
SOURCE_PATCHES = {
    "metal-fixed-batch.patch": "cdca795929189219ae9b8f6460818c63c460b1e5c32821cf1489772d8776ba55",
}

# Patches per target, in application order. Targets not listed build the pinned source unchanged.
TARGET_PATCHES = {
    "macos-arm64": ("metal-fixed-batch.patch",),
    "macos-amd64": ("metal-fixed-batch.patch",),
}


def source_patch_records(target: str) -> list[dict]:
    """The patch identities a build of target records and every later stage checks."""
    return [{"file": name, "sha256": SOURCE_PATCHES[name]} for name in TARGET_PATCHES.get(target, ())]


def source_revision(target: str) -> str:
    """The revision a build of target reports: KataGo marks a patched checkout as dirty."""
    return SOURCE_COMMIT + ("-dirty" if TARGET_PATCHES.get(target) else "")


def reported_revision(version_output: str) -> str | None:
    """The revision in `katago version` output, or None when it reports none."""
    match = re.search(r"(?m)^Git revision: (\S+)\s*$", version_output or "")
    return match.group(1) if match else None
