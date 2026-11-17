"""Exact evidence gate for limited native publication, not gameplay custody."""
import hashlib,json,os,sys
from pathlib import Path
R=Path(__file__).resolve().parents[2];D=R/'dist/packaged-smoke';backend=sys.argv[1]
assert backend in ('nbt','json')
required={1: ['wallet_default_off', 'native_nickname_change_exact_full_state', 'edit_receipt_precedes_new_access', 'old_graph_revoked_after_edit', 'old_iterator_revoked_after_edit', 'original_pokemon_still_revoked', 'untracked_clone_still_revoked', 'stale_session_request_refused', 'stale_generation_request_refused', 'foreign_object_request_refused', 'command_replay_refused', 'invalid_literal_refused', 'no_op_refused', 'direct_nickname_setter_refused', 'unapproved_level_change_refused', 'nickname_read_returns_detached_component', 'alternate_manager_refused', 'direct_current_loader_refused', 'original_custody_snapshot_unchanged', 'old_session_close_cannot_close_current_journal', 'queued_old_action_refused_after_second_edit', 'second_edit_chains_full_state_and_new_epoch', 'pre_enrollment_queued_write_cannot_revert_edit', 'same_native_batch_unrelated_owner_preserved', 'worker_thread_cannot_mutate_current_session', 'real_autosave_preserves_mutated_current_and_unrelated', 'quarantine_dirty_remains_unacknowledged', 'disconnect_stale_request_refused', 'native_disconnect_republication_preserves_edits', 'native_save_ack_requires_exact_durable_mutated_state', 'post_write_exception_does_not_acknowledge_success', 'post_write_incomplete_record_stays_quarantined', 'post_write_denial_has_no_fallback', 'temporary_destination_conflict_preserved', 'temp_conflict_quarantines_without_empty_fallback', 'intent_committed_access_closed', 'hard_halt_after_intent_before_native_write'], 2: ['wallet_default_off', 'restart_loads_latest_edit_not_original_snapshot', 'replay_refused_after_restart', 'restart_quarantines_pending-halt', 'no_fallback_pending-halt', 'restart_quarantines_after-write', 'no_fallback_after-write', 'restart_quarantines_temp-conflict', 'no_fallback_temp-conflict', 'edit_commit_not_exposed_before_validation', 'hard_halt_after_edit_commit_before_publication'], 3: ['wallet_default_off', 'complete_committed_edit_revalidated_as_fresh_graph', 'committed_command_not_replayed', 'sendout_still_unsupported', 'changed_current_file_blocks_edit_without_advancing_receipt', 'changed_current_withdraws_access', 'post_restart_native_mutation_commits_new_generation', 'mixed_native_shutdown_preserves_mutated_and_unrelated'], 4: ['wallet_default_off', 'graceful_restart_preserves_final_full_state', 'persistent_replay_refusal_edit-one', 'persistent_replay_refusal_edit-two', 'persistent_replay_refusal_edit-three', 'later_restart_quarantine_pending-halt', 'later_restart_quarantine_after-write', 'later_restart_quarantine_temp-conflict', 'unrelated_owner_survives_final_restart', 'returned_direct_native_mutations_remain_blocked', 'no_admission_or_playable_release_enabled']}
pins={'jail_sha256':'5c2653a9c14d3199581fdd67e05d055b334ec2746fe7047e3c80454eb732474e','companion_sha256':'61f6add6c68364b1c9dda4486948faf8d104c45ca2db2c40199de64a04ea38f7'}
rows=[]
for phase,names in required.items():
 row=json.loads((D/f'mutation-{backend}-phase{phase}.json').read_text());assert row['commit']==os.environ['GITHUB_SHA'] and row['phase']==phase and row['backend']==backend
 assert all(row.get(n) is True for n in names),[n for n in names if row.get(n) is not True]
 assert all(row.get(k) is False for k in ('admission_enabled','playable_handback_verified','production_changed'))
 assert row['bounded_mutation_verified'] is True and all(row[k]==v for k,v in pins.items())
 identity=json.loads((D/f'split-identity-jail_integrated_smoke-{phase}-normal.json').read_text());assert identity['commit']==row['commit'] and identity['phase']==str(phase)
 assert all(identity[k]==v for k,v in pins.items())
 assert all(identity[k] is True for k in ('separate_jars_loaded','old_jail_classes_absent','non_jail_mods_loaded','escape_reward_empty'))
 logdir=D/f'mutation-{backend}-phase{phase}-logs'
 if phase<3:
  crash=json.loads((logdir/'planned-halt.json').read_text());assert crash['commit']==row['commit'] and crash['phase']==phase and crash['backend']==backend and crash['actual_exit']==95+phase and crash['planned_halt_verified'] is True and crash['graceful_save_used'] is False
 else:assert (logdir/'shutdown-ready.txt').is_file()
 rows.append(row)
source=json.loads((D/'mutation-source-hashes.json').read_text())
for p,digest in source.items():assert hashlib.sha256((R/p).read_bytes()).hexdigest()==digest,p
wrappers=json.loads((D/'mutation-wrapper-manifest.json').read_text())
for row in wrappers['classes']:assert hashlib.sha256((D/'live-wrapper-source'/f"{row['mixin']}.java").read_bytes()).hexdigest()==row['sha256']
for name,digest in wrappers['extra_hooks'].items():assert hashlib.sha256((D/'live-wrapper-source'/f'{name}.java').read_bytes()).hexdigest()==digest
summary={'commit':os.environ['GITHUB_SHA'],'backend':backend,'phases':4,'required_phase_observations':sum(map(len,required.values())),'rows':rows,'source_hashes':source,'baseline_handback_commit':'549ca5db231446e3cb9802345e53a4055b7fa3bf','bounded_mutation_verified':True,'playable_handback_verified':False,'admission_enabled':False,'production_changed':False}
(D/f'mutation-{backend}-summary.json').write_text(json.dumps(summary,indent=2)+'\n')
print('Verified',sum(map(len,required.values())),'required phase observations; gameplay custody remains unverified')
