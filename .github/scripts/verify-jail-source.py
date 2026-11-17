"""Prove extraction provenance and non-jail source preservation without game launches."""
import argparse
import json
from pathlib import Path
import subprocess
from jail_split import BASE_COMMIT, MIXINS, relocated_source
from companion_certificate import CERTIFICATE, validate_extension

parser = argparse.ArgumentParser()
parser.add_argument('--base', default=BASE_COMMIT)
args = parser.parse_args()
base = args.base
root = Path('.')
manifest = json.loads((root / 'minecraft-jail/extraction-manifest.json').read_text())
assert manifest['base_commit'] == BASE_COMMIT

def old(path):
    return subprocess.check_output(['git', 'show', f'{base}:{path}'])

def text(path):
    return old(path).decode('utf-8')

removed = set()
for source, target in manifest['moves']:
    expected = old(source)
    if source.endswith('.java'):
        expected = relocated_source(expected.decode('utf-8')).encode('utf-8')
    # Explicit reviewed production deltas from the verified split. Never skip
    # comparison for a whole directory or accept an arbitrary changed file.
    delta_path = root / '.github/packed-wallet/production-delta.json'
    if delta_path.exists():
        import hashlib
        delta = json.loads(delta_path.read_text())['changed'].get(target)
        if delta is not None:
            assert hashlib.sha256(expected).hexdigest() == delta['before'], f'Wrong increment base: {target}'
            candidate = (root / target).read_bytes()
            assert hashlib.sha256(candidate).hexdigest() == delta['after'], f'Unreviewed increment: {target}'
            expected = candidate
    if target.endswith(CERTIFICATE):
        validate_extension(expected, (root / target).read_bytes())
    else:
        assert (root / target).read_bytes() == expected, f'Behavior/resource drift in {target}'
    assert not (root / source).exists(), f'Old jail source still present: {source}'
    removed.add(source)
for source, target in manifest['legacy_fixtures']:
    assert (root / target).read_bytes() == old(source), f'Historical fixture changed: {source}'
    assert not (root / source).exists()
    removed.add(source)
for source, target in manifest['copied_helpers']:
    assert (root / source).read_bytes() == old(source)
    expected = text(source).replace('package dev.unyielding.quests.essentials;', 'package dev.unyielding.jail;')
    assert (root / target).read_bytes() == expected.encode()

prefix = 'unyielding-quests/src/main/java/dev/unyielding/quests/'
allowed = {prefix + name for name in ('Config.java', 'Registration.java', 'essentials/Essentials.java', 'devtest/GroundCleanupSmoke.java')}
mixpath = 'unyielding-quests/src/main/resources/assets/unyieldingquests/pokemon.mixins.json'
allowed.add(mixpath)
for path in subprocess.check_output(['git', 'ls-tree', '-r', '--name-only', base, '--', 'unyielding-quests'], text=True).splitlines():
    if path not in allowed and path not in removed:
        assert (root / path).read_bytes() == old(path), f'Unrelated source/resource changed: {path}'

path=prefix+'Config.java'; expected=text(path).replace('    public static final ModConfigSpec.BooleanValue JAIL_CERTIFIED_ADMISSIONS;\n','')
start=expected.index('        b.push("jail");\n'); end=expected.index('        b.pop();\n',start)+len('        b.pop();\n')
assert (root/path).read_bytes() == (expected[:start]+expected[end:]).encode()
path=prefix+'Registration.java'; expected=''.join(line for line in text(path).splitlines(keepends=True) if not line.startswith(('    public static final DeferredRegister.Blocks BLOCKS =','    public static final net.neoforged.neoforge.registries.DeferredBlock<dev.unyielding.quests.jail.JailCustodyBlock> CUSTODY_VAULT =')))
expected=expected.replace('        BLOCKS.register(bus);\n','');assert (root/path).read_bytes()==expected.encode()
path=prefix+'essentials/Essentials.java';expected=text(path)
for line in ('            e.addNodes(dev.unyielding.quests.jail.JailCommands.MANAGE);\n','            dev.unyielding.quests.jail.JailCommands.register(e.getDispatcher());\n','            dev.unyielding.quests.jail.JailPlacement.register(e.getDispatcher());\n'):
    expected=expected.replace(line,'')
assert (root/path).read_bytes()==expected.encode()
path=prefix+'devtest/GroundCleanupSmoke.java';expected=text(path)
start=expected.index('    private static void checkJailGeometry(');end=expected.index('    private static void checkQuestSharingPersistence()',start)
expected=(expected[:start]+expected[end:]).replace('                checkJailGeometry(server);\n','')
assert (root/path).read_bytes()==expected.encode()
expected=json.loads(text(mixpath));expected['mixins']=[name for name in expected['mixins'] if name not in MIXINS]
assert json.loads((root/mixpath).read_text())==expected
for path in (root/'unyielding-quests/src/main/java').rglob('*.java'):
    content=path.read_text()
    assert 'dev.unyielding.quests.jail' not in content and 'dev.unyielding.jail' not in content, f'Unyielding retains a jail dependency: {path}'
for path in (root/'minecraft-jail/src/main/java').rglob('*.java'):
    assert 'dev.unyielding.quests' not in path.read_text(), f'Standalone still depends on Unyielding: {path}'
print(json.dumps({'baseline':base,'relocated_files':len(manifest['moves']),'historical_fixtures_preserved':len(manifest['legacy_fixtures']),'non_jail_sources_unchanged_except_explicit_hook_removals':True}))
