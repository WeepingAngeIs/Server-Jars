#!/usr/bin/env bash
set -euo pipefail
[[ "${GITHUB_ACTIONS:-}" == true && "${RUNNER_ENVIRONMENT:-}" == github-hosted ]]
export UQ_PACKAGED_JAIL_SMOKE=1 UQ_WORLD_NAME=jail_xp_smoke UQ_RESULT_FILE=/tmp/uq-xp-result
export LIBGL_ALWAYS_SOFTWARE=true ALSOFT_DRIVERS=null
collect_evidence() {
python3 - <<'PY'
import hashlib, json, shutil
from pathlib import Path
world = Path('dist/packaged-harness/run-server/jail_xp_smoke')
target = Path('dist/packaged-smoke/retained-xp-world-evidence')
target.mkdir(exist_ok=True)
for name in ('integrated-jail-v1', 'custody-interface-v1', 'playerdata', 'advancements', 'stats'):
    if (world / name).is_dir():
        shutil.copytree(world / name, target / name, dirs_exist_ok=True)
for name in ('expected-equipment.nbt', 'expected-xp.nbt', 'active-expanded', 'data/unyielding_jail.dat', 'data/neoorigins_origin_claims.dat'):
    dest = target / name
    dest.parent.mkdir(parents=True, exist_ok=True)
    if (world / name).is_file():
        shutil.copy2(world / name, dest)
manifest = {'release_sha256': hashlib.sha256(Path('dist/120-release/Unyielding-1.2.0.jar').read_bytes()).hexdigest(), 'files': []}
for file in sorted(target.rglob('*')):
    if file.is_file():
        manifest['files'].append({'path': file.relative_to(target).as_posix(), 'sha256': hashlib.sha256(file.read_bytes()).hexdigest(), 'size': file.stat().st_size})
(target / 'manifest.json').write_text(json.dumps(manifest, indent=2))
PY
}
trap collect_evidence EXIT
for phase in 1 2 3 4 5 6; do
  export JAVA_TOOL_OPTIONS="-Duq.integratedJail=true -Duq.retainedXp=true -Duq.retainedPhase=$phase"
  bash .github/scripts/meeting-two-player.sh
  cp -a dist/packaged-smoke/jail-player-logs "dist/packaged-smoke/xp-phase$phase"
done
