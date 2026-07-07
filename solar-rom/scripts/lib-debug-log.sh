#!/usr/bin/env bash
# Append one NDJSON debug line for Cursor debug sessions (ROM build + device audit).
# Usage: debug_rom_log HYPOTHESIS_ID LOCATION MESSAGE [key=val ...]
debug_rom_log() {
    local hypothesis_id="$1" location="$2" message="$3"
    shift 3
    local log_path="${SOLAR_DEBUG_LOG:-$REPO_ROOT/.cursor/debug-40acef.log}"
    local ts data_json pair k v
    ts=$(date +%s%3N 2>/dev/null || echo "$(($(date +%s)*1000))")
    data_json=""
    for pair in "$@"; do
        k="${pair%%=*}"
        v="${pair#*=}"
        if [ -n "$data_json" ]; then data_json="$data_json,"; fi
        # Minimal JSON escape for paths and short strings.
        v="${v//\\/\\\\}"
        v="${v//\"/\\\"}"
        data_json="$data_json\"$k\":\"$v\""
    done
    mkdir -p "$(dirname "$log_path")"
    printf '{"sessionId":"40acef","hypothesisId":"%s","location":"%s","message":"%s","data":{%s},"timestamp":%s}\n' \
        "$hypothesis_id" "$location" "$message" "$data_json" "$ts" >>"$log_path"
}
