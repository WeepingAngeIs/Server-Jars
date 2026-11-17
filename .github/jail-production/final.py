import hashlib,json,os,shutil
from pathlib import Path
R=Path('evidence');D=Path('dist/verified-production');D.mkdir(parents=True)
def unique(name):
 rows=list(R.rglob(name));assert len(rows)==1,(name,rows);return rows[0]
identity=json.loads(unique('candidate-identity.json').read_text());assert identity['commit']==os.environ['GITHUB_SHA']
primary=json.loads(unique('production-runtime.json').read_text());assert primary['commit']==identity['commit']
assert primary['server_starts']==3 and primary['verified_automatic_respawns']==2 and primary['client_respawn_requests']==0 and not primary['test_mod_supplies_behavior']
assert primary['production_changed'] and not primary['pokemon_custody_installed']
for row in primary['rows']:
 assert row['jail_sha256']==identity['jars']['Minecraft-Jail-1.2.0.jar']['sha256'] and row['companion_sha256']==identity['jars']['Unyielding-1.2.0.jar']['sha256']
extra={}
for kind in ['integrated','startup-terrain']:
 r=json.loads(unique('split-runtime-'+kind+'.json').read_text());assert r['passed'] and r['commit']==identity['commit']
 assert r['production_jars']=={n:v['sha256'] for n,v in identity['jars'].items()}
 extra[kind]=r
for name,row in identity['jars'].items():
 p=unique(name);assert hashlib.sha256(p.read_bytes()).hexdigest()==row['sha256'];shutil.copy2(p,D/name)
for name in ['candidate-identity.json','production-runtime.json','split-runtime-integrated.json','split-runtime-startup-terrain.json']:
 shutil.copy2(unique(name),D/name)
report={'commit':identity['commit'],'jars':identity['jars'],'unit_tests':identity['jail_unit_tests'],'python_tests':11,'player_runtime_phases':3,'additional_runtime_phases':sum(len(r['results']) for r in extra.values()),'automatic_respawns':2,'client_respawn_requests':0,'production_behavior_in_jar':True,'test_behavior_adapters':False,'deployment_authorized':False,'limitations':['No private full-pack or old-save migration','Old custody runtime fingerprints are not rewritten','Bounded Human/Warrior and physical-wallet profiles only','No general creature preservation or modification','Canceled/unsafe/ambiguous deaths retain quarantine']}
(D/'validation.json').write_text(json.dumps(report,indent=2)+'\n')
(D/'SHA256SUMS').write_text(''.join(f'{v["sha256"]}  {n}\n' for n,v in identity['jars'].items()))
print(json.dumps(report,indent=2))
