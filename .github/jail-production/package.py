"""Exact candidate identity and unchanged companion/dependency evidence; no installation."""
import hashlib,json,os,shutil,subprocess,sys,xml.etree.ElementTree as E
from pathlib import Path
from zipfile import ZipFile
R=Path(__file__).resolve().parents[2];B='36816aff5d96ab87a97f5285a41288c5f46e040f'
sys.path.insert(0,str(R/'.github/scripts'))
from companion_certificate import verify_companion, CERTIFICATE
D=R/'dist/production-candidate';D.mkdir(parents=True,exist_ok=True)
p=R/'minecraft-jail/build/libs/Minecraft-Jail-1.2.0.jar';old=R/'dist/original-verified/Minecraft-Jail-1.2.0.jar';comp=R/'dist/original-verified/Unyielding-1.2.0.jar'
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
assert sha(old)=='5c2653a9c14d3199581fdd67e05d055b334ec2746fe7047e3c80454eb732474e'
assert sha(comp)=='61f6add6c68364b1c9dda4486948faf8d104c45ca2db2c40199de64a04ea38f7'
verify_companion(comp)
changed=[];identical=0
allowed={'JailAdmissions','JailAutoRespawn','JailArtifactBinding','JailEnforcement','JailEscapeCustody','JailHumanAudit','JailConfig'}
with ZipFile(p) as z,ZipFile(old) as before:
 names=z.namelist();assert len(names)==len(set(names))
 assert all(not any(token in n for token in ['playeronly/','bundlesmoke/','party/live/','devtest/','legacySmoke/']) for n in names)
 assert 'dev/unyielding/jail/JailAutoRespawn.class' in names
 for n in set(names)|set(before.namelist()):
  if n in names and n in before.namelist() and z.read(n)==before.read(n):identical+=1;continue
  assert n.startswith('dev/unyielding/jail/') and n.endswith('.class') and n.rsplit('/',1)[-1].split('$')[0].removesuffix('.class') in allowed,n
  changed.append(n)
 certificate=z.read(CERTIFICATE);assert certificate==before.read(CERTIFICATE)==subprocess.check_output(['git','show',B+':minecraft-jail/src/main/resources/'+CERTIFICATE],cwd=R)
 assert z.read('META-INF/neoforge.mods.toml')==before.read('META-INF/neoforge.mods.toml')
 assert z.read('minecraftjail.mixins.json')==before.read('minecraftjail.mixins.json')
 for name in ['JailAutoRespawn','JailAdmissions','JailEnforcement']:
  data=z.read('dev/unyielding/jail/'+name+'.class');assert b'com/cobblemon/' not in data and b'getParty' not in data and b'getPC' not in data
suite=[E.parse(x).getroot() for x in (R/'minecraft-jail/build/test-results/test').glob('TEST-*.xml')]
assert suite and all(int(s.get(k,'0'))==0 for s in suite for k in ['failures','errors','skipped'])
count=sum(int(s.get('tests','0')) for s in suite);assert count>=73,count
files={}
for f in (p,comp):
 shutil.copy2(f,D/f.name);files[f.name]={'sha256':sha(f),'sha512':hashlib.sha512(f.read_bytes()).hexdigest(),'bytes':f.stat().st_size}
source={str(f.relative_to(R)):sha(f) for root in ['minecraft-jail/src/main','minecraft-jail/src/test','.github/jail-production'] for f in sorted((R/root).rglob('*')) if f.is_file() and '__pycache__' not in str(f)}
report={'commit':os.environ['GITHUB_SHA'],'baseline':B,'policy':'player-only-autorespawn-v1','jail_unit_tests':count,'production_source_integrated':True,'dependency_certificate_unchanged':True,'dependency_certificate_sha256':hashlib.sha256(certificate).hexdigest(),'loader_metadata_unchanged':True,'production_mixins_unchanged':True,'companion_unchanged':True,'unchanged_jail_entries':identical,'changed_jail_entries':sorted(changed),'jars':files,'source_sha256':source,'deployment_authorized':False}
(D/'candidate-identity.json').write_text(json.dumps(report,indent=2)+'\n')
(D/'SHA256SUMS').write_text(''.join(f'{v["sha256"]}  {n}\n' for n,v in files.items()))
print(json.dumps(report,indent=2))
