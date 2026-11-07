#!/usr/bin/env bash
set -euo pipefail

repository=$(cd "$(dirname "$0")/../.." && pwd)
project="$repository/unyielding-quests"
results="$repository/tutorial-smoke"
mkdir -p "$results" "$project/run-server" "$project/run" /tmp/uqshots

cat > "$project/run-server/eula.txt" <<'EOF'
eula=true
EOF
cat > "$project/run-server/server.properties" <<'EOF'
online-mode=false
server-ip=127.0.0.1
server-port=25565
level-type=minecraft:flat
generator-settings={"layers":[{"block":"minecraft:bedrock","height":1},{"block":"minecraft:dirt","height":2},{"block":"minecraft:grass_block","height":1}],"biome":"minecraft:plains"}
spawn-protection=0
view-distance=2
simulation-distance=2
max-players=2
enable-command-block=false
generate-structures=false
sync-chunk-writes=false
motd=Unyielding tutorial cloud test
EOF
cat > "$project/run/options.txt" <<'EOF'
guiScale:2
renderDistance:2
simulationDistance:2
maxFps:30
enableVsync:false
onboardAccessibility:false
soundCategory_master:0.0
tutorialStep:none
EOF
printf 'tutorialtest\nadmintest\n' > /tmp/uq-cmd.txt
rm -f /tmp/uq-tutorial-test-done /tmp/uq-admin-test-done /tmp/uq-admin-server-cmd /tmp/uq-admin-server-result

server_pid=
client_pid=
cleanup() {
    if [[ -n "$client_pid" ]]; then kill -- -"$client_pid" 2>/dev/null || true; fi
    if [[ -n "$server_pid" ]]; then kill -- -"$server_pid" 2>/dev/null || true; fi
    cp -a /tmp/uqshots "$results/screenshots" 2>/dev/null || true
    cp /tmp/uq-tutorial-test-done "$results/" 2>/dev/null || true
    cp /tmp/uq-admin-test-done /tmp/uq-admin-server-result "$results/" 2>/dev/null || true
    cp "$project/run-server/config/unyieldingquests-common.toml" "$results/final-server-config.toml" 2>/dev/null || true
    cp "$project/run/logs/latest.log" "$results/client-latest.log" 2>/dev/null || true
    cp "$project/run-server/logs/latest.log" "$results/server-latest.log" 2>/dev/null || true
    cp -a "$project/run/crash-reports" "$results/client-crashes" 2>/dev/null || true
    cp -a "$project/run-server/crash-reports" "$results/server-crashes" 2>/dev/null || true
}
trap cleanup EXIT

cd "$project"
./gradlew --no-daemon --console=plain -Dorg.gradle.jvmargs=-Xmx768m \
    -I "$repository/.github/scripts/ci-runs.gradle" -Duq.autotest=1 \
    createServerLaunchScript createClientLaunchScript > "$results/prepare-console.log" 2>&1
server_script=$(find build -type f -name 'runServer.sh' -print -quit)
client_script=$(find build -type f -name 'runClient.sh' -print -quit)
if [[ ! -f "$server_script" || ! -f "$client_script" ]]; then
    cat "$results/prepare-console.log"
    echo 'ModDev standalone launch scripts were not generated' >&2
    exit 1
fi
setsid bash "$server_script" > "$results/server-console.log" 2>&1 &
server_pid=$!
for ((attempt=0; attempt<240; attempt++)); do
    if grep -q 'Done (' "$results/server-console.log"; then break; fi
    if ! kill -0 "$server_pid" 2>/dev/null; then
        tail -100 "$results/server-console.log"
        echo 'Smoke server exited before reaching ready state' >&2
        exit 1
    fi
    sleep 2
done
if ! grep -q 'Done (' "$results/server-console.log"; then
    tail -100 "$results/server-console.log"
    echo 'Smoke server did not become ready in eight minutes' >&2
    exit 1
fi

export LIBGL_ALWAYS_SOFTWARE=true
export ALSOFT_DRIVERS=null
setsid xvfb-run -a -s '-screen 0 1280x800x24' \
    bash "$client_script" \
    > "$results/client-console.log" 2>&1 &
client_pid=$!
for ((attempt=0; attempt<360; attempt++)); do
    if [[ -f /tmp/uq-tutorial-test-done ]]; then
        if ! grep -qi '^passed' /tmp/uq-tutorial-test-done; then
            cat /tmp/uq-tutorial-test-done
            exit 1
        fi
        if [[ -f /tmp/uq-admin-test-done ]]; then
            if ! grep -qi '^passed' /tmp/uq-admin-test-done; then
                cat /tmp/uq-admin-test-done
                exit 1
            fi
            if [[ $(find /tmp/uqshots -type f -name '*.png' | wc -l) -lt 8 ]]; then
                echo 'Game smoke completed without the expected screenshots' >&2
                exit 1
            fi
            cat /tmp/uq-tutorial-test-done /tmp/uq-admin-test-done
            exit 0
        fi
    fi
    if ! kill -0 "$client_pid" 2>/dev/null; then
        tail -100 "$results/client-console.log"
        echo 'Smoke client exited before completing tutorial assertions' >&2
        exit 1
    fi
    if grep -Eq "autotest failed|Couldn't connect to server" "$results/client-console.log"; then
        tail -100 "$results/client-console.log"
        exit 1
    fi
    if ! kill -0 "$server_pid" 2>/dev/null; then
        tail -100 "$results/server-console.log"
        echo 'Smoke server exited during the client exercise' >&2
        exit 1
    fi
    sleep 2
done
tail -100 "$results/client-console.log"
echo 'Tutorial smoke assertions did not finish in twelve minutes' >&2
exit 1
