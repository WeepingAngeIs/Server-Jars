"""Bound the implementation diff; never rebuild or bless a different companion."""
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys
import zipfile
import xml.etree.ElementTree as ET
ROOT=Path(__file__).resolve().parents[2]
BASE='d1e300df17d9a5e9caa7699c6bbd418c01463504'
FAMILIES={'JailAdmissions','JailConfig','JailEquipmentEffects','JailEquipmentPort','JailIntegrated','JailPackedWallet','PackedWalletPolicy'}
PRODUCTION={f'minecraft-jail/src/main/java/dev/unyielding/jail/{n}.java' for n in FAMILIES}
ALLOWED_FIXED={'.github/workflows/jail-packed-wallet-audit.yml','.github/workflows/jail-packed-wallet-custody.yml','minecraft-jail/PACKED-WALLET-CUSTODY.md','minecraft-jail/src/test/java/dev/unyielding/jail/PackedWalletPolicyTest.java'}
changes=subprocess.check_output(['git','diff','--name-only',BASE,'HEAD'],cwd=ROOT,text=True).splitlines()
assert changes and all(p in PRODUCTION|ALLOWED_FIXED or p.startswith('.github/packed-wallet/') for p in changes),changes
subprocess.run(['git','diff','--exit-code',BASE,'HEAD','--','minecraft-jail/src/main/resources','minecraft-jail/companion-certificate.json','minecraft-jail/src/legacySmoke','unyielding-quests','combined-unyielding','currency-bridge','model-hotfix','iv-substitute-update','riding-update','settings-update','tutorial-update'],cwd=ROOT,check=True)
if '--source-only' in sys.argv:
    print('Scoped Jail-only source change:',changes);raise SystemExit(0)
old=ROOT/'dist/original-verified/Minecraft-Jail-1.2.0.jar'
new=ROOT/'minecraft-jail/build/libs/Minecraft-Jail-1.2.0.jar'
companion=ROOT/'dist/original-verified/Unyielding-1.2.0.jar'
assert hashlib.sha256(old.read_bytes()).hexdigest()=='d3145b1bc8f04c0370db30ff10e87158b6c3c88c0fde68cb0f226d3b73f79baf'
assert hashlib.sha256(companion.read_bytes()).hexdigest()=='61f6add6c68364b1c9dda4486948faf8d104c45ca2db2c40199de64a04ea38f7'
def entries(p):
    with zipfile.ZipFile(p) as z:
        assert len(z.namelist())==len(set(z.namelist()))
        return {n:z.read(n) for n in z.namelist() if not n.endswith('/')}
a,b=entries(old),entries(new)
changed=sorted(n for n in a.keys()|b.keys() if a.get(n)!=b.get(n))
assert not a.keys()-b.keys(),('Removed original class/resource',a.keys()-b.keys())
for n in changed:
    assert n.startswith('dev/unyielding/jail/') and n.endswith('.class') and n.rsplit('/',1)[-1].split('$')[0].split('.')[0] in FAMILIES,n
assert not any('bundlesmoke' in n or 'devtest' in n or 'legacySmoke' in n for n in b)
sys.path.insert(0,str(ROOT/'.github/scripts'))
from class_dependencies import missing_references
classes={n:v for n,v in b.items() if n.endswith('.class')}
assert not missing_references(classes),missing_references(classes)
other={n:v for n,v in entries(companion).items() if n.endswith('.class') and n.startswith('dev/unyielding/')}
assert not classes.keys()&other.keys(),classes.keys()&other.keys()
xml=list((ROOT/'minecraft-jail/build/test-results/test').glob('TEST-*.xml'));assert xml
counts={key:sum(int(ET.parse(p).getroot().get(key,'0')) for p in xml) for key in ('tests','failures','errors','skipped')}
assert counts['tests']>=68 and not any(counts[k] for k in ('failures','errors','skipped')),counts
out=ROOT/'dist/packed-wallet-pair';out.mkdir(parents=True,exist_ok=True)
pair={}
for p in (new,companion):
    (out/p.name).write_bytes(p.read_bytes());pair[p.name]=hashlib.sha256(p.read_bytes()).hexdigest()
(out/'SHA256SUMS').write_text(''.join(f'{h}  {n}\n' for n,h in pair.items()))
(out/'build-verification.json').write_text(json.dumps({'commit':os.environ['GITHUB_SHA'],'base':BASE,'jars':pair,'junit':counts,'changed_jail_entries':changed,'unchanged_jail_entries':len(a)-len(set(changed)&a.keys()),'unchanged_companion':True,'certificates_unchanged':True,'scoped_source_changes':changes,'runtime_verified':False},indent=2)+'\n')
print((out/'build-verification.json').read_text())
