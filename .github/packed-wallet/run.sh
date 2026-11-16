#!/usr/bin/env bash
set -euo pipefail
[[ "${GITHUB_ACTIONS:-}" == true && "${RUNNER_ENVIRONMENT:-}" == github-hosted ]]
scenario="${1:?scenario required}"
case "$scenario" in
 lifecycle) phases=5;; occupied|return-unsaved|return-saved) phases=4;; remove-unsaved|remove-saved) phases=3;; *) exit 2;;
esac
export UQ_PACKAGED_JAIL_SMOKE=1 LIBGL_ALWAYS_SOFTWARE=true ALSOFT_DRIVERS=null
export UQ_WORLD_NAME=jail_integrated_smoke UQ_RESULT_FILE=/tmp/uq-packed-wallet-result
for ((phase=1; phase<=phases; phase++)); do
  export JAVA_TOOL_OPTIONS="-Duq.integratedJail=true -Duq.certifiedJail=true -Duq.packedWallet=true -Duq.walletScenario=$scenario -Duq.walletPhase=$phase -Duq.integratedPhase=$phase -Duq.certifiedPhase=$phase"
  rm -f dist/packaged-smoke/jail-player-logs/server-exit-code
  bash .github/scripts/packed-wallet-two-player.generated.sh
  cp -a dist/packaged-smoke/jail-player-logs "dist/packaged-smoke/packed-$scenario-phase$phase-logs"
  python3 .github/packed-wallet/verify.py "$scenario" "$phase"
done
python3 .github/packed-wallet/verify.py "$scenario"
