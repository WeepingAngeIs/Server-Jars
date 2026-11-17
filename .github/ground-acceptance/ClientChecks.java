package dev.unyielding.bundlesmoke;

import dev.unyielding.quests.net.Net;
import dev.unyielding.quests.settings.GroundSettings;
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

/** Actual loopback client actions; never packaged in the production JAR. */
@EventBusSubscriber(modid="uq_bundle_smoke",value=Dist.CLIENT)
public final class ClientChecks {
    static final Path ROOT=Path.of("/tmp/uq-ground-controls");
    static String seen="",name="";
    static int reconnect;
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        if(!"true".equals(System.getenv("GITHUB_ACTIONS"))||!"github-hosted".equals(System.getenv("RUNNER_ENVIRONMENT")))return;
        var mc=Minecraft.getInstance();String token="0";
        try{
            if(reconnect>0){if(--reconnect==0)ConnectScreen.startConnecting(new TitleScreen(),mc,ServerAddress.parseString("127.0.0.1:25565"),new ServerData("Ground fixture","127.0.0.1:25565",ServerData.Type.OTHER),false,null);return;}
            if(mc.player==null)return;name=mc.player.getGameProfile().getName();
            if(!name.equals("Dev")&&!name.equals("Visitor"))return;
            Path file=ROOT.resolve(name+".txt");if(!Files.exists(file))return;
            String value=Files.readString(file);if(value.equals(seen))return;
            String[] a=value.split("\t",-1);token=a[0];mc.setScreen(null);
            if(a[1].equals("drop_all")){
                String held=mc.player.getMainHandItem().getHoverName().getString();int count=mc.player.getMainHandItem().getCount();
                boolean ready=held.equals(a[2])&&count==Integer.parseInt(a[3])&&mc.player.distanceToSqr(Double.parseDouble(a[4]),Double.parseDouble(a[5]),Double.parseDouble(a[6]))<.25;
                Files.writeString(ROOT.resolve(name+".status"),"token="+token+" position="+mc.player.position()+" held="+held+" count="+count+" expected="+a[2]+"/"+a[3]+" ready="+ready);
                if(!ready)return;
            }
            seen=value;
            if(a[1].startsWith("ground_"))Net.sendToServer(new GroundSettings.Action(a[1].substring(7),Integer.parseInt(a[2])));
            else if(a[1].equals("drop_all")){if(!mc.player.drop(true))throw new IllegalStateException("native drop returned false");}
            else if(a[1].equals("reconnect")){mc.getConnection().getConnection().disconnect(Component.literal("Disposable ground reconnect"));reconnect=60;}
            else throw new IllegalArgumentException("Unknown ground action "+a[1]);
            Files.writeString(ROOT.resolve(name+".ack"),token+"\tpassed");
        }catch(Throwable bad){bad.printStackTrace();try{Files.writeString(ROOT.resolve(name+".ack"),token+"\tfailed: "+bad);}catch(Exception ignored){}}
    }
}
