#!/usr/bin/env bash
set -euo pipefail
[[ "${GITHUB_ACTIONS:-}" == true && "${RUNNER_ENVIRONMENT:-}" == github-hosted ]]
python3 .github/player-only/isolate.py
export UQ_PACKAGED_JAIL_SMOKE=1 LIBGL_ALWAYS_SOFTWARE=true ALSOFT_DRIVERS=null
export UQ_WORLD_NAME=jail_certified_smoke UQ_RESULT_FILE=/tmp/uq-player-only-result
for phase in 1 2 3; do
  export JAVA_TOOL_OPTIONS="-Duq.integratedJail=true -Duq.certifiedJail=true -Duq.playerOnly=true -Duq.playerOnlyPhase=$phase -Duq.certifiedPhase=$phase"
  if bash .github/scripts/meeting-two-player.sh; then status=0; else status=$?; fi
  cp -a dist/packaged-smoke/jail-player-logs "dist/packaged-smoke/player-only-phase$phase-logs"
  if [[ "$status" != 0 ]]; then tail -100 dist/packaged-smoke/jail-player-logs/prepare.log || true; exit "$status"; fi
done
python3 .github/player-only/check.py
