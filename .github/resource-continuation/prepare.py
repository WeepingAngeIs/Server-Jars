"""Add only hosted synthetic fixtures around the unchanged approved JAR pair."""
import os
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[2]
if os.environ.get('GITHUB_ACTIONS') != 'true' or os.environ.get('RUNNER_ENVIRONMENT') != 'github-hosted':
    raise SystemExit('Hosted disposable runner required')
subprocess.run(['python3', '.github/party-wallet/prepare.py'], cwd=ROOT, check=True)
source = ROOT/'dist/packaged-harness/src/main/java/dev/unyielding/bundlesmoke'
p = source/'PartyWalletProbe.java'
s = p.read_text()
needle = '        if(!Boolean.getBoolean("uq.partyWalletProbe")||finished)return;'
assert s.count(needle) == 1
s = s.replace(needle, needle+'\n        if(Boolean.getBoolean("uq.resourceContinuation"))return;', 1)
p.write_text(s.replace('private static', 'static'))
(source/'ContinuationProbe.java').write_text((ROOT/'.github/resource-continuation/ContinuationProbe.java').read_text())
