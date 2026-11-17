package dev.unyielding.bundlesmoke;

import dev.unyielding.quests.net.Net;
import dev.unyielding.quests.settings.ConfigEditor;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import java.nio.file.*;
import java.util.*;

/** Real C2S config actions and native audit commands. Never shipped in the production JAR. */
@EventBusSubscriber(modid="uq_bundle_smoke",value=Dist.CLIENT)
public final class ClientChecks {
    static final Path ROOT=Path.of("/tmp/uq-config-audit-controls");
    static final List<String> messages=new ArrayList<>();
    static String name="",seen="";
    static String[] pending;
    static int tick,sentAt,generation;
    static Object oldPlayer;
    static void write(Path path,String value)throws Exception {
        Path temp=path.resolveSibling(path.getFileName()+".tmp");Files.writeString(temp,value);
        Files.move(temp,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
    }
    static void log(String value)throws Exception{Files.writeString(ROOT.resolve(name+".events.log"),value+"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);}
    @SubscribeEvent public static void chat(ClientChatReceivedEvent.System event){if(!event.isOverlay())messages.add(event.getMessage().getString());}
    static void begin(Minecraft mc,String[] a)throws Exception {
        messages.clear();mc.setScreen(null);
        switch(a[1]) {
            case "edit" -> {
                BundleSmoke.require(a.length==5,"Malformed native config action");
                log(a[0]+"\tnative ConfigEditor.Action\t"+a[2]+" expected="+a[3]+" value="+a[4]);
                Net.sendToServer(new ConfigEditor.Action(a[2],a[3],a[4]));
            }
            case "commands" -> {
                BundleSmoke.require(a.length==3,"Missing native command");
                for(String command:a[2].split(";")){log(a[0]+"\tnative command\t"+command);mc.getConnection().sendCommand(command);}
            }
            case "clear" -> {}
            default -> throw new IllegalArgumentException("Unknown config-audit fixture action "+a[1]);
        }
        Net.sendToServer(new BundleSmoke.Barrier(a[0]));
    }
    static void completed(Minecraft mc)throws Exception {
        var observation=new CompoundTag();observation.putString("screen",mc.screen==null?"none":mc.screen.getClass().getName());
        var text=new ListTag();for(String message:messages)text.add(StringTag.valueOf(message));observation.put("messages",text);
        write(ROOT.resolve(name+".observation."+pending[0]+".snbt"),observation.toString());
        log(pending[0]+"\t"+pending[1]+"\tordered server receipt; observation="+observation);
        write(ROOT.resolve(name+".ack"),pending[0]+"\tpassed");pending=null;
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if(!"true".equals(System.getenv("GITHUB_ACTIONS"))||!"github-hosted".equals(System.getenv("RUNNER_ENVIRONMENT")))return;
        var mc=Minecraft.getInstance();tick++;
        try {
            if(mc.player==null||mc.level==null)return;
            name=mc.player.getGameProfile().getName();if(!name.equals("Dev")&&!name.equals("Visitor"))return;
            if(oldPlayer!=mc.player){oldPlayer=mc.player;generation++;}
            if(mc.player.tickCount<100)return;
            Files.createDirectories(ROOT);write(ROOT.resolve(name+".ready"),mc.player.getUUID()+"\t"+generation);
            if(pending!=null) {
                BundleSmoke.require(tick-sentAt<600,"Config audit action did not receive an ordered reply");
                if(BundleSmoke.RECEIPTS.remove(pending[0]))completed(mc);
                return;
            }
            Path file=ROOT.resolve(name+".txt");if(!Files.exists(file))return;
            String value=Files.readString(file);if(value.equals(seen))return;
            String[] a=value.split("\t",-1);BundleSmoke.require(a.length>=2,"Malformed fixture command");
            seen=value;pending=a;sentAt=tick;begin(mc,a);
        } catch(Throwable bad) {
            bad.printStackTrace();
            try{String id=pending==null?"0":pending[0];log(id+"\tfailed\t"+bad);write(ROOT.resolve(name+".ack"),id+"\tfailed: "+bad);}catch(Exception ignored){}finally{pending=null;}
        }
    }
}
