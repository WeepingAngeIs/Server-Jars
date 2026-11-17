"""Prepare one isolated navigation fixture; reuse the verified assembled JAR."""
import os
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
assert os.environ.get('GITHUB_ACTIONS') == 'true' and os.environ.get('RUNNER_ENVIRONMENT') == 'github-hosted'
source = (ROOT / '.github/scripts/prepare-party-confirmation.py').read_text(encoding='utf-8')
source = source.replace('party-harness', 'compact-harness').replace('party-confirmation', 'compact-navigation')
exec(compile(source, 'compact_preparation', 'exec'), {'__file__': str(ROOT / '.github/scripts/prepare-party-confirmation.py')})
with (ROOT / 'dist/compact-harness/build.gradle').open('a', encoding='utf-8') as target:
    target.write("\ndependencies { compileOnly files('../../unyielding-quests/libs/kotlin-stdlib.jar') }\n")
