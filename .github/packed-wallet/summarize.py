"""Publish only an exact pair with all new custody and prior regression slices passing."""
import hashlib
import json
import os
from pathlib import Path
import shutil
from verify import PHASES
root=Path('dist');pair=root/'packed-wallet-pair';evidence=root/'wallet-evidence'
identity=json.loads((pair/'build-verification.json').read_text());commit=os.environ['GITHUB_SHA']
assert identity['commit']==commit
for name,sha in identity['jars'].items():assert hashlib.sha256((pair/name).read_bytes()).hexdigest()==sha
summaries=[]
for scenario,n in PHASES.items():
    paths=list(evidence.rglob(f'packed-wallet-{scenario}-summary.json'));assert len(paths)==1,(scenario,paths)
    r=json.loads(paths[0].read_text())
    assert r['commit']==commit and r['all_passed'] is True and r['phase_count']==n and len(r['reports'])==n
    assert r['jail_sha256']==identity['jars']['Minecraft-Jail-1.2.0.jar'] and r['companion_sha256']==identity['jars']['Unyielding-1.2.0.jar']
    summaries.append(r)
regressions=[]
for slice in ('integrated','retained-xp','certified','startup-terrain'):
    paths=list(evidence.rglob(f'split-runtime-{slice}.json'));assert len(paths)==1,(slice,paths)
    r=json.loads(paths[0].read_text());assert r['passed'] is True and r['commit']==commit and r['production_jars']==identity['jars']
    regressions.append(r)
out=root/'packed-wallet-verified';shutil.copytree(pair,out)
report={'commit':commit,'jars':identity['jars'],'junit':identity['junit'],'custody_phases':sum(PHASES.values()),'regression_phases':sum(len(r['results']) for r in regressions),'custody':summaries,'regressions':regressions,'bounded_support':'Opt-in exact one non-selected packed occupied leather wallet containing native gold coins; zero XP; unchanged approved runtime only','blocked':['Nonempty Pokemon parties','Equipped/native/Curios wallets','Multiple wallets or functional/upgraded wallet components','Uncertain unreceipted mutations; no automatic replay','Unreviewed full modpack and old-save migration'],'live_worlds_touched':False,'non_jail_companion_changed':False}
(out/'wallet-runtime-verification.json').write_text(json.dumps(report,indent=2)+'\n')
shutil.copy2('minecraft-jail/PACKED-WALLET-CUSTODY.md',out/'PACKED-WALLET-CUSTODY.md')
print('Exact-commit delivery accepted:',commit)
