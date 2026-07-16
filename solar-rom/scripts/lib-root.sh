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
    elif [ "$(id -u)" -ne 0 ] && command -v sudo >/dev/null 2>&1; then
        if [ -z "${SUDO_ASKPASS:-}" ]; then
            local askpass_path="/home/deck/.local/bin/solar-askpass.sh"
            if [ ! -w "/home/deck/.local/bin" ] && ! mkdir -p "/home/deck/.local/bin" 2>/dev/null; then
                askpass_path="/tmp/solar-askpass-${USER:-deck}.sh"
            fi
            cat > "$askpass_path" << 'EOF'
#!/usr/bin/env bash
prompt="${1:-Sudo password required for Solar ROM build:}"
if command -v kdialog >/dev/null 2>&1 && [ -n "${DISPLAY:-${WAYLAND_DISPLAY:-}}" ]; then
    kdialog --password "$prompt" --title "Solar ROM Builder (sudo)"
elif command -v zenity >/dev/null 2>&1 && [ -n "${DISPLAY:-${WAYLAND_DISPLAY:-}}" ]; then
    zenity --password --title "Solar ROM Builder (sudo)" --text "$prompt"
elif command -v ssh-askpass >/dev/null 2>&1 && [ -n "${DISPLAY:-${WAYLAND_DISPLAY:-}}" ]; then
    ssh-askpass "$prompt"
elif command -v systemd-ask-password >/dev/null 2>&1 && [ -n "${DISPLAY:-${WAYLAND_DISPLAY:-}}" ]; then
    systemd-ask-password "$prompt"
else
    read -rsp "$prompt " pass
    echo >&2
    echo "$pass"
fi
EOF
            chmod 755 "$askpass_path" 2>/dev/null || true
            export SUDO_ASKPASS="$askpass_path"
        fi
        if [ ! -t 0 ] || [ -n "${USE_SUDO_ASKPASS:-}" ]; then
            sudo() {
                command sudo -A "$@"
            }
            export -f sudo 2>/dev/null || true
        fi
    fi
}

ensure_sudo_shim_when_root
