#!/usr/bin/env bash
set -euo pipefail
[[ "${GITHUB_ACTIONS:-}" == true && "${RUNNER_ENVIRONMENT:-}" == github-hosted ]]
export UQ_PACKAGED_JAIL_SMOKE=1 UQ_WORLD_NAME=jail_integrated_smoke UQ_RESULT_FILE=/tmp/uq-integrated-result
export LIBGL_ALWAYS_SOFTWARE=true ALSOFT_DRIVERS=null
for phase in 1 2; do
  export JAVA_TOOL_OPTIONS="-Duq.integratedJail=true -Duq.integratedPhase=$phase"
  bash .github/scripts/meeting-two-player.sh
  cp -a dist/packaged-smoke/jail-player-logs "dist/packaged-smoke/integrated-phase$phase"
done
# Load a genuine extra, uncertified low-code NeoForge mod on the next restart.
# Do not replace or alter the assembled production JAR or its certificate.
python3 - <<'PY'
from pathlib import Path
from zipfile import ZipFile, ZIP_DEFLATED
p = Path('dist/packaged-harness/libs/uq-uncertified-runtime.jar')
assert not p.exists()
with ZipFile(p, 'x', compression=ZIP_DEFLATED) as jar:
    jar.writestr('META-INF/neoforge.mods.toml', '''modLoader="lowcodefml"
loaderVersion="[1,)"
license="MIT"
[[mods]]
modId="uq_uncertified_runtime"
version="1.0.0"
displayName="Disposable uncertified dependency probe"
''')
PY
export JAVA_TOOL_OPTIONS='-Duq.integratedJail=true -Duq.integratedPhase=3 -Duq.integratedDependencyProbe=true'
bash .github/scripts/meeting-two-player.sh
cp -a dist/packaged-smoke/jail-player-logs dist/packaged-smoke/integrated-dependency-refusal
python3 - <<'PY'
from pathlib import Path
p = Path('dist/packaged-harness/libs/uq-uncertified-runtime.jar')
assert p.is_file() and p.resolve().is_relative_to(Path('dist/packaged-harness/libs').resolve())
p.unlink()
PY
export JAVA_TOOL_OPTIONS='-Duq.integratedJail=true -Duq.integratedPhase=3'
bash .github/scripts/meeting-two-player.sh
cp -a dist/packaged-smoke/jail-player-logs dist/packaged-smoke/integrated-phase3
python3 - <<'PY'
import hashlib, json, shutil
from pathlib import Path
world = Path('dist/packaged-harness/run-server/jail_integrated_smoke')
target = Path('dist/packaged-smoke/integrated-world-evidence')
target.mkdir(parents=True, exist_ok=True)
for name in ('integrated-jail-v1', 'custody-interface-v1', 'playerdata'):
    shutil.copytree(world / name, target / name)
for name in ('expected-items.nbt', 'active-transaction', 'data/unyielding_jail.dat', 'data/neoorigins_origin_claims.dat'):
    dest = target / name
    dest.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(world / name, dest)
release = Path('dist/120-release/Unyielding-1.2.0.jar')
manifest = {'release_sha256': hashlib.sha256(release.read_bytes()).hexdigest(), 'files': []}
for file in sorted(target.rglob('*')):
    if file.is_file():
        manifest['files'].append({'path': file.relative_to(target).as_posix(), 'size': file.stat().st_size, 'sha256': hashlib.sha256(file.read_bytes()).hexdigest()})
(target / 'manifest.json').write_text(json.dumps(manifest, indent=2))
PY
