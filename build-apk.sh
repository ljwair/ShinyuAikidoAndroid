#!/usr/bin/env bash
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT_DIR"
if [[ ! -x ./gradlew ]]; then
  echo "Gradle wrapper not found. Run ./install-android-sdk.sh first."
  exit 1
fi
./gradlew assembleDebug
APK="$ROOT_DIR/app/build/outputs/apk/debug/app-debug.apk"
if [[ -f "$APK" ]]; then
  echo "APK created: $APK"
fi
