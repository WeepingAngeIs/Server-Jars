"""Require observed native player outcomes; no Pokemon preservation claim."""
import hashlib,json,os
from pathlib import Path
R=Path(__file__).resolve().parents[2];D=R/'dist/packaged-smoke'
required={
1:['fresh_admission_and_wallet_defaults_off','party_nonempty_before_admission','wallet_opt_in_still_required','nonempty_party_player_admitted_with_wallet_and_equipment','commands_teleport_and_external_damage_still_blocked','boundary_movement_caused_actual_player_death','real_client_observed_automatic_alive_replacement','automatic_respawn_inside_without_client_request','exact_inventory_equipment_accessory_wallet_and_origin_return','escape_achievement_remains_one_time','outside_position_tick_also_kills_player','disconnected_after_durable_death_before_auto_tick'],
2:['actual_restart_and_reconnect_auto_respawn_without_button','recovery_works_with_new_admissions_disabled','commands_teleport_and_external_damage_still_blocked','occupied_return_refused_then_exact_release','unsafe_destination_does_not_kill_or_respawn','cancelled_death_retains_quarantine_without_replay'],
3:['cancelled_attempt_quarantined_across_actual_restart']}
pins={'jail_sha256':'5c2653a9c14d3199581fdd67e05d055b334ec2746fe7047e3c80454eb732474e','companion_sha256':'61f6add6c68364b1c9dda4486948faf8d104c45ca2db2c40199de64a04ea38f7'}
rows=[]
for phase,keys in required.items():
 r=json.loads((D/f'player-only-phase{phase}.json').read_text());assert r['phase']==phase and r['commit']==os.environ['GITHUB_SHA']
 assert all(r.get(k) is True for k in keys),[(k,r.get(k)) for k in keys]
 assert all(r[k]==v for k,v in pins.items()) and not r['production_changed'] and not r['pokemon_custody_installed']
 assert r['client_respawn_requests']==0 and r['automatic_completions']==r['automatic_calls']==(1 if phase<3 else 0)
 logs=D/f'player-only-phase{phase}-logs';text=(logs/'server.log').read_text()
 assert text.count('Visitor joined the game')>=1 and text.count('Dev joined the game')>=1
 assert text.count('JAIL_PLAYER_AUTO_RESPAWN tx=')==(1 if phase<3 else 0)
 assert (logs/'shutdown-ready.txt').is_file() and (logs/'uq-player-only-result').read_text().startswith('passed:')
 identity=json.loads((D/f'split-identity-jail_certified_smoke-{phase}-normal.json').read_text())
 assert identity['commit']==os.environ['GITHUB_SHA'] and all(identity[k]==v for k,v in pins.items())
 assert all(identity[k] is True for k in ['separate_jars_loaded','non_jail_mods_loaded','old_jail_classes_absent','escape_reward_empty'])
 rows.append(r)
c=json.loads((D/'player-only-client-Visitor-1.json').read_text());assert c['observed_dead'] and c['alive_after_death'] and c['respawn_actions_sent']==0 and c['replacement_objects']>=1
s=json.loads((D/'player-only-source.json').read_text());assert s['other_admission_checks_preserved'] and not s['old_party_test_mods_loaded'] and not s['production_changed']
for p,digest in s['source_sha256'].items():assert hashlib.sha256((R/p).read_bytes()).hexdigest()==digest,p
report={'commit':os.environ['GITHUB_SHA'],'server_starts':3,'verified_automatic_respawns':2,'client_respawn_requests':0,'required_observations':sum(map(len,required.values())),'test_mod_only':True,'production_changed':False,'pokemon_custody_installed':False,'rows':rows,'client':c,'source':s}
(D/'player-only-verification.json').write_text(json.dumps(report,indent=2)+'\n')
print(json.dumps(report,indent=2))
