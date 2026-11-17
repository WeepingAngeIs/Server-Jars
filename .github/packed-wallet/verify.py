"""Require actual wallet custody plus exact test artifacts, not generic preservation markers."""
import hashlib,json,os,sys
from pathlib import Path
root=Path('dist/packaged-smoke');mode=sys.argv[1]
inputs=json.loads((root/'split-inputs.json').read_text());assert inputs['commit']==os.environ['GITHUB_SHA']
assert inputs['jars']['Unyielding-1.2.0.jar']=='61f6add6c68364b1c9dda4486948faf8d104c45ca2db2c40199de64a04ea38f7'
for name,digest in inputs['jars'].items():assert hashlib.sha256((Path('dist/packaged-harness/libs')/name).read_bytes()).hexdigest()==digest
prefix='packed-wallet-phase' if mode=='core' else 'packed-wallet-fault'
required=range(1,6) if mode=='core' else range(1,5)
reports=[]
for phase in required:
    row=json.loads((root/f'{prefix}{phase}.json').read_text());assert row['commit']==inputs['commit'] and row['phase']==phase
    assert row['jail_sha256']==inputs['jars']['Minecraft-Jail-1.2.0.jar'] and row['companion_sha256']==inputs['jars']['Unyielding-1.2.0.jar']
    reports.append(row)
if mode=='core':
    assert reports[0]['negative_cases_complete'] and reports[0]['occupied_destination_refused_without_overwrite']
    assert all(r['actual_nonempty_wallet_admission'] and not r['party_admission_enabled'] and r['complete_wallet_owned_by_bound_escrow'] for r in reports)
    assert reports[0]['exact_wallet_and_nested_slots_returned'] and reports[4]['exact_wallet_and_nested_slots_returned']
else:
    assert reports[0]['saved_removal_interrupted_and_held'] and reports[0]['real_disconnect_observed']
    assert reports[1]['saved_removal_recovered_and_released_with_admission_off'] and reports[1]['real_disconnect_observed']
    assert reports[2]['completed_release_survived_restart_without_duplication'] and reports[2]['hard_halt_before_player_save'] and reports[2]['unsaved_removal_not_replayed']
    assert reports[3]['hard_restart_restored_saved_source_without_replay'] and reports[3]['uncertain_outcome_remains_quarantined']
identities=list(root.glob('split-identity-*.json'));assert len(identities)==len(reports)
for path in identities:
    row=json.loads(path.read_text());assert row['separate_jars_loaded'] and row['non_jail_mods_loaded'] and row['old_jail_classes_absent']
    assert row['jail_sha256']==inputs['jars']['Minecraft-Jail-1.2.0.jar'] and row['companion_sha256']==inputs['jars']['Unyielding-1.2.0.jar']
report={'commit':inputs['commit'],'slice':mode,'passed':True,'jars':inputs['jars'],'phases':reports,'limits':['Only one packed leather wallet; equipped/other wallets and nonempty Pokemon parties remain blocked','Ambiguous unsaved removal remains quarantined, no automatic replay','Synthetic reviewed runtime only; no private-save/full-pack migration']}
(root/f'wallet-verified-{mode}.json').write_text(json.dumps(report,indent=2)+'\n');print(json.dumps(report,indent=2))
