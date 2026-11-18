#!/usr/bin/env bash
set -eu
[[ "${RUNNER_ENVIRONMENT:-}" == github-hosted ]]
df -h "$RUNNER_TEMP"
free -h
# NeoForge, Mesa's software GPU and the full resource reload share one hosted VM.
available_kib=$(df --output=avail -k "$RUNNER_TEMP" | tail -1)
swap_gib=6
if [[ ${GITHUB_JOB:-} == pack-custody ]] && (( available_kib >= 59*1024*1024 )); then
    # Full JEI registration on login exhausted 9 GiB of swap. This runner has room
    # for both installations and a larger swapfile on its disposable disk.
    swap_gib=24
fi
if (( available_kib > (swap_gib+10)*1024*1024 )); then
    swapfile="$RUNNER_TEMP/jail-client-swap"
    sudo fallocate -l "${swap_gib}G" "$swapfile"
    sudo chmod 600 "$swapfile"
    sudo mkswap "$swapfile"
    sudo swapon "$swapfile"
fi
# The full client and server used roughly 4.5 GiB of swap together. Let
# the hosted kernel compress cold pages while retaining disk swap as backing.
zswap_settings=/sys/module/zswap/parameters
if [[ -r "$zswap_settings/enabled" ]]; then
    if [[ $(<"$zswap_settings/enabled") == N ]]; then
        if ! echo 1 | sudo tee "$zswap_settings/enabled" >/dev/null; then
            echo "Compressed swap unavailable on this runner"
        fi
    fi
    echo "Compressed swap enabled: $(<"$zswap_settings/enabled")"
fi
swapon --show
echo 'MALLOC_ARENA_MAX=2' >> "$GITHUB_ENV"
echo 'LP_NUM_THREADS=2' >> "$GITHUB_ENV"
if [[ ${GITHUB_JOB:-} == pack-custody ]]; then
    # Once one game has loaded, let it release idle heap while its peer starts.
    # The following workflow step starts both JVMs with their own heap limits.
    echo "JDK_JAVA_OPTIONS=${JDK_JAVA_OPTIONS:+$JDK_JAVA_OPTIONS }-XX:+UseG1GC -XX:G1PeriodicGCInterval=30000 -XX:MinHeapFreeRatio=25 -XX:MaxHeapFreeRatio=40 -Xlog:gc" >> "$GITHUB_ENV"
fi
