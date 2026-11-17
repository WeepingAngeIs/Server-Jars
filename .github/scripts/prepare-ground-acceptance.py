"""Pinned public item mods, two real clients, and the actual jail-free candidate."""
import os
import hashlib
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
assert os.environ.get('GITHUB_ACTIONS') == 'true' and os.environ.get('RUNNER_ENVIRONMENT') == 'github-hosted'
source = (ROOT / '.github/scripts/prepare-party-confirmation.py').read_text(encoding='utf-8')
source = source.replace('party-harness', 'ground-harness').replace('party-confirmation', 'ground-acceptance')
# Extend only the public input resolver, before its existing namespace main call.
needle = "exec(compile(source, 'party_preparation', 'exec'),"
assert needle in source
injection = "namespace['DEPENDENCIES'][\"Farmer's Delight exact installed 1.3.4\"]='XTVZDOol'\nnamespace['DEPENDENCIES'][\"My Nether's Delight exact installed 1.10.4.1\"]='SGFJBL5q'\nnamespace['main']()"
source = source.replace(needle, "source=source.replace(\"namespace['main']()\", " + repr(injection) + ")\n" + needle)
exec(compile(source, 'ground_preparation', 'exec'), {'__file__': str(ROOT / '.github/scripts/prepare-party-confirmation.py')})
for name, expected in {
    'FarmersDelight-1.21.1-1.3.4.jar': '1f6f8796469f747cff36f477b4f589a3d69613882399f9ee7f86db36c7bca9aa4828731f43db15dc6c7f5a4c1bbe3d4092a232e8cef010b09cec0581848fda90',
    'MyNethersDelight-1.21.1-1.10.4.1.jar': '36df146974d70fe5f7c92c4aeef613d1e0192efbb550faa88efaa8170212c07f9fc5938961d1b8da489355579910e95fea2cd5ae714b614ce17f1b8e352f0bbf',
}.items():
    assert hashlib.sha512((ROOT / 'dist/ground-harness/libs' / name).read_bytes()).hexdigest() == expected, name

