"""Add the supplied finished bridge to the verified release on GitHub only."""
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import re
import tomllib
import zipfile

if os.environ.get('GITHUB_ACTIONS') != 'true' or os.environ.get('RUNNER_ENVIRONMENT') != 'github-hosted':
    raise SystemExit('Assembly and validation run only on GitHub-hosted runners.')

ROOT = Path(__file__).resolve().parents[1]
release = json.loads((ROOT / 'currency-bridge/release.json').read_text())
MODS = 'META-INF/neoforge.mods.toml'
MANIFEST = 'META-INF/MANIFEST.MF'
CHANGED = {MODS, MANIFEST}


def read_input(info):
    path = ROOT / info['path']
    assert hashlib.sha256(path.read_bytes()).hexdigest() == info['sha256'], f'Input changed: {path.name}'
    with zipfile.ZipFile(path) as archive:
        assert archive.testzip() is None
        names = [i.filename for i in archive.infolist() if not i.is_dir()]
        assert len(names) == len(set(names)), 'Duplicate archive entries'
        assert not any(re.search(r'^META-INF/[^/]+\.(SF|RSA|DSA|EC)$', n, re.I) for n in names), 'Signed input requires source rebuild'
        assert not any(n.startswith('/') or '\\' in n or '..' in PurePosixPath(n).parts for n in names), 'Unsafe archive entry'
        return {n: archive.read(n) for n in names}


base = read_input(release['base'])
bridge = read_input(release['bridge'])
assert set(base).intersection(bridge) == CHANGED, 'Unexpected file conflict'
base_meta = tomllib.loads(base[MODS].decode())
bridge_text = bridge[MODS].decode()
bridge_meta = tomllib.loads(bridge_text)
assert set(bridge_meta) == {'modLoader', 'loaderVersion', 'license', 'mods', 'dependencies'}
for field in ('modLoader', 'loaderVersion', 'license'):
    assert base_meta[field] == bridge_meta[field], f'Incompatible metadata: {field}'
assert len(base_meta['mods']) == 8
assert len(bridge_meta['mods']) == 1
assert bridge_meta['mods'][0]['modId'] == release['bridge']['mod_id']
assert bridge_meta['mods'][0]['version'] == release['bridge']['version']
assert release['bridge']['mod_id'] not in base_meta['dependencies']
metadata = base[MODS].decode().rstrip() + '\n\n# Currency bridge: preserve the supplied module and currency identifiers.\n' + bridge_text[bridge_text.index('[[mods]]'):]
merged_meta = tomllib.loads(metadata)
assert merged_meta['mods'] == base_meta['mods'] + bridge_meta['mods']
assert merged_meta['dependencies'] == base_meta['dependencies'] | bridge_meta['dependencies']
for key, value in base_meta.items():
    if key not in ('mods', 'dependencies'):
        assert merged_meta[key] == value, f'Existing metadata changed: {key}'
assert len({m['modId'] for m in merged_meta['mods']}) == 9
assert '${' not in metadata

entries = base | bridge
entries[MODS] = metadata.encode()
entries[MANIFEST] = (
    'Manifest-Version: 1.0\r\n'
    'FMLModType: MOD\r\n'
    'Implementation-Title: Unyielding\r\n'
    f"Implementation-Version: {release['version']}\r\n"
    'Unyielding-Quests-Version: 1.6.3\r\n'
    f"Unyielding-Currency-Bridge-Version: {release['bridge']['version']}\r\n\r\n"
).encode()
assert not any('/quests/devtest/' in n or '/bundlesmoke/' in n or '/currencysmoke/' in n for n in entries)
assert len([n for n in entries if n.startswith('com/uwustudio/cobblecurrency/') and n.endswith('.class')]) == 12
assert len([n for n in entries if n.startswith('assets/unyieldingquests/textures/quest_art/') and n.endswith('.png')]) == 923
output = ROOT / release['output']
output.parent.mkdir(parents=True, exist_ok=True)
with zipfile.ZipFile(output, 'w', zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
    for name, data in sorted(entries.items()):
        item = zipfile.ZipInfo(name, date_time=(2026, 11, 7, 0, 0, 0))
        item.compress_type = zipfile.ZIP_DEFLATED
        item.external_attr = 0o644 << 16
        archive.writestr(item, data)
with zipfile.ZipFile(output) as archive:
    assert archive.testzip() is None
    assert set(archive.namelist()) == set(entries)
    for source in (base, bridge):
        for name, data in source.items():
            if name not in CHANGED:
                assert archive.read(name) == data, f'Gameplay file changed: {name}'
digest = hashlib.sha256(output.read_bytes()).hexdigest()
report = {
    'status': 'passed', 'version': release['version'], 'sha256': digest,
    'source_commit': os.environ.get('GITHUB_SHA'),
    'cloud_run': f"https://github.com/{os.environ.get('GITHUB_REPOSITORY')}/actions/runs/{os.environ.get('GITHUB_RUN_ID')}",
    'inputs': {key: release[key] for key in ('base', 'bridge')},
    'production_module_count': len(merged_meta['mods']),
    'preserved_base_entries': len(base) - 2, 'preserved_bridge_entries': len(bridge) - 2,
    'modified_entries': sorted(CHANGED), 'bridge_classes': 12,
    'external_dependencies_bundled': False,
    'new_required_mods': bridge_meta['dependencies'][release['bridge']['mod_id']],
}
(output.parent / 'assembly-report.json').write_text(json.dumps(report, indent=2) + '\n')
(output.parent / 'SHA256SUMS.txt').write_text(f'{digest}  {output.name}\n')
print(json.dumps(report, indent=2))
