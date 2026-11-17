"""Scope native kill damage to the already-journaled player escape; no creature hooks."""
import hashlib,json,os,shutil
from pathlib import Path
R=Path(__file__).resolve().parents[2]
assert os.environ.get('GITHUB_ACTIONS')=='true' and os.environ.get('RUNNER_ENVIRONMENT')=='github-hosted'
S=R/'dist/packaged-harness/src/main/java';P=S/'dev/unyielding/playeronly'
p=P/'PlayerOnlyAccess.java';s=p.read_text();needle=' public static void escape(ServerPlayer p){'
assert s.count(needle)==1
s=s.replace(needle,' public static boolean ownDamage(ServerPlayer p)throws IOException{return (boolean)call(JailEscapeCustody.class,"ownDamage",new Class<?>[]{ServerPlayer.class},p);}\n'+needle,1);p.write_text(s)
(P/'mixin/PlayerOnlyEscapeDamageMixin.java').write_text('''package dev.unyielding.playeronly.mixin;
import dev.unyielding.jail.JailEscapeCustody;
import dev.unyielding.playeronly.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
/** Only the single verified native player escape hit; normal damage elsewhere is unchanged. */
@Mixin(value=JailEscapeCustody.class,remap=false)
public abstract class PlayerOnlyEscapeDamageMixin {
 @WrapOperation(method="attempt",at=@At(value="INVOKE",target="Lnet/minecraft/server/level/ServerPlayer;hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z",remap=true),require=1)
 private static boolean escapeHit(ServerPlayer player,DamageSource source,float amount,Operation<Boolean> original){
  if(!PlayerOnlyJail.enabled())return original.call(player,source,amount);
  try{
   if(player.server==null||!player.server.isSameThread()||!PlayerOnlyAccess.ownDamage(player)||amount!=1000.0F)throw new IllegalStateException("Not the bound native escape hit");
   // Native kill damage handles vanilla respawn grace/cooldown. Existing preflight still
   // refuses creative/invulnerable/unsafe profiles; NeoForge hurt/death cancellation runs.
   System.out.println("JAIL_PLAYER_ESCAPE_DAMAGE player="+player.getUUID()+" cooldown="+player.invulnerableTime+" source=generic_kill");
   return original.call(player,player.damageSources().genericKill(),amount);
  }catch(java.io.IOException failure){throw new IllegalStateException("Cannot verify native owned escape damage",failure);}
 }
}
''')
p=R/'dist/packaged-harness/src/main/resources/player-only.mixins.json';c=json.loads(p.read_text());c['mixins'].append('PlayerOnlyEscapeDamageMixin');p.write_text(json.dumps(c,indent=2))
p=S/'dev/unyielding/bundlesmoke/PlayerOnlyClient.java';s=p.read_text();needle='row.put("alive_after_death",aliveAfter);';assert s.count(needle)==1
s=s.replace(needle,needle+'row.put("death_screen_cleared",aliveAfter&&!(mc.screen instanceof net.minecraft.client.gui.screens.DeathScreen));',1);p.write_text(s)
p=S/'dev/unyielding/bundlesmoke/PlayerOnlySmoke.java';s=p.read_text();needle='if(!Files.exists(report)||!com.google.gson.JsonParser.parseString(Files.readString(report)).getAsJsonObject().get("alive_after_death").getAsBoolean())return;';assert s.count(needle)==1
s=s.replace(needle,needle+'\n        if(!com.google.gson.JsonParser.parseString(Files.readString(report)).getAsJsonObject().get("death_screen_cleared").getAsBoolean())return;',1);p.write_text(s)
p=R/'dist/packaged-smoke/player-only-source.json';proof=json.loads(p.read_text());proof['generated_sha256']={str(p.relative_to(S)):hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted(P.rglob('*.java'))};proof['owned_escape_damage']='native generic_kill, ordinary death cancellation retained';p.write_text(json.dumps(proof,indent=2)+'\n')
shutil.copytree(P,R/'dist/player-only-source/generated-adapter',dirs_exist_ok=True)
print('Bound native escape hit uses kill damage; player checks and death cancellation remain, death-screen clearing observed')
