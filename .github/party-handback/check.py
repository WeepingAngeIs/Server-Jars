"""Exact evidence gate for limited native publication, not gameplay custody."""
import hashlib,json,os,sys
from pathlib import Path
R=Path(__file__).resolve().parents[2];D=R/'dist/packaged-smoke';backend=sys.argv[1]
assert backend in ('nbt','json')
required={
1:'''wallet_default_off durable_retirement_precedes_access_grant fresh_native_view_full_state_primary direct_disk_load_still_denied_primary fresh_gameplay_mutation_denied_primary fresh_cache_objects_not_old_aliases old_party_still_denied old_pokemon_still_denied untracked_same_uuid_clone_denied alternate_manager_cannot_expose_successor second_publication_refused old_queued_payload_rejected_after_retirement same_queued_batch_unrelated_owner_saved revoked_cache_predicate_returns_false quarantine_request_no_throw_no_fallback real_mixed_owner_autosave_passed quarantined_dirty_retained_not_acknowledged disconnected_epoch_not_accessible disconnected_fresh_reference_revoked iterator_lease_revoked_on_disconnect disconnect_republication_uses_new_epoch queued_old_epoch_action_denied mixed_native_shutdown_drains_without_lock_deadlock unrelated_owner_survives_shared_shutdown swapped_cache_without_receipt_not_accessible hard_halt_after_swap_before_retirement'''.split(),
2:'''wallet_default_off fresh_native_view_full_state_primary direct_disk_load_still_denied_primary fresh_gameplay_mutation_denied_primary restart_revalidates_before_fresh_publication interrupted_prepared_retirement_stays_quarantined prepared_restart_has_no_native_fallback changed_native_destination_blocks_publication publication_conflict_preserves_bytes_and_generations committed_epoch_not_exposed_early hard_halt_after_retirement_before_access'''.split(),
3:'''wallet_default_off fresh_native_view_full_state_primary direct_disk_load_still_denied_primary fresh_gameplay_mutation_denied_primary fresh_native_view_full_state_retire-halt direct_disk_load_still_denied_retire-halt fresh_gameplay_mutation_denied_retire-halt committed_but_unexposed_restart_revalidates_new_epoch retirement_record_cannot_publish_twice fresh_sendout_remains_unsupported unchanged_read_only_save_verified_without_native_queue conflicting_owner_still_quarantined prepared_record_not_replayed_on_later_restart final_shared_shutdown_preserves_unrelated_owner no_player_admission_or_gameplay_release_enabled'''.split()}
pins={'jail_sha256':'5c2653a9c14d3199581fdd67e05d055b334ec2746fe7047e3c80454eb732474e','companion_sha256':'61f6add6c68364b1c9dda4486948faf8d104c45ca2db2c40199de64a04ea38f7'}
rows=[]
for phase,names in required.items():
 row=json.loads((D/f'handback-{backend}-phase{phase}.json').read_text());assert row['commit']==os.environ['GITHUB_SHA'] and row['phase']==phase and row['backend']==backend
 assert all(row.get(n) is True for n in names),[n for n in names if row.get(n) is not True]
 assert all(row.get(k) is False for k in ('admission_enabled','playable_handback_verified','production_changed'))
 assert row['read_only_native_publication'] is True and all(row[k]==v for k,v in pins.items())
 identity=json.loads((D/f'split-identity-jail_integrated_smoke-{phase}-normal.json').read_text());assert identity['commit']==row['commit'] and identity['phase']==str(phase)
 assert all(identity[k]==v for k,v in pins.items())
 assert all(identity[k] is True for k in ('separate_jars_loaded','old_jail_classes_absent','non_jail_mods_loaded','escape_reward_empty'))
 logdir=D/f'handback-{backend}-phase{phase}-logs'
 if phase<3:
  crash=json.loads((logdir/'planned-halt.json').read_text());assert crash['commit']==row['commit'] and crash['phase']==phase and crash['backend']==backend and crash['actual_exit']==93+phase and crash['planned_halt_verified'] is True and crash['graceful_save_used'] is False
 else:assert (logdir/'shutdown-ready.txt').is_file()
 rows.append(row)
source=json.loads((D/'handback-source-hashes.json').read_text())
for p,digest in source.items():assert hashlib.sha256((R/p).read_bytes()).hexdigest()==digest,p
wrappers=json.loads((D/'handback-wrapper-manifest.json').read_text())
for row in wrappers['classes']:assert hashlib.sha256((D/'live-wrapper-source'/f"{row['mixin']}.java").read_bytes()).hexdigest()==row['sha256']
for name,digest in wrappers['extra_hooks'].items():assert hashlib.sha256((D/'live-wrapper-source'/f'{name}.java').read_bytes()).hexdigest()==digest
summary={'commit':os.environ['GITHUB_SHA'],'backend':backend,'phases':3,'required_phase_observations':sum(map(len,required.values())),'rows':rows,'source_hashes':source,'baseline_fence_commit':'87c2b6383ea528b1d604f415c0ffab49131884ac','read_only_native_publication':True,'playable_handback_verified':False,'admission_enabled':False,'production_changed':False}
(D/f'handback-{backend}-summary.json').write_text(json.dumps(summary,indent=2)+'\n')
print('Verified',sum(map(len,required.values())),'required phase observations; gameplay custody remains unverified')
