"""Isolated real-client config-audit checks with generated disposable configurations."""
import os
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
assert os.environ.get('GITHUB_ACTIONS') == 'true' and os.environ.get('RUNNER_ENVIRONMENT') == 'github-hosted'
source = (ROOT / '.github/scripts/prepare-party-confirmation.py').read_text(encoding='utf-8')
source = source.replace('party-harness', 'config-audit-harness').replace('party-confirmation', 'config-audit-acceptance')
exec(compile(source, 'config_audit_preparation', 'exec'), {'__file__': str(ROOT / '.github/scripts/prepare-party-confirmation.py')})
with (ROOT / 'dist/config-audit-harness/build.gradle').open('a', encoding='utf-8') as target:
    target.write("\ndependencies { compileOnly files('../../unyielding-quests/libs/kotlin-stdlib.jar') }\n")
