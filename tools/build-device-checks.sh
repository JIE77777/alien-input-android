#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
: "${JAVA_HOME:?source ~/project/dev-env/android-env.sh first}"
BT="$ANDROID_HOME/build-tools/35.0.0"
PLATFORM="$ANDROID_HOME/platforms/android-35/android.jar"
OUT="$PWD/build/device-checks"
mkdir -p "$OUT/classes" "$OUT/dex" "$OUT/assets/alien"
cp lexicon/generated/lexicon.tsv "$OUT/assets/alien/lexicon.tsv"
cp -R trime/app/src/main/assets/alien/glyphs "$OUT/assets/alien/"
python3 - "$OUT/assets/shared" <<'PY'
import shutil, sys
from pathlib import Path
target = Path(sys.argv[1])
if target.exists():
    shutil.rmtree(target)
shutil.copytree('trime/app/src/main/assets/shared', target, symlinks=False)
PY
mkdir -p "$OUT/native/lib/arm64-v8a"
cp trime/app/prebuilt/arm64-v8a/librime_jni.so "$OUT/native/lib/arm64-v8a/"
cat > "$OUT/AndroidManifest.xml" <<'XML'
<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="io.github.alieninput.checks">
<uses-sdk android:minSdkVersion="26" android:targetSdkVersion="35" />
<application android:label="Alien integration checks" android:debuggable="true" android:extractNativeLibs="true">
<provider android:name="io.github.alieninput.androidsemantic.AlienImageProvider" android:authorities="io.github.alieninput.checks.alien-images" android:exported="false" android:grantUriPermissions="true" />
</application>
<instrumentation android:name="io.github.alieninput.androidsemantic.DeviceChecks" android:targetPackage="io.github.alieninput.checks" />
<instrumentation android:name="io.github.alieninput.androidsemantic.WindowProbe" android:targetPackage="io.github.alieninput.checks" />
</manifest>
XML
"$BT/aapt2" link -o "$OUT/base.apk" -I "$PLATFORM" --manifest "$OUT/AndroidManifest.xml" -A "$OUT/assets"
"$JAVA_HOME/bin/javac" --release 8 -encoding UTF-8 -cp "$PLATFORM" -d "$OUT/classes" \
 semantic-core/src/main/java/io/github/alieninput/semantic/*.java \
 android-integration/src/main/java/io/github/alieninput/androidsemantic/*.java \
 android-integration/native-probe/com/osfans/trime/core/*.java \
 android-integration/src/test/java/io/github/alieninput/androidsemantic/*.java
"$JAVA_HOME/bin/jar" cf "$OUT/classes.jar" -C "$OUT/classes" .
"$BT/d8" --lib "$PLATFORM" --min-api 26 --output "$OUT/dex" "$OUT/classes.jar"
cp "$OUT/base.apk" "$OUT/unsigned.apk"
(cd "$OUT/dex" && zip -q "$OUT/unsigned.apk" classes*.dex)
(cd "$OUT/native" && zip -qr "$OUT/unsigned.apk" lib)
"$BT/zipalign" -f 4 "$OUT/unsigned.apk" "$OUT/aligned.apk"
"$BT/apksigner" sign --ks "$DEV_ENV_ROOT/keys/alien-input-debug.jks" --ks-key-alias androiddebugkey --ks-pass pass:android --key-pass pass:android --out "$OUT/checks.apk" "$OUT/aligned.apk"
echo "$OUT/checks.apk"
