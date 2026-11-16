"""Final delivery is emitted only after all exact-commit runtime slices pass."""
import hashlib
import json
import os
from pathlib import Path
import shutil

out=Path('dist/verified-split-runtime');out.mkdir(parents=True,exist_ok=True)
build=Path('dist/jail-split');reports={}
for slice in ('integrated','retained-xp','certified','startup-terrain'):
    found=list(Path('dist/runtime-evidence').rglob(f'split-runtime-{slice}.json'))
    assert len(found)==1, ('Missing or duplicate runtime slice',slice,found)
    r=json.loads(found[0].read_text());assert r['passed'] and r['commit']==os.environ['GITHUB_SHA']
    reports[slice]=r
    shutil.copy2(found[0],out/f'runtime-{slice}.json')
code=json.loads((build/'verification.json').read_text());assert code['commit']==os.environ['GITHUB_SHA']
for name,metadata in code['artifacts'].items():
    digest=hashlib.sha256((build/name).read_bytes()).hexdigest();assert metadata['sha256']==digest
    assert all(r['production_jars'][name]==digest for r in reports.values())
    shutil.copy2(build/name,out/name)
shutil.copy2(build/'SHA256SUMS',out/'SHA256SUMS')
shutil.copy2(build/'verification.json',out/'code-verification.json')
for doc in ('README.md','RUNTIME-VALIDATION.md','companion-certificate.json'):
    shutil.copy2(Path('minecraft-jail')/doc,out/doc)
summary={'commit':os.environ['GITHUB_SHA'],'run_id':os.environ['GITHUB_RUN_ID'],'all_bounded_runtime_slices_passed':True,
         'artifacts':code['artifacts'],'code_verification':code,'slices':reports,
         'no_live_world_or_install_changes':True,'admission_default_enabled':False,
         'remaining_limits':['Only explicitly certified dependency files and native state contracts are supported.',
           'Nonempty Pokemon parties and occupied wallets stay blocked without mutation.',
           'Unknown or ambiguous custody remains quarantined; successful restart confirmation is not automatic replay.',
           'Pre-split live-save migration and the full personal modpack were not tested.',
           'No art work or manual visual/client usability review was performed.']}
(out/'runtime-verification.json').write_text(json.dumps(summary,indent=2)+'\n')
print(json.dumps({k:v for k,v in summary.items() if k not in ('code_verification','slices')},indent=2))
