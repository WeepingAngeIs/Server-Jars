"""Run the accepted currency/final-JAR checks against the updated quest component."""
import os
from pathlib import Path
import shutil
import hashlib
import urllib.request


if os.environ.get("GITHUB_ACTIONS") != "true" or os.environ.get("RUNNER_ENVIRONMENT") != "github-hosted":
    raise SystemExit("Preparation and game checks run only on GitHub-hosted runners.")
ROOT = Path(__file__).resolve().parents[1]
template = (ROOT / "currency-bridge/prepare_smoke.py").read_text()
replacements = {
    "currency-bridge/smoke-harness": "tutorial-update/smoke-harness",
    "dist/currency-smoke": "dist/tutorial-smoke",
    "dist/currency/Unyielding-1.1.1.jar": "dist/tutorial/Unyielding-1.1.7.jar",
    "currency-bridge/run_smoke.generated.sh": "tutorial-update/run_smoke.generated.sh",
}
for before, after in replacements.items():
    assert before in template, "Accepted currency harness changed; review the adapter"
    template = template.replace(before, after)
# Include the exact EMI release used by the player's instance in the cloud runtime.
template = template.replace('namespace["main"]()', 'namespace["DEPENDENCIES"]["EMI 1.1.24 NeoForge"] = "5sIPA1To"\nnamespace["main"]()')
# Exercise only resources embedded in the production JAR. Keep older release harnesses unchanged.
anchor = 'template = (ROOT / "combined-unyielding/prepare_smoke.py").read_text()'
assert template.count(anchor) == 1
template = template.replace(anchor, anchor + '''
pack_start = template.index('    pack = HARNESS / "run/resourcepacks/Mawonna.zip"')
pack_end = template.index('    # Copy the already-accepted EULA', pack_start)
template = template[:pack_start] + template[pack_end:]
''')
namespace = {"__name__": "tutorial_test_preparation", "__file__": str(__file__)}
exec(compile(template, "accepted_currency_harness_preparation", "exec"), namespace)

# This optional audio mod is client-only. Keep it out of the dedicated server's classpath.
music = ROOT / "tutorial-update/smoke-harness/run/mods/reactivemusic-neoforge-1.3.5+1.21.1.jar"
music.parent.mkdir(parents=True, exist_ok=True)
request = urllib.request.Request(
    "https://cdn.modrinth.com/data/Bqnfeesx/versions/bqvWqBAp/reactivemusic-neoforge-1.3.5%2B1.21.1.jar",
    headers={"User-Agent": "Unyielding-Cloud-Validation/1.0"})
with urllib.request.urlopen(request, timeout=180) as response, music.open("wb") as target:
    shutil.copyfileobj(response, target)
assert hashlib.sha512(music.read_bytes()).hexdigest() == "faae7a2bdc191f441d7cff8caa2d13d3acb9e0429ebce72b3e5e1b0344a2902f91a32b48b71818bad99df7ece812baaff443480b5712904cec0fefb5d45f80ff"

checks = ROOT / "tutorial-update/smoke-harness/src/main/java/dev/unyielding/bundlesmoke"
for source in (ROOT / "tutorial-update/tests").glob("*.java"):
    shutil.copy2(source, checks / source.name)

client = checks / "BundleClientSmoke.java"
source = client.read_text()
old = 'mc.getResourcePackRepository().getSelectedIds().contains("file/Mawonna.zip")'
assert source.count(old) == 1
source = source.replace(old, 'mc.getResourcePackRepository().getSelectedIds().stream().noneMatch(id -> id.startsWith("file/"))')
source = source.replace('"Pinned Mawonna resource pack was not loaded"', '"A separate resource pack was unexpectedly loaded"')
source = source.replace('report.put("mawonna_pack_retained", true);', 'report.put("embedded_custom_resources_loaded", true);')
source = source.replace('report.put("species_textures", textures);', 'report.put("resolved_texture_variant_count", textures.size() * 2);')
client.write_text(source)

