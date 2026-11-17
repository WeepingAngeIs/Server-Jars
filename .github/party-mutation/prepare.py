"""Hosted-only composition of unchanged baselines and the bounded mutation test policy."""
import hashlib,json,os,shutil,subprocess
from pathlib import Path
R=Path(__file__).resolve().parents[2]
assert os.environ.get('GITHUB_ACTIONS')=='true' and os.environ.get('RUNNER_ENVIRONMENT')=='github-hosted'
subprocess.run(['python3','.github/party-handback/prepare.py'],cwd=R,check=True)
h=R/'dist/packaged-harness';d=R/'dist/packaged-smoke';java=h/'src/main/java'
for name in ('HandbackProbe.java','PublicationGate.java'):
 p=java/'dev/unyielding/jail/party/live'/name
 assert p.is_file();p.unlink()
for p in (R/'.github/party-mutation/java').rglob('*.java'):
 target=java/p.relative_to(R/'.github/party-mutation/java');target.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(p,target)
manifest=json.loads((d/'handback-wrapper-manifest.json').read_text())
for row in manifest['classes']:
 p=java/'dev/unyielding/jail/party/live/mixin'/(row['mixin']+'.java')
 s=p.read_text();assert 'PublicationGate' in s;s=s.replace('PublicationGate','MutationGate');p.write_text(s)
 (d/'live-wrapper-source'/p.name).write_text(s);row['sha256']=hashlib.sha256(s.encode()).hexdigest()
for name in manifest['extra_hooks']:
 p=java/'dev/unyielding/jail/party/live/mixin'/(name+'.java')
 s=p.read_text();assert 'PublicationGate' in s;s=s.replace('PublicationGate','MutationGate');p.write_text(s)
 (d/'live-wrapper-source'/p.name).write_text(s);manifest['extra_hooks'][name]=hashlib.sha256(s.encode()).hexdigest()
(d/'mutation-wrapper-manifest.json').write_text(json.dumps(manifest,indent=2)+'\n')
driver=R/'dist/party-live-harness/meeting-two-player.sh';s=driver.read_text()
needle='.github/party-handback/check_halt.py';assert s.count(needle)==1
s=s.replace(needle,'.github/party-mutation/check_halt.py');driver.write_text(s)
proof=json.loads((R/'dist/reused-handback/handback-verification.json').read_text())
assert proof['commit']=='549ca5db231446e3cb9802345e53a4055b7fa3bf' and proof['runtime_phases']==6
assert proof['read_only_native_publication'] is True and proof['playable_handback_verified'] is False
for row in proof['native']:
 for path,digest in row['source_hashes'].items():assert hashlib.sha256((R/path).read_bytes()).hexdigest()==digest,path
files=list((R/'.github/party-mutation').rglob('*'))+[R/'.github/workflows/jail-party-mutation.yml']
hashes={str(p.relative_to(R)):hashlib.sha256(p.read_bytes()).hexdigest() for p in files if p.is_file() and '__pycache__' not in p.parts}
(d/'mutation-source-hashes.json').write_text(json.dumps(hashes,indent=2)+'\n')
print('Prepared bounded nickname mutation test mod; production JARs and baseline source unchanged')
