#!/usr/bin/env bash
set -euo pipefail
[[ "${GITHUB_ACTIONS:-}" == true && "${RUNNER_ENVIRONMENT:-}" == github-hosted ]]
export UQ_PACKAGED_JAIL_SMOKE=1 LIBGL_ALWAYS_SOFTWARE=true ALSOFT_DRIVERS=null
export UQ_WORLD_NAME=jail_integrated_smoke UQ_RESULT_FILE=/tmp/uq-session-result
unset UQ_EXPECTED_HALT_CODE
for phase in 1 2; do
 export JAVA_TOOL_OPTIONS="-Duq.integratedJail=true -Duq.partyLiveFence=true -Duq.partyMutation=true -Duq.partySession=true -Duq.sessionDiagnostic=true -Duq.liveBackend=nbt -Duq.livePhase=$phase -Duq.sessionPhase=$phase -Duq.integratedPhase=$phase"
 if bash dist/party-live-harness/meeting-two-player.sh; then status=0; else status=$?; fi
 cp -a dist/packaged-smoke/jail-player-logs "dist/packaged-smoke/session-phase$phase-logs"
 if [[ "$status" != 0 ]]; then tail -100 dist/packaged-smoke/jail-player-logs/prepare.log || true; exit "$status"; fi
done
python3 .github/party-session/check.py
