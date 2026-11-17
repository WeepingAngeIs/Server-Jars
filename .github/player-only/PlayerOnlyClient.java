package dev.unyielding.bundlesmoke;

import com.google.gson.GsonBuilder;
import java.nio.file.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Observation only: no keys, Respawn button, command packet, or client respawn call. */
@EventBusSubscriber(modid="uq_bundle_smoke",value=Dist.CLIENT)
public final class PlayerOnlyClient {
    static boolean dead,aliveAfter;static Object deadObject,last;static int replacements;static String previous="";
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        if(!Boolean.getBoolean("uq.playerOnly"))return;
        var mc=Minecraft.getInstance();var p=mc.player;if(p==null||mc.getConnection()==null)return;
        if(last!=null&&last!=p)replacements++;last=p;
        if(!p.isAlive()||p.getHealth()<=0){dead=true;deadObject=p;}
        else if(dead&&deadObject!=p)aliveAfter=true;
        var row=new LinkedHashMap<String,Object>();row.put("commit",System.getenv("GITHUB_SHA"));row.put("phase",Integer.getInteger("uq.playerOnlyPhase"));row.put("name",p.getGameProfile().getName());row.put("observed_dead",dead);row.put("alive_after_death",aliveAfter);row.put("current_alive",p.isAlive());row.put("replacement_objects",replacements);row.put("respawn_actions_sent",0);
        String text=new GsonBuilder().setPrettyPrinting().create().toJson(row);if(text.equals(previous))return;previous=text;
        try{Files.writeString(Path.of(System.getenv("GITHUB_WORKSPACE"),"dist/packaged-smoke/player-only-client-"+p.getGameProfile().getName()+"-"+Integer.getInteger("uq.playerOnlyPhase")+".json"),text);}catch(Exception failure){throw new IllegalStateException("Client observation write failed",failure);}
    }
}
