"""Independent assembled-JAR mining fixture, with pinned OPAC and actual clients."""
import os
from pathlib import Path
import shutil
ROOT = Path(__file__).resolve().parents[2]
if os.environ.get('GITHUB_ACTIONS') != 'true' or os.environ.get('RUNNER_ENVIRONMENT') != 'github-hosted':
    raise SystemExit('GitHub-hosted only')
source=(ROOT/'.github/scripts/prepare-party-confirmation.py').read_text(encoding='utf-8')
source=source.replace('party-harness','vein-harness').replace('party-confirmation','vein-protection')
# The nested pinned-input preparation resolves these names from its reward template.
needle="exec(compile(source, 'party_preparation', 'exec'),"
assert needle in source
dependencies = {
    'Open Parties and Claims 0.33.0': 'yWmXD7GW',
    'OPAC Bonus Claims 1.2.0 exact installed build': 'ZvUIVthw',
    'OPAC PvP 1.0.1 exact installed build': '6AhVNkOg',
    'Necronomicon 1.6.0 exact installed dependency': '4iO8Tbfj',
    'Xaero Minimap 26.5.0 exact installed build': 'Q1tuMQBB',
    'Xaero World Map 1.46.0 exact installed build': '9Ckiihkz',
}
injection = ''.join(f"namespace['DEPENDENCIES'][{name!r}] = {version!r}\n" for name, version in dependencies.items()) + "namespace['main']()"
source=source.replace(needle,"source=source.replace(\"namespace['main']()\", "+repr(injection)+")\n"+needle)
exec(compile(source,'vein_preparation','exec'),{'__file__':str(ROOT/'.github/scripts/prepare-party-confirmation.py')})
shutil.copy2(ROOT / '.github/vein-protection/AcceptanceCases.java', ROOT / 'dist/vein-harness/src/main/java/dev/unyielding/bundlesmoke/AcceptanceCases.java')
# Reproduce only the observed, non-sensitive HUD placement. No waypoints/maps/config
# contents from the user's profile are copied into this disposable environment.
for directory in ('run', 'run-visitor'):
    config = ROOT / 'dist/vein-harness' / directory / 'config'
    config.mkdir(parents=True, exist_ok=True)
    (config / 'xaerohud.txt').write_text('module;id=xaerominimap:minimap;x=0;y=0;centered=false;fromRight=true;fromBottom=false;flippedVer=false;flippedHor=false;\n')
