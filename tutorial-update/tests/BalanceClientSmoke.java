package dev.unyielding.bundlesmoke;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import static dev.unyielding.bundlesmoke.BundleSmoke.require;

@EventBusSubscriber(modid=BundleSmoke.MOD_ID,value=Dist.CLIENT)
public final class BalanceClientSmoke {
    private static boolean done;
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        var mc=Minecraft.getInstance();
        if(done||mc.player==null||!Files.exists(Path.of("/tmp/uq-balance-live-ready"))) return;
        try {
            var field=Class.forName("dev.unyielding.balance.ArmorHalver").getDeclaredField("synced");
            field.setAccessible(true); Object values=field.get(null); if(values==null) return;
            double armor=number(values,"armor"), toughness=number(values,"toughness"), knockback=number(values,"knockback");
            if(armor!=75||toughness!=25||knockback!=50||!MusicClientSmoke.received()) return;
            var report=new LinkedHashMap<>(BundleSmoke.verifyProductionJar());
            report.put("server_armor_values_reached_existing_client","passed");
            BalanceUiSmoke.exercise(mc,report);
            MusicClientSmoke.exercise(mc,report);
            BundleSmoke.write("/tmp/uq-balance-client-passed.json",report); done=true;
        }catch(Throwable failure){done=true;BundleSmoke.fail("/tmp/uq-balance-client-failed.txt",failure);}
    }
    private static double number(Object record,String name)throws Exception {
        var method=record.getClass().getDeclaredMethod(name);method.setAccessible(true);return ((Number)method.invoke(record)).doubleValue();
    }
}
