#!/usr/bin/env bash
set -euo pipefail

if [[ "$(uname -s)" != "Darwin" ]]; then
  echo "create_macos_drag_dmg.sh only supports macOS." >&2
  exit 1
fi

if [[ $# -lt 3 || $# -gt 4 ]]; then
  echo "Usage: $0 <volume-name> <source-folder-containing-app> <output.dmg> [architecture-label]" >&2
  exit 1
fi

VOLUME_NAME="$1"
SOURCE_FOLDER="$2"
OUTPUT_DMG="$3"
ARCHITECTURE_LABEL="${4:-macOS}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
BACKGROUND_TEMPLATE="$ROOT_DIR/packaging/macos/dmg-background.svg"

if [[ ! -d "$SOURCE_FOLDER" ]]; then
  echo "Source folder not found: $SOURCE_FOLDER" >&2
  exit 1
fi

if ! command -v hdiutil >/dev/null 2>&1; then
  echo "hdiutil not found." >&2
  exit 1
fi
for required_command in diskutil osascript python3 sips strings tiffutil; do
  if ! command -v "$required_command" >/dev/null 2>&1; then
    echo "$required_command not found; polished macOS DMG layout cannot be created." >&2
    exit 1
  fi
done
if [[ ! -f "$BACKGROUND_TEMPLATE" ]]; then
  echo "DMG background template not found: $BACKGROUND_TEMPLATE" >&2
  exit 1
fi

APP_BUNDLES=()
while IFS= read -r -d '' app_bundle; do
  APP_BUNDLES+=("$app_bundle")
done < <(find "$SOURCE_FOLDER" -maxdepth 1 -type d -name '*.app' -print0)
if [[ "${#APP_BUNDLES[@]}" -ne 1 ]]; then
  echo "Expected exactly one .app bundle in $SOURCE_FOLDER; found ${#APP_BUNDLES[@]}." >&2
  exit 1
fi

APP_NAME="$(basename "${APP_BUNDLES[0]}")"
OUTPUT_DIR="$(dirname "$OUTPUT_DMG")"
mkdir -p "$OUTPUT_DIR"

WORK_DIR="$(mktemp -d -t lizzieyzy-dmg-layout.XXXXXX)"
BUILD_VOLUME_NAME="LizzieYzy Layout $$-$RANDOM"
STAGING_DIR="$WORK_DIR/staging"
RW_DMG="$WORK_DIR/layout-rw.dmg"
MOUNT_POINT="$WORK_DIR/mount"
BACKGROUND_SVG="$WORK_DIR/install-background.svg"
BACKGROUND_1X="$WORK_DIR/install-background.png"
BACKGROUND_2X="$WORK_DIR/install-background@2x.png"
MOUNT_POINT_REAL=""
MOUNT_DEVICE=""
MOUNT_PARENT_DEVICE=""
MOUNTED_AT=""
MOUNT_PARENT_FILESYSTEM=""
MOUNTED=0
UNSAFE_DETACH_EXIT=70

load_mount_metadata() {
  local plist_path="$1"
  local expected_mount="$2"
  local metadata
  local parsed_device
  local parsed_parent
  local parsed_mount

  if ! metadata="$(
    python3 - "$plist_path" "$expected_mount" <<'PY'
import os
import plistlib
import re
import sys

plist_path, expected_mount = sys.argv[1:]
try:
    with open(plist_path, "rb") as stream:
        payload = plistlib.load(stream)
except Exception:
    raise SystemExit(2)

mounted_entities = []


def visit(value):
    if isinstance(value, dict):
        device = value.get("dev-entry")
        mount_point = value.get("mount-point")
        if isinstance(device, str) and isinstance(mount_point, str):
            mounted_entities.append((device, mount_point))
        for child in value.values():
            visit(child)
    elif isinstance(value, list):
        for child in value:
            visit(child)


visit(payload)
expected_real = os.path.realpath(expected_mount)
matches = [
    (device, mount_point)
    for device, mount_point in mounted_entities
    if os.path.realpath(mount_point) == expected_real
]
if len(matches) != 1:
    raise SystemExit(3)

device, mount_point = matches[0]
match = re.fullmatch(r"(/dev/disk[0-9]+)(?:s[0-9]+)*", device)
if match is None:
    raise SystemExit(4)
print("\t".join((device, match.group(1), os.path.realpath(mount_point))))
PY
  )"; then
    return 1
  fi
  IFS=$'\t' read -r parsed_device parsed_parent parsed_mount <<<"$metadata"
  if [[ -z "$parsed_device" || -z "$parsed_parent" || "$parsed_mount" != "$MOUNT_POINT_REAL" ]]; then
    return 1
  fi
  MOUNT_DEVICE="$parsed_device"
  MOUNT_PARENT_DEVICE="$parsed_parent"
  MOUNTED_AT="$parsed_mount"
  return 0
}

discover_mount_metadata() {
  local info_plist="$WORK_DIR/hdiutil-discovery.plist"
  local info_log="$WORK_DIR/hdiutil-discovery.log"

  rm -f "$info_plist" "$info_log"
  if ! hdiutil info -plist >"$info_plist" 2>"$info_log"; then
    return 1
  fi
  load_mount_metadata "$info_plist" "$MOUNT_POINT_REAL"
}

device_is_active() {
  local info_plist="$WORK_DIR/hdiutil-info.plist"
  local info_log="$WORK_DIR/hdiutil-info.log"
  local info_status
  local current_filesystem

  if [[ -z "$MOUNT_DEVICE" || -z "$MOUNT_PARENT_DEVICE" \
    || -z "$MOUNT_PARENT_FILESYSTEM" ]]; then
    return 0
  fi
  rm -f "$info_plist" "$info_log"
  if ! hdiutil info -plist >"$info_plist" 2>"$info_log"; then
    return 0
  fi
  if python3 - "$info_plist" "$MOUNT_DEVICE" "$MOUNT_PARENT_DEVICE" <<'PY'
import plistlib
import sys

plist_path = sys.argv[1]
targets = set(sys.argv[2:])
try:
    with open(plist_path, "rb") as stream:
        payload = plistlib.load(stream)
except Exception:
    raise SystemExit(2)

devices = set()


def visit(value):
    if isinstance(value, dict):
        device = value.get("dev-entry")
        if isinstance(device, str):
            devices.add(device)
        for child in value.values():
            visit(child)
    elif isinstance(value, list):
        for child in value:
            visit(child)


visit(payload)
raise SystemExit(0 if devices.intersection(targets) else 1)
PY
  then
    return 0
  else
    info_status=$?
  fi
  if [[ "$info_status" -ne 1 ]]; then
    return 0
  fi
  if stat "$MOUNT_DEVICE" >/dev/null 2>&1 \
    || stat "$MOUNT_PARENT_DEVICE" >/dev/null 2>&1; then
    return 0
  fi
  if ! current_filesystem="$(stat -f '%d' "$MOUNT_POINT" 2>/dev/null)" \
    || [[ -z "$current_filesystem" \
      || "$current_filesystem" != "$MOUNT_PARENT_FILESYSTEM" ]]; then
    return 0
  fi
  return 1
}

wait_for_detach() {
  local check

  for check in 1 2 3 4 5; do
    if ! device_is_active; then
      return 0
    fi
    sleep 1
  done
  return 1
}

detach_mount() {
  local attempt
  local detach_log="$WORK_DIR/hdiutil-detach.log"
  local detach_target

  if [[ "$MOUNTED" -ne 1 ]]; then
    return 0
  fi
  if [[ -z "$MOUNT_DEVICE" || -z "$MOUNT_PARENT_DEVICE" ]]; then
    echo "Mounted DMG has no exact device identity; refusing an ambiguous detach." >&2
    return 1
  fi
  detach_target="$MOUNT_DEVICE"
  for attempt in 1 2 3 4 5; do
    rm -f "$detach_log"
    echo "Detaching writable DMG $detach_target (attempt $attempt/5)..." >&2
    if hdiutil detach "$detach_target" >"$detach_log" 2>&1; then
      if wait_for_detach; then
        MOUNTED=0
        return 0
      fi
      echo "Writable DMG detach reported success but the mount remained active." >&2
    else
      if ! device_is_active; then
        MOUNTED=0
        return 0
      fi
      echo "Writable DMG detach attempt $attempt/5 failed:" >&2
    fi
    cat "$detach_log" >&2
    sleep "$attempt"
  done

  rm -f "$detach_log"
  echo "Forcing writable DMG detach for $detach_target after normal attempts were exhausted..." >&2
  if hdiutil detach "$detach_target" -force >"$detach_log" 2>&1; then
    if wait_for_detach; then
      MOUNTED=0
      return 0
    fi
    echo "Forced writable DMG detach reported success but the mount remained active." >&2
  else
    if ! device_is_active; then
      MOUNTED=0
      return 0
    fi
    echo "Forced writable DMG detach failed:" >&2
  fi
  cat "$detach_log" >&2
  echo "Unable to detach the writable DMG; refusing to continue." >&2
  return 1
}

cleanup() {
  local status=$?

  trap - EXIT
  if ! detach_mount; then
    echo "DMG cleanup could not detach $MOUNT_POINT; retaining $WORK_DIR." >&2
    status="$UNSAFE_DETACH_EXIT"
  fi
  if [[ "$MOUNTED" -eq 0 ]]; then
    if ! rm -rf "$WORK_DIR"; then
      echo "Unable to remove temporary DMG work directory: $WORK_DIR" >&2
      if [[ "$status" -eq 0 ]]; then
        status=1
      fi
    fi
  fi
  exit "$status"
}
trap cleanup EXIT

create_writable_dmg() {
  local attempt
  for attempt in 1 2 3; do
    rm -f "$RW_DMG"
    echo "Creating writable DMG (attempt $attempt/3)..." >&2
    if hdiutil create \
      -volname "$BUILD_VOLUME_NAME" \
      -fs HFS+ \
      -srcfolder "$STAGING_DIR" \
      -format UDRW \
      -ov \
      "$RW_DMG"; then
      return 0
    fi
    echo "Writable DMG creation attempt $attempt/3 failed; retrying..." >&2
    sleep "$attempt"
  done
  echo "Unable to create the writable DMG after 3 attempts." >&2
  exit 1
}

attach_writable_dmg() {
  local attempt
  local attach_plist="$WORK_DIR/hdiutil-attach.plist"
  local attach_log="$WORK_DIR/hdiutil-attach.log"

  for attempt in 1 2 3; do
    rm -f "$attach_plist" "$attach_log"
    if [[ -d "$MOUNT_POINT" ]] && ! rmdir "$MOUNT_POINT" 2>/dev/null; then
      echo "Unable to reset the DMG mount point safely: $MOUNT_POINT" >&2
      return 1
    fi
    mkdir -p "$MOUNT_POINT"
    MOUNT_POINT_REAL="$(cd "$MOUNT_POINT" && pwd -P)"
    if ! MOUNT_PARENT_FILESYSTEM="$(stat -f '%d' "$MOUNT_POINT" 2>/dev/null)" \
      || [[ -z "$MOUNT_PARENT_FILESYSTEM" ]]; then
      echo "Unable to record the mount point's parent filesystem before attach." >&2
      return 1
    fi
    MOUNT_DEVICE=""
    MOUNT_PARENT_DEVICE=""
    MOUNTED_AT=""
    echo "Attaching writable DMG (attempt $attempt/3)..." >&2
    if hdiutil attach "$RW_DMG" \
      -mountpoint "$MOUNT_POINT" \
      -readwrite \
      -noverify \
      -noautoopen \
      -nobrowse \
      -plist \
      >"$attach_plist" 2>"$attach_log"; then
      if ! load_mount_metadata "$attach_plist" "$MOUNT_POINT_REAL" \
        && ! discover_mount_metadata; then
        MOUNTED=1
        echo "Attached DMG metadata did not identify the exact mounted device." >&2
        cat "$attach_log" >&2
        return 1
      fi
      MOUNTED=1
      echo "Attached $MOUNT_DEVICE at $MOUNTED_AT (parent $MOUNT_PARENT_DEVICE)." >&2
      return 0
    fi

    echo "Writable DMG attach attempt $attempt/3 failed:" >&2
    cat "$attach_log" >&2
    if load_mount_metadata "$attach_plist" "$MOUNT_POINT_REAL" \
      || discover_mount_metadata; then
      MOUNTED=1
      if ! detach_mount; then
        return 1
      fi
    else
      if [[ -d "$MOUNT_POINT" ]] && ! rmdir "$MOUNT_POINT" 2>/dev/null; then
        MOUNTED=1
        echo "Unable to prove that the failed attach left no mounted image; refusing unsafe cleanup." >&2
        return 1
      fi
      if [[ -e "$MOUNT_POINT" ]]; then
        MOUNTED=1
        echo "The failed attach left an unexpected mount-point entry; refusing unsafe cleanup." >&2
        return 1
      fi
      mkdir -p "$MOUNT_POINT"
      MOUNT_POINT_REAL="$(cd "$MOUNT_POINT" && pwd -P)"
    fi
    sleep "$attempt"
  done

  echo "Unable to attach the writable DMG after 3 attempts." >&2
  exit 1
}

mkdir -p "$STAGING_DIR" "$MOUNT_POINT"
MOUNT_POINT_REAL="$(cd "$MOUNT_POINT" && pwd -P)"
ditto "$SOURCE_FOLDER" "$STAGING_DIR"
rm -f "$STAGING_DIR/.DS_Store"
rm -rf "$STAGING_DIR/Applications"
ln -s /Applications "$STAGING_DIR/Applications"
mkdir -p "$STAGING_DIR/.background"

python3 - "$BACKGROUND_TEMPLATE" "$BACKGROUND_SVG" "$ARCHITECTURE_LABEL" <<'PY'
from pathlib import Path
import sys

template_path = Path(sys.argv[1])
output_path = Path(sys.argv[2])
architecture_label = sys.argv[3]
template = template_path.read_text(encoding="utf-8")
if "__ARCH_LABEL__" not in template:
    raise SystemExit(f"DMG background template has no architecture token: {template_path}")
output_path.write_text(
    template.replace("__ARCH_LABEL__", architecture_label),
    encoding="utf-8",
)
PY

sips -s format png "$BACKGROUND_SVG" --out "$BACKGROUND_2X" >/dev/null
sips -z 500 800 "$BACKGROUND_2X" --out "$BACKGROUND_1X" >/dev/null

BACKGROUND_1X_SIZE="$(sips -g pixelWidth -g pixelHeight "$BACKGROUND_1X" 2>/dev/null)"
BACKGROUND_2X_SIZE="$(sips -g pixelWidth -g pixelHeight "$BACKGROUND_2X" 2>/dev/null)"
if ! grep -q "pixelWidth: 800" <<<"$BACKGROUND_1X_SIZE" \
  || ! grep -q "pixelHeight: 500" <<<"$BACKGROUND_1X_SIZE"; then
  echo "Unexpected 1x DMG background dimensions." >&2
  echo "$BACKGROUND_1X_SIZE" >&2
  exit 1
fi
if ! grep -q "pixelWidth: 1600" <<<"$BACKGROUND_2X_SIZE" \
  || ! grep -q "pixelHeight: 1000" <<<"$BACKGROUND_2X_SIZE"; then
  echo "Unexpected 2x DMG background dimensions." >&2
  echo "$BACKGROUND_2X_SIZE" >&2
  exit 1
fi

tiffutil -cathidpicheck "$BACKGROUND_1X" "$BACKGROUND_2X" \
  -out "$STAGING_DIR/.background/install-background.tiff" >/dev/null
python3 - "$STAGING_DIR/.background/layout.json" "$ARCHITECTURE_LABEL" <<'PY'
from pathlib import Path
import json
import sys

output_path = Path(sys.argv[1])
architecture_label = sys.argv[2]
output_path.write_text(
    json.dumps(
        {
            "schemaVersion": 1,
            "architecture": architecture_label,
            "background": "install-background.tiff",
            "window": {"width": 800, "height": 500},
            "appPosition": {"x": 185, "y": 300},
            "applicationsPosition": {"x": 615, "y": 300},
        },
        ensure_ascii=False,
        indent=2,
    )
    + "\n",
    encoding="utf-8",
)
PY

create_writable_dmg
attach_writable_dmg

LAYOUT_CREATED=0
for attempt in 1 2 3; do
  echo "Applying Finder layout (attempt $attempt/3)..." >&2
  if osascript >/dev/null <<OSA
tell application "Finder"
  set dmgRoot to POSIX file "$MOUNT_POINT" as alias
  set backgroundImage to POSIX file "$MOUNT_POINT/.background/install-background.tiff" as alias
  open dmgRoot
  delay 2
  set dmgWindow to container window of dmgRoot
  set current view of dmgWindow to icon view
  try
    set toolbar visible of dmgWindow to false
  end try
  try
    set statusbar visible of dmgWindow to false
  end try
  set the bounds of dmgWindow to {160, 100, 960, 620}
  set viewOptions to the icon view options of dmgWindow
  set arrangement of viewOptions to not arranged
  set icon size of viewOptions to 128
  set text size of viewOptions to 14
  set background picture of viewOptions to backgroundImage
  set position of item "$APP_NAME" of dmgRoot to {185, 300}
  set position of item "Applications" of dmgRoot to {615, 300}
  update dmgRoot without registering applications
  set toolbar visible of dmgWindow to false
  set statusbar visible of dmgWindow to false
  delay 2
  close dmgWindow
end tell
OSA
  then
    if sync; then
      sleep 1
      DS_STORE_STRINGS="$(strings -a "$MOUNT_POINT/.DS_Store" 2>/dev/null || true)"
      if grep -q "install-background.tiff" <<<"$DS_STORE_STRINGS"; then
        LAYOUT_CREATED=1
        break
      fi
    else
      echo "Filesystem sync failed after Finder layout attempt $attempt/3." >&2
    fi
  fi
  echo "Finder layout attempt $attempt/3 did not persist the branded background; retrying..." >&2
  sleep "$attempt"
done

if [[ "$LAYOUT_CREATED" -ne 1 ]]; then
  echo "Finder layout customization failed; refusing to publish a plain DMG." >&2
  exit 1
fi
if [[ ! -s "$MOUNT_POINT/.DS_Store" ]]; then
  echo "Finder did not persist DMG layout metadata." >&2
  exit 1
fi
if [[ ! -s "$MOUNT_POINT/.background/install-background.tiff" ]]; then
  echo "DMG background is missing after Finder layout generation." >&2
  exit 1
fi

RENAME_LOG="$WORK_DIR/diskutil-rename.log"
echo "Renaming mounted DMG volume to $VOLUME_NAME..." >&2
if ! diskutil rename "$MOUNT_POINT" "$VOLUME_NAME" >"$RENAME_LOG" 2>&1; then
  echo "Unable to rename the mounted DMG volume:" >&2
  cat "$RENAME_LOG" >&2
  exit 1
fi

echo "Flushing mounted DMG changes before detach..." >&2
if ! sync; then
  echo "Filesystem sync failed before writable DMG detach." >&2
  exit 1
fi
if ! detach_mount; then
  exit 1
fi

TMP_OUTPUT="$WORK_DIR/$(basename "$OUTPUT_DMG")"

convert_release_dmg() {
  local attempt
  local convert_log

  for attempt in 1 2 3; do
    convert_log="$WORK_DIR/hdiutil-convert-$attempt.log"
    rm -f "$TMP_OUTPUT" "$convert_log"
    echo "Converting writable DMG (attempt $attempt/3)..." >&2
    if hdiutil convert "$RW_DMG" \
      -format UDZO \
      -imagekey zlib-level=9 \
      -o "$TMP_OUTPUT" \
      >"$convert_log" 2>&1; then
      if [[ -s "$TMP_OUTPUT" ]]; then
        return 0
      fi
      echo "DMG conversion attempt $attempt/3 returned success without a non-empty image." >&2
    else
      echo "DMG conversion attempt $attempt/3 failed:" >&2
    fi
    cat "$convert_log" >&2
    rm -f "$TMP_OUTPUT"
    if [[ "$attempt" -lt 3 ]]; then
      sleep "$attempt"
    fi
  done

  echo "Unable to convert the writable DMG after 3 attempts." >&2
  return 1
}

rm -f "$OUTPUT_DMG" "$TMP_OUTPUT"
if ! convert_release_dmg; then
  exit 1
fi
if ! mv "$TMP_OUTPUT" "$OUTPUT_DMG"; then
  echo "Unable to move the completed DMG to $OUTPUT_DMG." >&2
  exit 1
fi

echo "Created drag-install DMG: $OUTPUT_DMG"
