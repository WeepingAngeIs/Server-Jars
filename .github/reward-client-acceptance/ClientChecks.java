package dev.unyielding.bundlesmoke;

import dev.unyielding.quests.client.LuckySpinScreen;
import dev.unyielding.quests.net.Net;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import java.nio.file.*;
import java.util.*;

/** Real loopback client input. This fixture is never packaged in the production JAR. */
@EventBusSubscriber(modid="uq_bundle_smoke",value=Dist.CLIENT)
public final class ClientChecks {
    static final Path ROOT=Path.of("/tmp/uq-reward-client-controls");
    static String seen="",name="";
    static String[] pending;
    static int ticks,sentAt,reconnect,connectionGeneration;
    static Object lastPlayer;
    static void require(boolean ok,String message){BundleSmoke.require(ok,message);}
    static Path tokenFile(String player,String label){return ROOT.resolve(player+".token."+label);}
    static void log(String message)throws Exception{Files.writeString(ROOT.resolve(name+".events.log"),message+"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);}
    static void write(Path path,String value)throws Exception {
        Path temp=path.resolveSibling(path.getFileName()+".tmp");Files.writeString(temp,value);Files.move(temp,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
    }
    static Object field(Object owner,String name)throws Exception {
        var f=owner.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(owner);
    }
    static void finish(String detail)throws Exception {
        String token=pending[0];log(token+"\t"+pending[1]+"\t"+detail);
        write(ROOT.resolve(name+".ack"),token+"\tpassed");pending=null;
    }
    static void sendSpin(String token) {
        var tag=new CompoundTag();tag.putString("action","spin");tag.putString("token",token);
        Net.sendToServer(new Net.SpinMenuAction(tag));
    }
    static void begin(Minecraft mc,String[] a)throws Exception {
        switch(a[1]) {
            case "status" -> {
                mc.setScreen(null);mc.setScreen(new LuckySpinScreen(null));
                require(mc.screen instanceof LuckySpinScreen,"Native Lucky Spin screen did not open");
                // init() sends the real status request. Never mint a successful token here.
            }
            case "spin" -> {
                String token=Files.readString(tokenFile(a[2],a[3]));require(!token.isEmpty(),"Missing captured token");
                mc.setScreen(null);for(int i=0;i<Integer.parseInt(a[4]);i++)sendSpin(token);
            }
            case "literal" -> {
                mc.setScreen(null);for(int i=0;i<Integer.parseInt(a[3]);i++)sendSpin(a[2]);
            }
            case "click", "click_close" -> {
                require(mc.screen instanceof LuckySpinScreen,"Native Lucky Spin screen was closed before click");
                var button=mc.screen.children().stream().filter(c->c instanceof Button b&&b.getMessage().getString().equals("Spin")).map(c->(Button)c).findFirst().orElseThrow();
                require(button.active,"Native Spin button is disabled");button.onPress();
            }
            case "claims" -> {
                mc.setScreen(null);
                for(String pair:a[2].split(";")){String[] fields=pair.split(",");Net.sendToServer(new Net.Claim(Long.parseLong(fields[0]),Integer.parseInt(fields[1])));}
            }
            case "reconnect" -> {
                log(a[0]+"\treconnect\tdisconnecting native client");
                write(ROOT.resolve(name+".ack"),a[0]+"\tpassed");
                mc.getConnection().getConnection().disconnect(Component.literal("Disposable reward token reconnect"));
                reconnect=60;pending=null;return;
            }
            default -> throw new IllegalArgumentException("Unknown reward client action "+a[1]);
        }
        // Same connection and main-thread handler order as the action above. The server
        // records receipt and replies AFTER production handling; its reply is also ordered
        // after all production response packets, avoiding a stale state capture on re-open.
        Net.sendToServer(new BundleSmoke.Barrier(a[0]));
    }
    static void completed(Minecraft mc)throws Exception {
        String[] a=pending;
        if(a[1].equals("status")) {
            require(mc.screen instanceof LuckySpinScreen,"Native status screen disappeared");
            var screen=(LuckySpinScreen)mc.screen;
            require(!(boolean)field(screen,"waiting"),"Receipt arrived before native status response");
            var state=((CompoundTag)field(screen,"state")).copy();String token=state.getString("token");
            require(!token.isEmpty(),"Server response has no token");
            require(field(screen,"result")==null,"Status request unexpectedly delivered a prize");
            var button=(Button)field(screen,"spin");state.putBoolean("fixtureButtonActive",button.active);
            state.putString("fixtureScreen",screen.getClass().getName());
            write(tokenFile(name,a[2]),token);
            write(ROOT.resolve(name+".screen."+a[2]+".snbt"),state.toString());
            finish("native screen state="+state);
        } else if(a[1].equals("click")||a[1].equals("click_close")) {
            require(mc.screen instanceof LuckySpinScreen,"Native reel screen disappeared");
            var screen=(LuckySpinScreen)mc.screen;
            require(field(screen,"result")!=null,"Accepted native click produced no reel result");
            require(!(boolean)field(screen,"waiting"),"Native click did not receive refreshed balance");
            long age=Util.getMillis()-(long)field(screen,"started");
            if(a[1].equals("click_close")) {
                require(age>=0&&age<4500,"Reel already finished before early close: "+age);
                screen.onClose();require(!(mc.screen instanceof LuckySpinScreen),"Native Back/close left the reel open");
            }
            finish("native reel received; closed="+a[1].equals("click_close")+"; age_ms="+age);
        } else finish("ordered server receipt received");
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if(!"true".equals(System.getenv("GITHUB_ACTIONS"))||!"github-hosted".equals(System.getenv("RUNNER_ENVIRONMENT")))return;
        var mc=Minecraft.getInstance();ticks++;
        try {
            if(reconnect>0) {
                if(--reconnect==0)ConnectScreen.startConnecting(new TitleScreen(),mc,ServerAddress.parseString("127.0.0.1:25565"),new ServerData("Reward fixture","127.0.0.1:25565",ServerData.Type.OTHER),false,null);
                return;
            }
            if(mc.player==null||mc.level==null)return;
            name=mc.player.getGameProfile().getName();if(!name.equals("Dev")&&!name.equals("Visitor"))return;
            if(mc.player!=lastPlayer){lastPlayer=mc.player;connectionGeneration++;}
            if(mc.player.tickCount<100)return;
            Files.createDirectories(ROOT);
            write(ROOT.resolve(name+".ready"),mc.player.getUUID()+"\t"+connectionGeneration);
            if(pending!=null) {
                require(ticks-sentAt<400,"Client action timed out waiting for ordered server reply");
                if(BundleSmoke.RECEIPTS.remove(pending[0]))completed(mc);
                return;
            }
            Path file=ROOT.resolve(name+".txt");if(!Files.exists(file))return;
            String value=Files.readString(file);if(value.equals(seen))return;
            String[] a=value.split("\t",-1);require(a.length>=2,"Malformed fixture command");
            seen=value;pending=a;sentAt=ticks;begin(mc,a);
        } catch(Throwable bad) {
            bad.printStackTrace();
            try {
                String token=pending==null?"0":pending[0];log(token+"\tfailed\t"+bad);
                write(ROOT.resolve(name+".ack"),token+"\tfailed: "+bad);
            } catch(Exception ignored) {} finally {pending=null;}
        }
    }
}
