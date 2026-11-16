#!/usr/bin/env bash
set -euo pipefail
[[ "${GITHUB_ACTIONS:-}" == true && "${RUNNER_ENVIRONMENT:-}" == github-hosted ]]
kind="${1:?party, wallet, or both required}"
[[ "$kind" == party || "$kind" == wallet || "$kind" == both ]]
export UQ_PACKAGED_JAIL_SMOKE=1 LIBGL_ALWAYS_SOFTWARE=true ALSOFT_DRIVERS=null
export UQ_WORLD_NAME=jail_integrated_smoke UQ_RESULT_FILE=/tmp/uq-resource-continuation-result
for phase in 1 2 3 4; do
  export JAVA_TOOL_OPTIONS="-Duq.integratedJail=true -Duq.partyWalletProbe=true -Duq.resourceContinuation=true -Duq.continuationKind=$kind -Duq.continuationPhase=$phase -Duq.integratedPhase=$phase"
  bash .github/scripts/meeting-two-player.sh
  cp -a dist/packaged-smoke/jail-player-logs "dist/packaged-smoke/continuation-$kind-phase$phase-logs"
done
python3 .github/resource-continuation/verify.py "$kind"
