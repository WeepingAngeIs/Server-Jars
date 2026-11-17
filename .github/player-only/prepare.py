"""Hosted test copies only. Never load the retired Pokemon investigation mods."""
import hashlib,json,os,shutil,subprocess
from pathlib import Path
R=Path(__file__).resolve().parents[2]
assert os.environ.get('GITHUB_ACTIONS')=='true' and os.environ.get('RUNNER_ENVIRONMENT')=='github-hosted'
subprocess.run(['python3','.github/scripts/prepare-split-server.py'],cwd=R,check=True)
h=R/'dist/packaged-harness'; src=h/'src/main/java'; res=h/'src/main/resources'; d=R/'dist/packaged-smoke'
def write(path,text):
 p=src/path;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(text)
def once(s,a,b):
 assert s.count(a)==1,(a,s.count(a));return s.replace(a,b,1)
p=R/'minecraft-jail/src/main/java/dev/unyielding/jail/JailAdmissions.java'; original=p.read_text()
assert subprocess.check_output(['git','hash-object',str(p)],text=True).strip()=='5b68646b5bbbe6b1a7d89f1fbf7f775625bf824b'
start=original.index('    static void boundary(ServerPlayer p,boolean retainXp)throws IOException {')
end=original.index('    /** Guard',start)
method=original[start:end]
removed='        for(var pokemon:com.cobblemon.mod.common.Cobblemon.INSTANCE.getStorage().getParty(p))if(pokemon!=null)throw new IOException("A nonempty Pokemon party is not certified for jail death. Deposit the party in a PC first; no Pokemon were moved");\n'
body=once(method,removed,'')
body=once(body,'    static void boundary(ServerPlayer p,boolean retainXp)throws IOException {','    private static void playerOnlyBoundary(ServerPlayer p,boolean retainXp,Operation<Void> original)throws IOException {\n        if(!PlayerOnlyJail.enabled()){original.call(p,retainXp);return;}\n        if(p.server.getWorldData().isHardcore())throw new IOException("Hardcore player respawn is not supported");')
assert 'cobblemon' not in body and 'pokemon' not in body
write(Path('dev/unyielding/jail/PlayerOnlyAdmissionMixin.java'),'''package dev.unyielding.jail;
import java.io.IOException;
import java.util.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
@Mixin(value=JailAdmissions.class,remap=false)
public abstract class PlayerOnlyAdmissionMixin {
    @WrapMethod(method="boundary(Lnet/minecraft/server/level/ServerPlayer;Z)V")
'''+body+'}\n')
write(Path('dev/unyielding/jail/PlayerOnlyEnforcementMixin.java'),'''package dev.unyielding.jail;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(value=JailEnforcement.class,remap=false)
public abstract class PlayerOnlyEnforcementMixin {
 @Inject(method="enforce",at=@At("HEAD"),require=1)
 private static void outside(ServerPlayer p,CallbackInfo ci){PlayerOnlyJail.outside(p);}
}
''')
write(Path('dev/unyielding/jail/PlayerOnlyCommandMixin.java'),'''package dev.unyielding.jail;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.network.protocol.game.ServerboundClientCommandPacket;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class PlayerOnlyCommandMixin {
 @Shadow public ServerPlayer player;
 @Inject(method="handleClientCommand",at=@At("HEAD"),require=1)
 private void observe(ServerboundClientCommandPacket packet,CallbackInfo ci){PlayerOnlyJail.command(player,packet);}
}
''')
for name,package in [('PlayerOnlyJail','dev/unyielding/jail'),('PlayerOnlySmoke','dev/unyielding/bundlesmoke'),('PlayerOnlyClient','dev/unyielding/bundlesmoke')]:
 write(Path(package)/(name+'.java'),(R/'.github/player-only'/f'{name}.java').read_text())
# Retain the exact existing equipment/real-origin fixture helpers; do not run their old lifecycle driver.
p=src/'dev/unyielding/bundlesmoke/CertifiedJailSmoke.java';s=p.read_text()
s=once(s,'if(!Boolean.getBoolean("uq.certifiedJail"))return;','if(!Boolean.getBoolean("uq.certifiedJail")||Boolean.getBoolean("uq.playerOnly"))return;')
s=once(s,'new JailEquipmentPort(target,id,retired,true)','new JailEquipmentPort(target,id,retired,true,true)');p.write_text(s)
shutil.copy2(R/'.github/packed-wallet/PackedWalletProof.java',src/'dev/unyielding/bundlesmoke/PackedWalletProof.java')
config={'required':True,'minVersion':'0.8','package':'dev.unyielding.jail','compatibilityLevel':'JAVA_21','mixins':['PlayerOnlyAdmissionMixin','PlayerOnlyEnforcementMixin','PlayerOnlyCommandMixin'],'injectors':{'defaultRequire':1}}
(res/'player-only.mixins.json').write_text(json.dumps(config,indent=2))
p=res/'META-INF/neoforge.mods.toml';p.write_text(p.read_text()+'\n[[mixins]]\nconfig="player-only.mixins.json"\n')
for p in src.rglob('*.java'):
 assert p.name not in ['NativeFence.java','MutationGate.java','SessionBridge.java','PublicationGate.java'],p
runtime=(src/'dev/unyielding/jail/PlayerOnlyJail.java').read_text()
assert 'com.cobblemon' not in runtime and 'getParty(' not in runtime and 'getPC(' not in runtime
proof={'commit':os.environ['GITHUB_SHA'],'baseline_admissions_blob':'5b68646b5bbbe6b1a7d89f1fbf7f775625bf824b','removed_statement':removed.strip(),'other_admission_checks_preserved':True,'production_changed':False,'old_party_test_mods_loaded':False,'source_sha256':{str(p.relative_to(R)):hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted((R/'.github/player-only').glob('*')) if p.is_file()},'generated_sha256':{str(p.relative_to(src)):hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted((src/'dev/unyielding/jail').glob('PlayerOnly*.java'))}}
(d/'player-only-source.json').write_text(json.dumps(proof,indent=2)+'\n')
print('Prepared isolated player-only Jail test mod; production pair and certificates unchanged')
