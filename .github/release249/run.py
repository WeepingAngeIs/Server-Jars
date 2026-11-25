"""Focused fresh-default release proof; one native restart on the reviewed public fixture."""
import hashlib
import os
from pathlib import Path

assert os.environ.get("GITHUB_ACTIONS") == "true" and os.environ.get("RUNNER_ENVIRONMENT") == "github-hosted"
root = Path(__file__).resolve().parents[2]
storage = Path(os.environ["COMBINED_STORAGE_JAR"]).resolve()
swap = Path(os.environ["UQ_SWAP_JAR"]).resolve()
case = "fresh_default_off"
expected_companion = "9dbfb659a2ba2c995d0f3974fa48cf9f37b9b5f8162c3e2e4faf2af9946e0904"
expected_storage = "afd62612c2f09c6bf156831b0e798da58ef0fd1632297dc936ea69e11b66e91e"
assert hashlib.sha256(swap.read_bytes()).hexdigest() == expected_companion
assert hashlib.sha256(storage.read_bytes()).hexdigest() == expected_storage
source = (root / ".github/native249/full_run.py").read_text()

def replace(before, after):
    global source
    assert source.count(before) == 1, (before, source.count(before))
    source = source.replace(before, after)

replace('companion = server/"mods/Unyielding-1.2.0.jar"', '''
published_companion="ec3ae2c48399e40ffa615ddec85408bc29b2479c5a35eb7236c3e036ebdf762c"
profile=json.loads((root/".github/pack249/profile.json").read_text())
published_row=next(row for row in profile["mods"] if row["name"]=="Unyielding-1.2.0.jar")
cached_companion=scratch/"jail-pack249-downloads"/published_row["sha512"]
replacement_report={"phase":phases[0],"requested_sha256":expected_companion,
                    "published_sha256":published_companion,"cache_sha256_before":digest(cached_companion),"targets":[]}
replacement_path=report/f"release-companion-substitution-phase{phases[0]}.json"
for game,side in ((server,"server"),(client,"client")):
    target=game/"mods/Unyielding-1.2.0.jar"
    stat=target.stat()
    replacement_report["targets"].append({"side":side,"sha256_before":digest(target),
                                          "device_before":stat.st_dev,"inode_before":stat.st_ino,"links_before":stat.st_nlink})
replacement_path.write_text(json.dumps(replacement_report,indent=2))
required_before=published_companion if phases[0]==1 else expected_companion
assert all(row["sha256_before"]==required_before for row in replacement_report["targets"]), replacement_report
assert replacement_report["cache_sha256_before"]==published_companion, "Published download cache changed"
# Provisioning hard-links both installations to its cache. Validate both original
# inputs before any mutation, then detach each directory entry before copying.
for game,row in zip((server,client),replacement_report["targets"]):
    target=game/"mods/Unyielding-1.2.0.jar"
    if phases[0]==1:
        target.unlink()
        shutil.copy2(swap_input,target)
    assert digest(target)==expected_companion, "Wrong policy companion"
    row["sha256_after"]=digest(target)
    row["inode_after"]=target.stat().st_ino
    dest=game/"mods"/storage.name
    if dest.exists(): assert digest(dest)==expected_storage
    else: shutil.copy2(storage,dest)
    assert digest(dest)==expected_storage
replacement_report["cache_sha256_after"]=digest(cached_companion)
replacement_path.write_text(json.dumps(replacement_report,indent=2))
assert replacement_report["cache_sha256_after"]==published_companion, "Replacement modified the published download cache"
companion = server/"mods/Unyielding-1.2.0.jar"
''')
replace('assert digest(companion) == "ec3ae2c48399e40ffa615ddec85408bc29b2479c5a35eb7236c3e036ebdf762c"',
        'assert digest(companion) == expected_companion')
replace('== 367, "Expected all 365 pack files plus candidate and test JAR"',
        '== 368, "Expected published client files plus candidate, test observer and exact Storage"')
manifest = '''
def manifest(game, side, stage):
    rows=[]
    for sub in ("mods","config","defaultconfigs","kubejs","datapacks","resourcepacks"):
        path=game/sub
        if not path.exists(): continue
        for file in sorted(x for x in path.rglob("*") if x.is_file()):
            row={"path":file.relative_to(game).as_posix(),"size":file.stat().st_size,"sha256":digest(file)}
            if sub=="mods" and file.suffix==".jar": row["sha512"]=digest(file,"sha512")
            rows.append(row)
    (report/f"release-{side}-{stage}.json").write_text(json.dumps({"side":side,"stage":stage,"files":rows},indent=2))
for game,side in ((server,"server"),(client,"client")):
    manifest(game,side,"before-phase"+str(phases[0]))
'''
replace('for phase in phases:', manifest + '\nfor phase in phases:')
replace('            stop_group(srv)\n            for game,name in ((client,"client"),(server,"server")):',
        '            stop_group(srv)\n            for crash in client.glob("hs_err_pid*.log"):\n                shutil.copy2(crash,logs/crash.name)\n            for game,name in ((client,"client"),(server,"server")):')
replace('    assert proof["escape_native_respawn_in_cell_six_without_client_action"]',
        '    assert proof["on_held_transaction_saved_for_restart"]')
