"""Reuse pinned assembled-JAR inputs, keeping this destructive two-client fixture separate."""
import os
from pathlib import Path
import shutil

ROOT = Path(__file__).resolve().parents[2]
if os.environ.get('GITHUB_ACTIONS') != 'true' or os.environ.get('RUNNER_ENVIRONMENT') != 'github-hosted':
    raise SystemExit('GitHub-hosted only')
source = (ROOT / '.github/scripts/prepare-reward-server.py').read_text(encoding='utf-8')
source = source.replace("dist/reward-harness", "dist/party-harness").replace("dist/reward-recovery", "dist/party-confirmation")
source = source.replace(".github/reward-recovery/BundleSmoke.java", ".github/party-confirmation/BundleSmoke.java")
exec(compile(source, 'party_preparation', 'exec'), {'__file__': str(ROOT / '.github/scripts/prepare-reward-server.py')})
harness = ROOT / 'dist/party-harness'
java = harness / 'src/main/java/dev/unyielding/bundlesmoke'
shutil.copy2(ROOT / '.github/party-confirmation/ClientChecks.java', java / 'ClientChecks.java')
shutdown = (ROOT / 'unyielding-quests/src/main/java/dev/unyielding/quests/devtest/ShutdownSmoke.java').read_text(encoding='utf-8')
shutdown = shutdown.replace('package dev.unyielding.quests.devtest;', 'package dev.unyielding.bundlesmoke;').replace('@EventBusSubscriber(modid=UnyieldingQuests.MODID)', '@EventBusSubscriber(modid="uq_bundle_smoke")')
(java / 'ShutdownSmoke.java').write_text(shutdown, encoding='utf-8')
build = harness / 'build.gradle'
text = build.read_text(encoding='utf-8')
args = "            programArguments.addAll('--quickPlayMultiplayer', '127.0.0.1:25565', '--username', 'Dev', '--width', '1280', '--height', '800')\n"
assert text.count(args) == 1
build.write_text(text.replace(args,''),encoding='utf-8')
