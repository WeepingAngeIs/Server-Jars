"""Prepare independent native reference fixtures, never production changes."""
import os
from pathlib import Path
import shutil
import subprocess
root=Path(__file__).resolve().parents[2]
assert os.environ.get('GITHUB_ACTIONS')=='true' and os.environ.get('RUNNER_ENVIRONMENT')=='github-hosted'
subprocess.run(['python3','.github/party-wallet/prepare.py'],cwd=root,check=True)
source=root/'dist/packaged-harness/src/main/java/dev/unyielding/bundlesmoke'
p=source/'PartyWalletProbe.java';s=p.read_text()
needle='        if(!Boolean.getBoolean("uq.partyWalletProbe")||finished)return;'
assert s.count(needle)==1
s=s.replace(needle,needle+'\n        if(Boolean.getBoolean("uq.nativeResourceProbe"))return;',1)
p.write_text(s.replace('private static','static'))
shutil.copy2(root/'.github/resource-native/NativeResourceProbe.java',source)
