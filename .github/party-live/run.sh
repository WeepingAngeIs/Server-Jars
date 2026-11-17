#!/usr/bin/env bash
set -euo pipefail
[[ "${GITHUB_ACTIONS:-}" == true && "${RUNNER_ENVIRONMENT:-}" == github-hosted ]]
backend="${1:?backend}"; [[ "$backend" == nbt || "$backend" == json ]]
export UQ_PACKAGED_JAIL_SMOKE=1 LIBGL_ALWAYS_SOFTWARE=true ALSOFT_DRIVERS=null
export UQ_WORLD_NAME=jail_integrated_smoke UQ_RESULT_FILE=/tmp/uq-party-live-result
export UQ_LIVE_BACKEND="$backend"
python3 -m unittest discover -s .github/party-live -p 'test_halt.py' -v 2>&1 | tee dist/packaged-smoke/planned-halt-tests.log
for phase in 1 2 3; do
  export UQ_LIVE_PHASE="$phase"
  rm -f dist/packaged-smoke/jail-player-logs/planned-halt.json
  if [[ "$phase" == 1 || "$phase" == 2 ]]; then export UQ_EXPECTED_HALT_CODE="$((91+phase))"; else unset UQ_EXPECTED_HALT_CODE; fi
  export JAVA_TOOL_OPTIONS="-Duq.integratedJail=true -Duq.partyLiveFence=true -Duq.liveBackend=$backend -Duq.livePhase=$phase -Duq.integratedPhase=$phase"
  if bash dist/party-live-harness/meeting-two-player.sh; then status=0; else status=$?; fi
  # Preserve even failed phases; do not replace an earlier result with a later marker.
  cp -a dist/packaged-smoke/jail-player-logs "dist/packaged-smoke/party-live-$backend-phase$phase-logs"
  if [[ "$status" != 0 ]]; then
    tail -100 dist/packaged-smoke/jail-player-logs/prepare.log || true
    exit "$status"
  fi
done
python3 .github/party-live/check.py "$backend"
