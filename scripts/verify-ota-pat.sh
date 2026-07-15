#!/usr/bin/env bash
# Fail fast when SOLAR_GITHUB_PAT cannot push to thesolarproject/solar-update.
# 2026-07-16 — Rebase+retry so concurrent main/nightly CI jobs do not false-fail with
# "fetch first" / non-fast-forward (was misread as missing Contents write).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
# shellcheck source=/dev/null
source "$ROOT/scripts/env.sh"

PAT="${SOLAR_GITHUB_PAT:-${SOLAR_UPDATES_PAT:-}}"
UPDATE_REPO="${SOLAR_UPDATE_REPO:-thesolarproject/solar-update}"
REMOTE_URL="https://x-access-token:${PAT}@github.com/${UPDATE_REPO}.git"

if [[ -z "$PAT" ]]; then
  echo "::error::SOLAR_GITHUB_PAT secret is missing — add a PAT with Contents read/write on ${UPDATE_REPO}." >&2
  exit 1
fi

HTTP_CODE="$(curl -sS -o /tmp/solar-ota-repo.json -w "%{http_code}" \
  -H "Authorization: token ${PAT}" \
  "https://api.github.com/repos/${UPDATE_REPO}")"
if [[ "$HTTP_CODE" != "200" ]]; then
  echo "::error::SOLAR_GITHUB_PAT cannot access github.com/${UPDATE_REPO} (HTTP ${HTTP_CODE}) — rotate the repo secret." >&2
  exit 128
fi

python3 - /tmp/solar-ota-repo.json <<'PY'
import json, sys
with open(sys.argv[1], encoding="utf-8") as handle:
    repo = json.load(handle)
perms = repo.get("permissions") or {}
if perms.get("push") is False:
    raise SystemExit(
        "SOLAR_GITHUB_PAT has no push permission on solar-update "
        "(API permissions.push=false — need Contents: Read and write)")
print("  API permissions.push=%s admin=%s" % (
    perms.get("push"), perms.get("admin")))
PY

WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
if ! git clone --depth 20 "$REMOTE_URL" "$WORK/repo" >/dev/null 2>&1; then
  echo "::error::SOLAR_GITHUB_PAT cannot clone github.com/${UPDATE_REPO}." >&2
  exit 128
fi

cd "$WORK/repo"
git config user.name "thesolarproject"
git config user.email "anonymous@local"
git config pull.rebase true

# Unique marker so concurrent CI jobs do not collide on the same tree content.
MARKER=".ci-ota-pat-write-check"
RUN_ID="${GITHUB_RUN_ID:-local}-$(date -u +%s)-$RANDOM"
echo "$RUN_ID" > "$MARKER"
git add "$MARKER"
git commit -m "CI PAT write preflight ${RUN_ID} [skip ci]" >/dev/null

push_with_rebase() {
  local attempt=1
  local max=5
  while [[ "$attempt" -le "$max" ]]; do
    if git push "$REMOTE_URL" HEAD:main 2>/tmp/solar-ota-push.err; then
      return 0
    fi
    echo "  push attempt ${attempt}/${max} rejected — rebasing onto origin/main"
    cat /tmp/solar-ota-push.err >&2 || true
    git fetch "$REMOTE_URL" main:refs/remotes/origin/main 2>/dev/null || git fetch origin main 2>/dev/null || true
    if ! git rebase origin/main 2>/tmp/solar-ota-rebase.err; then
      # Recreate commit on top of latest main if rebase conflicts on disposable marker.
      git rebase --abort 2>/dev/null || true
      git reset --hard origin/main
      echo "$RUN_ID" > "$MARKER"
      git add "$MARKER"
      git commit -m "CI PAT write preflight ${RUN_ID} [skip ci]" >/dev/null
    fi
    attempt=$((attempt + 1))
    sleep 1
  done
  return 1
}

if ! push_with_rebase; then
  echo "::error::SOLAR_GITHUB_PAT cannot push to github.com/${UPDATE_REPO} after rebase retries." >&2
  echo "::error::If API permissions.push is true, this is usually a race — re-run the job." >&2
  echo "::error::If push is false, fine-grained PAT needs Contents: Read and write on ${UPDATE_REPO}." >&2
  exit 128
fi

# Cleanup marker (best-effort; do not fail the build if another job already moved main).
git fetch "$REMOTE_URL" main:refs/remotes/origin/main 2>/dev/null || true
git reset --hard origin/main
if [[ -f "$MARKER" ]]; then
  git rm -f "$MARKER" >/dev/null 2>&1 || rm -f "$MARKER"
  if git diff --staged --quiet 2>/dev/null; then
    :
  else
    git commit -m "CI PAT write preflight cleanup ${RUN_ID} [skip ci]" >/dev/null || true
    git push "$REMOTE_URL" HEAD:main 2>/dev/null || true
  fi
fi

echo "solar-update PAT preflight OK (Contents read + write verified on ${UPDATE_REPO})"
echo "  Note: GitHub Pages admin is separate; configure-solar-update-pages soft-warns if missing."
