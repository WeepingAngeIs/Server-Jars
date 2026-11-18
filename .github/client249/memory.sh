#!/usr/bin/env bash
set -eu
[[ "${RUNNER_ENVIRONMENT:-}" == github-hosted ]]
df -h "$RUNNER_TEMP"
free -h
# NeoForge, Mesa's software GPU and the full resource reload share one hosted VM.
if (( $(df --output=avail -k "$RUNNER_TEMP" | tail -1) > 16000000 )); then
    swapfile="$RUNNER_TEMP/jail-client-swap"
    sudo fallocate -l 6G "$swapfile"
    sudo chmod 600 "$swapfile"
    sudo mkswap "$swapfile"
    sudo swapon "$swapfile"
fi
echo 'MALLOC_ARENA_MAX=2' >> "$GITHUB_ENV"
echo 'LP_NUM_THREADS=2' >> "$GITHUB_ENV"