# The runtime language mod carries Kotlin in nested JARs; javac needs its compile-only API directly.
build = ROOT / "tutorial-update/smoke-harness/build.gradle"
build.write_text(build.read_text() + '\ndependencies { compileOnly files("../../unyielding-quests/libs/kotlin-stdlib.jar") }\n')

for helper in ("CompactTaskClientSmoke.java", "TutorialAccessServerSmoke.java", "AdminFeatureSmoke.java", "AdminFeatureClientSmoke.java", "EmiQuestClientSmoke.java", "ConfigEditorSmoke.java", "LootTableServerSmoke.java", "LootEditorClientSmoke.java", "BalanceEditorSmoke.java", "BalanceLiveSmoke.java", "BalanceClientSmoke.java", "BalanceUiSmoke.java", "MusicEditorSmoke.java", "MusicClientSmoke.java", "PokemonEditorSmoke.java", "PokemonLiveClientSmoke.java", "PokemonEditorClientSmoke.java", "PokemonCounterSmoke.java"):
    assert (checks / helper).is_file(), f"Runtime assertions are missing: {helper}"
runner_path = ROOT / "tutorial-update/run_smoke.generated.sh"
runner = runner_path.read_text()
runner_replacements = {
    "/tmp/uq-currency-failed.txt; do": "/tmp/uq-currency-failed.txt /tmp/uq-compact-tasks-failed.txt /tmp/uq-admin-features-failed.txt /tmp/uq-admin-features-client-failed.txt /tmp/uq-tutorial-access-failed.txt /tmp/uq-emi-quest-failed.txt /tmp/uq-config-editor-failed.txt /tmp/uq-loot-tables-failed.txt /tmp/uq-loot-editor-client-failed.txt /tmp/uq-balance-live-failed.txt /tmp/uq-balance-client-failed.txt; do",
    "&& -f /tmp/uq-currency-client-passed.json ]]; then": "&& -f /tmp/uq-currency-client-passed.json && -f /tmp/uq-compact-tasks-passed.json && -f /tmp/uq-admin-features-passed.json && -f /tmp/uq-admin-features-client-passed.json && -f /tmp/uq-tutorial-access-passed.json && -f /tmp/uq-emi-quest-passed.json && -f /tmp/uq-config-editor-passed.json && -f /tmp/uq-loot-tables-passed.json && -f /tmp/uq-loot-editor-client-passed.json && -f /tmp/uq-balance-live-passed.json && -f /tmp/uq-balance-client-passed.json ]]; then",
    "/tmp/uq-currency-*; do": "/tmp/uq-currency-* /tmp/uq-compact-tasks-* /tmp/uq-admin-features-* /tmp/uq-tutorial-access-* /tmp/uq-emi-quest-* /tmp/uq-config-editor-* /tmp/uq-loot-tables-* /tmp/uq-loot-editor-client-* /tmp/uq-balance-*; do",
    "Passed final-JAR server, client, currency, tutorial and admin settings checks.": "Passed final-JAR server, client, currency, player guide, compact tasks and admin settings checks.",
}
for before, after in runner_replacements.items():
    assert runner.count(before) == 1, "Accepted runner changed; review regression proof handling"
    runner = runner.replace(before, after)
runner = runner.replace("/tmp/uq-balance-client-failed.txt; do", "/tmp/uq-balance-client-failed.txt /tmp/uq-pokemon-server-failed.txt /tmp/uq-pokemon-client-failed.txt; do")
runner = runner.replace("&& -f /tmp/uq-balance-client-passed.json ]]; then", "&& -f /tmp/uq-balance-client-passed.json && -f /tmp/uq-pokemon-server-passed.json && -f /tmp/uq-pokemon-client-passed.json ]]; then")
runner = runner.replace("/tmp/uq-balance-*; do", "/tmp/uq-balance-* /tmp/uq-pokemon-*; do")
runner = runner.replace('resourcePacks:["vanilla","file/Mawonna.zip"]', 'resourcePacks:["vanilla"]')
runner_path.write_text(runner)
