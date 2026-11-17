"""Compose test-only global provider/transport hooks around exact unchanged production binaries."""
import hashlib,json,os,re,shutil,subprocess,zipfile
from pathlib import Path
R=Path(__file__).resolve().parents[2]
assert os.environ.get('GITHUB_ACTIONS')=='true' and os.environ.get('RUNNER_ENVIRONMENT')=='github-hosted'
subprocess.run(['python3','.github/party-mutation/prepare.py'],cwd=R,check=True)
h=R/'dist/packaged-harness';d=R/'dist/packaged-smoke';java=h/'src/main/java';package=java/'dev/unyielding/jail/party/live'
(package/'MutationProbe.java').unlink()
# Do not mutate prior source: only this disposable harness copy gets the new reviewed-global profile.
p=package/'HandoffCoordinator.java';before=p.read_text()
s=before.replace('|| !((List<?>)field(adapter,"children")).isEmpty()', '|| (!((List<?>)field(adapter,"children")).isEmpty() && !GlobalProfile.reviewed(adapter))',1)
needle='    public static String providerIdentity(OneToOneFileStoreAdapter adapter)throws Exception {\n'
assert before.count(needle)==1 and s!=before
s=s.replace(needle,needle+'        if(Boolean.getBoolean("uq.partySession"))return GlobalProfile.identity(adapter);\n',1);p.write_text(s)
(d/'session-handoff-copy.json').write_text(json.dumps({'original_sha256':hashlib.sha256(before.encode()).hexdigest(),'test_copy_sha256':hashlib.sha256(s.encode()).hexdigest(),'copy_only':True},indent=2))
(d/'session-HandoffCoordinator.java').write_text(s)
for p in (R/'.github/party-session/java').rglob('*.java'):
 dst=java/p.relative_to(R/'.github/party-session/java');dst.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(p,dst)
manifest=json.loads((d/'mutation-wrapper-manifest.json').read_text())
for row in manifest['classes']:
 p=package/'mixin'/(row['mixin']+'.java');s=p.read_text();assert 'MutationGate.invoke(' in s
 s=s.replace('import dev.unyielding.jail.party.live.MutationGate;','import dev.unyielding.jail.party.live.SessionBridge;').replace('MutationGate.invoke(','SessionBridge.invoke(')
 p.write_text(s);(d/'live-wrapper-source'/p.name).write_text(s);row['sha256']=hashlib.sha256(s.encode()).hexdigest()
# Native gameplay remains denied at its caller boundary, not by swallowing arbitrary errors.
jar=next(p for p in (h/'libs').glob('*.jar') if p.name.startswith('Cobblemon-'))
primitive={'V':'void','Z':'boolean','B':'byte','C':'char','S':'short','I':'int','J':'long','F':'float','D':'double'}
def typ(desc,i):
 if desc[i]=='[':
  t,j=typ(desc,i+1);return t+'[]',j
 if desc[i]=='L':
  j=desc.index(';',i);return desc[i+1:j].replace('/','.').replace('$','.'),j+1
 return primitive[desc[i]],i+1
config_path=h/'src/main/resources/party-live.mixins.json';config=json.loads(config_path.read_text());packet_hooks=[]
with zipfile.ZipFile(jar) as z:
 targets=[n[:-6].replace('/','.') for n in z.namelist() if n.startswith('com/cobblemon/mod/common/net/serverhandling/') and n.endswith('Handler.class') and '$' not in n]
for target in sorted(targets):
 text=subprocess.check_output(['javap','-p','-s','-classpath',str(jar),target],text=True);lines=text.splitlines();methods=[]
 for i,line in enumerate(lines):
  if not line.strip().startswith('public void handle(com.cobblemon.mod.common.net.messages.server.'):continue
  desc=lines[i+1].strip().removeprefix('descriptor: ');pos=1;args=[]
  while desc[pos]!=')':
   t,pos=typ(desc,pos);args.append(t)
  if args[-2:]==['net.minecraft.server.MinecraftServer','net.minecraft.server.level.ServerPlayer'] and len(args)==3 and desc[pos:]==')V':methods.append((desc,args))
 if not methods:continue
 name='SessionPacketBoundary'+str(len(packet_hooks));body=['package dev.unyielding.jail.party.live.mixin;','import org.spongepowered.asm.mixin.Mixin;','import org.spongepowered.asm.mixin.injection.*;','import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;','import dev.unyielding.jail.party.live.SessionBridge;','@Mixin(value='+target+'.class,remap=false)','public abstract class '+name+' {']
 for i,(desc,args) in enumerate(methods):
  body+=[' @Inject(method="handle'+desc+'",at=@At("HEAD"),cancellable=true,remap=false)', ' private void deny'+str(i)+'('+','.join(t+' p'+str(k) for k,t in enumerate(args))+',CallbackInfo ci){if(SessionBridge.blockNative(p2,"'+target+'"))ci.cancel();}']
 body+=['}'];source='\n'.join(body)+'\n';(package/'mixin'/(name+'.java')).write_text(source);(d/'live-wrapper-source'/(name+'.java')).write_text(source)
 config['mixins'].append(name);packet_hooks.append({'class':target,'mixin':name,'methods':[desc for desc,args in methods],'sha256':hashlib.sha256(source.encode()).hexdigest()})
assert any('SwapPartyPokemonHandler' in r['class'] for r in packet_hooks)
config_path.write_text(json.dumps(config,indent=2));manifest['session_packet_hooks']=packet_hooks
(d/'session-wrapper-manifest.json').write_text(json.dumps(manifest,indent=2))
for target in ['com.cobblemon.mod.common.net.messages.server.storage.party.SwapPartyPokemonPacket','com.cobblemon.mod.common.CobblemonNetwork']:
 (d/(target+'.session-api.javap')).write_text(subprocess.check_output(['javap','-p','-s','-classpath',str(jar),target],text=True))
proof=json.loads((R/'dist/reused-mutation/mutation-verification.json').read_text())
assert proof['commit']=='a512e532bc105ff8ccfcf913ace9ca28873ea7ca' and proof['runtime_phases']==8
for row in proof['native']:
 for path,digest in row['source_hashes'].items():assert hashlib.sha256((R/path).read_bytes()).hexdigest()==digest,path
files=list((R/'.github/party-session').rglob('*'))+[R/'.github/workflows/jail-party-session.yml']
hashes={str(p.relative_to(R)):hashlib.sha256(p.read_bytes()).hexdigest() for p in files if p.is_file() and '__pycache__' not in p.parts}
(d/'session-source-hashes.json').write_text(json.dumps(hashes,indent=2))
print('Prepared actual global NBT/client session test; prior source and both production JARs unchanged')
