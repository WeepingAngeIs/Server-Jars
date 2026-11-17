"""Run actual production classes. Only synthetic stimuli and passive counters enter the test mod."""
import hashlib,json,os,shutil,subprocess
from pathlib import Path
R=Path(__file__).resolve().parents[2]
assert os.environ.get('GITHUB_ACTIONS')=='true' and os.environ.get('RUNNER_ENVIRONMENT')=='github-hosted'
subprocess.run(['python3','.github/scripts/prepare-split-server.py'],cwd=R,check=True)
h=R/'dist/packaged-harness';src=h/'src/main/java';res=h/'src/main/resources';d=R/'dist/packaged-smoke'
p=src/'dev/unyielding/bundlesmoke/CertifiedJailSmoke.java';s=p.read_text()
for a,b in [('if(!Boolean.getBoolean("uq.certifiedJail"))return;','if(!Boolean.getBoolean("uq.certifiedJail")||Boolean.getBoolean("uq.playerOnly"))return;'),('new JailEquipmentPort(target,id,retired,true)','new JailEquipmentPort(target,id,retired,true,true)')]:
 assert s.count(a)==1;s=s.replace(a,b,1)
p.write_text(s)
shutil.copy2(R/'.github/packed-wallet/PackedWalletProof.java',src/'dev/unyielding/bundlesmoke/PackedWalletProof.java')
for p in (R/'.github/jail-production').glob('*.java'):
 sub='proof/' if p.name.endswith('Observer.java') and p.name!='ProductionObserver.java' else ''
 target=src/'dev/unyielding/bundlesmoke'/sub/p.name;target.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(p,target)
# Reuse the exact historical stimulus/observer fixtures; never copy their old behavior adapters.
s=(R/'.github/player-only/PlayerOnlySmoke.java').read_text().replace('PlayerOnlyJail','ProductionObserver')
s=s.replace('Synthetic fixture around unchanged JARs.','Synthetic fixture around the actual production candidate JAR.')
s=s.replace('&&ProductionObserver.fallbackDeaths==1','').replace('proof.put("production_changed",false)','proof.put("production_changed",true)')
s=s.replace('    static void setup()throws Exception{','    static void setup()throws Exception{\n        ProductionObserver.identity();')
a='held();flag("actual_restart_and_reconnect_auto_respawn_without_button");'
assert s.count(a)==1
s=s.replace(a,'held();ProductionObserver.selfBinding(p());held();flag("artifact_bytes_bound_to_custody_runtime");flag("actual_restart_and_reconnect_auto_respawn_without_button");',1)
a='if(!Files.exists(report)||!com.google.gson.JsonParser.parseString(Files.readString(report)).getAsJsonObject().get("alive_after_death").getAsBoolean())return;'
assert s.count(a)==1
s=s.replace(a,a+'\n        if(com.google.gson.JsonParser.parseString(Files.readString(report)).getAsJsonObject().getAsJsonArray("verified_respawns").isEmpty())return;',1)
(src/'dev/unyielding/bundlesmoke/PlayerOnlySmoke.java').write_text(s)
s=(R/'.github/player-only/PlayerOnlyClient.java').read_text().replace('"respawn_actions_sent",0','"respawn_actions_sent",ClientRequests.sent')
(src/'dev/unyielding/bundlesmoke/PlayerOnlyClient.java').write_text(s)
config={'required':True,'minVersion':'0.8','package':'dev.unyielding.bundlesmoke.proof','compatibilityLevel':'JAVA_21','mixins':['RespawnCompletionObserver','ServerRequestObserver'],'client':['ClientRequestObserver'],'injectors':{'defaultRequire':1}}
(res/'jail-production-observers.mixins.json').write_text(json.dumps(config,indent=2))
p=res/'META-INF/neoforge.mods.toml';p.write_text(p.read_text()+'\n[[mixins]]\nconfig="jail-production-observers.mixins.json"\n')
assert not (src/'dev/unyielding/jail').exists()
for p in src.rglob('*.java'):
 assert p.name not in ['PlayerOnlyJail.java','PlayerOnlyAccess.java','NativeFence.java','MutationGate.java','SessionBridge.java','PublicationGate.java','JailAutoRespawn.java'],p
for p in (src/'dev/unyielding/bundlesmoke/proof').glob('*.java'):
 s=p.read_text();assert all(token not in s for token in ['WrapOperation','WrapMethod','Redirect','Overwrite','cancellable','setReturnValue','setCanceled','ci.cancel']),p
proof={'commit':os.environ['GITHUB_SHA'],'production_changed':True,'behavior_supplied_by_candidate_jar':True,'old_party_test_mods_loaded':False,'behavior_adapters_loaded':False,'source_sha256':{str(p.relative_to(R)):hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted((R/'.github/jail-production').glob('*')) if p.is_file()},'harness_sha256':{str(p.relative_to(src)):hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted(src.rglob('*.java'))},'observation_mixins':config}
(d/'production-harness.json').write_text(json.dumps(proof,indent=2)+'\n')
print('Production behavior is loaded from candidate JAR; test mixins are passive observers only')
