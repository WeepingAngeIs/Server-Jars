"""Prepare the isolated jail-free reward protocol fixture using public pinned inputs."""
import os
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
assert os.environ.get('GITHUB_ACTIONS') == 'true' and os.environ.get('RUNNER_ENVIRONMENT') == 'github-hosted'
source = (ROOT / '.github/scripts/prepare-party-confirmation.py').read_text(encoding='utf-8')
source = source.replace('party-harness', 'reward-client-harness').replace('party-confirmation', 'reward-client-acceptance')
exec(compile(source, 'reward_client_preparation', 'exec'), {'__file__': str(ROOT / '.github/scripts/prepare-party-confirmation.py')})
build = ROOT / 'dist/reward-client-harness/build.gradle'
with build.open('a', encoding='utf-8') as target:
    target.write("\ndependencies { compileOnly files('../../unyielding-quests/libs/kotlin-stdlib.jar') }\n")
