#!/usr/bin/env bash
# solar-update OTA hosts APKs on main — Pages should deploy from branch, not Actions deploy-pages.
# 2026-07-16 — Pages Admin API is optional; fine-grained PATs often lack it while Contents write works.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
# shellcheck source=/dev/null
source "$ROOT/scripts/env.sh"

PAT="${SOLAR_GITHUB_PAT:-${SOLAR_UPDATES_PAT:-}}"
UPDATE_REPO="${SOLAR_UPDATE_REPO:-thesolarproject/solar-update}"

if [[ -z "$PAT" ]]; then
  echo "::error::SOLAR_GITHUB_PAT required to configure ${UPDATE_REPO} Pages." >&2
  exit 1
fi

echo "== Configure github.com/${UPDATE_REPO} Pages: legacy deploy from main =="
HTTP_CODE="$(curl -sS -o /tmp/solar-pages.json -w "%{http_code}" \
  -X PUT \
  -H "Authorization: token ${PAT}" \
  -H "Accept: application/vnd.github+json" \
  "https://api.github.com/repos/${UPDATE_REPO}/pages" \
  -d '{"build_type":"legacy","source":{"branch":"main","path":"/"}}' || true)"
if [[ "$HTTP_CODE" != "200" && "$HTTP_CODE" != "201" && "$HTTP_CODE" != "204" ]]; then
  # Soft-fail: Contents write is enough to publish APKs; Pages may already be set in the UI.
  echo "::warning::Pages API returned HTTP ${HTTP_CODE} for ${UPDATE_REPO} (PAT may lack Pages admin)."
  if [[ -f /tmp/solar-pages.json ]]; then
    cat /tmp/solar-pages.json >&2 || true
    echo "" >&2
  fi
  GET_CODE="$(curl -sS -o /tmp/solar-pages-get.json -w "%{http_code}" \
    -H "Authorization: token ${PAT}" \
    -H "Accept: application/vnd.github+json" \
    "https://api.github.com/repos/${UPDATE_REPO}/pages" || true)"
  if [[ "$GET_CODE" == "200" ]]; then
    echo "  Pages site already exists (GET 200) — continuing without reconfigure."
    python3 - /tmp/solar-pages-get.json <<'PY' || true
import json, sys
pages = json.load(open(sys.argv[1], encoding="utf-8"))
print("  Pages build_type=%s branch=%s url=%s" % (
    pages.get("build_type", ""),
    (pages.get("source") or {}).get("branch", ""),
    pages.get("html_url", "")))
PY
  else
    echo "::warning::Could not reconfigure or read Pages (GET ${GET_CODE})."
    echo "  Ensure thesolarproject.github.io/solar-update is enabled in repo Settings → Pages."
  fi
else
  if [[ "$HTTP_CODE" == "204" ]]; then
    HTTP_CODE="$(curl -sS -o /tmp/solar-pages.json -w "%{http_code}" \
      -H "Authorization: token ${PAT}" \
      -H "Accept: application/vnd.github+json" \
      "https://api.github.com/repos/${UPDATE_REPO}/pages" || true)"
  fi
  if [[ "$HTTP_CODE" == "200" ]]; then
    python3 - /tmp/solar-pages.json <<'PY' || true
import json, sys
pages = json.load(open(sys.argv[1], encoding="utf-8"))
build = pages.get("build_type", "")
branch = (pages.get("source") or {}).get("branch", "")
print(f"  Pages build_type={build} branch={branch} url={pages.get('html_url', '')}")
PY
  fi
fi

WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
git clone --depth 1 "https://x-access-token:${PAT}@github.com/${UPDATE_REPO}.git" "$WORK/repo"
cd "$WORK/repo"
git config user.name "thesolarproject"
git config user.email "anonymous@local"

REMOVED=0
if [[ -d .github/workflows ]]; then
  for wf in .github/workflows/*.yml .github/workflows/*.yaml; do
    [[ -f "$wf" ]] || continue
    if grep -qE 'deploy-pages|upload-pages-artifact|github-pages' "$wf" 2>/dev/null; then
      echo "  removing workflow $(basename "$wf") (conflicts with branch Pages)"
      git rm -f "$wf"
      REMOVED=1
    fi
  done
fi

if [[ "$REMOVED" -eq 1 ]]; then
  git commit -m "Remove Actions Pages deploy — OTA serves from main branch root."
  git push "https://x-access-token:${PAT}@github.com/${UPDATE_REPO}.git" HEAD:main
  echo "  removed deploy-pages workflow(s)"
else
  echo "  no deploy-pages workflow present"
fi

echo "solar-update Pages configure step finished (Contents write verified separately by verify-ota-pat)"
