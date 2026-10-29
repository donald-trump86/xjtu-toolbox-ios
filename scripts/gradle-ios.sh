#!/bin/sh
# Use a system JDK 21, or the optional repository-local toolchain. Never replace HOME.
set -eu
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
if [ -z "${JAVA_HOME:-}" ] && [ -x "$ROOT/build/toolchains/jdk21/Contents/Home/bin/java" ]; then
    JAVA_HOME="$ROOT/build/toolchains/jdk21/Contents/Home"
    export JAVA_HOME
fi
GRADLE_USER_HOME=${GRADLE_USER_HOME:-"$ROOT/.gradle-user-home"}
KONAN_DATA_DIR=${KONAN_DATA_DIR:-"$ROOT/build/konan"}
ANDROID_USER_HOME=${ANDROID_USER_HOME:-"$ROOT/build/android-user-home"}
if [ -z "${ANDROID_HOME:-}" ] && [ -d "$ROOT/build/toolchains/android-sdk/cmdline-tools" ]; then
    ANDROID_HOME="$ROOT/build/toolchains/android-sdk"
    export ANDROID_HOME
fi
mkdir -p "$ANDROID_USER_HOME"
export GRADLE_USER_HOME KONAN_DATA_DIR ANDROID_USER_HOME
exec sh "$ROOT/gradlew" -p "$ROOT" "$@"
