#!/usr/bin/env bash
# Runs N cold JVMs (default 20); pass "warm" to apply the fix. Exits 1 on the first deadlock.
set -euo pipefail
cd "$(dirname "$0")"
attempts=${1:-20}
shift || true
./gradlew -q installDist
for i in $(seq 1 "$attempts"); do
    printf 'attempt %s: ' "$i"
    build/install/exposed-init-deadlock-repro/bin/exposed-init-deadlock-repro "$@" || exit 1
done
