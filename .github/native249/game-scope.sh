#!/usr/bin/env bash
set -euo pipefail

if [[ ${GITHUB_ACTIONS:-} != true || ${RUNNER_ENVIRONMENT:-} != github-hosted || $EUID == 0 ]]; then
    echo "This test requires an unprivileged user on a GitHub-hosted runner." >&2
    exit 1
fi
if [[ $# != 2 || ! $1 =~ ^[12]$ ]]; then
    echo "Usage: bash game-scope.sh PHASE CANDIDATE_JAR" >&2
    exit 2
fi
phase=$1
candidate=$2
mkdir -p dist/packaged-smoke/full249-scope-logs
report="dist/packaged-smoke/full249-scope-logs/phase-${phase}.log"
log() { printf '%s %s\n' "$(date -u +%FT%TZ)" "$*" | tee -a "$report"; }

cgroups=/sys/fs/cgroup
if [[ ! -r "$cgroups/cgroup.controllers" ]] || ! grep -qw memory "$cgroups/cgroup.controllers"; then
    log "The host lacks the cgroup v2 memory controller. Running the same game test directly."
    exec python3 .github/native249/full_run.py "$candidate" --phase "$phase"
fi

unit="jailpack249-${GITHUB_RUN_ID:?}-${GITHUB_RUN_ATTEMPT:?}-p${phase}-$$.scope"
group="$cgroups/system.slice/$unit"
high=$(( 25 * 1024 * 1024 * 1024 / 2 ))
maximum=$(( 13 * 1024 * 1024 * 1024 ))

sample() {
    local measurements=() file value
    [[ -r "$group/memory.current" ]] || return 0
    for file in memory.current memory.peak memory.high memory.max memory.swap.current memory.swap.max; do
        if value=$(cat "$group/$file" 2>/dev/null); then measurements+=("$file=$value"); fi
    done
    if value=$(tr '\n' ' ' < "$group/memory.events" 2>/dev/null); then measurements+=("events=[$value]"); fi
    log "${measurements[*]}"
}

game() {
    local worker=$BASHPID result
    # Attach this existing user process. No game arguments, credentials or environment
    # go to the privileged helper. Python, Xvfb and the JVMs inherit this shell's UID,
    # working directory and environment, and only start once the limits are effective.
    sudo -n busctl --system --quiet call org.freedesktop.systemd1 /org/freedesktop/systemd1 \
        org.freedesktop.systemd1.Manager StartTransientUnit 'ssa(sv)a(sa(sv))' "$unit" fail 6 \
        Slice s system.slice MemoryAccounting b true MemoryHigh t "$high" MemoryMax t "$maximum" \
        MemorySwapMax t 18446744073709551615 PIDs au 1 "$worker" 0
    for (( attempt=0; attempt<100; attempt++ )); do
        if grep -Fxq "0::/system.slice/$unit" "/proc/$worker/cgroup" && [[ -r "$group/memory.swap.max" ]]; then
            break
        fi
        sleep 0.1
    done
    if ! grep -Fxq "0::/system.slice/$unit" "/proc/$worker/cgroup" ||
        [[ $(cat "$group/memory.high") != "$high" || $(cat "$group/memory.max") != "$maximum" ||
           $(cat "$group/memory.swap.max") != max ]]; then
        log "The host advertised cgroup memory support but did not apply the requested limits."
        return 1
    fi
    log "Verified $unit. Worker UID $EUID. Memory quantities below are in bytes."
    sample
    if python3 .github/native249/full_run.py "$candidate" --phase "$phase"; then result=0; else result=$?; fi
    sample
    return "$result"
}

game <&0 &
worker_pid=$!
cleanup() {
    if [[ -n "$worker_pid" ]]; then kill "$worker_pid" 2>/dev/null || true; fi
    if [[ -d "$group" ]]; then
        sample
        sudo -n systemctl stop --no-block "$unit" || true
    fi
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM HUP
# Keep the monitor and the Actions worker outside the game group's hard limit.
while kill -0 "$worker_pid" 2>/dev/null; do
    sleep 15
    sample
done
if wait "$worker_pid"; then status=0; else status=$?; fi
worker_pid=
exit "$status"
