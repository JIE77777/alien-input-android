#!/usr/bin/env bash
# Dependency-free APK build using the official Android SDK. JDK 17 required.
set -euo pipefail
cd "$(dirname "$0")/.."
: "${JAVA_HOME:?source ~/project/dev-env/android-env.sh first}"
: "${ANDROID_HOME:?ANDROID_HOME is required}"
BT="$ANDROID_HOME/build-tools/35.0.0"
PLATFORM="$ANDROID_HOME/platforms/android-35/android.jar"
BUILD="$PWD/build/local"
mkdir -p "$BUILD/classes" "$BUILD/gen" "$BUILD/dex" "$BUILD/tests"
"$JAVA_HOME/bin/javac" --release 8 -encoding UTF-8 -d "$BUILD/tests" app/src/main/java/io/github/alieninput/android/Codebook.java tests/CodebookTest.java
"$JAVA_HOME/bin/java" -cp "$BUILD/tests" io.github.alieninput.android.CodebookTest app/src/main/assets
"$BT/aapt2" compile --dir app/src/main/res -o "$BUILD/resources.zip"
sed 's/<manifest /<manifest package="io.github.alieninput.android" /' app/src/main/AndroidManifest.xml > "$BUILD/AndroidManifest.xml"
"$BT/aapt2" link -o "$BUILD/base.apk" -I "$PLATFORM" --manifest "$BUILD/AndroidManifest.xml" --java "$BUILD/gen" --min-sdk-version 25 --target-sdk-version 35 --version-code 1 --version-name 0.1.0 -A app/src/main/assets "$BUILD/resources.zip"
SOURCES=()
while IFS= read -r -d '' file; do SOURCES+=("$file"); done < <(find app/src/main/java "$BUILD/gen" -name '*.java' -print0)
"$JAVA_HOME/bin/javac" --release 8 -encoding UTF-8 -classpath "$PLATFORM" -d "$BUILD/classes" "${SOURCES[@]}"
"$JAVA_HOME/bin/jar" cf "$BUILD/classes.jar" -C "$BUILD/classes" .
"$BT/d8" --lib "$PLATFORM" --min-api 25 --output "$BUILD/dex" "$BUILD/classes.jar"
cp "$BUILD/base.apk" "$BUILD/unsigned.apk"
(cd "$BUILD/dex" && zip -q "$BUILD/unsigned.apk" classes*.dex)
"$BT/zipalign" -f -p 4 "$BUILD/unsigned.apk" "$BUILD/aligned.apk"
KEY_DIR="${DEV_ENV_ROOT:-$HOME/project/dev-env}/keys"
mkdir -p "$KEY_DIR"
if [ ! -f "$KEY_DIR/alien-input-debug.jks" ]; then
  "$JAVA_HOME/bin/keytool" -genkeypair -keystore "$KEY_DIR/alien-input-debug.jks" -storepass android -keypass android -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 10000 -dname 'CN=Android Debug,O=Android,C=US'
  chmod 600 "$KEY_DIR/alien-input-debug.jks"
fi
"$BT/apksigner" sign --ks "$KEY_DIR/alien-input-debug.jks" --ks-key-alias androiddebugkey --ks-pass pass:android --key-pass pass:android --out "$BUILD/alien-input-android-0.1.0-debug.apk" "$BUILD/aligned.apk"
"$BT/apksigner" verify --verbose "$BUILD/alien-input-android-0.1.0-debug.apk"
"$BT/aapt2" dump badging "$BUILD/alien-input-android-0.1.0-debug.apk" | sed -n '1,12p'
printf '\nAPK: %s\n' "$BUILD/alien-input-android-0.1.0-debug.apk"
