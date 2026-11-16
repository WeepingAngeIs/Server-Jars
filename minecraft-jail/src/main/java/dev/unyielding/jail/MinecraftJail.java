package dev.unyielding.jail;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.server.permission.events.PermissionGatherEvent;
import org.slf4j.Logger;

/** Entry point for the extracted implementation, not a replacement jail system. */
@Mod(MinecraftJail.MODID)
public final class MinecraftJail {
    public static final String MODID = "minecraftjail";
    public static final Logger LOGGER = LogUtils.getLogger();

    public MinecraftJail(IEventBus modBus, ModContainer container) {
        var companion = ModList.get().getModFileById("unyieldingquests");
        if (companion != null) LegacyBundleGuard.verify(companion.getFile().getFilePath());
        container.registerConfig(ModConfig.Type.COMMON, JailConfig.SPEC, "minecraftjail-common.toml");
        JailRegistration.register(modBus);
        NeoForge.EVENT_BUS.addListener(MinecraftJail::commands);
        NeoForge.EVENT_BUS.addListener(MinecraftJail::permissions);
    }
    private static void commands(RegisterCommandsEvent event) {
        JailCommands.register(event.getDispatcher());
        JailPlacement.register(event.getDispatcher());
    }
    private static void permissions(PermissionGatherEvent.Nodes event) {
        event.addNodes(JailCommands.MANAGE);
    }
}
