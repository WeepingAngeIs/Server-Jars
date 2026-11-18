package dev.unyielding.packprobe;

import com.google.gson.GsonBuilder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

@Mod("uq_pack249_client_probe")
public final class ClientSmoke {
    private boolean finished;
    private String lastScreen = "";
    public ClientSmoke() { NeoForge.EVENT_BUS.addListener(this::tick); }

    private void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        String screen = (mc.screen == null ? "none" : mc.screen.getClass().getName())
                + " / " + (mc.getOverlay() == null ? "none" : mc.getOverlay().getClass().getName());
        if (!lastScreen.equals(screen)) {
            com.mojang.logging.LogUtils.getLogger().info("Jail client test screen {}", screen);
            lastScreen = screen;
        }
        if (finished || !(mc.screen instanceof TitleScreen) || mc.getOverlay() != null) return;
        finished = true;
        try {
            var models = mc.getModelManager();
            var ids = List.of("cell_plate_1", "cell_plate_2", "cell_plate_3", "cell_plate_4",
                    "cell_plate_5", "cell_plate_6", "rose_inlay", "smoked_chestnut", "pearl_ashlar",
                    "ward_grille", "petal_lantern", "ceramic_blossom");
            for (String id : ids) {
                var block = BuiltInRegistries.BLOCK.getOptional(ResourceLocation.fromNamespaceAndPath("minecraftjail", id)).orElseThrow();
                if (mc.getBlockRenderer().getBlockModel(block.defaultBlockState()) == models.getMissingModel())
                    throw new IllegalStateException("Missing Rosecourt model: " + id);
            }
            var versions = new TreeMap<String,String>();
            for (var mod : ModList.get().getMods()) versions.put(mod.getModId(),mod.getVersion().toString());
            if (!"21.1.249".equals(versions.get("neoforge")) || !versions.containsKey("minecraftjail"))
                throw new IllegalStateException("Unexpected client runtime");
            var report = Map.of("versions", versions, "models", ids, "screen", mc.screen.getClass().getName());
            Files.writeString(Path.of(System.getenv("CLIENT249_REPORT")), new GsonBuilder().setPrettyPrinting().create().toJson(report));
            mc.stop();
        } catch (Exception e) { throw new IllegalStateException("Client test failed", e); }
    }
}
