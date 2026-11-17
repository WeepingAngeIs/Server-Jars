"""Independent test mod only. Never rewrite production classes, resources or configuration."""
import hashlib, os, shutil, subprocess
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
assert os.environ.get('GITHUB_ACTIONS')=='true' and os.environ.get('RUNNER_ENVIRONMENT')=='github-hosted'
subprocess.run(['python3','.github/scripts/prepare-split-server.py'],cwd=ROOT,check=True)
source=ROOT/'dist/packaged-harness/src/main/java/dev/unyielding/bundlesmoke'
p=source/'IntegratedJailSmoke.java';s=p.read_text();needle='    @SubscribeEvent public static void tick(ServerTickEvent.Post event){'
assert s.count(needle)==1
p.write_text(s.replace(needle,needle+'\n        if(Boolean.getBoolean("uq.partyStorageBoundary"))return;',1))
shutil.copy2(ROOT/'.github/party-storage/PartyStorageBoundaryProbe.java',source/'PartyStorageBoundaryProbe.java')
for key,digest in [('UQ_BUNDLE','61f6add6c68364b1c9dda4486948faf8d104c45ca2db2c40199de64a04ea38f7'),('UQ_JAIL','5c2653a9c14d3199581fdd67e05d055b334ec2746fe7047e3c80454eb732474e')]:
    name='Unyielding-1.2.0.jar' if key=='UQ_BUNDLE' else 'Minecraft-Jail-1.2.0.jar'
    assert hashlib.sha256((ROOT/'dist/packaged-harness/libs'/name).read_bytes()).hexdigest()==digest
