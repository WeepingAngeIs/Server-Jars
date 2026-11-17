#!/usr/bin/env bash
set -euo pipefail
[[ "${GITHUB_ACTIONS:-}" == true && "${RUNNER_ENVIRONMENT:-}" == github-hosted ]]
backend="${1:?backend}"; [[ "$backend" == nbt || "$backend" == json ]]
export UQ_PACKAGED_JAIL_SMOKE=1 LIBGL_ALWAYS_SOFTWARE=true ALSOFT_DRIVERS=null
export UQ_WORLD_NAME=jail_integrated_smoke UQ_RESULT_FILE=/tmp/uq-party-live-result
for phase in 1 2 3; do
  export JAVA_TOOL_OPTIONS="-Duq.integratedJail=true -Duq.partyLiveFence=true -Duq.liveBackend=$backend -Duq.livePhase=$phase -Duq.integratedPhase=$phase"
  if ! bash .github/scripts/meeting-two-player.sh; then
    tail -100 dist/packaged-smoke/jail-player-logs/prepare.log || true
    exit 1
  fi
  cp -a dist/packaged-smoke/jail-player-logs "dist/packaged-smoke/party-live-$backend-phase$phase-logs"
done
python3 .github/party-live/check.py "$backend"
