"""Accept only exact complete raw evidence; native counterexamples remain required regressions."""
import hashlib,json,os,subprocess,sys
from pathlib import Path
backend=sys.argv[1];assert backend in ('nbt','json')
subprocess.run(['python3','.github/party-storage/check.py',backend],check=True)
root=Path('dist/packaged-smoke');reports=[]
flags={
1:['guard_reservation_before_empty_generation','guard_complete_native_snapshot_preserved','guard_stale_receipt_refused','guard_occupied_destination_refused','guard_alternate_provider_refused','guard_exclusive_identity_reservation','guard_native_duplicate_party_pc_rejected','guard_io_failure_no_receipt_despite_queue_sentinel'],
2:['guard_backup_resurrection_refused_after_restart','guard_exact_managed_return_after_restart','guard_no_repeat_return_or_stale_empty_load','guard_uncertain_write_quarantined_after_real_halt'],
3:['guard_exact_restored_state_survived_second_restart','guard_terminal_reservations_no_replay','guard_uncertain_write_still_quarantined'],
}
for phase in (1,2,3):
    r=json.loads((root/f'party-storage-{backend}-phase{phase}.json').read_text());reports.append(r)
    assert r['commit']==os.environ['GITHUB_SHA'] and r['phase']==phase and r['backend']==backend
    assert r['nonempty_admission_enabled'] is False and r['wallet_default_off'] is True
    for flag in flags[phase]:assert r.get(flag) is True,flag
    identity=json.loads((root/f'split-identity-jail_integrated_smoke-{phase}-normal.json').read_text())
    assert identity['commit']==r['commit']
    for flag in ['separate_jars_loaded','non_jail_mods_loaded','old_jail_classes_absent','escape_reward_empty']:assert identity[flag] is True
    for key,digest in [('jail','5c2653a9c14d3199581fdd67e05d055b334ec2746fe7047e3c80454eb732474e'),('companion','61f6add6c68364b1c9dda4486948faf8d104c45ca2db2c40199de64a04ea38f7')]:assert identity[key+'_sha256']==r[key+'_sha256']==digest
source_hashes={str(p):hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted(Path('minecraft-jail/src/partySafety').rglob('*.java'))}
source_hashes.update({str(p):hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted(Path('minecraft-jail/src/partySafetyNative').rglob('*.java'))})
summary={'commit':os.environ['GITHUB_SHA'],'backend':backend,'phases':3,'guard_flags':15,'legacy_counterexample_flags':14,'admission_verified':False,'global_native_hooks_installed':False,'production_jar_changed':False,'sources':source_hashes,'reports':reports}
(root/f'party-guard-{backend}-summary.json').write_text(json.dumps(summary,indent=2)+'\n')
print(json.dumps({k:v for k,v in summary.items() if k not in ('reports','sources')},indent=2))
