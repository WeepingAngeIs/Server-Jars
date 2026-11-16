"""Prepare independent native reference fixtures, never production changes."""
import os
from pathlib import Path
import subprocess
root=Path(__file__).resolve().parents[2]
assert os.environ.get('GITHUB_ACTIONS')=='true' and os.environ.get('RUNNER_ENVIRONMENT')=='github-hosted'
subprocess.run(['python3','.github/party-wallet/prepare.py'],cwd=root,check=True)
source=root/'dist/packaged-harness/src/main/java/dev/unyielding/bundlesmoke'
p=source/'PartyWalletProbe.java';s=p.read_text()
needle='        if(!Boolean.getBoolean("uq.partyWalletProbe")||finished)return;'
assert s.count(needle)==1
s=s.replace(needle,needle+'\n        if(Boolean.getBoolean("uq.nativeResourceProbe"))return;',1)
p.write_text(s.replace('private static','static'))
s=(root/'.github/resource-native/NativeResourceProbe.java').read_text()
old='player.teleportTo(player.serverLevel(),1000.5,5,1000.5,0,0);admin.teleportTo(admin.serverLevel(),1200.5,5,1000.5,0,0);admin.setInvulnerable(true);'
new='''int y=player.serverLevel().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING,1000,1000);
                player.teleportTo(player.serverLevel(),1000.5,y,1000.5,0,0);admin.teleportTo(admin.serverLevel(),1016.5,y,1000.5,0,0);admin.setInvulnerable(true);'''
assert s.count(old)==1;s=s.replace(old,new,1)
old='                player.getInventory().clearContent();player.setInvulnerable(false);player.setHealth(player.getMaxHealth());'
new='''                // Use the already audited selected Human/Warrior synthetic fixture,
                // rather than an unselected-origin newcomer. No jail admission occurs.
                CertifiedJailSmoke.admin=admin;CertifiedJailSmoke.target=player;CertifiedJailSmoke.world=world;CertifiedJailSmoke.seed();
                require(PartyWalletProbe.command("jailcustody init")==1&&PartyWalletProbe.command("jailintegrated init")==1,"Empty control stores were not initialized");
                require(JailData.get(admin.server).assignment(owner)==null&&!JailCustodyService.quarantined(player),"Native reference unexpectedly admitted");
                report.put("profile","unjailed selected Human plus Warrior; initialized empty jail stores; no custody transaction");
                player.getInventory().clearContent();player.setInvulnerable(false);player.setHealth(player.getMaxHealth());'''
assert s.count(old)==1;s=s.replace(old,new,1)
old='player.hurt(player.damageSources().generic(),1000F);'
new='''report.put("death_trigger","vanilla /kill on explicitly unjailed synthetic player");
                require(PartyWalletProbe.command("kill Visitor")==1,"Vanilla kill command failed");
                int after=player.getStats().getValue(net.minecraft.stats.Stats.CUSTOM.get(net.minecraft.stats.Stats.DEATHS));
                String diagnostic="alive="+player.isAlive()+",health="+player.getHealth()+",deathsBefore="+deaths+",deathsAfter="+after+",invulnerable="+player.isInvulnerable()+",abilityInvulnerable="+player.getAbilities().invulnerable+",protectedCustody="+JailAdmissions.protectedCustody(player);
                report.put("death_diagnostic",diagnostic);
                Files.writeString(Path.of(System.getenv("GITHUB_WORKSPACE"),"dist/packaged-smoke/native-"+kind+"-death-diagnostic.txt"),diagnostic);'''
assert s.count(old)==1;s=s.replace(old,new,1)
old='    static boolean started,waiting,finished;'
assert s.count(old)==1;s=s.replace(old,old+'\n    static int readyTicks;',1)
old='if(admin==null||player==null||event.getServer().getTickCount()<120||player.tickCount<100)return;'
new='''if(admin==null||player==null||event.getServer().getTickCount()<120)return;
            if(++readyTicks<100)return;'''
assert s.count(old)==1;s=s.replace(old,new,1)
(source/'NativeResourceProbe.java').write_text(s)
