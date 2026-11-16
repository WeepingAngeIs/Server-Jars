"""Independent synthetic harness around the actual new Jail JAR and unchanged companion."""
import os
from pathlib import Path
import subprocess

ROOT=Path(__file__).resolve().parents[2]
if os.environ.get('GITHUB_ACTIONS')!='true' or os.environ.get('RUNNER_ENVIRONMENT')!='github-hosted':
    raise SystemExit('Hosted disposable environment required')
subprocess.run(['python3','.github/scripts/prepare-split-server.py'],cwd=ROOT,check=True)
source=ROOT/'dist/packaged-harness/src/main/java/dev/unyielding/bundlesmoke'
for name in ('IntegratedJailSmoke','CertifiedJailSmoke'):
    p=source/(name+'.java');s=p.read_text();needle='    @SubscribeEvent public static void tick(ServerTickEvent.Post event){'
    assert s.count(needle)==1
    p.write_text(s.replace(needle,needle+'\n        if(Boolean.getBoolean("uq.packedWallet"))return;',1))
p=source/'OriginCustodyPort.java';s=p.read_text();needle='||!(Boolean.getBoolean("uq.originCustody")'
assert s.count(needle)==1
p.write_text(s.replace(needle,'||!(Boolean.getBoolean("uq.packedWallet")&&player.server.getWorldPath(LevelResource.ROOT).toRealPath().getFileName().toString().equals("jail_integrated_smoke")||Boolean.getBoolean("uq.originCustody")',1))
(source/'PackedWalletCustodySmoke.java').write_text((ROOT/'.github/packed-wallet/PackedWalletCustodySmoke.java').read_text())
# Capture the owned JVM's actual exit status without altering production JARs.
s=(ROOT/'.github/scripts/meeting-two-player.sh').read_text()
old='setsid bash "$server_script" < "$server_input" > "$logs/server.log" 2>&1 & server_pid=$!'
new='setsid bash -c \'set +e; bash "$1"; rc=$?; printf "%s\\n" "$rc" > "$2"; exit "$rc"\' _ "$server_script" "$logs/server-exit-code" < "$server_input" > "$logs/server.log" 2>&1 & server_pid=$!'
assert s.count(old)==1
(ROOT/'.github/scripts/packed-wallet-two-player.generated.sh').write_text(s.replace(old,new,1))
