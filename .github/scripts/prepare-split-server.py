"""Adapt preserved fixtures, not production sources, to two final packaged JARs."""
import hashlib
import json
import os
from pathlib import Path
import shutil

ROOT = Path(__file__).resolve().parents[2]
if os.environ.get('GITHUB_ACTIONS') != 'true' or os.environ.get('RUNNER_ENVIRONMENT') != 'github-hosted':
    raise SystemExit('Split runtime checks require GitHub-hosted disposable runners')
harness = ROOT / 'dist/packaged-harness'
if harness.exists():
    raise SystemExit('Refuse stale harness: use a fresh disposable job')
legacy = 'minecraft-jail/src/legacySmoke/java/dev/unyielding/quests/devtest/'
old = 'unyielding-quests/src/main/java/dev/unyielding/quests/devtest/'

def replace_once(text, before, after):
    assert text.count(before) == 1, ('Harness template drift', before, text.count(before))
    return text.replace(before, after, 1)

path = ROOT / '.github/scripts/prepare-packaged-server.py'
text = path.read_text()
for name in ('VanillaCustodySmoke', 'VanillaRestoreSmoke', 'JailPlayerSmoke', 'JailTerrainPlayerSmoke'):
    text = replace_once(text, old+name+'.java', legacy+name+'.java')
text = replace_once(text, old+'{name}.java', legacy+'{name}.java')
exec(compile(text, str(path), 'exec'), {'__file__': str(path), '__name__': 'split_package_preparation'})

jail = ROOT / 'minecraft-jail/build/libs/Minecraft-Jail-1.2.0.jar'
loaded = harness / 'libs' / jail.name
shutil.copy2(jail, loaded)
with Path(os.environ['GITHUB_ENV']).open('a') as env:
    env.write(f'UQ_JAIL_JAR={loaded.resolve()}\nUQ_JAIL_SHA256={hashlib.sha256(loaded.read_bytes()).hexdigest()}\n')

path = ROOT / '.github/scripts/install-origin-test-runtime.py'
text = path.read_text()
text = replace_once(text, 'with zipfile.ZipFile(release) as jar:', 'with zipfile.ZipFile(ROOT / "minecraft-jail/build/libs/Minecraft-Jail-1.2.0.jar") as jar:')
text = replace_once(text, "ROOT / 'unyielding-quests/src/main/resources'", "ROOT / 'minecraft-jail/src/main/resources'")
text = replace_once(text, old+'{name}.java', legacy+'{name}.java')
exec(compile(text, str(path), 'exec'), {'__file__': str(path), '__name__': 'split_origin_preparation'})

source = harness / 'src/main/java/dev/unyielding/bundlesmoke'
for file in source.glob('*.java'):
    text = file.read_text()
    for before, after in (
        ('dev.unyielding.quests.jail.', 'dev.unyielding.jail.'),
        ('dev.unyielding.quests.mixin.JailEquipmentAccess', 'dev.unyielding.jail.mixin.JailEquipmentAccess'),
        ('dev.unyielding.quests.essentials.Loc', 'dev.unyielding.jail.Loc'),
        ('dev.unyielding.quests.Config.JAIL_CERTIFIED_ADMISSIONS', 'dev.unyielding.jail.JailConfig.JAIL_CERTIFIED_ADMISSIONS'),
        ('import dev.unyielding.quests.Registration;', 'import dev.unyielding.jail.JailRegistration;'),
        ('Registration.CUSTODY_VAULT', 'JailRegistration.CUSTODY_VAULT'),
    ):
        text = text.replace(before, after)
    if file.name == 'BundleSmoke.java':
        text = replace_once(text, '    private void started(ServerStartedEvent event) {', '    private void started(ServerStartedEvent event) {\n        SplitAssertions.identity(event.getServer());')
    if file.name == 'IntegratedJailSmoke.java':
        text = replace_once(text, 'require(net.neoforged.fml.ModList.get().isLoaded("uq_uncertified_runtime"),"extra dependency did not actually load");', 'require(Boolean.getBoolean("uq.integratedCompanionProbe") || net.neoforged.fml.ModList.get().isLoaded("uq_uncertified_runtime"),"extra dependency did not actually load");')
        text = replace_once(text, 'if(Boolean.getBoolean("uq.integratedDependencyProbe"))dependencyProbe();', 'if(Boolean.getBoolean("uq.integratedDependencyProbe") || Boolean.getBoolean("uq.integratedCompanionProbe"))dependencyProbe();')
    if file.name in ('RetainedXpSmoke.java', 'CertifiedJailSmoke.java'):
        before = 'last=record().id();held();target.setInvulnerable(false);'
        text = replace_once(text, before, 'last=record().id();held();SplitAssertions.enforcement(target);target.setInvulnerable(false);')
    file.write_text(text)
shutil.copy2(ROOT / '.github/split-smoke/SplitAssertions.java', source / 'SplitAssertions.java')
# Compile all fixture types while excluding them from both production artifacts.
# Production artifacts are never unpacked, patched or replaced by source sets.
pair = {}
for file in (ROOT/'dist/120-release/Unyielding-1.2.0.jar', jail):
    assert file.read_bytes() == (harness/'libs'/file.name).read_bytes()
    pair[file.name] = hashlib.sha256(file.read_bytes()).hexdigest()
(ROOT/'dist/packaged-smoke/split-inputs.json').write_text(json.dumps({'commit': os.environ['GITHUB_SHA'], 'jars': pair, 'fixture_source': 'preserved originals plus isolated split assertions'}, indent=2)+'\n')
print('Prepared unchanged two-JAR runtime', pair)
