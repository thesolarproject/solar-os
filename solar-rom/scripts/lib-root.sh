#!/usr/bin/env bash
# When loop-mount runs as uid 0 (docker --privileged), existing `sudo cmd` lines still work.
ensure_sudo_shim_when_root() {
    # Docker --privileged often runs uid 0 without the sudo package; scripts still call `sudo …`.
    if [ "$(id -u)" -eq 0 ] && ! command -v sudo >/dev/null 2>&1; then
        mkdir -p /usr/local/bin
        printf '%s\n' '#!/bin/sh' 'exec "$@"' > /usr/local/bin/sudo
        chmod 755 /usr/local/bin/sudo
        # CI/docker PATH may omit /usr/local/bin — prepend so require_cmd sudo passes.
        export PATH="/usr/local/bin:${PATH:-/usr/bin:/bin}"
    fi
}
