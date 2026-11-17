"""Prepare a private test-mod extension; never edit the baseline fence or production sources."""
import hashlib,json,os,shutil,subprocess
from pathlib import Path
R=Path(__file__).resolve().parents[2]
assert os.environ.get('GITHUB_ACTIONS')=='true' and os.environ.get('RUNNER_ENVIRONMENT')=='github-hosted'
subprocess.run(['python3','.github/party-live/prepare.py'],cwd=R,check=True)
h=R/'dist/packaged-harness';dst=h/'src/main/java';proof=R/'dist/packaged-smoke'
(dst/'dev/unyielding/jail/party/live/LiveFenceProbe.java').unlink()
for p in (R/'.github/party-handback/java').rglob('*.java'):
 target=dst/p.relative_to(R/'.github/party-handback/java');target.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(p,target)
manifest=json.loads((proof/'live-wrapper-manifest.json').read_text())
for row in manifest['classes']:
 p=dst/'dev/unyielding/jail/party/live/mixin'/f"{row['mixin']}.java"
 text=p.read_text().replace('import dev.unyielding.jail.party.live.NativeFence;','import dev.unyielding.jail.party.live.PublicationGate;').replace('NativeFence.invoke(','PublicationGate.invoke(')
 p.write_text(text);(proof/'live-wrapper-source'/p.name).write_text(text);row['sha256']=hashlib.sha256(text.encode()).hexdigest()
config_path=h/'src/main/resources/party-live.mixins.json';config=json.loads(config_path.read_text())
extras={}
for i,target in enumerate(['com.cobblemon.mod.common.api.storage.PokemonStore','com.cobblemon.mod.common.pokemon.Pokemon','com.cobblemon.mod.common.api.storage.pc.PCBox']):
 name=f'HandbackBirth{i}'
 extras[name]='''package dev.unyielding.jail.party.live.mixin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import dev.unyielding.jail.party.live.PublicationGate;
@Mixin(value=TARGET.class,remap=false)
public abstract class NAME {
 @Inject(method="<init>",at=@At("RETURN"),remap=false)
 private void newlyConstructed(CallbackInfo ci){PublicationGate.born(this);}
}
'''.replace('TARGET',target).replace('NAME',name)
extras['HandbackLifecycle']='''package dev.unyielding.jail.party.live.mixin;
import org.spongepowered.asm.mixin.Mixin;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.cobblemon.mod.common.api.storage.factory.FileBackedPokemonStoreFactory;
import com.cobblemon.mod.common.api.storage.adapter.SerializedStore;
import net.minecraft.core.RegistryAccess;
import java.util.List;
import dev.unyielding.jail.party.live.PublicationGate;
@Mixin(value=FileBackedPokemonStoreFactory.class,remap=false)
public abstract class HandbackLifecycle {
 @WrapMethod(method="shutdown(Lnet/minecraft/core/RegistryAccess;)V",remap=false)
 private void stop(RegistryAccess r,Operation<Void> original)throws Throwable{
  PublicationGate.shutdown((FileBackedPokemonStoreFactory)(Object)this,()->{original.call(r);return null;});
 }
 @WrapMethod(method="save$lambda$0(Lcom/cobblemon/mod/common/api/storage/factory/FileBackedPokemonStoreFactory;Lcom/cobblemon/mod/common/api/storage/adapter/SerializedStore;)V",remap=false)
 private static void queued(FileBackedPokemonStoreFactory f,SerializedStore s,Operation<Void> original)throws Throwable{
  PublicationGate.worker(f,List.of(s),()->{original.call(f,s);return null;});
 }
 @WrapMethod(method="saveAll$lambda$1(Ljava/util/List;Lcom/cobblemon/mod/common/api/storage/factory/FileBackedPokemonStoreFactory;)V",remap=false)
 private static void batch(List<SerializedStore> rows,FileBackedPokemonStoreFactory f,Operation<Void> original)throws Throwable{
  PublicationGate.worker(f,rows,()->{original.call(rows,f);return null;});
 }
}
'''
for name,text in extras.items():
 (dst/'dev/unyielding/jail/party/live/mixin'/f'{name}.java').write_text(text)
 (proof/'live-wrapper-source'/f'{name}.java').write_text(text);config['mixins'].append(name)
config_path.write_text(json.dumps(config,indent=2)+'\n');manifest['extra_hooks']={name:hashlib.sha256(text.encode()).hexdigest() for name,text in extras.items()}
(proof/'handback-wrapper-manifest.json').write_text(json.dumps(manifest,indent=2)+'\n')
# Adapt only the already verified private launcher copy and its strict crash checker.
p=R/'dist/party-live-harness/meeting-two-player.sh';s=p.read_text();assert s.count('.github/party-live/check_halt.py')==1
s=s.replace('.github/party-live/check_halt.py','.github/party-handback/check_halt.py');p.write_text(s)
(proof/'handback-driver.sh').write_text(s);(proof/'handback-driver.sha256').write_text(hashlib.sha256(s.encode()).hexdigest()+'\n')
# Verify both reused baselines against their original recorded source hashes.
old=json.loads((R/'dist/reused-live/live-verification.json').read_text());assert old['commit']=='87c2b6383ea528b1d604f415c0ffab49131884ac' and old['runtime_phases']==6
for row in old['native']:
 for path,digest in row['source_hashes'].items():assert hashlib.sha256((R/path).read_bytes()).hexdigest()==digest,path
source_hashes={str(p.relative_to(R)):hashlib.sha256(p.read_bytes()).hexdigest() for p in (R/'.github/party-handback').rglob('*') if p.is_file() and '__pycache__' not in str(p)}
(proof/'handback-source-hashes.json').write_text(json.dumps(source_hashes,indent=2)+'\n')
