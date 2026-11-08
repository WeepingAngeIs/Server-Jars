"""Reuse the accepted final-JAR harness, adding currency compatibility checks."""
import os
from pathlib import Path
import shutil

ROOT = Path(__file__).resolve().parents[1]
if os.environ.get("GITHUB_ACTIONS") != "true" or os.environ.get("RUNNER_ENVIRONMENT") != "github-hosted":
    raise SystemExit("Preparation and gameplay checks run only on GitHub-hosted runners.")

HARNESS = ROOT / "currency-bridge/smoke-harness"
shutil.copytree(ROOT / "combined-unyielding/smoke-harness", HARNESS,
                ignore=shutil.ignore_patterns(".gradle", "build", "libs", "run", "run-server"))
checks = HARNESS / "src/main/java/dev/unyielding/bundlesmoke"
for source in (ROOT / "currency-bridge/tests").glob("*.java"):
    shutil.copy2(source, checks / source.name)

bundle = checks / "BundleSmoke.java"
text = bundle.read_text()
old = '"mawonna", "unyieldingquests");'
assert text.count(old) == 1
bundle.write_text(text.replace(old, '"mawonna", "unyieldingquests", "uwucobblecurrency");'))

template = (ROOT / "combined-unyielding/prepare_smoke.py").read_text()
replacements = {
    'ROOT / "combined-unyielding/smoke-harness"': 'ROOT / "currency-bridge/smoke-harness"',
    'ROOT / "dist/combined-smoke"': 'ROOT / "dist/currency-smoke"',
    'ROOT / "dist/combined/Unyielding-1.1.0.jar"': 'ROOT / "dist/currency/Unyielding-1.1.1.jar"',
}
for old, new in replacements.items():
    assert template.count(old) == 1, "Original cloud harness changed; review the reuse adapter"
    template = template.replace(old, new)
namespace = {"__name__": "currency_test_preparation", "__file__": str(__file__)}
exec(compile(template, "accepted_combined_harness_preparation", "exec"), namespace)
namespace["DEPENDENCIES"].update({
    "CobbleDollars 2.0.0 Beta 6.1 NeoForge": "Iqiqjfsi",
    "Lightman's Currency 2.3.0.5 NeoForge": "n2ynBeOK",
})
namespace["main"]()

runner = (ROOT / "combined-unyielding/run_smoke.sh").read_text()
runner = runner.replace('project="$repository/combined-unyielding/smoke-harness"',
                        'project="$repository/currency-bridge/smoke-harness"')
runner = runner.replace('results="$repository/dist/combined-smoke"',
                        'results="$repository/dist/currency-smoke"')
runner = runner.replace('for failed in /tmp/uq-bundle-client-failed.txt /tmp/uq-bundle-server-failed.txt; do',
                        'for failed in /tmp/uq-bundle-client-failed.txt /tmp/uq-bundle-server-failed.txt /tmp/uq-currency-failed.txt; do')
runner = runner.replace('&& -f /tmp/uq-bundle-client-passed.json ]]; then',
                        '&& -f /tmp/uq-bundle-client-passed.json && -f /tmp/uq-currency-server-passed.json && -f /tmp/uq-currency-client-passed.json ]]; then')
runner = runner.replace('/tmp/uq-admin-server-result /tmp/uq-bundle-*; do',
                        '/tmp/uq-admin-server-result /tmp/uq-bundle-* /tmp/uq-currency-*; do')
runner = runner.replace('        cat /tmp/uq-tutorial-test-done /tmp/uq-admin-test-done',
                        '''        if grep -q 'ConfigParsingException' "$results/server-console.log" "$results/client-console.log"; then
            echo 'Currency configuration failed to synchronize or parse' >&2
            exit 1
        fi
        cat /tmp/uq-tutorial-test-done /tmp/uq-admin-test-done''')
runner = runner.replace('Passed final-JAR server, client, commands, Mawonna, tutorial and admin settings checks.',
                        'Passed final-JAR server, client, currency, tutorial and admin settings checks.')
assert '/tmp/uq-currency-server-passed.json' in runner
(ROOT / "currency-bridge/run_smoke.generated.sh").write_text(runner)
