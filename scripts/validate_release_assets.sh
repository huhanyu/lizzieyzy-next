#!/usr/bin/env bash
set -euo pipefail

PLATFORM="${1:-}"
RELEASE_DIR="${2:-dist/release}"
DATE_TAG="${3:-}"
RELEASE_TAG="${4:-}"
RELEASE_PRERELEASE="${5:-}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

if [[ -z "$PLATFORM" || -z "$DATE_TAG" ]]; then
  echo "Usage: $0 <windows|mac-arm64|mac-amd64|linux> [release_dir] <date_tag> [release_tag] [prerelease]"
  exit 1
fi

if [[ ! -d "$RELEASE_DIR" ]]; then
  echo "Release directory not found: $RELEASE_DIR"
  exit 1
fi

expected=()
case "$PLATFORM" in
  windows)
    expected=(
      "${DATE_TAG}-windows64.opencl.installer.exe"
      "${DATE_TAG}-windows64.opencl.portable.zip"
      "${DATE_TAG}-windows64.nvidia.installer.exe"
      "${DATE_TAG}-windows64.nvidia.portable.zip"
      "${DATE_TAG}-windows64.nvidia50.cuda.installer.exe"
      "${DATE_TAG}-windows64.nvidia50.cuda.portable.zip"
      "${DATE_TAG}-windows64.with-katago.installer.exe"
      "${DATE_TAG}-windows64.with-katago.portable.zip"
      "${DATE_TAG}-windows64.without.engine.installer.exe"
      "${DATE_TAG}-windows64.without.engine.portable.zip"
      "${DATE_TAG}-windows64.core-update.zip"
      "lizzieyzy-next-update-manifest.json"
      "${DATE_TAG}-windows64.nvidia.tensorrt.portable.7z.001"
      "${DATE_TAG}-windows64.nvidia.tensorrt.portable.7z.002"
      "${DATE_TAG}-windows64.nvidia.tensorrt.portable.README.txt"
      "${DATE_TAG}-windows64.nvidia.tensorrt.portable.manifest.json"
      "${DATE_TAG}-windows64.nvidia.tensorrt.portable.sha256.txt"
    )
    ;;
  mac-arm64)
    expected=("${DATE_TAG}-mac-apple-silicon.with-katago.dmg")
    ;;
  mac-amd64)
    expected=("${DATE_TAG}-mac-intel.with-katago.dmg")
    ;;
  linux)
    expected=(
      "${DATE_TAG}-linux64.opencl.zip"
      "${DATE_TAG}-linux64.nvidia.zip"
      "${DATE_TAG}-linux64.with-katago.zip"
    )
    ;;
  *)
    echo "Unsupported platform: $PLATFORM"
    exit 1
    ;;
esac

actual=()
shopt -s nullglob
for path in "$RELEASE_DIR"/*; do
  [[ -f "$path" ]] || continue
  actual+=("$(basename "$path")")
done
shopt -u nullglob

if [[ "${#actual[@]}" -eq 0 ]]; then
  echo "No release assets found in $RELEASE_DIR"
  exit 1
fi

for name in "${actual[@]}"; do
  case "$name" in
    *.txt|*.sha256|*.sha256.txt|*.md)
      if [[ "$PLATFORM" != "windows" ]] || [[ "$name" != "${DATE_TAG}-windows64.nvidia.tensorrt.portable.README.txt" && "$name" != "${DATE_TAG}-windows64.nvidia.tensorrt.portable.sha256.txt" ]]; then
        echo "Unexpected helper file in public release set: $name"
        exit 1
      fi
      ;;
  esac
done

if [[ "${#actual[@]}" -ne "${#expected[@]}" ]]; then
  echo "Unexpected asset count for $PLATFORM"
  printf 'Expected (%s):\n' "${#expected[@]}"
  printf '  %s\n' "${expected[@]}"
  printf 'Actual (%s):\n' "${#actual[@]}"
  printf '  %s\n' "${actual[@]}"
  exit 1
fi

for name in "${expected[@]}"; do
  if [[ ! -f "$RELEASE_DIR/$name" ]]; then
    echo "Missing expected asset: $name"
    exit 1
  fi
done

for name in "${actual[@]}"; do
  match="false"
  for expected_name in "${expected[@]}"; do
    if [[ "$name" == "$expected_name" ]]; then
      match="true"
      break
    fi
  done
  if [[ "$match" != "true" ]]; then
    echo "Unexpected asset in public release set: $name"
    exit 1
  fi
done

case "$PLATFORM" in
  windows)
    if [[ -z "$RELEASE_TAG" || ( "$RELEASE_PRERELEASE" != "true" && "$RELEASE_PRERELEASE" != "false" ) ]]; then
      echo "Windows validation requires the exact release tag and prerelease=true|false" >&2
      exit 1
    fi
    PYTHON_BIN="python3"
    if ! command -v "$PYTHON_BIN" >/dev/null 2>&1; then
      PYTHON_BIN="python"
    fi
    "$PYTHON_BIN" "$SCRIPT_DIR/validate_windows_release_assets.py" \
      "$RELEASE_DIR" \
      "$DATE_TAG" \
      "$RELEASE_TAG" \
      "$RELEASE_PRERELEASE"
    ;;
  mac-arm64|mac-amd64)
    if command -v hdiutil >/dev/null 2>&1; then
      "$SCRIPT_DIR/validate_macos_dmg_layout.sh" \
        "$RELEASE_DIR/${expected[0]}" \
        "" \
        "$RELEASE_TAG"
    else
      echo "Skipping macOS DMG layout validation because hdiutil is unavailable."
    fi
    ;;
esac

echo "Validated public release assets for $PLATFORM:"
printf '  %s\n' "${actual[@]}"
