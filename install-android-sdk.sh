#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SDK_ROOT="${ANDROID_HOME:-$ROOT_DIR/.android-sdk}"
TOOLS_VERSION="15859902"
TOOLS_SHA256="4e4c464f145a7512b57d088ac6c278c03c9eea610886b35a5e0804e74eedf583"
TOOLS_ZIP="commandlinetools-linux-${TOOLS_VERSION}_latest.zip"
TOOLS_URL="https://dl.google.com/android/repository/${TOOLS_ZIP}"
GRADLE_VERSION="8.9"
GRADLE_ZIP="gradle-${GRADLE_VERSION}-bin.zip"
GRADLE_URL="https://services.gradle.org/distributions/${GRADLE_ZIP}"
CACHE_DIR="$ROOT_DIR/.downloads"

if [[ "$(uname -s)" != "Linux" ]]; then
  echo "This helper is for Linux. On macOS/Windows install Android Studio or use the matching Android command-line tools package."
  exit 1
fi

mkdir -p "$SDK_ROOT/cmdline-tools" "$CACHE_DIR"

if [[ ! -x "$SDK_ROOT/cmdline-tools/latest/bin/sdkmanager" ]]; then
  echo "Downloading Android command-line tools ${TOOLS_VERSION}..."
  curl -fL "$TOOLS_URL" -o "$CACHE_DIR/$TOOLS_ZIP"
  echo "$TOOLS_SHA256  $CACHE_DIR/$TOOLS_ZIP" | sha256sum -c -
  rm -rf "$SDK_ROOT/cmdline-tools/latest" "$CACHE_DIR/cmdline-tools"
  unzip -q "$CACHE_DIR/$TOOLS_ZIP" -d "$CACHE_DIR"
  mv "$CACHE_DIR/cmdline-tools" "$SDK_ROOT/cmdline-tools/latest"
fi

export ANDROID_HOME="$SDK_ROOT"
export ANDROID_SDK_ROOT="$SDK_ROOT"
export PATH="$SDK_ROOT/cmdline-tools/latest/bin:$SDK_ROOT/platform-tools:$PATH"

# The explicit 'yes' accepts Google's SDK component licenses. Run this script only if you accept them.
yes | sdkmanager --licenses >/dev/null || true
sdkmanager "platform-tools" "platforms;android-35" "build-tools;35.0.0"

printf 'sdk.dir=%s\n' "$SDK_ROOT" > "$ROOT_DIR/local.properties"

if [[ ! -x "$ROOT_DIR/gradlew" ]]; then
  echo "Creating Gradle ${GRADLE_VERSION} wrapper..."
  curl -fL "$GRADLE_URL" -o "$CACHE_DIR/$GRADLE_ZIP"
  rm -rf "$CACHE_DIR/gradle-${GRADLE_VERSION}"
  unzip -q "$CACHE_DIR/$GRADLE_ZIP" -d "$CACHE_DIR"
  "$CACHE_DIR/gradle-${GRADLE_VERSION}/bin/gradle" -p "$ROOT_DIR" wrapper --gradle-version "$GRADLE_VERSION"
fi

echo
echo "Android SDK installed at: $SDK_ROOT"
echo "sdkmanager: $(sdkmanager --version | head -1)"
echo "Next: ./gradlew assembleDebug"
