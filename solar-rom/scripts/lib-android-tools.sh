#!/usr/bin/env bash
# Resolve aapt / zipalign / apksigner from PATH or ANDROID_HOME (CI + local Gradle SDK).
find_android_build_tool() {
    local name="$1"
    if command -v "$name" >/dev/null 2>&1; then
        command -v "$name"
        return 0
    fi
    if [ -n "${ANDROID_HOME:-}" ] && [ -d "$ANDROID_HOME/build-tools" ]; then
        local p
        p="$(find "$ANDROID_HOME/build-tools" -name "$name" -type f 2>/dev/null | sort -V | tail -1)"
        if [ -n "$p" ] && [ -x "$p" ]; then
            echo "$p"
            return 0
        fi
    fi
    return 1
}
