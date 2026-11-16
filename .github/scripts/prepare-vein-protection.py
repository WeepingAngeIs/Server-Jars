"""Independent assembled-JAR mining fixture, with pinned OPAC and actual clients."""
import os
from pathlib import Path
import shutil
ROOT = Path(__file__).resolve().parents[2]
if os.environ.get('GITHUB_ACTIONS') != 'true' or os.environ.get('RUNNER_ENVIRONMENT') != 'github-hosted':
    raise SystemExit('GitHub-hosted only')
source=(ROOT/'.github/scripts/prepare-party-confirmation.py').read_text(encoding='utf-8')
source=source.replace('party-harness','vein-harness').replace('party-confirmation','vein-protection')
# The nested pinned-input preparation resolves these names from its reward template.
needle="exec(compile(source, 'party_preparation', 'exec'),"
assert needle in source
source=source.replace(needle,"source=source.replace(\"namespace['main']()\",\"namespace['DEPENDENCIES']['Open Parties and Claims 0.33.0'] = 'yWmXD7GW'\\nnamespace['main']()\")\n"+needle)
exec(compile(source,'vein_preparation','exec'),{'__file__':str(ROOT/'.github/scripts/prepare-party-confirmation.py')})
