"""Fail closed on partial, stale, or mismatched backend evidence."""
import hashlib,json,os,sys
from pathlib import Path
ROOT=Path('dist/packaged-smoke');backend=sys.argv[1]
assert backend in ('nbt','json')
expected={'jail':'5c2653a9c14d3199581fdd67e05d055b334ec2746fe7047e3c80454eb732474e','companion':'61f6add6c68364b1c9dda4486948faf8d104c45ca2db2c40199de64a04ea38f7'}
reports=[json.loads((ROOT/f'party-storage-{backend}-phase{i}.json').read_text()) for i in (1,2)]
for i,r in enumerate(reports,1):
    assert r['commit']==os.environ['GITHUB_SHA'] and r['phase']==i and r['backend']==backend
    assert r['nonempty_admission_enabled'] is False and r['wallet_default_off'] is True and r['native_save_returns_void'] is True
    for key,value in expected.items():assert r[key+'_sha256']==value
    identity=json.loads((ROOT/f'split-identity-jail_integrated_smoke-{i}-normal.json').read_text())
    assert identity['commit']==r['commit'] and all(identity[k] for k in ('separate_jars_loaded','non_jail_mods_loaded','old_jail_classes_absent','escape_reward_empty'))
    for key,value in expected.items():assert identity[key+'_sha256']==value
for flag in ['ordinary_full_state_roundtrip','save_returned_before_write','queue_fence_succeeded_after_failed_write','dirty_cleared_despite_failure','saveAll_did_not_retry_failed_dirty_store','empty_source_saved_then_current_file_corrupted','same_uuid_accepted_in_party_and_pc','native_set_replaces_occupied_destination','party_and_pc_can_resolve_different_native_factories','hard_halt_after_save_return_before_write']:
    assert reports[0].get(flag) is True,flag
for flag in ['queued_empty_save_not_committed_at_halt','native_backup_resurrected_pre_removal_party','same_uuid_in_party_and_pc_survived_restart','complete_party_pc_snapshots_preserved_in_conflict']:
    assert reports[1].get(flag) is True,flag
assert 'PARTY_STORAGE_PLANNED_HALT_91' in (ROOT/f'party-storage-{backend}-phase1-logs/server.log').read_text()
for prefix,key in [('UQ_JAIL','jail'),('UQ_BUNDLE','companion')]:assert hashlib.sha256(Path(os.environ[prefix+'_JAR']).read_bytes()).hexdigest()==expected[key]
out={'commit':os.environ['GITHUB_SHA'],'backend':backend,'phase_count':2,'checked_counterexample_flags':14,'sha256':expected,'reports':reports,'custody_admission_verified':False,'power_loss_durability_verified':False,'outcome':'Native completion/ownership shortcuts rejected; production unchanged'}
(ROOT/f'party-storage-{backend}-summary.json').write_text(json.dumps(out,indent=2)+'\n')
print(json.dumps({k:v for k,v in out.items() if k!='reports'},indent=2))
