#!/usr/bin/env bash
# Builds build/ihatemyphone.apk using only the Android SDK's own tools + the JDK.
# No Gradle, no third-party code. Run tools/fetch-sdk.sh first.
set -euo pipefail
cd "$(dirname "$0")"

SDK="${ANDROID_SDK:-$PWD/.sdk}"
BT="$SDK/build-tools/36.0.0"
JAR="$SDK/platforms/android-36/android.jar"
OUT=build
VERSION_CODE="${VERSION_CODE:-1}"
VERSION_NAME="${VERSION_NAME:-1.0}"

if [ ! -x "$BT/aapt2" ] || [ ! -f "$JAR" ]; then
  echo "Android SDK not found in $SDK. Run ./tools/fetch-sdk.sh first." >&2
  exit 1
fi

rm -rf "$OUT"
mkdir -p "$OUT/gen" "$OUT/classes" "$OUT/dex"

echo "==> resources"
"$BT/aapt2" compile --dir res -o "$OUT/res.zip"
"$BT/aapt2" link -I "$JAR" \
  --manifest AndroidManifest.xml \
  --min-sdk-version 29 --target-sdk-version 36 \
  --version-code "$VERSION_CODE" --version-name "$VERSION_NAME" \
  --java "$OUT/gen" \
  -o "$OUT/unsigned.apk" "$OUT/res.zip"

echo "==> java"
find src "$OUT/gen" -name '*.java' > "$OUT/sources.txt"
javac -source 8 -target 8 -Xlint:-options -Xlint:deprecation -encoding UTF-8 \
  -bootclasspath "$JAR:$BT/core-lambda-stubs.jar" -d "$OUT/classes" @"$OUT/sources.txt"

echo "==> dex"
find "$OUT/classes" -name '*.class' > "$OUT/classes.txt"
"$BT/d8" --release --min-api 29 --lib "$JAR" --output "$OUT/dex" @"$OUT/classes.txt"
(cd "$OUT/dex" && zip -q -X ../unsigned.apk classes.dex)

echo "==> align + sign"
"$BT/zipalign" -f -p 4 "$OUT/unsigned.apk" "$OUT/aligned.apk"

KEYSTORE="${KEYSTORE:-$PWD/keystore/release.jks}"
KEYSTORE_PASS="${KEYSTORE_PASS:-ihatemyphone}"
if [ ! -f "$KEYSTORE" ]; then
  echo "No keystore at $KEYSTORE, generating one. KEEP IT: updates must be signed with the same key."
  mkdir -p "$(dirname "$KEYSTORE")"
  keytool -genkeypair -keystore "$KEYSTORE" -storepass "$KEYSTORE_PASS" -keypass "$KEYSTORE_PASS" \
    -alias app -keyalg RSA -keysize 3072 -validity 36500 -dname "CN=ihatemyphone" >/dev/null
fi
"$BT/apksigner" sign --ks "$KEYSTORE" --ks-pass "pass:$KEYSTORE_PASS" --ks-key-alias app \
  --out "$OUT/ihatemyphone.apk" "$OUT/aligned.apk"
"$BT/apksigner" verify "$OUT/ihatemyphone.apk"

echo "==> $OUT/ihatemyphone.apk"
