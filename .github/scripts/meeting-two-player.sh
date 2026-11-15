#!/usr/bin/env bash
set -euo pipefail
repository=$(cd "$(dirname "$0")/../.." && pwd)
project="$repository/unyielding-quests"
logs="$repository/meeting-two-player-logs"
if [[ "${UQ_PACKAGED_JAIL_SMOKE:-0}" == 1 ]]; then
  [[ "${GITHUB_ACTIONS:-}" == true && "${RUNNER_ENVIRONMENT:-}" == github-hosted ]]
  project="$repository/dist/packaged-harness"
  logs="$repository/dist/packaged-smoke/jail-player-logs"
fi
result_file="${UQ_RESULT_FILE:-/tmp/uq-meeting-two-player-result}"
mkdir -p "$logs" "$project/run-server" "$project/run" "$project/run-visitor"
for client_dir in "$project/run" "$project/run-visitor"; do
  cat > "$client_dir/options.txt" <<'EOF'
guiScale:2
renderDistance:2
simulationDistance:2
maxFps:30
enableVsync:false
onboardAccessibility:false
soundCategory_master:0.0
tutorialStep:none
EOF
done
printf 'eula=true\n' > "$project/run-server/eula.txt"
cat > "$project/run-server/server.properties" <<'EOF'
online-mode=false
server-ip=127.0.0.1
server-port=25565
level-type=minecraft:flat
generator-settings={"layers":[{"block":"minecraft:bedrock","height":1},{"block":"minecraft:dirt","height":2},{"block":"minecraft:grass_block","height":1}],"biome":"minecraft:plains"}
spawn-protection=0
difficulty=peaceful
spawn-monsters=false
view-distance=2
simulation-distance=2
max-players=4
generate-structures=false
max-tick-time=-1
EOF
rm -f "$result_file"
server_pid= client_pid= visitor_pid=
cleanup() {
  for pid in "$visitor_pid" "$client_pid" "$server_pid"; do
    if [[ -n "$pid" ]]; then kill -- -"$pid" 2>/dev/null || true; fi
  done
  for pid in "$visitor_pid" "$client_pid" "$server_pid"; do
    if [[ -n "$pid" ]]; then wait "$pid" 2>/dev/null || true; fi
  done
  cp "$project/run-server/logs/latest.log" "$logs/server-latest.log" 2>/dev/null || true
  cp "$result_file" "$logs/" 2>/dev/null || true
}
trap cleanup EXIT
cd "$project"
"$repository/unyielding-quests/gradlew" --no-daemon --console=plain -Dorg.gradle.jvmargs=-Xmx768m \
  -I "$repository/.github/scripts/meeting-two-player.gradle" \
  createServerLaunchScript createClientLaunchScript createVisitorLaunchScript > "$logs/prepare.log" 2>&1
server_script=$(find build -type f -name runServer.sh -print -quit)
client_script=$(find build -type f -name runClient.sh -print -quit)
visitor_script=$(find build -type f -name runVisitor.sh -print -quit)
test -n "$server_script" && test -n "$client_script" && test -n "$visitor_script"
setsid bash "$server_script" > "$logs/server.log" 2>&1 & server_pid=$!
for ((i=0; i<240; i++)); do
  if grep -q 'Done (' "$logs/server.log"; then break; fi
  kill -0 "$server_pid" 2>/dev/null || { tail -100 "$logs/server.log"; exit 1; }
  sleep 2
done
grep -q 'Done (' "$logs/server.log"
mkdir -p "$project/run/tmp" "$project/run-visitor/tmp"
# LWJGL extracts native libraries at startup. Separate temporary directories prevent
# two simultaneous JVMs racing while creating or loading the same native library.
setsid env TMPDIR="$project/run/tmp" JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:-} -Djava.io.tmpdir=$project/run/tmp" \
  xvfb-run -a -s '-screen 0 640x480x24' bash "$client_script" > "$logs/client.log" 2>&1 & client_pid=$!
sleep 5
setsid env TMPDIR="$project/run-visitor/tmp" JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:-} -Djava.io.tmpdir=$project/run-visitor/tmp" \
  xvfb-run -a -s '-screen 0 640x480x24' bash "$visitor_script" > "$logs/visitor.log" 2>&1 & visitor_pid=$!
for ((i=0; i<360; i++)); do
  if [[ -f "$result_file" ]]; then
    cat "$result_file"
    grep -q '^passed:' "$result_file"
    exit
  fi
  kill -0 "$server_pid" 2>/dev/null && kill -0 "$client_pid" 2>/dev/null && kill -0 "$visitor_pid" 2>/dev/null || break
  sleep 2
done
for log in "$logs/server.log" "$logs/client.log" "$logs/visitor.log"; do
  echo "Last 100 lines of $log:"
  tail -n 100 -- "$log" || true
done
exit 1
