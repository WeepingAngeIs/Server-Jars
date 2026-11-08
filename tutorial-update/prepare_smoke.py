"""Run the accepted currency/final-JAR checks against the updated quest component."""
import os
from pathlib import Path
import shutil


if os.environ.get("GITHUB_ACTIONS") != "true" or os.environ.get("RUNNER_ENVIRONMENT") != "github-hosted":
    raise SystemExit("Preparation and game checks run only on GitHub-hosted runners.")
ROOT = Path(__file__).resolve().parents[1]
template = (ROOT / "currency-bridge/prepare_smoke.py").read_text()
replacements = {
    "currency-bridge/smoke-harness": "tutorial-update/smoke-harness",
    "dist/currency-smoke": "dist/tutorial-smoke",
    "dist/currency/Unyielding-1.1.1.jar": "dist/tutorial/Unyielding-1.1.2.jar",
    "currency-bridge/run_smoke.generated.sh": "tutorial-update/run_smoke.generated.sh",
}
for before, after in replacements.items():
    assert before in template, "Accepted currency harness changed; review the adapter"
    template = template.replace(before, after)
namespace = {"__name__": "tutorial_test_preparation", "__file__": str(__file__)}
exec(compile(template, "accepted_currency_harness_preparation", "exec"), namespace)

checks = ROOT / "tutorial-update/smoke-harness/src/main/java/dev/unyielding/bundlesmoke"
for source in (ROOT / "tutorial-update/tests").glob("*.java"):
    shutil.copy2(source, checks / source.name)

assert (checks / "CompactTaskClientSmoke.java").is_file(), "Compact task runtime assertions are missing"
runner_path = ROOT / "tutorial-update/run_smoke.generated.sh"
runner = runner_path.read_text()
runner_replacements = {
    "/tmp/uq-currency-failed.txt; do": "/tmp/uq-currency-failed.txt /tmp/uq-compact-tasks-failed.txt; do",
    "&& -f /tmp/uq-currency-client-passed.json ]]; then": "&& -f /tmp/uq-currency-client-passed.json && -f /tmp/uq-compact-tasks-passed.json ]]; then",
    "/tmp/uq-currency-*; do": "/tmp/uq-currency-* /tmp/uq-compact-tasks-*; do",
    "Passed final-JAR server, client, currency, tutorial and admin settings checks.": "Passed final-JAR server, client, currency, player guide, compact tasks and admin settings checks.",
}
for before, after in runner_replacements.items():
    assert runner.count(before) == 1, "Accepted runner changed; review regression proof handling"
    runner = runner.replace(before, after)
runner_path.write_text(runner)
