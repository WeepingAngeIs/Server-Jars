#!/usr/bin/env bash
set -euo pipefail
if [[ "${GITHUB_ACTIONS:-}" != true || "${RUNNER_ENVIRONMENT:-}" != github-hosted ]]; then
    echo 'Game checks run only on GitHub-hosted runners.' >&2
    exit 1
fi
repository=$(cd "$(dirname "$0")/.." && pwd)
project="$repository/combined-unyielding/smoke-harness"
results="$repository/dist/combined-smoke"
mkdir -p "$results" /tmp/uqshots
test -f "$project/run-server/eula.txt"
test -f "$UQ_BUNDLE_JAR"

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
difficulty=peaceful
motd=Unyielding combined cloud checks
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
resourcePacks:["vanilla","file/Mawonna.zip"]
EOF
printf 'tutorialtest\nadmintest\n' > /tmp/uq-cmd.txt
rm -f /tmp/uq-tutorial-test-done /tmp/uq-admin-test-done /tmp/uq-admin-server-cmd /tmp/uq-admin-server-result \
    /tmp/uq-bundle-server-passed.json /tmp/uq-bundle-server-failed.txt \
    /tmp/uq-bundle-client-passed.json /tmp/uq-bundle-client-failed.txt

server_pid=
client_pid=
cleanup() {
    if [[ -n "$client_pid" ]]; then kill -- -"$client_pid" 2>/dev/null || true; fi
    if [[ -n "$server_pid" ]]; then kill -- -"$server_pid" 2>/dev/null || true; fi
    cp -a /tmp/uqshots "$results/screenshots" 2>/dev/null || true
    for proof in /tmp/uq-tutorial-test-done /tmp/uq-admin-test-done /tmp/uq-admin-server-result /tmp/uq-bundle-*; do
        [[ ! -f "$proof" ]] || cp "$proof" "$results/"
    done
    cp "$project/run-server/config/unyieldingquests-common.toml" "$results/final-server-config.toml" 2>/dev/null || true
    cp "$project/run/logs/latest.log" "$results/client-latest.log" 2>/dev/null || true
    cp "$project/run-server/logs/latest.log" "$results/server-latest.log" 2>/dev/null || true
    cp -a "$project/run/crash-reports" "$results/client-crashes" 2>/dev/null || true
    cp -a "$project/run-server/crash-reports" "$results/server-crashes" 2>/dev/null || true
}
trap cleanup EXIT

cd "$project"
# Finish Gradle before booting either game, leaving memory available for Cobblemon.
"$repository/unyielding-quests/gradlew" --no-daemon --console=plain -Dorg.gradle.jvmargs=-Xmx768m \
    createServerLaunchScript createClientLaunchScript > "$results/prepare-console.log" 2>&1 || {
        tail -120 "$results/prepare-console.log"; exit 1;
    }
server_script=$(find build -type f -name 'runServer.sh' -print -quit)
client_script=$(find build -type f -name 'runClient.sh' -print -quit)
if [[ ! -f "$server_script" || ! -f "$client_script" ]]; then
    cat "$results/prepare-console.log"
    echo 'Standalone game launch scripts were not generated' >&2
    exit 1
fi
setsid bash "$server_script" > "$results/server-console.log" 2>&1 &
server_pid=$!
for ((attempt=0; attempt<300; attempt++)); do
    if [[ -f /tmp/uq-bundle-server-failed.txt ]]; then cat /tmp/uq-bundle-server-failed.txt; exit 1; fi
    if grep -q 'Done (' "$results/server-console.log" && [[ -f /tmp/uq-bundle-server-passed.json ]]; then break; fi
    if ! kill -0 "$server_pid" 2>/dev/null; then
        tail -120 "$results/server-console.log"
        echo 'Combined server exited before its assertions passed' >&2
        exit 1
    fi
    sleep 2
done
if [[ ! -f /tmp/uq-bundle-server-passed.json ]]; then
    tail -120 "$results/server-console.log"
    echo 'Combined server checks did not finish in ten minutes' >&2
    exit 1
fi

export LIBGL_ALWAYS_SOFTWARE=true
export ALSOFT_DRIVERS=null
setsid xvfb-run -a -s '-screen 0 1280x800x24' bash "$client_script" > "$results/client-console.log" 2>&1 &
client_pid=$!
for ((attempt=0; attempt<420; attempt++)); do
    for failed in /tmp/uq-bundle-client-failed.txt /tmp/uq-bundle-server-failed.txt; do
        if [[ -f "$failed" ]]; then cat "$failed"; exit 1; fi
    done
    for marker in /tmp/uq-tutorial-test-done /tmp/uq-admin-test-done; do
        if [[ -f "$marker" ]] && ! grep -qi '^passed' "$marker"; then cat "$marker"; exit 1; fi
    done
    if [[ -f /tmp/uq-tutorial-test-done && -f /tmp/uq-admin-test-done && -f /tmp/uq-bundle-client-passed.json ]]; then
        if [[ $(find /tmp/uqshots -type f -name '*.png' | wc -l) -lt 8 ]]; then
            echo 'Combined client smoke finished without the expected screenshots' >&2
            exit 1
        fi
        cat /tmp/uq-tutorial-test-done /tmp/uq-admin-test-done
        echo 'Passed final-JAR server, client, commands, Mawonna, tutorial and admin settings checks.'
        exit 0
    fi
    if ! kill -0 "$client_pid" 2>/dev/null; then
        tail -120 "$results/client-console.log"
        echo 'Combined client exited before completing assertions' >&2
        exit 1
    fi
    if grep -Eq "autotest failed|Couldn't connect to server" "$results/client-console.log"; then
        tail -120 "$results/client-console.log"
        exit 1
    fi
    if ! kill -0 "$server_pid" 2>/dev/null; then
        tail -120 "$results/server-console.log"
        echo 'Combined server exited during client checks' >&2
        exit 1
    fi
    sleep 2
done
tail -120 "$results/client-console.log"
echo 'Combined client checks did not finish in fourteen minutes' >&2
exit 1
