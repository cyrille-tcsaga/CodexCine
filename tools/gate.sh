#!/usr/bin/env bash
# The quality gate of every migration story: unit tests, debug build and lint must all pass.
set -euo pipefail
cd "$(dirname "$0")/.."
./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug -q
echo "gate: OK"
