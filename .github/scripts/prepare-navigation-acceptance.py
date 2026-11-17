"""Prepare one isolated navigation fixture; reuse the verified assembled JAR."""
import os
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
assert os.environ.get('GITHUB_ACTIONS') == 'true' and os.environ.get('RUNNER_ENVIRONMENT') == 'github-hosted'
source = (ROOT / '.github/scripts/prepare-party-confirmation.py').read_text(encoding='utf-8')
source = source.replace('party-harness', 'navigation-harness').replace('party-confirmation', 'navigation-acceptance')
exec(compile(source, 'navigation_preparation', 'exec'), {'__file__': str(ROOT / '.github/scripts/prepare-party-confirmation.py')})
with (ROOT / 'dist/navigation-harness/build.gradle').open('a', encoding='utf-8') as target:
    target.write("\ndependencies { compileOnly files('../../unyielding-quests/libs/kotlin-stdlib.jar') }\n")
