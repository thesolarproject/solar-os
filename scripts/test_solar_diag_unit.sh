#!/usr/bin/env bash
# 2026-07-15 — Host unit tests for solar-diag client + reporter (no device required).
set -euo pipefail
REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$REPO_ROOT"
./gradlew :app:testDebugUnitTest \
  --tests "com.solar.launcher.diag.SolarDiagClientTest" \
  --tests "com.solar.launcher.soulseek.SolarDiagnosticReporterTest" \
  --tests "com.solar.launcher.soulseek.SolarDeveloperAccountsTest" \
  -q
echo "OK: solar-diag unit checks"
