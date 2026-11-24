"""Compile passive/stimulus-only acceptance code around the exact packaged Jail JAR.

This preparation may run only on the already authorized disposable hosted runner.
The production candidate and companion are never compiled or modified here.
"""
import os
from pathlib import Path

assert os.environ.get("GITHUB_ACTIONS") == "true" and os.environ.get("RUNNER_ENVIRONMENT") == "github-hosted"
root = Path(__file__).resolve().parents[2]
assert os.environ["UQ_POLICY_CASE"] in ("on_to_off", "off_to_on")
assert os.environ.get("POLYMORPH_CONTROLS") == "1", "Policy proof requires the existing native Polymorph controls"
source = (root / ".github/native249/combined_prepare.py").read_text()
marker = "source = source.replace(marker, insert + marker)"
assert source.count(marker) == 1
policy_insert = r'''
for file in (root/".github/policy249/java").rglob("*.java"):
    dest=src/file.relative_to(root/".github/policy249/java")
    dest.parent.mkdir(parents=True,exist_ok=True)
    shutil.copy2(file,dest)
mixins=json.loads(observer_config.read_text())
mixins["mixins"].extend(["PolicyInventoryObserver","PolicyCustodyPortObserver"])
observer_config.write_text(json.dumps(mixins,indent=2))
replace(full, '    static void begin(ServerPlayer player) throws Exception {',
        '    static void begin(ServerPlayer player) throws Exception {\n        if (Policy249Proof.retained()) { Policy249Proof.beginOff(player); return; }')
replace(full, '        require(!player.hasPermissions(2), "Still an administrator after fixture setup");',
        '        require(!player.hasPermissions(2), "Still an administrator after fixture setup");\n        Policy249Proof.defaults(player);')
replace(full, '        FullPackDiagnostics.tracingOrigin=true;\n        try {\n            require(cmd("jail Visitor 6")==1',
        '        Policy249Proof.beforeOnAdmission(player);\n        FullPackDiagnostics.tracingOrigin=true;\n        try {\n            require(cmd("jail Visitor 6")==1')
replace(full, '        remember(player);',
        '        remember(player);\n        Policy249Proof.admittedOn(player);')
replace(full, '    static void resumed(ServerPlayer player) throws Exception {',
        '    static void resumed(ServerPlayer player) throws Exception {\n        if (Policy249Proof.retained()) { Policy249Proof.resumedOff(player); return; }\n        Policy249Proof.resumedOn(player);')
replace(full, '        flag("full_wallet_and_equipment_return_after_restart");',
        '        flag("full_wallet_and_equipment_return_after_restart");\n        Policy249Proof.completedOn(player);')
replace(full, '        deadline=player.server.getTickCount()+400;',
        '        deadline=player.server.getTickCount()+2400;')
replace(full, '        CombinedStorageProof.seedLightTransition(player);',
        '        PolicyTeleportProof.holdIncoming(player);\n        CombinedStorageProof.seedLightTransition(player);\n        PolicyTeleportProof.beforeEscape(player);')
replace(full, '            release(player);',
        '            PolicyTeleportProof.beforeRelease(player);\n            release(player);\n            PolicyTeleportProof.afterRelease(player);')
replace(tick, '                FullPackSession.traceEscape(visitor);',
        '                if (Policy249Proof.retained()) Policy249Proof.traceOff(visitor); else FullPackSession.traceEscape(visitor);')
replace(tick, '                afterRespawn(dev,visitor);',
        '                if (Policy249Proof.retained()) Policy249Proof.afterRespawnOff(visitor); else afterRespawn(dev,visitor);')
replace(tick, '        flag("escape_native_respawn_in_cell_six_without_client_action");',
        '        flag("escape_native_respawn_in_cell_six_without_client_action");\n        Policy249Proof.afterOnRespawn(visitor);')
# The exact same source-level Polymorph provider controls must also attempt admission.
poly=src/"PolymorphControls.java"
if poly.exists():
    for text,label in [
        ('Actual admission check accepted an active Polymorph menu','polymorph_active_menu'),
        ('Actual admission check accepted a selected Polymorph recipe','polymorph_active_recipe'),
        ('Actual admission check accepted a pending Polymorph recipe','polymorph_pending_recipe')]:
        old='            require(cmd("jailintegrated check Visitor")==0,"'+text+'");'
        replace(poly,old,old+'\n            Custody249Proof.refused(player,"'+label+'");')
combined=src/"CombinedStorageProof.java"
for text,label in [
    ('Active combat passed admission preflight','active_combat'),
    ('Active Storage pocket lease passed preflight','active_storage_lease')]:
    old='            require(cmd("jailintegrated check Visitor") == 0, "'+text+'");'
    replace(combined,old,old+'\n            Custody249Proof.refused(player,"'+label+'");')
'''
source = source.replace(marker, "source = source.replace(marker, insert + policy_insert + marker)")
exec(compile(source, str(root / ".github/native249/combined_prepare.py"), "exec"),
     {"__file__": str(root / ".github/native249/combined_prepare.py"), "policy_insert": policy_insert})
