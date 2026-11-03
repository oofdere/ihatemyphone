#!/usr/bin/env bash
# Downloads the ONLY two things this app needs to build, straight from Google,
# and refuses to use them unless their SHA-256 matches the values pinned below.
#
#   - Android build-tools 36.0.0 (aapt2, d8, zipalign, apksigner)
#   - Android platform 36 (android.jar, the API stubs we compile against)
#
# No Gradle, no Maven, no plugins, no libraries.
set -euo pipefail

SDK_DIR="${1:-$(cd "$(dirname "$0")/.." && pwd)/.sdk}"
BASE="https://dl.google.com/android/repository"

BT_ZIP="build-tools_r36_linux.zip"
BT_SHA256="5d9ac77fb6ff43d9da518a337b4fcf8f9097113df531d99ccefe80ef7ce8250b"
PLAT_ZIP="platform-36_r02.zip"
PLAT_SHA256="37607369a28c5b640b3a7998868d45898ebcb777565a0e85f9acf36f29631d2e"

mkdir -p "$SDK_DIR/dl" "$SDK_DIR/build-tools" "$SDK_DIR/platforms"

fetch() {
  local zip="$1" sha="$2"
  local file="$SDK_DIR/dl/$zip"
  if [ ! -f "$file" ] || ! echo "$sha  $file" | sha256sum -c --quiet - 2>/dev/null; then
    echo "Downloading $zip ..."
    curl -fL --proto '=https' --tlsv1.2 -o "$file.part" "$BASE/$zip"
    mv "$file.part" "$file"
  fi
  if ! echo "$sha  $file" | sha256sum -c --quiet -; then
    echo "CHECKSUM MISMATCH for $zip -- refusing to continue." >&2
    rm -f "$file"
    exit 1
  fi
  echo "$zip: checksum OK"
}

fetch "$BT_ZIP" "$BT_SHA256"
fetch "$PLAT_ZIP" "$PLAT_SHA256"

if [ ! -x "$SDK_DIR/build-tools/36.0.0/aapt2" ]; then
  rm -rf "$SDK_DIR/build-tools/36.0.0" "$SDK_DIR/tmp" && mkdir -p "$SDK_DIR/tmp"
  unzip -q "$SDK_DIR/dl/$BT_ZIP" -d "$SDK_DIR/tmp"
  mv "$SDK_DIR/tmp/android-16" "$SDK_DIR/build-tools/36.0.0"
  rm -rf "$SDK_DIR/tmp"
fi
if [ ! -f "$SDK_DIR/platforms/android-36/android.jar" ]; then
  rm -rf "$SDK_DIR/platforms/android-36" "$SDK_DIR/tmp" && mkdir -p "$SDK_DIR/tmp"
  unzip -q "$SDK_DIR/dl/$PLAT_ZIP" -d "$SDK_DIR/tmp"
  mv "$SDK_DIR/tmp/android-36" "$SDK_DIR/platforms/android-36"
  rm -rf "$SDK_DIR/tmp"
fi

echo "SDK ready in $SDK_DIR"
