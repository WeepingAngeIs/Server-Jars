#!/usr/bin/env bash
set -euo pipefail
[[ "${GITHUB_ACTIONS:-}" == true && "${RUNNER_ENVIRONMENT:-}" == github-hosted ]]
repository=$(cd "$(dirname "$0")/../.." && pwd)
project="$repository/dist/packaged-harness"
results="$repository/dist/packaged-smoke"
mkdir -p "$results" "$project/run-server"
test -f "$project/run-server/eula.txt"
test -f "$UQ_BUNDLE_JAR"
cat > "$project/run-server/server.properties" <<'EOF'
online-mode=false
server-ip=127.0.0.1
server-port=25565
level-type=minecraft:flat
spawn-protection=0
difficulty=peaceful
spawn-monsters=false
view-distance=2
simulation-distance=2
max-tick-time=-1
EOF
cd "$project"
"$repository/unyielding-quests/gradlew" --no-daemon --console=plain -Dorg.gradle.jvmargs=-Xmx768m createServerLaunchScript > "$results/prepare.log" 2>&1 || { tail -100 "$results/prepare.log"; exit 1; }
server_script=$(find build -type f -name runServer.sh -print -quit)
test -n "$server_script"
pid=
cleanup() { if [[ -n "$pid" ]]; then kill -- -"$pid" 2>/dev/null || true; wait "$pid" 2>/dev/null || true; fi; }
trap cleanup EXIT
for phase in 1 2; do
  marker="/tmp/uq-packaged-$phase.txt"
  rm -f "$marker"
  UQ_PACKAGED_PHASE="$phase" setsid bash "$server_script" > "$results/server-$phase.log" 2>&1 & pid=$!
  for ((i=0; i<360; i++)); do
    if [[ -f "$marker" ]]; then
      cat "$marker"
      grep -q '^passed:' "$marker" || exit 1
      break
    fi
    kill -0 "$pid" 2>/dev/null || { tail -100 "$results/server-$phase.log"; exit 1; }
    sleep 2
  done
  if [[ ! -f "$marker" ]]; then tail -100 "$results/server-$phase.log"; exit 1; fi
  cp "$marker" "$results/"
  kill -- -"$pid" 2>/dev/null || true
  wait "$pid" 2>/dev/null || true
  pid=
done
