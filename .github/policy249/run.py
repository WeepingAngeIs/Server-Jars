"""Run a policy case on the exact public companion/Storage inputs, with one native restart."""
import hashlib
import os
from pathlib import Path

assert os.environ.get("GITHUB_ACTIONS") == "true" and os.environ.get("RUNNER_ENVIRONMENT") == "github-hosted"
root = Path(__file__).resolve().parents[2]
storage = Path(os.environ["COMBINED_STORAGE_JAR"]).resolve()
swap = Path(os.environ["UQ_SWAP_JAR"]).resolve()
case = os.environ["UQ_POLICY_CASE"]
assert case in ("on_to_off", "off_to_on")
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
for game in (server, client):
    target=game/"mods/Unyielding-1.2.0.jar"
    observed=digest(target)
    if phases[0]==1:
        assert observed=="ec3ae2c48399e40ffa615ddec85408bc29b2479c5a35eb7236c3e036ebdf762c", "Published pack input drift"
        shutil.copy2(swap_input,target)
    assert digest(target)==expected_companion, "Wrong policy companion"
    dest=game/"mods"/storage.name
    if dest.exists(): assert digest(dest)==expected_storage
    else: shutil.copy2(storage,dest)
    assert digest(dest)==expected_storage
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
    (report/f"policy-{case}-{side}-{stage}.json").write_text(json.dumps({"side":side,"stage":stage,"files":rows},indent=2))
for game,side in ((server,"server"),(client,"client")):
    manifest(game,side,"before-phase"+str(phases[0]))
'''
replace('for phase in phases:', manifest + '\nfor phase in phases:')
stop = '    proof=json.loads((report/f"center-phase{phase}.json").read_text())'
assert source.count(stop) == 1
source = source.split(stop, 1)[0] + '''
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
        for check in ["confiscation_defaults_on","nonadministrator_policy_edit_refused","stale_server_ui_edit_refused",
                      "server_admin_ui_policy_saved_and_read_back","polymorph_native_controls",
                      "active_storage_lease_refused_without_change","active_combat_refused_without_change",
                      "refused_active_combat","refused_active_storage_lease",
                      "refused_polymorph_active_menu","refused_polymorph_active_recipe","refused_polymorph_pending_recipe",
                      "refused_unrelated_saved_state","refused_preexisting_combat_not_owned_by_jail","refused_unrelated_insanelib_state",
                      "refused_active_outgoing_tpa","refused_active_outgoing_tpahere","refused_active_incoming_tpa",
                      "refused_malformed_teleport_request_state","unrelated_teleport_request_preserved",
                      "expired_request_uses_native_sixty_second_semantics","pending_teleport_admission_controls_preserved_inventory",
                      "held_incoming_native_request_preserved_and_accept_command_blocked",
                      "held_incoming_native_request_live_at_actual_escape","held_incoming_request_kept_through_escape_and_respawn",
                      "escape_native_respawn_in_cell_six_without_client_action"]:
            assert proof[check],check
        assert (report/"full249-onboarding-complete.txt").is_file()
        timing=json.loads((report/"full249-respawn-timing.json").read_text())
        assert 0 < timing["server_ticks"] <= 2400 and timing["elapsed_ms"] > 0,timing
        client_view=json.loads((report/"player-only-client-Visitor-1.json").read_text())
        assert client_view["respawn_actions_sent"]==0 and client_view["kill_packets"]==client_view["respawn_packets_after_kill"]==1
        respawn, = client_view["verified_respawns"]
        assert respawn["alive"] and respawn["no_modal_ticks"]>=20 and respawn["dead_player_object"]!=respawn["alive_player_object"]
        (report/"full249-first-session-saved.json").write_text(json.dumps({"commit":os.environ["GITHUB_SHA"],"origin_config":original_origin_config}))
    else:
        for check in ["held_incoming_native_request_preserved_and_accept_command_blocked",
                      "held_incoming_native_request_live_at_actual_release","held_incoming_request_did_not_block_actual_release",
                      "released_native_deny_closed_synthetic_incoming_request"]:
            assert proof[check],check
    if case=="on_to_off":
        for check in (["on_admission_snapshots_policy_before_off_flip","off_flip_keeps_existing_escrow_and_inventory_unchanged",
                       "on_escape_kept_escrow_after_policy_off","storage_bag_held_once_with_original_contents_and_upgrade",
                       "held_recovery_safely_stopped","verified_corpse_light_refresh_with_bound_own_damage",
                       "refused_equipped_wallet","refused_multiple_wallets","refused_auto_exchange_wallet",
                       "refused_twilight_pending_charm_inventory"] if phase==1 else
                      ["on_to_off_active_policy_survived_restart","existing_on_custody_returns_once_while_global_off",
                       "held_recovery_retry_after_restart_safely_stopped","storage_bag_returned_once_to_original_slot",
                       "stale_recovery_after_release_did_not_duplicate_bag","occupied_return_kept_conflict_and_escrow",
                       "full_wallet_and_equipment_return_after_restart","native_persisted_progress_survived_clone_and_restart"]):
            assert proof[check],check
    else:
        writes=proof["inventory_write_observations"]
        assert not writes["direct_jail_inventory_writes"] and not writes["confiscation_or_return_calls"],writes
        if phase==1: assert writes["native_respawn_inventory_copies"].get("replaceWith")==1,writes
        for check in (["off_resolves_only_reviewed_inventory_compatibility_refusal","refused_on_only_copper_inventory_provider",
                       "refused_off_unsafe_confinement","refused_off_unreviewed_active_item",
                       "off_admission_has_no_confiscation_inventory_write_or_return_staging",
                       "off_held_recovery_did_not_create_copy","on_flip_did_not_retroactively_confiscate_active_off_session",
                       "off_commands_teleport_and_external_damage_still_blocked",
                       "off_actual_escape_death_retained_original_inventory","off_no_button_respawn_preserved_inventory_with_lighting_churn"]
                      if phase==1 else ["off_to_on_active_policy_survived_restart_without_confiscation",
                       "off_held_recovery_after_restart_did_not_create_copy","off_stale_recovery_after_release_did_not_duplicate",
                       "off_duplicate_recovery_after_release_did_not_duplicate",
                       "off_release_kept_same_inventory_wallet_and_marked_upgraded_bag_in_original_slots",
                       "off_release_has_no_inventory_write_or_return_staging",
                       "on_after_completed_off_returns_same_slots_once",
                       "on_return_receipts_survive_later_off_admission_and_release_unchanged",
                       "on_after_on_off_receipt_chain_returns_same_slots_once"]):
            assert proof[check],check
        if phase==2:
            sequential=proof["sequential_off_inventory_write_observations"]
            assert not sequential["direct_jail_inventory_writes"] and not sequential["confiscation_or_return_calls"],sequential
            assert len(set(proof["sequential_policy_transactions"].values()))==3
    proof["policy_case"]=case
    proof["raw_storage_sha256"]=expected_storage
    proof["scope"]="Published fresh-world pack with exact replacement companion; private Windows/server saves unverified"
    (report/f"policy-{case}-phase{phase}.json").write_text(json.dumps(proof,indent=2))
    print(f"Policy {case}: exact candidate passed real-player phase {phase}.",flush=True)
'''
exec(compile(source, str(root / ".github/native249/full_run.py"), "exec"),
     {"__file__": str(root / ".github/native249/full_run.py"), "storage": storage,
      "swap_input": swap, "case": case, "expected_companion": expected_companion,
      "expected_storage": expected_storage})
