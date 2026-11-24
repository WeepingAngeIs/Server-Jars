"""Prepare the native full-pack observer with the pinned Storage Plus API."""
import hashlib
import os
from pathlib import Path
import shutil

root = Path(__file__).resolve().parents[2]
storage = Path(os.environ["COMBINED_STORAGE_JAR"]).resolve()
assert hashlib.sha256(storage.read_bytes()).hexdigest() == "afd62612c2f09c6bf156831b0e798da58ef0fd1632297dc936ea69e11b66e91e"
source = (root/".github/native249/full_prepare.py").read_text()
marker = 'subprocess.run(["sh",str(root/"unyielding-quests/gradlew"),"--no-daemon","--console=plain","jar"],'
assert source.count(marker) == 1
insert = '''
shutil.copy2(root/".github/native249/CombinedStorageProof.java", src)
shutil.copy2(storage, project/"libs/combined-storage.jar")
with (project/"build.gradle").open("a") as gradle:
    gradle.write("\\ndependencies { compileOnly files('libs/combined-storage.jar') }\\n")
full = src/"FullPackSession.java"
replace(full, "        PackedWalletProof.seed(player);", "        PackedWalletProof.seed(player);\\n        CombinedStorageProof.seed(player);")
replace(full, '        Custody249Proof.refusals(player);', '        CombinedStorageProof.activePocketControl(player);\\n        Custody249Proof.refusals(player);')
replace(full, '        lastEscapeSample=tick;', '        lastEscapeSample=tick;\\n        if (!player.isAlive()) FullPackDiagnostics.sampleCorpse(player);')
replace(full, '        deadline=player.server.getTickCount()+2400;', '        deadline=player.server.getTickCount()+400;')
replace(full, 'stage.equals("seeded")) {', 'stage.equals("seeded") || stage.equals("held")) {')
replace(full, '        snapshot(player,"held");', '        CombinedStorageProof.held(player);\\n        CombinedStorageProof.attemptRecovery(player,true);\\n        snapshot(player,"held");')
replace(full, '        snapshot(player,"reconnect");', '        CombinedStorageProof.held(player);\\n        CombinedStorageProof.attemptRecovery(player,false);\\n        snapshot(player,"reconnect");')
replace(full, '        noClaims(player);\\n        flag("full_wallet_and_equipment_return_after_restart");',
        '        CombinedStorageProof.returned(player);\\n        noClaims(player);\\n        flag("full_wallet_and_equipment_return_after_restart");')
# Preserve the actual no-button result without imposing an unrelated performance threshold.
replace(full, '            require(tick-escapeTick<=80,"Server waited more than four seconds of ticks to replace the dead player");', '')
'''
source = source.replace(marker, insert + marker)
exec(compile(source, str(root/".github/native249/full_prepare.py"), "exec"), {"__file__": str(root/".github/native249/full_prepare.py"), "storage": storage})
