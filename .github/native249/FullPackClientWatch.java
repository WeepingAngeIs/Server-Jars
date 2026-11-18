package dev.unyielding.bundlesmoke;

import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Observation-only readiness and screen diagnostics from the synthetic full-pack client. */
@EventBusSubscriber(modid="uq_bundle_smoke", value=Dist.CLIENT)
public final class FullPackClientWatch {
    private static String previous="";
    private static long next;
    private FullPackClientWatch() {}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) throws Exception {
        if (!Boolean.getBoolean("uq.playerOnly") || !"github-hosted".equals(System.getenv("RUNNER_ENVIRONMENT"))) return;
        var game=Minecraft.getInstance();
        String screen=game.screen==null?"none":game.screen.getClass().getName();
        String status="screen="+screen+" world="+(game.level!=null)+" player="+(game.player!=null)+" connection="+(game.getConnection()!=null);
        long now=System.nanoTime();
        if (status.equals(previous) && now<next) return;
        next=now+20_000_000_000L;
        previous=status;
        Path report=Path.of(System.getenv("GITHUB_WORKSPACE"),"dist/packaged-smoke","client249-phase"+Integer.getInteger("uq.playerOnlyPhase")+"-status.txt");
        Files.writeString(report,status+"\n");
        System.out.println("FULL249_CLIENT "+status);
    }
}
