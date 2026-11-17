"""Require actual network/client/global-provider evidence; not end-to-end Jail certification."""
import hashlib,json,os
from pathlib import Path
R=Path(__file__).resolve().parents[2];D=R/'dist/packaged-smoke'
common=['actual_global_singleton_and_configured_factory','wallet_default_off','global_player_overload_returns_only_current_graph','actual_network_hello_received','full_state_snapshot_sent_after_receipt','stale_native_object_refused_after_network_edit','network_edit_receipt_precedes_current_cache','client_acknowledged_exact_snapshot','direct_primary_adapter_load_stays_blocked','real_global_mixed_autosave_preserves_exact_state','unrelated_global_owner_saved','nonempty_admission_not_enabled']
first=['global_pre_enrollment_queued_write_refused','global_source_and_staged_return_exact','unrelated_connection_cannot_claim_target','safe_request_denial_without_empty_fallback','network_action_captured_with_original_session','native_gameplay_request_rejected_at_boundary','actual_native_disconnect_suspends_and_revokes','old_serverplayer_reference_refused','real_reconnect_has_fresh_player_listener_nonce_epoch','queued_prior_connection_action_refused','native_sync_deferred_no_empty_party_substitute','stale_disconnect_cannot_retire_new_connection','quarantined_callback_deferred_before_lookup']
first += ['global_scope_'+suffix for suffix in ('unfrozen_payload_refused','wrong_suffix_refused','backup_refused','wrong_owner_refused','changed_payload_refused','load_refused','unissued_alias_refused','parent_traversal_refused','worker_refused','after_exit_refused','negative_probes_preserve_exact_state')]
client=['client_verified_exact_snapshot_hash','client_full_state_nickname_only_delta','exact_state_acknowledged_over_real_connection']
client_first=['malformed_request_denied_without_disconnect','forged_owner_refused','wrong_exact_receipt_refused','foreign_pokemon_refused','stale_generation_network_request_refused','duplicate_action_refused_same_connection','actual_native_swap_packet_sent','real_native_packet_rejected_without_disconnect','client_initiated_real_disconnect','actual_client_reconnected_with_new_nonce_and_epoch','old_connection_token_refused_on_reconnect','persistent_action_refused_on_reconnect']
pins={'jail_sha256':'5c2653a9c14d3199581fdd67e05d055b334ec2746fe7047e3c80454eb732474e','companion_sha256':'61f6add6c68364b1c9dda4486948faf8d104c45ca2db2c40199de64a04ea38f7'}
rows=[]
for phase in (1,2):
 r=json.loads((D/f'session-phase{phase}.json').read_text());assert r['commit']==os.environ['GITHUB_SHA'] and r['phase']==phase
 required=common+(first if phase==1 else ['restart_opens_original_custody_receipt'])
 assert all(r.get(k) is True for k in required),[k for k in required if r.get(k) is not True]
 assert all(r[k]==v for k,v in pins.items()) and r['production_changed'] is False and r['playable_custody_verified'] is False
 c=json.loads((D/f'session-client-Visitor-phase{phase}.json').read_text());assert c['commit']==r['commit'] and c['passed'] is True
 cr=client+(client_first if phase==1 else ['client_restart_preserves_prior_network_edit','both_persistent_actions_refused_after_server_restart'])
 assert all(c.get(k) is True for k in cr),[k for k in cr if c.get(k) is not True]
 assert c['final_name']==('Network Two' if phase==1 else 'Network Three') and c['ordinary_party_ui_verified'] is False
 dev=json.loads((D/f'session-client-Dev-phase{phase}.json').read_text());assert dev['passed'] is True and dev['unrelated_client_received_no_target_snapshot'] is True and dev['commit']==r['commit']
 stop=json.loads((D/f'session-phase{phase}-shutdown.json').read_text());assert stop['commit']==r['commit'] and stop['native_executor_terminated'] is True and stop['connections_revoked'] is True
 ident=json.loads((D/f'split-identity-jail_integrated_smoke-{phase}-normal.json').read_text());assert ident['commit']==r['commit'] and all(ident[k]==v for k,v in pins.items())
 assert all(ident[k] is True for k in ('separate_jars_loaded','old_jail_classes_absent','non_jail_mods_loaded','escape_reward_empty'))
 logs=D/f'session-phase{phase}-logs';assert (logs/'shutdown-ready.txt').is_file()
 text=(logs/'server.log').read_text();assert text.count('Visitor joined the game') >= (2 if phase==1 else 1)
 assert len({v['nonce'] for v in r['connections']})==len(r['connections'])==(2 if phase==1 else 1)
 rows.append({'server':r,'client':c,'control_client':dev,'shutdown':stop})
assert 'passed: 16 exact native path checks' in (D/'native-path-tests.txt').read_text()
source=json.loads((D/'session-source-hashes.json').read_text())
for path,digest in source.items():assert hashlib.sha256((R/path).read_bytes()).hexdigest()==digest,path
wrappers=json.loads((D/'session-wrapper-manifest.json').read_text())
for row in wrappers['classes']+wrappers['session_packet_hooks']:assert hashlib.sha256((D/'live-wrapper-source'/(row['mixin']+'.java')).read_bytes()).hexdigest()==row['sha256']
for name,digest in wrappers['extra_hooks'].items():assert hashlib.sha256((D/'live-wrapper-source'/(name+'.java')).read_bytes()).hexdigest()==digest
summary={'commit':os.environ['GITHUB_SHA'],'server_starts':2,'visitor_connections':3,'actual_network_reconnects':1,'rows':rows,'source_hashes':source,'baseline_mutation_commit':'a512e532bc105ff8ccfcf913ace9ca28873ea7ca','playable_custody_verified':False,'production_changed':False,'admission_enabled':False,'native_provider':'configured global NBT with pinned Reforged child, no fallback authorization'}
(D/'session-verification.json').write_text(json.dumps(summary,indent=2)+'\n');print('Verified actual global-provider test transport and reconnect; gameplay custody not enabled')
