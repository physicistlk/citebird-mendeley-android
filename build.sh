#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"
TOOLCHAIN=/mnt/nas_active/rea/.toolchain

export JAVA_HOME="$TOOLCHAIN/jdk21"
export PATH="$JAVA_HOME/bin:$PATH"
export ANDROID_HOME="$TOOLCHAIN/sdk"
export ANDROID_SDK_ROOT="$ANDROID_HOME"

GRADLE="$TOOLCHAIN/gradle-8.13/bin/gradle"
[ -x "$GRADLE" ] || GRADLE=gradle

exec "$GRADLE" -p "$ROOT" "$@"
