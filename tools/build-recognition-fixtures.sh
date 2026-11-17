#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
: "${JAVA_HOME:?source ~/project/dev-env/android-env.sh first}"
BT="$ANDROID_HOME/build-tools/35.0.0"
PLATFORM="$ANDROID_HOME/platforms/android-35/android.jar"
OUT="$PWD/build/recognition-fixtures"
mkdir -p "$OUT/classes" "$OUT/dex" "$OUT/assets/alien"
cp -R build/uniform-glyphs/glyphs "$OUT/assets/alien/"
cat > "$OUT/AndroidManifest.xml" <<'XML'
<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="io.github.alieninput.recognitionfixtures">
<uses-sdk android:minSdkVersion="26" android:targetSdkVersion="35" />
<application android:label="Alien recognition fixtures" android:debuggable="true" />
<instrumentation android:name="io.github.alieninput.androidsemantic.RecognitionFixtures" android:targetPackage="io.github.alieninput.recognitionfixtures" />
</manifest>
XML
"$BT/aapt2" link -o "$OUT/base.apk" -I "$PLATFORM" --manifest "$OUT/AndroidManifest.xml" -A "$OUT/assets"
"$JAVA_HOME/bin/javac" --release 8 -encoding UTF-8 -cp "$PLATFORM" -d "$OUT/classes" \
 semantic-core/src/main/java/io/github/alieninput/semantic/*.java \
 android-integration/src/main/java/io/github/alieninput/androidsemantic/AlienImages.java \
 android-integration/src/main/java/io/github/alieninput/androidsemantic/AlienPalette.java \
 android-integration/src/test/java/io/github/alieninput/androidsemantic/RecognitionFixtures.java
"$JAVA_HOME/bin/jar" cf "$OUT/classes.jar" -C "$OUT/classes" .
"$BT/d8" --lib "$PLATFORM" --min-api 26 --output "$OUT/dex" "$OUT/classes.jar"
cp "$OUT/base.apk" "$OUT/unsigned.apk"
(cd "$OUT/dex" && zip -q "$OUT/unsigned.apk" classes*.dex)
"$BT/zipalign" -f 4 "$OUT/unsigned.apk" "$OUT/aligned.apk"
"$BT/apksigner" sign --ks "$DEV_ENV_ROOT/keys/alien-input-debug.jks" --ks-key-alias androiddebugkey --ks-pass pass:android --key-pass pass:android --out "$OUT/fixtures.apk" "$OUT/aligned.apk"
echo "$OUT/fixtures.apk"
