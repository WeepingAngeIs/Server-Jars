package dev.unyielding.bundlesmoke;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.google.gson.GsonBuilder;
import dev.unyielding.jail.JailData;
import dev.unyielding.jail.JailIntegrated;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import static dev.unyielding.bundlesmoke.PlayerOnlySmoke.*;

/** Hosted-only observations around unchanged production JARs and one real client. */
final class SwapPreflight {
    private SwapPreflight() {}

    static void run(ServerPlayer player) throws Exception {
        String variant=System.getenv("UQ_SPAWN_VARIANT");
        require("baseline".equals(variant)||"spawn".equals(variant),"Unknown swap variant");
        Custody249Proof.runtime();
        var counts=spawnCounts(player,variant);
        CenterBuildProof.place(player,player);
        player.server.getPlayerList().deop(player.getGameProfile());
        VanillaCustodySmoke.seed(player);
        player.getInventory().selected=2;
        PackedWalletProof.seed(player);
        CombinedStorageProof.seed(player);
        CertifiedJailSmoke.flush();
        CertifiedJailSmoke.saved();
        require(cmd("jailcustody init")==1 && cmd("jailintegrated init")==1,"Cannot initialize disposable custody");
        require(cmd("jailintegrated mode supported")==1,"Cannot enable bounded admissions");

        var inventory=player.getInventory().save(new ListTag()).copy();
        int selected=player.getInventory().selected;
        require(cmd("jailintegrated check Visitor")==0,"Unapproved roster unexpectedly passed the actual preflight command");
        require(inventory.equals(player.getInventory().save(new ListTag())) && selected==player.getInventory().selected,
                "Preflight changed the synthetic inventory");
        Custody249Proof.refused(player,"unapproved_spawn_roster");
        require(JailData.get(player.server).assignment(player.getUUID())==null &&
                JailIntegrated.activeTransaction(player.server,player.getUUID())==null,"Refusal opened custody");

        var method=Class.forName("dev.unyielding.jail.JailHumanAudit").getDeclaredMethod("runtime",ServerPlayer.class);
        method.setAccessible(true);
        String reason;
        try { method.invoke(null,player); throw new IllegalStateException("Unapproved runtime unexpectedly certified"); }
        catch (InvocationTargetException exception) {
            Throwable cause=exception.getTargetException();
            require(cause instanceof IOException,"Unexpected runtime refusal type");
            reason=String.valueOf(cause.getMessage());
        }
        require(reason.matches("Uncertified NeoForge 21\\.1\\.249 server file-set SHA-256 [0-9a-f]{64}"),
                "Refusal was not the expected complete-roster check: "+reason);
        var report=new LinkedHashMap<String,Object>();
        report.put("variant",variant);
        report.put("real_connected_player",player.connection.player==player && !player.hasDisconnected());
        report.put("actual_neoforge_21_1_249",true);
        report.put("actual_preflight_command_refused",true);
        report.put("actual_admission_command_refused",true);
        report.put("inventory_and_custody_unchanged",true);
        report.put("roster_refusal",reason);
        report.put("spawn_counts",counts);
        report.put("jail_sha256",System.getenv("UQ_JAIL_SHA256"));
        report.put("companion_sha256",System.getenv("UQ_BUNDLE_SHA256"));
        report.put("commit",System.getenv("GITHUB_SHA"));
        Files.writeString(results.resolve("swap-preflight-"+variant+".json"),new GsonBuilder().setPrettyPrinting().create().toJson(report));
        pass();
    }

    private static Map<String,Object> spawnCounts(ServerPlayer player,String variant) throws Exception {
        var results=new LinkedHashMap<String,Object>();
        player.server.getPlayerList().op(player.getGameProfile());
        try {
            measure(player,results,"single","pokespawn lopunny",1,0);
            measure(player,results,"count_five","pokespawn lopunny 5",variant.equals("spawn")?5:-1,0);
            measure(player,results,"alias_five","spawnpokemon lopunny 5",variant.equals("spawn")?5:-1,0);
            measure(player,results,"level_three","pokespawn lopunny level=5 3",variant.equals("spawn")?3:-1,5);
        } finally { player.server.getPlayerList().deop(player.getGameProfile()); }
        require(!player.hasPermissions(2),"Synthetic player remained an administrator");
        return results;
    }

    private static void measure(ServerPlayer player,Map<String,Object> out,String label,String command,int expected,int level) {
        Map<UUID,PokemonEntity> before=pokemon(player);
        var origin=player.blockPosition();
        var pos=new Vec3(origin.getX()+0.5,origin.getY()+5+out.size()*3,origin.getZ()+0.5);
        int result=0;String error="";
        try {
            result=player.server.getCommands().getDispatcher().execute(command,player.createCommandSourceStack().withPosition(pos));
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException failure) { error=failure.getClass().getSimpleName(); }
        Map<UUID,PokemonEntity> added=pokemon(player);
        added.keySet().removeAll(before.keySet());
        if(expected>=0)require(added.size()==expected && result==expected,"Unexpected spawn result for "+command+": "+added.size()+"/"+result);
        for(PokemonEntity entity:added.values()) {
            require(entity.getPokemon().getSpecies().getResourceIdentifier().getPath().equals("lopunny"),"Wrong spawned species");
            if(level>0)require(entity.getPokemon().getLevel()==level,"Wrong spawned level");
        }
        out.put(label,Map.of("command",command,"result",result,"new_distinct_entities",added.size(),"error",error));
    }

    private static Map<UUID,PokemonEntity> pokemon(ServerPlayer player) {
        Map<UUID,PokemonEntity> found=new HashMap<>();
        for(var entity:player.serverLevel().getAllEntities())
            if(entity instanceof PokemonEntity pokemon)found.put(entity.getUUID(),pokemon);
        return found;
    }
}
