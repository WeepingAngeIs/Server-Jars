#!/usr/bin/env bash
set -euo pipefail
repository=$(cd "$(dirname "$0")/../.." && pwd)
project="$repository/unyielding-quests"
logs="$repository/meeting-two-player-logs"
mkdir -p "$logs" "$project/run-server" "$project/run" "$project/run-visitor"
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
rm -f /tmp/uq-meeting-two-player-result
server_pid= client_pid= visitor_pid=
cleanup() {
  for pid in "$visitor_pid" "$client_pid" "$server_pid"; do
    if [[ -n "$pid" ]]; then kill -- -"$pid" 2>/dev/null || true; fi
  done
  cp "$project/run-server/logs/latest.log" "$logs/server-latest.log" 2>/dev/null || true
  cp /tmp/uq-meeting-two-player-result "$logs/" 2>/dev/null || true
}
trap cleanup EXIT
cd "$project"
./gradlew --no-daemon --console=plain -Dorg.gradle.jvmargs=-Xmx768m \
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
setsid xvfb-run -a -s '-screen 0 640x480x24' bash "$client_script" > "$logs/client.log" 2>&1 & client_pid=$!
setsid xvfb-run -a -s '-screen 0 640x480x24' bash "$visitor_script" > "$logs/visitor.log" 2>&1 & visitor_pid=$!
for ((i=0; i<360; i++)); do
  if [[ -f /tmp/uq-meeting-two-player-result ]]; then
    cat /tmp/uq-meeting-two-player-result
    grep -q '^passed:' /tmp/uq-meeting-two-player-result
    exit
  fi
  kill -0 "$server_pid" 2>/dev/null && kill -0 "$client_pid" 2>/dev/null && kill -0 "$visitor_pid" 2>/dev/null || break
  sleep 2
done
tail -100 "$logs/server.log" "$logs/client.log" "$logs/visitor.log"
exit 1
