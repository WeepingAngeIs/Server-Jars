"""Generate required, exact-descriptor native method wrappers from the pinned binary.
No wildcard optional injections. Generated source and signatures are retained as evidence.
"""
import hashlib,json,re,subprocess,sys
from pathlib import Path
root=Path(__file__).resolve().parents[2]
h=root/'dist/packaged-harness';jar=next(p for p in (h/'libs').glob('*.jar') if p.name.startswith('Cobblemon-'))
classes=[
 'com.cobblemon.mod.common.api.storage.PokemonStoreManager',
 'com.cobblemon.mod.common.api.storage.factory.FileBackedPokemonStoreFactory',
 'com.cobblemon.mod.common.api.storage.adapter.CobblemonAdapterParent',
 'com.cobblemon.mod.common.api.storage.adapter.flatfile.OneToOneFileStoreAdapter',
 'com.cobblemon.mod.common.api.storage.adapter.flatfile.NBTStoreAdapter',
 'com.cobblemon.mod.common.api.storage.adapter.flatfile.JSONStoreAdapter',
 'com.cobblemon.mod.common.api.storage.adapter.conversions.ReforgedConversion',
 'com.cobblemon.mod.common.api.storage.PokemonStore',
 'com.cobblemon.mod.common.api.storage.party.PartyStore',
 'com.cobblemon.mod.common.api.storage.party.PlayerPartyStore',
 'com.cobblemon.mod.common.api.storage.pc.PCStore',
 'com.cobblemon.mod.common.api.storage.pc.PCBox',
 'com.cobblemon.mod.common.pokemon.Pokemon',
]
primitive={'V':'void','Z':'boolean','B':'byte','C':'char','S':'short','I':'int','J':'long','F':'float','D':'double'}
boxed={'void':'Void','boolean':'Boolean','byte':'Byte','char':'Character','short':'Short','int':'Integer','long':'Long','float':'Float','double':'Double'}
def typ(d,i):
 if d[i]=='[':
  v,j=typ(d,i+1);return v+'[]',j
 if d[i]=='L':
  j=d.index(';',i);return d[i+1:j].replace('/','.').replace('$','.'),j+1
 return primitive[d[i]],i+1
def signature(d):
 i=1;args=[]
 while d[i]!=')':
  v,i=typ(d,i);args.append(v)
 ret,j=typ(d,i+1);assert j==len(d)
 return args,ret
out=h/'src/main/java/dev/unyielding/jail/party/live/mixin';out.mkdir(parents=True,exist_ok=True)
evidence=root/'dist/packaged-smoke/live-wrapper-source';evidence.mkdir(parents=True,exist_ok=True)
manifest=[]
for index,target in enumerate(classes):
 text=subprocess.check_output(['javap','-p','-s','-classpath',str(jar),target],text=True)
 (evidence/(target+'.javap')).write_text(text)
 lines=text.splitlines();methods=[]
 for i,line in enumerate(lines):
  if not re.match(r'  (public) ',line) or '(' not in line or ' static ' in line or ' abstract ' in line:continue
  name=line.split('(')[0].split()[-1]
  if name==target or name in ('getUuid','getPlayerUUID','getPc','getFile','shutdown','unregisterFactory','unregisterAll') or name.endswith('$default'):continue
  desc=lines[i+1].strip().removeprefix('descriptor: ');assert desc.startswith('('),line
  args,ret=signature(desc)
  methods.append((name,desc,args,ret))
 if not methods:raise SystemExit('No methods selected for '+target)
 name='FenceMixin'+str(index)
 body=['package dev.unyielding.jail.party.live.mixin;',
       'import org.spongepowered.asm.mixin.Mixin;',
       'import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;',
       'import com.llamalad7.mixinextras.injector.wrapoperation.Operation;',
       'import dev.unyielding.jail.party.live.NativeFence;',
       '@Mixin(value='+target+'.class, remap=false)', 'public abstract class '+name+' {']
 for n,(method,desc,args,ret) in enumerate(methods):
  parameters=[t+' p'+str(k) for k,t in enumerate(args)]
  call=','.join('p'+str(k) for k in range(len(args)))
  parameters+=['Operation<'+boxed.get(ret,ret)+'> original']
  body+=[' @WrapMethod(method="'+method+desc+'", remap=false)',
         ' private '+ret+' intercept'+str(n)+'('+','.join(parameters)+') throws Throwable {']
  invocation='original.call(new Object[]{'+call+'})'
  run='() -> '+invocation if ret!='void' else '() -> { '+invocation+'; return null; }'
  expr='NativeFence.invoke(this,"'+target+'#'+method+desc+'",new Object[]{'+call+'},'+run+')'
  body+=['  '+(expr+';' if ret=='void' else 'return ('+boxed.get(ret,ret)+')'+expr+';'), ' }']
 body+=['}'];code='\n'.join(body)+'\n';(out/(name+'.java')).write_text(code);(evidence/(name+'.java')).write_text(code)
 manifest.append({'target':target,'mixin':name,'methods':[m+d for m,d,a,r in methods],'sha256':hashlib.sha256(code.encode()).hexdigest()})
config={'required':True,'minVersion':'0.8','package':'dev.unyielding.jail.party.live.mixin','compatibilityLevel':'JAVA_21','mixins':[m['mixin'] for m in manifest],'injectors':{'defaultRequire':1}}
(h/'src/main/resources/party-live.mixins.json').write_text(json.dumps(config,indent=2)+'\n')
(root/'dist/packaged-smoke/live-wrapper-manifest.json').write_text(json.dumps({'cobblemon_sha512':hashlib.sha512(jar.read_bytes()).hexdigest(),'classes':manifest,'method_count':sum(len(m['methods']) for m in manifest)},indent=2)+'\n')
print('Generated',len(classes),'native classes and',sum(len(m['methods']) for m in manifest),'required exact method wrappers')
