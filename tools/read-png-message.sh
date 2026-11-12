#!/usr/bin/env bash
set -euo pipefail
: "${JAVA_HOME:?source ~/project/dev-env/android-env.sh first}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
mkdir -p "$ROOT/build/png-reader"
"$JAVA_HOME/bin/javac" --release 8 -encoding UTF-8 -d "$ROOT/build/png-reader" "$ROOT/semantic-core/src/main/java/io/github/alieninput/semantic/PngMessage.java"
"$JAVA_HOME/bin/java" -cp "$ROOT/build/png-reader" io.github.alieninput.semantic.PngMessage "$1"
