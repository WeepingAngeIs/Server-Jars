#!/usr/bin/env bash
set -euo pipefail
[[ "${GITHUB_ACTIONS:-}" == true && "${RUNNER_ENVIRONMENT:-}" == github-hosted ]]
export UQ_PACKAGED_JAIL_SMOKE=1 LIBGL_ALWAYS_SOFTWARE=true ALSOFT_DRIVERS=null
slice="${1:?runtime slice required}"
phase() {
  local name="$1" flags="$2"
  export JAVA_TOOL_OPTIONS="$flags"
  bash .github/scripts/meeting-two-player.sh
  cp -a dist/packaged-smoke/jail-player-logs "dist/packaged-smoke/$name"
}
case "$slice" in
  integrated)
    export UQ_WORLD_NAME=jail_integrated_smoke UQ_RESULT_FILE=/tmp/uq-integrated-result
    phase integrated-phase1 '-Duq.integratedJail=true -Duq.integratedPhase=1'
    phase integrated-phase2 '-Duq.integratedJail=true -Duq.integratedPhase=2'
    python3 - <<'PY'
from pathlib import Path
from zipfile import ZipFile, ZIP_DEFLATED
p=Path('dist/packaged-harness/libs/uq-uncertified-runtime.jar')
with ZipFile(p,'x',compression=ZIP_DEFLATED) as z:
    z.writestr('META-INF/neoforge.mods.toml','''modLoader="lowcodefml"
loaderVersion="[1,)"
license="MIT"
[[mods]]
modId="uq_uncertified_runtime"
version="1.0.0"
displayName="Disposable uncertified dependency probe"
''')
PY
    phase integrated-dependency-refusal '-Duq.integratedJail=true -Duq.integratedPhase=3 -Duq.integratedDependencyProbe=true'
    rm -- dist/packaged-harness/libs/uq-uncertified-runtime.jar
    # Alter only the disposable runtime copy. Neither the release nor its certificate changes.
    cp "$UQ_BUNDLE_JAR" "$UQ_BUNDLE_JAR.reference"
    original_hash="$UQ_BUNDLE_SHA256"
    python3 - <<'PY'
import os
from zipfile import ZipFile
with ZipFile(os.environ['UQ_BUNDLE_JAR'],'a') as z:
    z.writestr('disposable-certificate-tamper-probe.txt','Unexpected companion bytes must refuse recovery.\n')
PY
    export UQ_BUNDLE_SHA256="$(sha256sum "$UQ_BUNDLE_JAR" | cut -d' ' -f1)"
    [[ "$UQ_BUNDLE_SHA256" != "$original_hash" ]]
    phase integrated-companion-refusal '-Duq.integratedJail=true -Duq.integratedPhase=3 -Duq.integratedCompanionProbe=true'
    mv -- "$UQ_BUNDLE_JAR.reference" "$UQ_BUNDLE_JAR"
    export UQ_BUNDLE_SHA256="$original_hash"
    phase integrated-phase3 '-Duq.integratedJail=true -Duq.integratedPhase=3'
    ;;
  retained-xp)
    bash .github/scripts/retained-xp-smoke.sh
    ;;
  certified)
    bash .github/scripts/certified-jail-smoke.sh
    ;;
  startup-terrain)
    bash .github/scripts/packaged-server-smoke.sh
    export UQ_WORLD_NAME=jail_terrain_smoke UQ_RESULT_FILE=/tmp/uq-jail-terrain-result
    phase terrain-phase1 '-Duq.jailTerrainPlayers=true -Duq.jailTerrainPhase=1'
    phase terrain-phase2 '-Duq.jailTerrainPlayers=true -Duq.jailTerrainPhase=2'
    ;;
  *) echo "Unknown bounded split runtime slice: $slice" >&2; exit 1;;
esac
python3 .github/scripts/verify-split-runtime-evidence.py "$slice"
