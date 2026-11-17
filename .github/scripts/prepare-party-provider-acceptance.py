"""Public OPAC and a synthetic permission provider, isolated from installed profiles."""
import os
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
assert os.environ.get('GITHUB_ACTIONS') == 'true' and os.environ.get('RUNNER_ENVIRONMENT') == 'github-hosted'
source = (ROOT / '.github/scripts/prepare-party-confirmation.py').read_text(encoding='utf-8')
source = source.replace('party-harness', 'meeting-provider-harness').replace('party-confirmation', 'party-provider-acceptance')
needle = "exec(compile(source, 'party_preparation', 'exec'),"
assert needle in source
injection = "namespace['DEPENDENCIES']['Open Parties and Claims 0.33.0']='yWmXD7GW'\nnamespace['main']()"
source = source.replace(needle, "source=source.replace(\"namespace['main']()\", " + repr(injection) + ")\n" + needle)
exec(compile(source, 'meeting_provider_preparation', 'exec'), {'__file__': str(ROOT / '.github/scripts/prepare-party-confirmation.py')})
harness = ROOT / 'dist/meeting-provider-harness'
with (harness / 'build.gradle').open('a', encoding='utf-8') as target:
    target.write("\ndependencies { compileOnly files('../../unyielding-quests/libs/kotlin-stdlib.jar') }\n")
# This is generated test policy in a fresh disposable world, never live policy.
config = harness / 'run-server/world/serverconfig/neoforge-server.toml'
config.parent.mkdir(parents=True, exist_ok=True)
config.write_text('permissionHandler="uq_bundle_smoke:meeting_contract"\n', encoding='utf-8')
