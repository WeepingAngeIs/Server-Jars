#!/usr/bin/env bash
set -euo pipefail
[[ "${GITHUB_ACTIONS:-}" == true && "${RUNNER_ENVIRONMENT:-}" == github-hosted ]]
slice="${1:?slice required}"
export UQ_PACKAGED_JAIL_SMOKE=1 LIBGL_ALWAYS_SOFTWARE=true ALSOFT_DRIVERS=null UQ_WORLD_NAME=jail_certified_smoke
case "$slice" in
  core)
    bash .github/scripts/certified-jail-smoke.sh
    python3 .github/scripts/verify-split-runtime-evidence.py certified
    ;;
  faults)
    export UQ_RESULT_FILE=/tmp/uq-packed-wallet-fault
    for phase in 1 2 3 4; do
      export JAVA_TOOL_OPTIONS="-Duq.integratedJail=true -Duq.certifiedJail=true -Duq.packedWalletFault=true -Duq.walletFaultPhase=$phase -Duq.certifiedPhase=$phase"
      bash .github/scripts/meeting-two-player.sh
      cp -a dist/packaged-smoke/jail-player-logs "dist/packaged-smoke/wallet-fault-phase$phase"
    done
    ;;
  *) exit 1;;
esac
python3 .github/packed-wallet/verify.py "$slice"
