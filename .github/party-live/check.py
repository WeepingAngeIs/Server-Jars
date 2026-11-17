import hashlib,json,os,sys
from pathlib import Path
root=Path('dist/packaged-smoke');backend=sys.argv[1];sha=os.environ['GITHUB_SHA']
required={1:'''wallet_default_off fence_reserve_native_empty_then_seal_order pre_enrollment_queued_write_denied_at_execution manager_party_denied manager_pc_denied manager_parties_denied direct_cached_factory_denied direct_native_current_load_denied direct_native_backup_load_denied alternate_provider_manager_denied alternate_provider_direct_denied retained_party_reference_denied retained_pokemon_mutation_denied preacquired_iterator_pokemon_denied retained_pc_box_denied reserved_identity_insert_elsewhere_denied raw_alias_mutation_cannot_save nested_alias_not_immutable_but_non_authoritative corrupt_current_cannot_trigger_backup_exposure synchronous_native_io_failure_no_held_receipt native_empty_without_held_receipt_stays_reserved unrelated_native_owner_unchanged'''.split(),
2:'''wallet_default_off durable_fence_recovered_before_native_lookup same_class_different_root_recovery_refused changed_native_pc_destination_refused restart_load_denied_before_fallback occupied_native_destination_refused exact_return_staged_before_any_unlock staged_return_not_player_accessible repeat_return_refused ambiguous_io-failure_no_owned_view ambiguous_io-failure_native_denied ambiguous_empty-interrupted_no_owned_view ambiguous_empty-interrupted_native_denied hard_halt_after_return_generation_before_native_write'''.split(),
3:'''wallet_default_off all_fences_survived_second_halt complete_staged_state_survived_restart_without_alias_change terminal_return_no_replay interrupted_return_native_mismatch_not_hidden interrupted_return_not_replayed persistent_native_quarantine_primary persistent_native_quarantine_io-failure persistent_native_quarantine_empty-interrupted persistent_native_quarantine_return-interrupted unrelated_native_owner_unchanged'''.split()}
rows=[]
for phase,flags in required.items():
 p=root/f'party-live-{backend}-phase{phase}.json';r=json.loads(p.read_text())
 assert r['commit']==sha and r['backend']==backend and r['phase']==phase
 assert r['jail_sha256']=='5c2653a9c14d3199581fdd67e05d055b334ec2746fe7047e3c80454eb732474e'
 assert r['companion_sha256']=='61f6add6c68364b1c9dda4486948faf8d104c45ca2db2c40199de64a04ea38f7'
 for flag in flags:assert r.get(flag) is True,(phase,flag,r)
 assert all(r.get(k) is False for k in ('nonempty_admission_enabled','player_handback_verified','production_changed'))
 assert r['native_denials'] and all(n>0 for n in r['native_denials'].values())
 ident=root/f'split-identity-jail_integrated_smoke-{phase}-normal.json';assert ident.exists()
 raw=json.loads(ident.read_text());assert raw['commit']==sha,(ident,raw)
 logs=(root/f'party-live-{backend}-phase{phase}-logs/server.log').read_text()
 if phase in (1,2):assert f'PARTY_LIVE_PLANNED_HALT_{91+phase}' in logs
 rows.append(r)
manifest=json.loads((root/'live-wrapper-manifest.json').read_text());assert len(manifest['classes'])==13 and manifest['method_count']>100
summary={'commit':sha,'backend':backend,'phases':3,'required_assertions':sum(map(len,required.values())),'rows':rows,'native_method_count':manifest['method_count'],'production_changed':False,'admission_verified':False,'player_handback_verified':False,'source_hashes':{str(p):hashlib.sha256(p.read_bytes()).hexdigest() for p in Path('.github/party-live').rglob('*') if p.is_file() and '__pycache__' not in str(p)}}
(root/f'party-live-{backend}-summary.json').write_text(json.dumps(summary,indent=2)+'\n')
print('PASSED native fence phases:',backend,summary['required_assertions'],'required assertions')
