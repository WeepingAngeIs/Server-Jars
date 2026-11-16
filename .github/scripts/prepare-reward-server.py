"""Prepare only the jail-free assembled Unyielding credit-recovery fixture."""
import os
from pathlib import Path
import shutil

ROOT = Path(__file__).resolve().parents[2]
if os.environ.get('GITHUB_ACTIONS') != 'true' or os.environ.get('RUNNER_ENVIRONMENT') != 'github-hosted':
    raise SystemExit('Runtime preparation is GitHub-hosted only')
harness = ROOT / 'dist/reward-harness'
shutil.copytree(ROOT / 'combined-unyielding/smoke-harness', harness,
                ignore=shutil.ignore_patterns('.gradle', 'build', 'libs', 'run', 'run-server'))
sources = harness / 'src/main/java'
shutil.rmtree(sources)
target = sources / 'dev/unyielding/bundlesmoke/BundleSmoke.java'
target.parent.mkdir(parents=True)
shutil.copy2(ROOT / '.github/reward-recovery/BundleSmoke.java', target)
template = (ROOT / 'combined-unyielding/prepare_smoke.py').read_text(encoding='utf-8')
for before, after in {
    'ROOT / "combined-unyielding/smoke-harness"': 'ROOT / "dist/reward-harness"',
    'ROOT / "dist/combined-smoke"': 'ROOT / "dist/reward-recovery"',
    'ROOT / "dist/combined/Unyielding-1.1.0.jar"': 'ROOT / "dist/120-release/Unyielding-1.2.0.jar"',
}.items():
    assert template.count(before) == 1
    template = template.replace(before, after)
start = template.index('    source = ROOT / "unyielding-quests/src/main/java/dev/unyielding/quests/devtest"')
end = template.index('    (EVIDENCE / "runtime-inputs.json")', start)
template = template[:start] + template[end:]
namespace = {'__name__': 'reward_preparation', '__file__': str(ROOT / 'combined-unyielding/prepare_smoke.py')}
exec(compile(template, 'reward_preparation', 'exec'), namespace)
namespace['main']()
