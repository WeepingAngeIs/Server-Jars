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
world_name="${UQ_WORLD_NAME:-world}"
[[ "$world_name" =~ ^[A-Za-z0-9_-]{1,32}$ ]] || { echo 'Invalid disposable world name' >&2; exit 1; }
printf 'level-name=%s\n' "$world_name" >> "$project/run-server/server.properties"
rm -f "$result_file"
server_pid= client_pid= visitor_pid= server_input=
group_alive() { ps -eo pgid=,stat= | awk -v group="$1" '$1==group && $2 !~ /^Z/ {live=1} END {exit !live}'; }
await_group() {
  for ((attempt=0; attempt<240; attempt++)); do
    if ! group_alive "$1"; then return 0; fi
    sleep 0.25
  done
  return 1
}
diagnose_group() {
  ps -eo pid,ppid,pgid,stat,args | awk -v group="$1" 'NR==1 || $3==group' >&2
  while read -r java_pid; do
    timeout 15s jcmd "$java_pid" Thread.print -l > "$logs/shutdown-threads-$java_pid.txt" 2>&1 || true
  done < <(ps -eo pid=,pgid=,comm= | awk -v group="$1" '$2==group && $3=="java" {print $1}')
}
cleanup() {
  # Drain the two clients while the server is still ticking their disconnects.
  for pid in "$visitor_pid" "$client_pid"; do
    if [[ -n "$pid" ]]; then kill -- -"$pid" 2>/dev/null || true; fi
  done
  for pid in "$visitor_pid" "$client_pid"; do
    if [[ -n "$pid" ]] && ! await_group "$pid"; then diagnose_group "$pid"; return 1; fi
  done
  # Ask our own server to stop and finish native world/player saves first.
  if [[ -n "$server_pid" ]] && group_alive "$server_pid"; then
    shutdown_offset=$(wc -c < "$logs/server.log")
    for ((attempt=0; attempt<30; attempt++)); do
      printf 'list\n' >&9
      sleep 0.5
      if tail -c +"$((shutdown_offset+1))" "$logs/server.log" | grep -q 'There are 0 of a max of'; then break; fi
    done
    rm -f /tmp/uq-smoke-shutdown-ready
    printf 'uqsmoke_shutdown_ready\n' >&9
    for ((attempt=0; attempt<120; attempt++)); do
      if [[ -f /tmp/uq-smoke-shutdown-ready ]]; then break; fi
      sleep 0.5
    done
    if [[ ! -f /tmp/uq-smoke-shutdown-ready ]]; then
      echo 'Native generation/save readiness did not settle before shutdown' >&2
      diagnose_group "$server_pid"
      printf 'stop\n' >&9
      return 1
    fi
    cp /tmp/uq-smoke-shutdown-ready "$logs/shutdown-ready.txt"
    printf 'stop\n' >&9
    if ! await_group "$server_pid"; then
      echo "Graceful disposable server shutdown timed out; terminating owned group $server_pid" >&2
      diagnose_group "$server_pid"
      kill -- -"$server_pid" 2>/dev/null || true
    fi
  fi
  # The wrapper shell can exit before its JVM finishes shutdown. Do not start
  # another server until every owned process group is gone; never remove a lock.
  for pid in "$visitor_pid" "$client_pid" "$server_pid"; do
    if [[ -n "$pid" ]]; then
      if ! await_group "$pid"; then
        echo "Owned disposable process group still shutting down: $pid" >&2
        diagnose_group "$pid"
        return 1
      fi
      wait "$pid" 2>/dev/null || true
    fi
  done
  python3 - "$project/run-server/$world_name/session.lock" <<'PYLOCK'
import errno, fcntl, pathlib, sys, time
path = pathlib.Path(sys.argv[1])
if path.exists():
    with path.open('r+b') as lock:
        for attempt in range(240):
            try:
                fcntl.lockf(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
                fcntl.lockf(lock, fcntl.LOCK_UN)
                break
            except OSError as error:
                if error.errno not in (errno.EACCES, errno.EAGAIN):
                    raise
                time.sleep(0.25)
        else:
            raise SystemExit('Previous disposable server still owns its world lock; refusing restart')
PYLOCK
  if [[ -n "$server_input" ]]; then exec 9>&-; rm -- "$server_input"; fi
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
server_input="$logs/server-input-$$"
mkfifo "$server_input"
exec 9<>"$server_input"
setsid bash "$server_script" < "$server_input" > "$logs/server.log" 2>&1 & server_pid=$!
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
