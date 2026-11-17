"""Adapt existing actual custody tests to a genuinely occupied packed wallet, not a preservation probe."""
import os
from pathlib import Path
import shutil
import subprocess
ROOT=Path(__file__).resolve().parents[2]
assert os.environ.get('GITHUB_ACTIONS')=='true' and os.environ.get('RUNNER_ENVIRONMENT')=='github-hosted'
subprocess.run(['python3','.github/scripts/prepare-split-server.py'],cwd=ROOT,check=True)
source=ROOT/'dist/packaged-harness/src/main/java/dev/unyielding/bundlesmoke'
p=source/'CertifiedJailSmoke.java';s=p.read_text()
def replace(a,b):
    global s
    assert s.count(a)==1, (a,s.count(a))
    s=s.replace(a,b,1)
replace('new JailEquipmentPort(target,id,retired,true)','new JailEquipmentPort(target,id,retired,true,true)')
replace('        flush();saved();UUID probe=', '        PackedWalletProof.seed(target);flush();saved();UUID probe=')
replace('    static void held()throws Exception {','    static void held()throws Exception {\n        PackedWalletProof.held(target,last);')
replace('    static void returned(float expectedHealth)throws Exception {','    static void returned(float expectedHealth)throws Exception {\n        PackedWalletProof.returned(target);')
replace('"certified preflight/mode failed");','"certified preflight/mode failed");PackedWalletProof.negatives();')
replace('SplitAssertions.enforcement(target);target.setInvulnerable(false);','SplitAssertions.enforcement(target);PackedWalletProof.occupiedDestination(target);target.setInvulnerable(false);')
replace('Files.writeString(Path.of("/tmp/uq-certified-result"),"passed:', 'PackedWalletProof.report(phase,target,last);Files.writeString(Path.of("/tmp/uq-certified-result"),"passed:')
replace('if(!Boolean.getBoolean("uq.certifiedJail"))return;', 'if(!Boolean.getBoolean("uq.certifiedJail")||Boolean.getBoolean("uq.packedWalletFault"))return;')
p.write_text(s)
for name in ('PackedWalletProof','PackedWalletFault'):
    shutil.copy2(ROOT/'.github/packed-wallet'/f'{name}.java',source/f'{name}.java')
