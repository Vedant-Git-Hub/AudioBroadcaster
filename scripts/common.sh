#!/usr/bin/env bash

PROJECT_ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
TOOLCHAIN_ROOT="${AUDIOBRIDGE_TOOLCHAIN_DIR:-$(cd -- "$PROJECT_ROOT/.." && pwd)}"
IDF_DIR="${AUDIOBRIDGE_IDF_PATH:-$TOOLCHAIN_ROOT/esp-idf}"
ADF_DIR="${AUDIOBRIDGE_ADF_PATH:-$TOOLCHAIN_ROOT/esp-adf}"
SDK_DIR="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}}"

IDF_COMMIT=4c2820d377d1375e787bcef612f0c32c1427d183
ADF_COMMIT=01488579932e18c327a151c5b61efff38c9af880

load_firmware_tools() {
    if [[ ! -f "$IDF_DIR/export.sh" || ! -f "$ADF_DIR/CMakeLists.txt" ]]; then
        printf 'Missing ESP-IDF or ESP-ADF. Run %s/setup_host first.\n' "$PROJECT_ROOT" >&2
        return 1
    fi
    # The export script makes idf.py, compilers, Python environment, and CMake available.
    source "$IDF_DIR/export.sh"
    export ADF_PATH="$ADF_DIR"
}

load_android_tools() {
    export ANDROID_HOME="$SDK_DIR"
    export ANDROID_SDK_ROOT="$SDK_DIR"
    if [[ ! -f "$SDK_DIR/platforms/android-36/android.jar" ||
          ! -x "$SDK_DIR/platform-tools/adb" ]]; then
        printf 'Android SDK platform 36 or platform-tools is missing. Run %s/setup_host first.\n' "$PROJECT_ROOT" >&2
        return 1
    fi
    export PATH="$SDK_DIR/platform-tools:$PATH"
}
