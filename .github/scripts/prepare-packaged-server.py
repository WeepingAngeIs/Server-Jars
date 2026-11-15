"""Prepare a separate disposable server whose only production source is the assembled JAR."""
import os
from pathlib import Path
import shutil

ROOT = Path(__file__).resolve().parents[2]
if os.environ.get('GITHUB_ACTIONS') != 'true' or os.environ.get('RUNNER_ENVIRONMENT') != 'github-hosted':
    raise SystemExit('Game checks run on GitHub-hosted runners only')
harness = ROOT / 'dist/packaged-harness'
shutil.copytree(ROOT / 'combined-unyielding/smoke-harness', harness,
                ignore=shutil.ignore_patterns('.gradle', 'build', 'libs', 'run', 'run-server'))
shutil.copy2(ROOT / '.github/packaged-smoke/BundleSmoke.java',
             harness / 'src/main/java/dev/unyielding/bundlesmoke/BundleSmoke.java')
(harness / 'src/main/java/dev/unyielding/bundlesmoke/BundleClientSmoke.java').unlink()
# The two-client init script supplies its own connection and display arguments.
# Do not supply the template client's arguments twice (Minecraft rejects duplicates).
build_file = harness / 'build.gradle'
build_text = build_file.read_text(encoding='utf-8')
client_args = "            programArguments.addAll('--quickPlayMultiplayer', '127.0.0.1:25565', '--username', 'Dev', '--width', '1280', '--height', '800')\n"
assert build_text.count(client_args) == 1
build_file.write_text(build_text.replace(client_args, ''), encoding='utf-8')
template = (ROOT / 'combined-unyielding/prepare_smoke.py').read_text(encoding='utf-8')
for before, after in {
    'ROOT / "combined-unyielding/smoke-harness"': 'ROOT / "dist/packaged-harness"',
    'ROOT / "dist/combined-smoke"': 'ROOT / "dist/packaged-smoke"',
    'ROOT / "dist/combined/Unyielding-1.1.0.jar"': 'ROOT / "dist/120-release/Unyielding-1.2.0.jar"',
}.items():
    assert template.count(before) == 1, before
    template = template.replace(before, after)
# Do not copy any production development classes into this independent test mod.
start = template.index('    source = ROOT / "unyielding-quests/src/main/java/dev/unyielding/quests/devtest"')
end = template.index('    (EVIDENCE / "runtime-inputs.json")', start)
template = template[:start] + template[end:]
namespace = {'__name__': 'packaged_preparation', '__file__': str(ROOT / 'combined-unyielding/prepare_smoke.py')}
exec(compile(template, 'packaged_preparation', 'exec'), namespace)
namespace['DEPENDENCIES']['Open Parties and Claims 0.33.0'] = 'yWmXD7GW'
namespace['DEPENDENCIES']['CobbleDollars 2.0.0 Beta 6.1 NeoForge'] = 'Iqiqjfsi'
namespace['DEPENDENCIES']["Lightman's Currency 2.3.0.5 NeoForge"] = 'n2ynBeOK'
if os.environ.get('UQ_ACCESSORY_INTEGRATION') == '1':
    namespace['DEPENDENCIES']['Accessories compatibility layer 0.1.12'] = 'JMgVNypu'
    namespace['DEPENDENCIES']['Curios 9.5.1 NeoForge'] = 'yohfFbgD'
namespace['main']()

if os.environ.get('UQ_ACCESSORY_INTEGRATION') == '1':
    for name in ('AccessoryCustodyPort', 'AccessoryCustodySmoke'):
        source = (ROOT / f'unyielding-quests/src/main/java/dev/unyielding/quests/devtest/{name}.java').read_text(encoding='utf-8')
        source = source.replace('package dev.unyielding.quests.devtest;', 'package dev.unyielding.bundlesmoke;', 1)
        source = source.replace('@EventBusSubscriber(modid=UnyieldingQuests.MODID)', '@EventBusSubscriber(modid="uq_bundle_smoke")', 1)
        (harness / f'src/main/java/dev/unyielding/bundlesmoke/{name}.java').write_text(source, encoding='utf-8')
    tags = harness / 'src/main/resources/data/accessories/tags/item'
    tags.mkdir(parents=True, exist_ok=True)
    (tags / 'ring.json').write_text('{"replace":false,"values":["minecraft:gold_ingot","minecraft:amethyst_shard","minecraft:diamond"]}', encoding='utf-8')

