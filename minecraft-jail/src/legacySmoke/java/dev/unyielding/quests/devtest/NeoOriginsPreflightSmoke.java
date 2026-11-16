package dev.unyielding.quests.devtest;

import dev.unyielding.quests.UnyieldingQuests;
import dev.unyielding.quests.jail.NeoOriginsPreflight;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** GitHub-only disposable-world probe. Never included in the production overlay. */
@EventBusSubscriber(modid = UnyieldingQuests.MODID)
public final class NeoOriginsPreflightSmoke {
    private static boolean done;
    private static void require(boolean value, String message) {
        if (!value) throw new IllegalStateException(message);
    }

    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        if (done || !Boolean.getBoolean("uq.neoOriginsPreflight")) return;
        var server = event.getServer();
        var player = server.getPlayerList().getPlayerByName("Dev");
        if (player == null || server.getTickCount() < 100) return;
        done = true;
        var output = Path.of("/tmp/uq-neoorigins-preflight-result");
        try {
            require(net.neoforged.fml.ModList.get().isLoaded("neoorigins"), "NeoOrigins missing");
            ResourceLocation layer = ResourceLocation.parse("neoorigins:origin");
            ResourceLocation human = ResourceLocation.parse("neoorigins:human");
            Class<?> layersClass = Class.forName("com.cyberday1.neoorigins.data.LayerDataManager");
            Object layers = layersClass.getField("INSTANCE").get(null);
            require(layersClass.getMethod("getLayer", ResourceLocation.class).invoke(layers, layer) != null, "main origin layer missing");
            Class<?> originsClass = Class.forName("com.cyberday1.neoorigins.data.OriginDataManager");
            Object origins = originsClass.getField("INSTANCE").get(null);
            require(Boolean.TRUE.equals(originsClass.getMethod("hasOrigin", ResourceLocation.class).invoke(origins, human)), "Human missing");
            Class<?> attachments = Class.forName("com.cyberday1.neoorigins.attachment.OriginAttachments");
            Object rawType = attachments.getMethod("originData").invoke(null);
            require(rawType instanceof AttachmentType<?>, "unexpected attachment type");
            AttachmentType<?> type = (AttachmentType<?>) rawType;
            Object data = player.getData(type); // synthetic fixture only, never a live profile
            Class<?> dataClass = Class.forName("com.cyberday1.neoorigins.attachment.PlayerOriginData");
            var set = dataClass.getMethod("setOrigin", ResourceLocation.class, ResourceLocation.class);
            var getAll = dataClass.getMethod("getOrigins");
            var version = dataClass.getMethod("version");
            set.invoke(data, layer, human);
            Map<?, ?> before = Map.copyOf((Map<?, ?>) getAll.invoke(data));
            Object versionBefore = version.invoke(data);
            int levelBefore = player.experienceLevel;
            NeoOriginsPreflight.Result result = NeoOriginsPreflight.inspect(player);
            require(result.status() == NeoOriginsPreflight.Status.INSPECTED, "human preflight status " + result.status());
            require(human.equals(result.original()), "human selection was not reported");
            require(before.equals(getAll.invoke(data)) && versionBefore.equals(version.invoke(data)), "preflight changed origin data");
            require(levelBefore == player.experienceLevel, "preflight changed player experience");
            // A disposable non-human fixture checks that inspection identifies the
            // origin to preserve; it does not perform an origin switch or callbacks.
            ResourceLocation merling = ResourceLocation.parse("neoorigins:merling");
            require(Boolean.TRUE.equals(originsClass.getMethod("hasOrigin", ResourceLocation.class).invoke(origins, merling)), "Merling missing");
            set.invoke(data, layer, merling);
            before = Map.copyOf((Map<?, ?>) getAll.invoke(data));
            versionBefore = version.invoke(data);
            result = NeoOriginsPreflight.inspect(player);
            require(result.status() == NeoOriginsPreflight.Status.INSPECTED && merling.equals(result.original()), "non-human selection was not inspected");
            require(before.equals(getAll.invoke(data)) && versionBefore.equals(version.invoke(data)), "preflight changed non-human origin data");
            Files.writeString(output, "passed: exact NeoOrigins, Human and Merling preflight; player origin snapshots unchanged\n");
        } catch (Throwable error) {
            UnyieldingQuests.LOGGER.error("NeoOrigins preflight smoke failed", error);
            try { Files.writeString(output, "failed: " + error + "\n"); } catch (Exception ignored) { }
        }
    }
}
