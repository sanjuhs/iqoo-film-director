#!/bin/sh
# Pre-event research tooling, 4 October 2026. Reuse local JDK/SDK/cache.
set -eu
TASK_ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
if [ -z "${JAVA_HOME:-}" ] && [ -d /opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ]; then
  export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
fi
if [ "$#" -eq 0 ]; then set -- :app:assembleDebug; fi
exec "$TASK_ROOT/gradlew" -p "$TASK_ROOT" "$@"