# Vanilla custody fixtures stay in this independent test mod. Only the pure
# transaction and journal are shipped; there is no production inventory adapter.
source = (ROOT / 'unyielding-quests/src/main/java/dev/unyielding/quests/devtest/VanillaCustodySmoke.java').read_text(encoding='utf-8')
source = source.replace('package dev.unyielding.quests.devtest;', 'package dev.unyielding.bundlesmoke;', 1)
source = source.replace('@EventBusSubscriber(modid = UnyieldingQuests.MODID)', '@EventBusSubscriber(modid = "uq_bundle_smoke")', 1)
(harness / 'src/main/java/dev/unyielding/bundlesmoke/VanillaCustodySmoke.java').write_text(source, encoding='utf-8')
source = (ROOT / 'unyielding-quests/src/main/java/dev/unyielding/quests/devtest/VanillaRestoreSmoke.java').read_text(encoding='utf-8')
source = source.replace('package dev.unyielding.quests.devtest;', 'package dev.unyielding.bundlesmoke;', 1)
source = source.replace('@EventBusSubscriber(modid = UnyieldingQuests.MODID)', '@EventBusSubscriber(modid = "uq_bundle_smoke")', 1)
(harness / 'src/main/java/dev/unyielding/bundlesmoke/VanillaRestoreSmoke.java').write_text(source, encoding='utf-8')

# Run the same two-real-player OPAC/jail checks from a separate test mod, never
# from production development classes or inside the assembled distributable.
source = (ROOT / 'unyielding-quests/src/main/java/dev/unyielding/quests/devtest/JailPlayerSmoke.java').read_text(encoding='utf-8')
before = 'package dev.unyielding.quests.devtest;'
assert source.count(before) == 1
source = source.replace(before, 'package dev.unyielding.bundlesmoke;', 1)
before = '@EventBusSubscriber(modid = UnyieldingQuests.MODID)'
assert source.count(before) == 1
source = source.replace(before, '@EventBusSubscriber(modid = "uq_bundle_smoke")', 1)
before = 'require(net.neoforged.fml.ModList.get().isLoaded("openpartiesandclaims"), "OPAC missing");'
assert source.count(before) == 1
source = source.replace(before, '''require(java.nio.file.Path.of(System.getenv("UQ_BUNDLE_JAR")).toRealPath().equals(
                    net.neoforged.fml.ModList.get().getModFileById("unyieldingquests").getFile().getFilePath().toRealPath()),
                    "player jail check did not load the assembled JAR");
            require(net.neoforged.fml.ModList.get().isLoaded("openpartiesandclaims"), "OPAC missing");''', 1)
(harness / 'src/main/java/dev/unyielding/bundlesmoke/JailPlayerSmoke.java').write_text(source, encoding='utf-8')

# Run terrain recovery against that same packaged production JAR in a separate world.
source = (ROOT / 'unyielding-quests/src/main/java/dev/unyielding/quests/devtest/JailTerrainPlayerSmoke.java').read_text(encoding='utf-8')
before = 'package dev.unyielding.quests.devtest;'
assert source.count(before) == 1
source = source.replace(before, 'package dev.unyielding.bundlesmoke;', 1)
before = '@EventBusSubscriber(modid = UnyieldingQuests.MODID)'
assert source.count(before) == 1
source = source.replace(before, '@EventBusSubscriber(modid = "uq_bundle_smoke")', 1)
before = 'require(net.neoforged.fml.ModList.get().isLoaded("openpartiesandclaims"), "OPAC missing");'
assert source.count(before) == 1
source = source.replace(before, '''require(java.nio.file.Path.of(System.getenv("UQ_BUNDLE_JAR")).toRealPath().equals(
                    net.neoforged.fml.ModList.get().getModFileById("unyieldingquests").getFile().getFilePath().toRealPath()),
                    "terrain check did not load the assembled JAR");
            require(net.neoforged.fml.ModList.get().isLoaded("openpartiesandclaims"), "OPAC missing");''', 1)
(harness / 'src/main/java/dev/unyielding/bundlesmoke/JailTerrainPlayerSmoke.java').write_text(source, encoding='utf-8')
