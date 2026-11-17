"""Compile candidate safeguards only into the separate hosted test mod; keep production JARs unchanged."""
import os, shutil, subprocess
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
assert os.environ.get('GITHUB_ACTIONS')=='true' and os.environ.get('RUNNER_ENVIRONMENT')=='github-hosted'
subprocess.run(['python3','.github/party-storage/prepare.py'],cwd=ROOT,check=True)
source=ROOT/'dist/packaged-harness/src/main/java'
for tree in ('partySafety','partySafetyNative'):
    for file in (ROOT/f'minecraft-jail/src/{tree}/java').rglob('*.java'):
        dest=source/file.relative_to(ROOT/f'minecraft-jail/src/{tree}/java')
        dest.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(file,dest)
shutil.copy2(ROOT/'.github/party-guard/PartyGuardProbe.java',source/'dev/unyielding/bundlesmoke/PartyGuardProbe.java')
p=source/'dev/unyielding/bundlesmoke/PartyStorageBoundaryProbe.java';s=p.read_text()
changes={
'ownership();queuedHalt();':'ownership();PartyGuardProbe.beforeHalt();queuedHalt();',
'evidence.put("complete_party_pc_snapshots_preserved_in_conflict",true);close(f);finish();':'evidence.put("complete_party_pc_snapshots_preserved_in_conflict",true);close(f);PartyGuardProbe.afterRestart();finish();',
'else if(phase==2)afterRestart();else throw':'else if(phase==2)afterRestart();else if(phase==3){PartyGuardProbe.afterReleaseRestart();finish();}else throw',
}
for old,new in changes.items():
    assert s.count(old)==1,old
    s=s.replace(old,new)
p.write_text(s)
