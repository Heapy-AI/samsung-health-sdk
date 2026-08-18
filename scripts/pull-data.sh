#!/usr/bin/env bash
# Pulls every JSON file exported by the PoC app into the repository's data/ folder.
#
# File names are discovered on the device, so all 24 data types are covered without
# the script knowing them in advance.
#
#   ./scripts/pull-data.sh [package-name] [--clean]
set -euo pipefail

PKG="com.example.shealthpoc"
CLEAN=0
for arg in "$@"; do
  case "$arg" in
    --clean) CLEAN=1 ;;
    *) PKG="$arg" ;;
  esac
done

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DEST="$PROJECT_ROOT/data"
REMOTE_DIR="/sdcard/Android/data/$PKG/files/data"
RUNAS_DIR="files/data"

ADB="${ADB:-adb}"
command -v "$ADB" >/dev/null 2>&1 || {
  for c in "${ANDROID_HOME:-}/platform-tools/adb" "${ANDROID_SDK_ROOT:-}/platform-tools/adb" \
           "${LOCALAPPDATA:-}/Android/Sdk/platform-tools/adb.exe"; do
    [ -x "$c" ] && ADB="$c" && break
  done
}
command -v "$ADB" >/dev/null 2>&1 || { echo "adb not found" >&2; exit 1; }

mkdir -p "$DEST"
echo "adb        : $ADB"
echo "device dir : $REMOTE_DIR"
echo "PC dir     : $DEST"
echo

if [ "$CLEAN" -eq 1 ]; then
  rm -f "$DEST"/*.json
  echo "cleaned existing *.json in data/"
  echo
fi

# --- discover the file list on the device ---------------------------------
list_remote() {
  # 1) plain shell ls on the app-specific external dir
  "$ADB" shell "ls $REMOTE_DIR" 2>/dev/null | tr -d '\r' | grep '\.json$' && return 0
  # 2) fallback: internal-storage mirror through run-as (debuggable build)
  "$ADB" exec-out run-as "$PKG" ls "$RUNAS_DIR" 2>/dev/null | tr -d '\r' | grep '\.json$'
}

mapfile -t FILES < <(list_remote || true)

if [ "${#FILES[@]}" -eq 0 ]; then
  echo "No JSON found on the device. Run the app first and grant the Samsung Health consents." >&2
  echo "Checked: $REMOTE_DIR  and  run-as $PKG $RUNAS_DIR" >&2
  exit 1
fi
echo "found ${#FILES[@]} file(s) on device"
echo

# --- fetch each file: adb pull first, run-as as fallback -------------------
pulled=0
for f in "${FILES[@]}"; do
  if "$ADB" pull "$REMOTE_DIR/$f" "$DEST/$f" >/dev/null 2>&1 && [ -s "$DEST/$f" ]; then
    echo "  [pull  ] $f"; pulled=$((pulled+1)); continue
  fi
  if "$ADB" exec-out run-as "$PKG" cat "$RUNAS_DIR/$f" > "$DEST/$f" 2>/dev/null && [ -s "$DEST/$f" ]; then
    echo "  [run-as] $f"; pulled=$((pulled+1))
  else
    rm -f "$DEST/$f"
    echo "  [MISS  ] $f"
  fi
done

echo
if [ "$pulled" -eq 0 ]; then
  echo "Nothing could be pulled." >&2
  exit 1
fi
ls -l "$DEST"/*.json
echo
echo "$pulled/${#FILES[@]} file(s) -> $DEST"
