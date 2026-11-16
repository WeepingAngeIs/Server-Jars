"""Compare assembled split outputs against the exact pre-extraction build.

Static binary/source verification only. This does not certify runtime recovery,
origin callbacks, packet mixing, UI, world loading or mod compatibility.
"""
import argparse
import hashlib
import json
from pathlib import Path
import shutil
import subprocess
import tomllib
import xml.etree.ElementTree as ET
from zipfile import ZipFile
from class_dependencies import application_references, missing_references
from jail_split import BASE_COMMIT, MIXINS, OLD_PACKAGE, NEW_PACKAGE, RESOURCES, is_jail_entry
from companion_certificate import CERTIFICATE, validate_extension, verify_companion

parser=argparse.ArgumentParser()
parser.add_argument('--before', required=True, type=Path)
parser.add_argument('--unyielding', default='dist/120-release/Unyielding-1.2.0.jar', type=Path)
parser.add_argument('--jail', default='minecraft-jail/build/libs/Minecraft-Jail-1.2.0.jar', type=Path)
args=parser.parse_args()

def members(path):
    with ZipFile(path) as archive:
        assert len(archive.namelist())==len(set(archive.namelist())), f'Duplicate entries in {path}'
        return {name:archive.read(name) for name in archive.namelist()}

def family(name, root):
    return name==root+'.class' or name.startswith(root+'$') and name.endswith('.class')

before=members(args.before); after=members(args.unyielding); jail=members(args.jail)
removed=set(before)-set(after)
assert all(is_jail_entry(name) for name in removed), f'Non-jail entries removed: {[n for n in removed if not is_jail_entry(n)]}'
assert not set(after)-set(before), f'Unexpected Unyielding additions: {set(after)-set(before)}'
assert not any(is_jail_entry(name) for name in after), 'Jail code/assets remain in Unyielding'
changed={name for name in set(before)&set(after) if before[name]!=after[name]}
allowed_roots=['dev/unyielding/quests/'+name for name in ('Config','Registration','essentials/Essentials')]
mixin_path='assets/unyieldingquests/pokemon.mixins.json'
assert all(name==mixin_path or any(family(name,root) for root in allowed_roots) for name in changed), f'Unrelated binary changes: {sorted(changed)}'
expected=json.loads(before[mixin_path])
for category in ('mixins','client','server'):
    if category in expected: expected[category]=[name for name in expected[category] if name not in MIXINS]
assert json.loads(after[mixin_path])==expected, 'Non-jail mixins changed'
for resource in RESOURCES:
    assert resource not in after, f'Jail resource remains in companion: {resource}'
    if resource == CERTIFICATE:
        validate_extension(before[resource], jail[resource])
    else:
        assert jail[resource]==before[resource], f'Resource not moved exactly: {resource}'
companion_proof = verify_companion(args.unyielding)
assert companion_proof['sha512'] in json.loads(jail[CERTIFICATE])['sha512']
for entries,label in ((after,'Unyielding'),(jail,'Minecraft Jail')):
    classes={name:data for name,data in entries.items() if name.endswith('.class')}
    missing=missing_references(classes)
    assert not missing, f'{label} unresolved application classes: {missing}'
    assert not any('/devtest/' in name or '/legacySmoke/' in name for name in classes), f'{label} contains smoke fixtures'
    for name,data in classes.items():
        refs=application_references(data)
        if label=='Unyielding':
            assert not any(ref.startswith((OLD_PACKAGE,NEW_PACKAGE)) for ref in refs), f'Jail dependency in {name}'
        else:
            assert name.startswith(NEW_PACKAGE), f'Unexpected embedded application/dependency class: {name}'
            assert all(ref.startswith(NEW_PACKAGE) for ref in refs), f'Unyielding dependency in {name}'
assert not ({name for name in after if name.endswith('.class')} & {name for name in jail if name.endswith('.class')}), 'Duplicate classes across split JARs'
metadata=tomllib.loads(jail['META-INF/neoforge.mods.toml'].decode())
assert [mod['modId'] for mod in metadata['mods']]==['minecraftjail']
deps={dep['modId']:dep for dep in metadata['dependencies']['minecraftjail']}
assert all(deps[mod]['type']=='required' for mod in ('minecraft','neoforge','cobblemon'))
assert deps['unyieldingquests']['type']=='optional'
assert metadata['mixins']==[{'config':'minecraftjail.mixins.json'}]
mixins=json.loads(jail['minecraftjail.mixins.json'])
assert mixins['package']=='dev.unyielding.jail.mixin' and set(mixins['mixins'])==set(MIXINS)
for mixin in MIXINS: assert NEW_PACKAGE+'mixin/'+mixin+'.class' in jail
advancement=json.loads(jail['data/unyieldingquests/advancement/jail_escape.json'])
assert 'rewards' not in advancement and advancement['criteria']['escape']['trigger']=='minecraft:impossible'

def junit(directory):
    totals=dict(tests=0,failures=0,errors=0,skipped=0)
    files=list(Path(directory).glob('TEST-*.xml'));assert files, f'No test evidence at {directory}'
    for path in files:
        suite=ET.parse(path).getroot()
        for key in totals: totals[key]+=int(suite.get(key,'0'))
    assert totals['tests']>0 and totals['failures']==totals['errors']==0, f'Tests did not pass: {directory}: {totals}'
    return totals

out=Path('dist/jail-split');out.mkdir(parents=True,exist_ok=True)
sha=subprocess.check_output(['git','rev-parse','HEAD'],text=True).strip()
# Refuse dirty tracked files, including a source edit after the build.
assert not subprocess.check_output(['git','status','--porcelain','--untracked-files=no'],text=True).strip(), 'Tracked source changed during verification'
report={
    'commit':sha,'extracted_from':BASE_COMMIT,'minecraft_launched':False,'runtime_verified':False,
    'non_jail_entries_unchanged':len(set(before)&set(after))-len(changed),
    'removed_jail_entries':len(removed),'changed_integration_entries':sorted(changed),
    'separate_application_class_closure':True,'duplicate_application_classes':False,
    'tests':{'unyielding':junit('unyielding-quests/build/test-results/test'),'jail':junit('minecraft-jail/build/test-results/test')},
    'companion_certificate':companion_proof,
    'artifacts':{}
}
lines=[]
for path in (args.unyielding,args.jail):
    target=out/path.name;shutil.copy2(path,target)
    digest=hashlib.sha256(target.read_bytes()).hexdigest()
    report['artifacts'][target.name]={'sha256':digest,'bytes':target.stat().st_size}
    lines.append(f'{digest}  {target.name}')
(out/'SHA256SUMS').write_text('\n'.join(lines)+'\n')
(out/'verification.json').write_text(json.dumps(report,indent=2)+'\n')
shutil.copy2('minecraft-jail/README.md',out/'README-Minecraft-Jail.md')
print(json.dumps(report,indent=2))
