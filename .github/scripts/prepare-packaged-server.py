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
namespace['main']()

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