fresh = """
if phases[0]==1:
    config=server/"config/minecraftjail-common.toml"
    default_files=list((server/"defaultconfigs").rglob("*jail*")) if (server/"defaultconfigs").exists() else []
    assert not config.exists(), "Fresh default proof cannot use an existing Jail config"
    assert not any(path.is_file() for path in default_files), "Fixture contains a Jail default-config override"
    assert not (world/"serverconfig/minecraftjail-common.toml").exists()
    (report/"release-fresh-config-before-start.json").write_text(json.dumps({
        "config_path":"config/minecraftjail-common.toml", "config_absent":True,
        "jail_defaultconfig_overrides":[], "world_absent":not world.exists(),
        "confiscation_setting_injected":False, "candidate_sha256":digest(candidate)
    },indent=2))
"""
replace('origin_config=server/"config/neoorigins/admin.toml"', fresh+'\norigin_config=server/"config/neoorigins/admin.toml"')
stop = '    proof=json.loads((report/f"center-phase{phase}.json").read_text())'
assert source.count(stop) == 1
source = source.split(stop, 1)[0] + """
    for game,side in ((server,"server"),(client,"client")):
        manifest(game,side,"after-phase"+str(phase))
    proof=json.loads((report/f"center-phase{phase}.json").read_text())
    assert proof["commit"]==os.environ["GITHUB_SHA"] and proof["jail_sha256"]==digest(candidate)
    assert proof["actual_neoforge_21_1_249"] and proof["companion_sha256"]==expected_companion
    assert digest(origin_config)==original_origin_config, "Origin admin config drifted"
    runtime=json.loads((report/f"runtime249-phase{phase}.json").read_text())
    assert runtime["neoforge"]["version"]=="21.1.249"
    assert runtime["minecraft"]["version"]=="1.21.1"
    assert runtime["minecraftjail"]["sha512"]==digest(candidate,"sha512")
    assert runtime["anyastorage"]["sha512"]==digest(storage,"sha512")
    assert any(row["sha512"]==digest(companion,"sha512") for row in runtime.values())
    if phase==1:
        for check in ["fresh_config_loaded_default_off", "master_admission_default_remains_disabled",
                      "default_off_normal_entry_admission", "default_off_inventory_untouched_during_confinement",
                      "default_off_commands_teleport_and_damage_blocked", "off_admission_policy_preserved_after_opt_in",
                      "off_release_without_inventory_writes_or_return_staging", "explicit_on_saved_by_admin_ui",
                      "explicit_on_actual_confiscation", "held_recovery_safely_stopped",
                      "on_held_transaction_saved_for_restart"]:
            assert proof[check],check
        assert proof["default_off_confined_server_ticks"]>=40
        observation=proof["default_off_inventory_write_observations"]
        assert not observation["direct_jail_inventory_writes"] and not observation["confiscation_or_return_calls"],observation
        assert proof["fresh_config_value"] is False and proof["saved_config_value_before_restart"] is True
        assert (report/"full249-onboarding-complete.txt").is_file()
        (report/"full249-first-session-saved.json").write_text(json.dumps({"commit":os.environ["GITHUB_SHA"],"origin_config":original_origin_config}))
    else:
        first=json.loads((report/"release-phase1.json").read_text())
        assert proof["on_held_transaction"]==first["on_held_transaction"]
        for check in ["explicit_saved_on_preserved_across_restart", "recorded_on_and_held_items_survived_restart",
                      "held_recovery_retry_after_restart_safely_stopped", "recorded_on_preserved_after_global_off",
                      "already_held_transaction_returned_once_while_global_off", "exact_original_inventory_slots_after_restart_release",
                      "storage_bag_returned_once_to_original_slot", "stale_recovery_after_release_did_not_duplicate_bag",
                      "duplicate_release_did_not_repeat_inventory_return"]:
            assert proof[check],check
        assert proof["saved_config_value_after_restart"] is True
        observation=proof["on_release_inventory_write_observations"]
        assert observation["confiscation_or_return_calls"].get("restoreExact")==1,observation
        assert proof["inventory_return_count_after_duplicate_release"]==1
        expected=(report/"release-inventory-expected.snbt").read_bytes()
        assert expected==(report/"release-inventory-after-off.snbt").read_bytes()
        assert expected==(report/"release-inventory-after-on-restart.snbt").read_bytes()
    proof["candidate_source_commit"]=os.environ["POLICY_CANDIDATE_COMMIT"]
    proof["candidate_build_run"]=os.environ["POLICY_CANDIDATE_RUN"]
    proof["workflow_commit"]=os.environ["GITHUB_SHA"]
    proof["raw_storage_sha256"]=expected_storage
    proof["scope"]="Focused fresh-default release check on exact published synthetic fresh-world fixture; prior escape/respawn and compatibility evidence reused"
    (report/f"release-phase{phase}.json").write_text(json.dumps(proof,indent=2))
    print(f"Focused default-Off release: exact candidate passed phase {phase}.",flush=True)
"""
exec(compile(source, str(root / ".github/native249/full_run.py"), "exec"),
     {"__file__": str(root / ".github/native249/full_run.py"), "storage": storage,
      "swap_input": swap, "case": case, "expected_companion": expected_companion,
      "expected_storage": expected_storage})
