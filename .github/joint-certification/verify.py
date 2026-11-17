"""An exact certificate overlay is not certified until all native slices pass."""
import hashlib
import json
import os
from pathlib import Path

root = Path('dist/joint-pair')
evidence = Path('dist/joint-evidence')
source = json.loads((root / 'pair-source.json').read_text())
jars = {p.name: hashlib.sha256(p.read_bytes()).hexdigest() for p in root.glob('*.jar')}
assert jars == source['jars'] and len(jars) == 2
reports = {}
wallet_evidence_commit = os.environ.get('JOINT_WALLET_EVIDENCE_COMMIT', os.environ['GITHUB_SHA'])
if wallet_evidence_commit != os.environ['GITHUB_SHA']:
    assert wallet_evidence_commit == 'b77f51cad7165153dfe23fd341c89edb5c7a7d61'
assert jars == {'Minecraft-Jail-1.2.0.jar': '43074f9301315e51a476f1072884f9d66a0c47b839734989693c938cbc160027',
                'Unyielding-1.2.0.jar': '7a0a1cec09001ca6f47ee6180371842f27ef6d83f7411a7e61fff6a83e74b414'}


def one(name):
    found = list(evidence.rglob(name))
    assert len(found) == 1, (name, found)
    return found[0]


for name, count in [('wallet-verified-core.json', 5), ('wallet-verified-faults.json', 4), ('split-runtime-integrated.json', 5)]:
    row = json.loads(one(name).read_text())
    assert row['passed'] and row['commit'] == wallet_evidence_commit
    assert row.get('jars', row.get('production_jars')) == jars
    if 'phases' in row:
        assert len(row['phases']) == count
        for phase in row['phases']:
            assert phase['commit'] == row['commit']
            assert phase['jail_sha256'] == jars['Minecraft-Jail-1.2.0.jar']
            assert phase['companion_sha256'] == jars['Unyielding-1.2.0.jar']
        if name.endswith('core.json'):
            assert row['phases'][0]['fresh_default_off']
            assert row['phases'][0]['negative_cases_complete']
            assert all(p['actual_nonempty_wallet_admission'] and not p['party_admission_enabled'] for p in row['phases'])
    else:
        assert len(row['results']) == count and all(v.startswith('passed:') for v in row['results'].values())
    reports[name] = row
cases = one('cases.txt').read_text().splitlines()
assert len(cases) == 42 and all(c.startswith('passed:') for c in cases), cases
identity = json.loads(one('paired-identity.json').read_text())
assert identity['commit'] == os.environ['GITHUB_SHA'] and identity['admissions_default_off']
assert all(identity[k] == v for k, v in jars.items())
report = {'commit': os.environ['GITHUB_SHA'], 'passed': True, 'jars': jars,
          'wallet_evidence_commit': wallet_evidence_commit,
          'certificate_recipe_commit': wallet_evidence_commit,
          'wallet_fixture_source': source['wallet_source'], 'companion_source': source['companion_source'],
          'runtime_phases': {'wallet_core': 5, 'wallet_faults': 4, 'integrated_negative_gates': 5},
          'non_jail_native_cases': len(cases), 'admissions_default_off': True,
          'reports': reports, 'non_jail_identity': identity,
          'scope': 'One exact companion pin with unchanged proven wallet-only Jail code; bounded synthetic runtime only',
          'limits': ['Nonempty Pokemon parties and equipped/unsupported wallets remain blocked',
                     'Non-jail addon/HUD regression runtime is not certified for custody admission',
                     'Ambiguous unreceipted outcomes remain quarantined without replay',
                     'Live claim/rank policy, full pack and old-save migration remain unverified'],
          'deployment_authorized': False, 'live_admissions_enabled': False}
(root / 'joint-runtime-verified.json').write_text(json.dumps(report, indent=2) + '\n')
print(json.dumps({k: v for k, v in report.items() if k != 'reports'}, indent=2))
