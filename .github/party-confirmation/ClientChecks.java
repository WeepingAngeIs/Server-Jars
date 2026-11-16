package dev.unyielding.bundlesmoke;

import dev.unyielding.quests.client.ClientData;
import dev.unyielding.quests.client.TeamScreen;
import dev.unyielding.quests.net.Net;
import dev.unyielding.quests.team.Team;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
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
import java.util.UUID;

@EventBusSubscriber(modid="uq_bundle_smoke",value=Dist.CLIENT)
public final class ClientChecks {
    static String seen="",name="";static int reconnect;
    @SubscribeEvent public static void tick(ClientTickEvent.Post e) {
        if(!"true".equals(System.getenv("GITHUB_ACTIONS")))return;
        var mc=Minecraft.getInstance();
        try {
            if(reconnect>0){if(--reconnect==0)ConnectScreen.startConnecting(new TitleScreen(),mc,ServerAddress.parseString("127.0.0.1:25565"),new ServerData("Fixture","127.0.0.1:25565",ServerData.Type.OTHER),false,null);return;}
            if(mc.player==null)return;name=mc.player.getGameProfile().getName();
            var path=Path.of("/tmp/uq-party-controls/"+name+".txt");if(!Files.exists(path))return;
            String text=Files.readString(path);if(text.equals(seen))return;seen=text;String[] a=text.split("\t",-1);
            switch(a[1]){
                case "ui_transfer","ui_disband"->{var screen=new TeamScreen();mc.setScreen(screen);var method=TeamScreen.class.getDeclaredMethod("confirmPartyAction",Team.class,UUID.class);method.setAccessible(true);method.invoke(screen,ClientData.myTeam(),a[1].equals("ui_disband")?null:UUID.fromString(a[2]));}
                case "ui_yes","ui_no"->{String wanted=a[1].equals("ui_yes")?"Yes":"No";var button=mc.screen.children().stream().filter(x->x instanceof Button b&&b.getMessage().getString().equals(wanted)).map(x->(Button)x).findFirst().orElseThrow();button.onPress();}
                case "reconnect"->{mc.getConnection().getConnection().disconnect(Component.literal("Disposable reconnect fixture"));reconnect=60;}
                default->Net.sendToServer(new Net.TeamAction(a[1],a[2],a[3]));
            }
            Files.writeString(Path.of("/tmp/uq-party-controls/"+name+".ack"),a[0]);
        }catch(Throwable bad){bad.printStackTrace();try{Files.writeString(Path.of("/tmp/uq-party-confirmation-result"),"failed: client "+name+": "+bad+"\n");}catch(Exception ignored){}}
    }
}
