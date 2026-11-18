package dev.unyielding.bundlesmoke;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.CommonComponents;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Performs normal first-join onboarding before custody; observes screens thereafter. */
@EventBusSubscriber(modid="uq_bundle_smoke", value=Dist.CLIENT)
public final class FullPackClientWatch {
    private static String previous="";
    private static long next;
    private static final Set<String> chosen=new LinkedHashSet<>();
    private static int titleTicks,clearTicks;
    private static boolean joined,tutorial,complete,declinedXaero;
    private FullPackClientWatch() {}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) throws Exception {
        if (!Boolean.getBoolean("uq.playerOnly") || !"github-hosted".equals(System.getenv("RUNNER_ENVIRONMENT"))) return;
        var game=Minecraft.getInstance();
        String screen=game.screen==null?"none":game.screen.getClass().getName();
        int phase=Integer.getInteger("uq.playerOnlyPhase");
        Path folder=Path.of(System.getenv("GITHUB_WORKSPACE"),"dist/packaged-smoke");
        if (!joined && game.level==null && game.player==null && game.getConnection()==null
                && !declinedXaero && screen.equals("xaero.lib.client.gui.GuiUpdateAll")) {
            // This is Xaero's stock ConfirmScreen. Only No declines both its
            // updater and external mod pages without changing update settings.
            for (var widget:game.screen.children()) {
                if (widget instanceof Button no && no.visible && no.active && CommonComponents.GUI_NO.equals(no.getMessage())) {
                    declinedXaero=true;
                    System.out.println("FULL249_ONBOARDING declined pre-title Xaero update offer");
                    no.onPress();
                    break;
                }
            }
        }
        if (!joined && game.screen instanceof TitleScreen title && game.getOverlay()==null) {
            if (++titleTicks>=60) {
                joined=true;
                String target="127.0.0.1:25565";
                ConnectScreen.startConnecting(title,game,ServerAddress.parseString(target),
                        new ServerData("Disposable pack test",target,ServerData.Type.OTHER),false,null);
            }
        } else titleTicks=0;
        if (phase==1 && !complete && game.player!=null && game.getConnection()!=null
                && Files.exists(folder.resolve("full249-onboarding-start.txt"))) {
            if (screen.equals("com.cyberday1.neoorigins.screen.OriginSelectionScreen")) {
                Object picker=game.screen;
                var presenterField=picker.getClass().getDeclaredField("presenter");
                presenterField.setAccessible(true);
                Object presenter=presenterField.get(picker);
                Object layer=presenter.getClass().getMethod("currentLayer").invoke(presenter);
                if (layer!=null) {
                    String id=layer.getClass().getMethod("id").invoke(layer).toString();
                    String choice=switch(id) {
                        case "neoorigins:origin" -> "neoorigins:human";
                        case "neoorigins:class" -> "neoorigins:class_warrior";
                        default -> throw new IllegalStateException("Unexpected native origin layer "+id);
                    };
                    var wanted=ResourceLocation.parse(choice);
                    if (!((List<?>)presenter.getClass().getMethod("allOriginIds").invoke(presenter)).contains(wanted))
                        throw new IllegalStateException("The pack's picker did not offer "+wanted);
                    if (chosen.add(choice)) {
                        var select=picker.getClass().getDeclaredMethod("selectOrigin",ResourceLocation.class);
                        var confirm=picker.getClass().getDeclaredMethod("confirmSelection");
                        select.setAccessible(true);
                        confirm.setAccessible(true);
                        select.invoke(picker,wanted);
                        confirm.invoke(picker);
                        System.out.println("FULL249_ONBOARDING confirmed "+choice);
                    }
                }
            } else if (!tutorial && chosen.size()==2 && screen.equals("dev.unyielding.quests.client.tutorial.TutorialScreen")) {
                tutorial=true;
                game.screen.onClose(); // Normal Later button calls this same handler.
                System.out.println("FULL249_ONBOARDING dismissed first-join tutorial");
            }
            clearTicks=tutorial && screen.equals("none") && game.getOverlay()==null && !game.isPaused()?clearTicks+1:0;
            if (clearTicks>=20) {
                complete=true;
                Files.writeString(folder.resolve("full249-onboarding-complete.txt"),
                        "native picker confirms="+chosen+"; tutorial Later=1; no further UI actions\n");
            }
        }
        String overlay=game.getOverlay()==null?"none":game.getOverlay().getClass().getName();
        String status="screen="+screen+" world="+(game.level!=null)+" player="+(game.player!=null)+" connection="+(game.getConnection()!=null)+" overlay="+overlay;
        if (PlayerOnlyClient.killPackets>0)
            status+=" kill_packets="+PlayerOnlyClient.killPackets+" respawn_packets="+PlayerOnlyClient.respawnPackets+
                    " clear_ticks="+Math.min(20,PlayerOnlyClient.clearTicks)+" alive="+(game.player!=null && game.player.isAlive());
        long now=System.nanoTime();
        if (status.equals(previous) && now<next) return;
        next=now+20_000_000_000L;
        previous=status;
        Path report=folder.resolve("client249-phase"+phase+"-status.txt");
        Files.writeString(report,status+"\n");
        System.out.println("FULL249_CLIENT "+status);
    }
}
