#!/usr/bin/env bash
set -euo pipefail
[[ "${GITHUB_ACTIONS:-}" == true && "${RUNNER_ENVIRONMENT:-}" == github-hosted ]]
repository=$(cd "$(dirname "$0")/../.." && pwd)
project="$repository/dist/reward-harness"
export UQ_REWARD_EVIDENCE="$repository/dist/reward-recovery"
cat > "$project/run-server/server.properties" <<'EOF'
online-mode=false
server-ip=127.0.0.1
server-port=25565
level-type=minecraft:flat
spawn-protection=0
difficulty=peaceful
view-distance=2
simulation-distance=2
max-tick-time=-1
EOF
cd "$project"
"$repository/unyielding-quests/gradlew" --no-daemon --console=plain -Dorg.gradle.jvmargs=-Xmx768m createServerLaunchScript > "$UQ_REWARD_EVIDENCE/prepare.log" 2>&1 || { tail -100 "$UQ_REWARD_EVIDENCE/prepare.log"; exit 1; }
server_script=$(find build -type f -name runServer.sh -print -quit)
test -n "$server_script"
for phase in 1 2 3 4 5 6; do
  # Drop precisely the latest balance/marker snapshot while keeping newer quest claims
  # and the forced journal: models one failed SavedData write, not a disk corruption claim.
  if [[ "$phase" == 3 ]]; then
    cp "$UQ_REWARD_EVIDENCE/balance-before-failed-save.dat" "run-server/world/data/unyieldingquests_daily.dat"
  fi
  UQ_REWARD_PHASE="$phase" timeout 720 bash "$server_script" > "$UQ_REWARD_EVIDENCE/server-$phase.log" 2>&1 || { tail -100 "$UQ_REWARD_EVIDENCE/server-$phase.log"; exit 1; }
  grep -q '^passed:' "$UQ_REWARD_EVIDENCE/phase-$phase.txt"
  cat "$UQ_REWARD_EVIDENCE/phase-$phase.txt"
done
