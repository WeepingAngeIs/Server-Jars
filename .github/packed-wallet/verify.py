"""Require exact-commit assertions, actual exit codes and loaded binary identity."""
import hashlib
import json
import os
from pathlib import Path
import sys
PHASES={'lifecycle':5,'occupied':4,'remove-unsaved':3,'remove-saved':3,'return-unsaved':4,'return-saved':4}
CRASH={('remove-unsaved',1):'ITEMS_MUTATED',('remove-saved',1):'ITEMS_DURABLE',('return-unsaved',2):'ITEMS_RETURN_MUTATED',('return-saved',2):'ITEMS_RETURN_DURABLE'}
REQUIRED={
 ('lifecycle',2):['actual_jailed_escape_death_with_wallet_in_escrow','command_and_teleport_confinement'],
 ('lifecycle',3):['restart_while_dead_kept_wallet_in_escrow','actual_native_respawn_rebound_authoritative_player','respawn_inside_jail'],
 ('lifecycle',4):['actual_release_after_death_disconnect_and_restart','native_wallet_exact_original_slot'],
 ('lifecycle',5):['released_state_survived_restart','completed_receipt_never_regrants_even_after_item_move'],
 ('occupied',2):['occupied_wallet_destination_refused_without_overwrite'],
 ('occupied',3):['occupied_destination_preserved_across_restart','occupied_destination_repeat_recovery_refused','release_only_after_explicit_external_conflict_resolution'],
 ('occupied',4):['competing_wallet_not_deleted','native_wallet_exact_original_slot'],
 ('remove-saved',2):['durable_removal_receipt_survived_abrupt_stop','saved_removal_explicitly_recovered_without_removal_replay','release_after_saved_removal_recovery'],
 ('remove-saved',3):['native_wallet_exact_original_slot'],
 ('return-saved',3):['saved_return_receipt_and_complete_wallet_survived_abrupt_stop','saved_return_explicitly_confirmed_without_regrant','saved_return_waits_for_explicit_final_review','normal_unjail_cannot_skip_recovery_review','unreviewed_saved_return_cannot_finish','final_review_completed_without_resource_regrant'],
 ('return-saved',4):['native_wallet_exact_original_slot'],
}
ROOT=Path(__file__).resolve().parents[2]
EVIDENCE=ROOT/'dist/packaged-smoke'
def require(ok,message):
    if not ok: raise ValueError(message)
def validate(report,scenario,phase,commit,jail,companion,exitcode):
    require(report.get('commit')==commit and report.get('scenario')==scenario and report.get('phase')==phase,'Wrong phase/commit')
    require(report.get('jail_sha256')==jail and report.get('companion_sha256')==companion,'Wrong binary identity')
    crash=CRASH.get((scenario,phase))
    require(report.get('hard_stop') is bool(crash),'Wrong crash claim')
    if crash:
        require(report.get('crash_boundary')==crash and exitcode=='86','Not an actual abrupt JVM termination')
        for key in ['crash_live_state_verified','crash_disk_state_verified']: require(report.get(key) is True,key)
    else:
        require(exitcode=='0','Server did not exit normally')
        require(report.get('actual_disconnect_observed') is True,'No real disconnect')
    if phase==1:
        for key in ['both_admission_switches_default_off','default_off_refused_without_mutation','legacy_supported_mode_does_not_enable_wallets','packed_wallet_native_preflight_passed']:require(report.get(key) is True,key)
        if not crash:require(report.get('actual_wallet_custody_admission') is True,'No real admission')
    if scenario=='remove-unsaved' and phase>1 or scenario=='return-unsaved' and phase>2:
        for key in ['ambiguous_save_remains_quarantined','ambiguous_save_resource_location_preserved','uncertain_save_never_replayed_or_granted']:require(report.get(key) is True,key)
    for key in REQUIRED.get((scenario,phase),[]):require(report.get(key) is True,key)
    return report

def main():
    scenario=sys.argv[1];phases=[int(sys.argv[2])] if len(sys.argv)==3 else range(1,PHASES[scenario]+1)
    jail=hashlib.sha256(Path(os.environ['UQ_JAIL_JAR']).read_bytes()).hexdigest();companion=hashlib.sha256(Path(os.environ['UQ_BUNDLE_JAR']).read_bytes()).hexdigest()
    require(companion=='61f6add6c68364b1c9dda4486948faf8d104c45ca2db2c40199de64a04ea38f7','Non-jail companion changed')
    reports=[]
    for phase in phases:
        report=json.loads((EVIDENCE/f'packed-wallet-{scenario}-phase{phase}.json').read_text());logs=EVIDENCE/f'packed-{scenario}-phase{phase}-logs'
        validate(report,scenario,phase,os.environ['GITHUB_SHA'],jail,companion,(logs/'server-exit-code').read_text().strip())
        identity=json.loads((EVIDENCE/f'split-identity-jail_integrated_smoke-{phase}-normal.json').read_text())
        require(identity.get('commit')==os.environ['GITHUB_SHA'] and identity.get('jail_sha256')==jail and identity.get('companion_sha256')==companion and identity.get('separate_jars_loaded') is True,'Missing loaded artifact evidence')
        if (scenario,phase) in CRASH:require('Stopping server' not in (logs/'server.log').read_text(),'Crash test actually stopped gracefully')
        reports.append(report)
    if len(sys.argv)==2:(EVIDENCE/f'packed-wallet-{scenario}-summary.json').write_text(json.dumps({'commit':os.environ['GITHUB_SHA'],'scenario':scenario,'jail_sha256':jail,'companion_sha256':companion,'phase_count':len(reports),'all_passed':True,'reports':reports},indent=2)+'\n')
    print('Verified',scenario,'phases',[r['phase'] for r in reports])
if __name__=='__main__':main()
