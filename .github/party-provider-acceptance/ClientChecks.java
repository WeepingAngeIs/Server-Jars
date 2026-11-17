package dev.unyielding.bundlesmoke;

import dev.unyielding.quests.net.Net;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import java.nio.file.*;

/** Actual client command input. Never copied into the production JAR. */
@EventBusSubscriber(modid="uq_bundle_smoke",value=Dist.CLIENT)
public final class ClientChecks {
    static final Path ROOT=Path.of("/tmp/uq-meeting-provider-controls");
    static String name="",seen="";
    static String[] pending;
    static int tick,sentAt,reconnect,generation;
    static Object oldPlayer;
    static void write(Path path,String value)throws Exception {
        Path temp=path.resolveSibling(path.getFileName()+".tmp");Files.writeString(temp,value);
        Files.move(temp,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
    }
    static void log(String value)throws Exception{Files.writeString(ROOT.resolve(name+".events.log"),value+"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if(!"true".equals(System.getenv("GITHUB_ACTIONS"))||!"github-hosted".equals(System.getenv("RUNNER_ENVIRONMENT")))return;
        var mc=Minecraft.getInstance();tick++;
        try {
            if(reconnect<0) {
                Path resume=ROOT.resolve(name+".resume");if(!Files.exists(resume))return;
                Files.delete(resume);reconnect=20;
            }
            if(reconnect>0) {
                if(--reconnect==0)ConnectScreen.startConnecting(new TitleScreen(),mc,ServerAddress.parseString("127.0.0.1:25565"),new ServerData("Meeting contract fixture","127.0.0.1:25565",ServerData.Type.OTHER),false,null);
                return;
            }
            if(mc.player==null||mc.level==null)return;
            name=mc.player.getGameProfile().getName();if(!name.equals("Dev")&&!name.equals("Visitor"))return;
            if(oldPlayer!=mc.player){oldPlayer=mc.player;generation++;}
            if(mc.player.tickCount<100)return;
            Files.createDirectories(ROOT);write(ROOT.resolve(name+".ready"),mc.player.getUUID()+"\t"+generation);
            if(pending!=null) {
                BundleSmoke.require(tick-sentAt<600,"Client did not receive ordered command receipt");
                if(BundleSmoke.RECEIPTS.remove(pending[0])) {
                    log(pending[0]+"\t"+pending[1]+"\tserver receipt received");
                    write(ROOT.resolve(name+".ack"),pending[0]+"\tpassed");pending=null;
                }
                return;
            }
            Path file=ROOT.resolve(name+".txt");if(!Files.exists(file))return;
            String value=Files.readString(file);if(value.equals(seen))return;
            String[] a=value.split("\t",-1);BundleSmoke.require(a.length>=2,"Malformed fixture command");
            seen=value;pending=a;sentAt=tick;mc.setScreen(null);
            if(a[1].equals("commands")) {
                BundleSmoke.require(a.length==3,"Missing native command");
                for(String command:a[2].split(";")){log(a[0]+"\tnative command\t"+command);mc.getConnection().sendCommand(command);}
                Net.sendToServer(new BundleSmoke.Barrier(a[0]));
            } else if(a[1].equals("reconnect")) {
                log(a[0]+"\treconnect\tdisconnecting native client");
                Files.deleteIfExists(ROOT.resolve(name+".resume"));
                write(ROOT.resolve(name+".ack"),a[0]+"\tpassed");
                mc.getConnection().getConnection().disconnect(Component.literal("Disposable meeting setter reconnect"));
                reconnect=-1;pending=null;
            } else throw new IllegalArgumentException("Unknown fixture action "+a[1]);
        } catch(Throwable bad) {
            bad.printStackTrace();
            try{String id=pending==null?"0":pending[0];log(id+"\tfailed\t"+bad);write(ROOT.resolve(name+".ack"),id+"\tfailed: "+bad);}catch(Exception ignored){}finally{pending=null;}
        }
    }
}
