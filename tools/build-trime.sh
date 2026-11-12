#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
: "${DEV_ENV_ROOT:?source ~/project/dev-env/android-env.sh first}"
TRIME_COMMIT=b5cad1a49095a3652e9188982de400ca3a653f37
DOUBLE_COMMIT=01a13287cbd27819be1c34fa1ddc1b3643d5001b
test "$(git -C trime rev-parse HEAD)" = "$TRIME_COMMIT" || { echo 'Trime commit differs from the pinned adapter'; exit 1; }
test "$(git -C vendor/rime-double-pinyin rev-parse HEAD)" = "$DOUBLE_COMMIT" || { echo 'Double-pinyin source differs from the pin'; exit 1; }
bash tools/test-semantic.sh
python3 tools/integrate_trime.py
python3 tools/prepare_trime_assets.py "$DEV_ENV_ROOT/downloads/trime-v3.3.13-arm64.apk"
GRADLE="$DEV_ENV_ROOT/gradle/gradle-9.8.1/bin/gradle"
test -x "$GRADLE" || { echo 'Install verified Gradle 9.8.1 in the shared environment'; exit 1; }
"$GRADLE" -p trime -PbuildABI=arm64-v8a :app:assembleDebug --console=plain "$@"
