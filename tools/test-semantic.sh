#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
: "${JAVA_HOME:?source ~/project/dev-env/android-env.sh first}"
mkdir -p build/semantic-tests
"$JAVA_HOME/bin/javac" --release 8 -encoding UTF-8 -d build/semantic-tests \
  semantic-core/src/main/java/io/github/alieninput/semantic/*.java \
  semantic-core/src/test/java/io/github/alieninput/semantic/*.java
"$JAVA_HOME/bin/java" -cp build/semantic-tests io.github.alieninput.semantic.LexiconTest lexicon/generated/lexicon.tsv
"$JAVA_HOME/bin/java" -cp build/semantic-tests io.github.alieninput.semantic.PngMessageTest app/src/main/assets/glyphs/F0835.png

"$JAVA_HOME/bin/java" -cp build/semantic-tests io.github.alieninput.semantic.ImageLayoutTest

"$JAVA_HOME/bin/java" -cp build/semantic-tests io.github.alieninput.semantic.NumeralsTest lexicon/generated/lexicon.tsv
