package dev.unyielding.bundlesmoke;

import dev.unyielding.quests.client.ClientData;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.Tag;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;

import static dev.unyielding.bundlesmoke.BundleSmoke.require;

/** Verifies real server settings packets reach a non-operator client. */
@EventBusSubscriber(modid = BundleSmoke.MOD_ID, value = Dist.CLIENT)
public final class AdminFeatureClientSmoke {
    private static boolean complete;

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (complete || mc.player == null || !Files.exists(Path.of("/tmp/uq-admin-features-client-ready.json"))) return;
        if (ClientData.inventoryButtonsEnabled || ClientData.questToastsEnabled || ClientData.ultimineEnabled
                || !ClientData.ultimineRequiresItem || ClientData.adminSettings.getBoolean("allowed")) return;
        try {
            require(ClientData.adminSettings.getList("settings", Tag.TAG_COMPOUND).isEmpty(),
                    "Non-operator received editable settings");
            var report = new LinkedHashMap<>(BundleSmoke.verifyProductionJar());
            report.put("inventory_quest_popup_and_vein_mining_flags", "passed");
            report.put("non_operator_received_no_editable_settings", "passed");
            BundleSmoke.write("/tmp/uq-admin-features-client-passed.json", report);
            complete = true;
        } catch (Throwable failure) {
            complete = true;
            BundleSmoke.fail("/tmp/uq-admin-features-failed.txt", failure);
        }
    }
}
