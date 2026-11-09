"""Exercise the complete JAR, including embedded models, in the accepted cloud harness."""
import hashlib
import json
import os
from pathlib import Path
import shutil

ROOT = Path(__file__).resolve().parents[1]
if os.environ.get("GITHUB_ACTIONS") != "true" or os.environ.get("RUNNER_ENVIRONMENT") != "github-hosted":
    raise SystemExit("Preparation and game checks run only on GitHub-hosted runners.")
config = json.loads((ROOT / "riding-update/release.json").read_text())
harness = ROOT / "riding-update/smoke-harness"
shutil.copytree(ROOT / "combined-unyielding/smoke-harness", harness,
                ignore=shutil.ignore_patterns(".gradle", "build", "libs", "run", "run-server"))
checks = harness / "src/main/java/dev/unyielding/bundlesmoke"
for folder in ("currency-bridge/tests", "tutorial-update/tests", "riding-update/tests"):
    for source in (ROOT / folder).glob("*.java"):
        shutil.copy2(source, checks / source.name)

bundle = checks / "BundleSmoke.java"
text = bundle.read_text()
assert text.count('"mawonna", "unyieldingquests");') == 1
bundle.write_text(text.replace('"mawonna", "unyieldingquests");',
                               '"mawonna", "unyieldingquests", "uwucobblecurrency");'))
client = checks / "BundleClientSmoke.java"
text = client.read_text()
old = 'BundleSmoke.require(mc.getResourcePackRepository().getSelectedIds().contains("file/Mawonna.zip"), "Pinned Mawonna resource pack was not loaded");'
assert old in text
text = text.replace(old, 'BundleSmoke.require(!mc.getResourcePackRepository().getSelectedIds().contains("file/Mawonna.zip"), "External pack masks embedded JAR assets");')
text = text.replace('"fairy_sliggoo", "inkyeon", "covislight", "mawonna", "mawonna_flower"',
                    '"fairy_sliggoo", "inkyeon", "covislight", "mawonna", "mawonna_flower", "lunavee", "buzzeon", "ghost_jirachi", "creepyeon"')
text = text.replace('report.put("mawonna_pack_retained", true);', 'report.put("embedded_mawonna_pack_without_external_zip", true);')
client.write_text(text)

template = (ROOT / "combined-unyielding/prepare_smoke.py").read_text()
replacements = {
    'ROOT / "combined-unyielding/smoke-harness"': 'ROOT / "riding-update/smoke-harness"',
    'ROOT / "dist/combined-smoke"': 'ROOT / "dist/riding-smoke"',
    'ROOT / "dist/combined/Unyielding-1.1.0.jar"': 'ROOT / "dist/riding/Unyielding-1.1.7.jar"',
}
for old, new in replacements.items():
    assert template.count(old) == 1
    template = template.replace(old, new)
namespace = {"__name__": "riding_test_preparation", "__file__": str(__file__)}
exec(compile(template, "accepted_combined_harness_preparation", "exec"), namespace)
namespace["PACK_COMMIT"] = config["pack"]["commit"]
namespace["PACK_BLOB"] = config["pack"]["blob"]
namespace["PACK_BASE"] = f"https://raw.githubusercontent.com/{config['pack']['repository']}/{config['pack']['commit']}"
namespace["DEPENDENCIES"].update({
    "CobbleDollars 2.0.0 Beta 6.1 NeoForge": "Iqiqjfsi",
    "Lightman's Currency 2.3.0.5 NeoForge": "n2ynBeOK",
    "EMI 1.1.24 NeoForge": "5sIPA1To",
})
namespace["main"]()
# Deliberately remove the verified companion pack from this fresh cloud-only runtime:
# all model assets and species data must load from the actual final release JAR.
for path in (harness / "run/resourcepacks/Mawonna.zip", harness / "run-server/world/datapacks/Mawonna.zip"):
    path.unlink()
inputs = ROOT / "dist/riding-smoke/runtime-inputs.json"
manifest = json.loads(inputs.read_text())
for item in manifest:
    if item.get("git_blob_sha1") == config["pack"]["blob"]:
        item["label"] = "Embedded Mawonna pack; external ZIP removed before server/client startup"
inputs.write_text(json.dumps(manifest, indent=2) + "\n")

music = namespace["download"](
    "https://cdn.modrinth.com/data/Bqnfeesx/versions/bqvWqBAp/reactivemusic-neoforge-1.3.5%2B1.21.1.jar",
    harness / "run/mods/reactivemusic-neoforge-1.3.5+1.21.1.jar")
assert hashlib.sha512(music).hexdigest() == "faae7a2bdc191f441d7cff8caa2d13d3acb9e0429ebce72b3e5e1b0344a2902f91a32b48b71818bad99df7ece812baaff443480b5712904cec0fefb5d45f80ff"

runner = (ROOT / "combined-unyielding/run_smoke.sh").read_text()
# Use the official setup-gradle action's verified installation, as the pack harness does.
runner = runner.replace('"$repository/unyielding-quests/gradlew"', 'gradle')
runner = runner.replace("combined-unyielding/smoke-harness", "riding-update/smoke-harness")
runner = runner.replace("dist/combined-smoke", "dist/riding-smoke")
runner = runner.replace('resourcePacks:["vanilla","file/Mawonna.zip"]', 'resourcePacks:["vanilla"]')
markers = ["currency-server", "currency-client", "compact-tasks", "admin-features", "admin-features-client",
           "tutorial-access", "emi-quest", "config-editor", "loot-tables", "loot-editor-client",
           "balance-live", "balance-client", "custom-riding"]
old = '&& -f /tmp/uq-bundle-client-passed.json ]]; then'
assert runner.count(old) == 1
runner = runner.replace(old, '&& -f /tmp/uq-bundle-client-passed.json ' +
                        ''.join('&& -f /tmp/uq-' + marker + '-passed.json ' for marker in markers) + ']]; then')
failed = ["currency", *markers]
old = 'for failed in /tmp/uq-bundle-client-failed.txt /tmp/uq-bundle-server-failed.txt; do'
assert runner.count(old) == 1
runner = runner.replace(old, 'for failed in /tmp/uq-bundle-client-failed.txt /tmp/uq-bundle-server-failed.txt ' +
                        ' '.join('/tmp/uq-' + marker + '-failed.txt' for marker in failed) + '; do')
runner = runner.replace('/tmp/uq-admin-server-result /tmp/uq-bundle-*; do',
                        '/tmp/uq-admin-server-result /tmp/uq-bundle-* ' +
                        ' '.join('/tmp/uq-' + marker + '-*' for marker in failed) + '; do')
runner = runner.replace('        cat /tmp/uq-tutorial-test-done /tmp/uq-admin-test-done',
                        '''        if grep -q 'ConfigParsingException' "$results/server-console.log" "$results/client-console.log"; then
            echo 'Currency configuration failed to synchronize or parse' >&2
            exit 1
        fi
        cat /tmp/uq-tutorial-test-done /tmp/uq-admin-test-done''')
runner = runner.replace('Passed final-JAR server, client, commands, Mawonna, tutorial and admin settings checks.',
                        'Passed combined JAR, embedded models, nine rideable custom species, currency and existing admin regression checks.')
(ROOT / "riding-update/run_smoke.generated.sh").write_text(runner)
