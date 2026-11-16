"""Reject partial/mismatched results; record explicit per-slice proof and limitations."""
import hashlib
import json
import os
from pathlib import Path
import sys

slice=sys.argv[1]
root=Path('dist/packaged-smoke')
expected={
 'integrated': [(f'integrated-phase{n}/uq-integrated-result') for n in (1,2,3)] + ['integrated-dependency-refusal/uq-integrated-result','integrated-companion-refusal/uq-integrated-result'],
 'retained-xp': [f'xp-phase{n}/uq-xp-result' for n in range(1,7)],
 'certified': [f'certified-phase{n}/uq-certified-result' for n in range(1,6)],
 'startup-terrain': ['uq-packaged-1.txt','uq-packaged-2.txt','terrain-phase1/uq-jail-terrain-result','terrain-phase2/uq-jail-terrain-result'],
}[slice]
results={}
for name in expected:
    text=(root/name).read_text().strip()
    assert text.startswith('passed:'), (name,text)
    results[name]=text
inputs=json.loads((root/'split-inputs.json').read_text())
assert inputs['commit']==os.environ['GITHUB_SHA']
for name,digest in inputs['jars'].items():
    assert hashlib.sha256((Path('dist/packaged-harness/libs')/name).read_bytes()).hexdigest()==digest, f'Runtime production artifact not restored: {name}'
identities=[json.loads(p.read_text()) for p in sorted(root.glob('split-identity-*.json'))]
assert len(identities)>=len(expected), ('Missing independent startup identity evidence',len(identities),len(expected))
for identity in identities:
    assert identity['commit']==inputs['commit'] and identity['separate_jars_loaded'] and identity['non_jail_mods_loaded'] and identity['old_jail_classes_absent'] and identity['escape_reward_empty']
    assert identity['jail_sha256']==inputs['jars']['Minecraft-Jail-1.2.0.jar']
    if identity['probe']=='companion-refusal':
        assert identity['companion_sha256']!=inputs['jars']['Unyielding-1.2.0.jar']
    else:
        assert identity['companion_sha256']==inputs['jars']['Unyielding-1.2.0.jar']
report={'commit':inputs['commit'],'slice':slice,'passed':True,'production_jars':inputs['jars'],'results':results,'startup_identities':identities,
        'real_worlds_touched':False,'production_installed':False,
        'scope':'GitHub-hosted loopback-only disposable worlds; preserved native fixture contracts only',
        'limitations':['No live-save migration validation','No arbitrary modpack or unknown origin/equipment compatibility claim','Uncertain unreceipted transactions remain quarantined; no automatic replay']}
(root/f'split-runtime-{slice}.json').write_text(json.dumps(report,indent=2)+'\n')
print(json.dumps(report,indent=2))
