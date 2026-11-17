package dev.unyielding.bundlesmoke;

import dev.unyielding.quests.client.DestinationScreen;
import dev.unyielding.quests.net.Net;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import java.nio.file.*;
import java.util.*;

/** Real destination menus and native commands. This class is never shipped in the release JAR. */
@EventBusSubscriber(modid="uq_bundle_smoke",value=Dist.CLIENT)
public final class ClientChecks {
    static final Path ROOT=Path.of("/tmp/uq-destination-controls");
    static final Map<String,DestinationScreen> screens=new HashMap<>();
    static String name="",seen="";
    static String[] pending;
    static int tick,sentAt,generation;
    static Object oldPlayer;
    static void write(Path path,String value)throws Exception {
        Path temp=path.resolveSibling(path.getFileName()+".tmp");Files.writeString(temp,value);
        Files.move(temp,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
    }
    static void log(String value)throws Exception{Files.writeString(ROOT.resolve(name+".events.log"),value+"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);}
    static CompoundTag state(DestinationScreen screen)throws Exception {
        var field=DestinationScreen.class.getDeclaredField("data");field.setAccessible(true);
        return ((CompoundTag)field.get(screen)).copy();
    }
    static void begin(Minecraft mc,String[] a)throws Exception {
        switch(a[1]) {
            case "list" -> {
                BundleSmoke.require(a.length==4,"Malformed native destination list action");
                mc.setScreen(null);
                String command=a[2].equals("home")?"listhomes":a[2].equals("warp")?"listwarps":null;
                BundleSmoke.require(command!=null,"Unknown destination kind");
                log(a[0]+"\tnative list command\t"+command);mc.getConnection().sendCommand(command);
            }
            case "inspect" -> {
                BundleSmoke.require(a.length==4&&mc.screen instanceof DestinationScreen,"Expected existing native destination screen");
            }
            case "clear" -> mc.setScreen(null);
            case "absent" -> BundleSmoke.require(!(mc.screen instanceof DestinationScreen),"Received an unrequested destination screen for another player");
            case "click" -> {
                BundleSmoke.require(a.length==4,"Malformed native destination click action");
                var screen=screens.get(a[2]);BundleSmoke.require(screen!=null,"No captured native screen "+a[2]);
                BundleSmoke.require(mc.screen==screen,"The captured native destination menu is no longer open");
                var data=state(screen);BundleSmoke.require(data.getBoolean("allowed"),"Captured screen was not allowed before the controlled change");
                var button=screen.children().stream().filter(c->c instanceof Button b&&b.getMessage().getString().equals(a[3])).map(c->(Button)c).findFirst().orElseThrow();
                BundleSmoke.require(button.active,"Native destination button is disabled");
                log(a[0]+"\tnative row button\t"+data.getString("kind")+" "+a[3]+"; active="+button.active);
                button.onPress();
            }
            case "commands" -> {
                BundleSmoke.require(a.length==3,"Missing native command");mc.setScreen(null);
                for(String command:a[2].split(";")){log(a[0]+"\tnative command\t"+command);mc.getConnection().sendCommand(command);}
            }
            default -> throw new IllegalArgumentException("Unknown destination fixture action "+a[1]);
        }
        Net.sendToServer(new BundleSmoke.Barrier(a[0]));
    }
    static void completed(Minecraft mc)throws Exception {
        if(pending[1].equals("absent"))BundleSmoke.require(!(mc.screen instanceof DestinationScreen),"Ordered receipt followed an unrequested destination screen");
        if(pending[1].equals("list")||pending[1].equals("inspect")) {
            BundleSmoke.require(mc.screen instanceof DestinationScreen,"Native destination screen did not open");
            var screen=(DestinationScreen)mc.screen;var data=state(screen);
            BundleSmoke.require(data.getString("kind").equals(pending[2]),"Native destination kind changed");
            screens.put(pending[3],screen);data.putString("fixtureScreen",screen.getClass().getName());
            write(ROOT.resolve(name+".screen."+pending[3]+".snbt"),data.toString());
            log(pending[0]+"\tnative screen state\t"+data);
        }
        log(pending[0]+"\t"+pending[1]+"\tordered server receipt received");
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
                BundleSmoke.require(tick-sentAt<600,"Native destination action did not receive an ordered reply");
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
