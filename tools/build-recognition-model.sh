#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
: "${JAVA_HOME:?source the Android environment first}"
OUT="$ROOT/build/reader-model"
mkdir -p "$OUT"
"$JAVA_HOME/bin/javac" --release 8 -d "$OUT" "$ROOT/android-integration/src/main/java/io/github/alieninput/androidsemantic/GlyphMath.java" "$ROOT/tools/java/ReaderModelBuilder.java"
"$JAVA_HOME/bin/java" -cp "$OUT" io.github.alieninput.androidsemantic.ReaderModelBuilder "$1" "$ROOT/experiments/recognition/fixtures/android/glyphs"
