package dev.unyielding.quests;

import com.mojang.logging.LogUtils;
import dev.unyielding.quests.net.Net;
import dev.unyielding.quests.quest.QuestManager;
import dev.unyielding.quests.team.TeamManager;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

@Mod(UnyieldingQuests.MODID)
public class UnyieldingQuests {
    public static final String MODID = "unyieldingquests";
    public static final Logger LOGGER = LogUtils.getLogger();

    public UnyieldingQuests(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
        container.registerConfig(ModConfig.Type.COMMON, dev.unyielding.quests.essentials.EssConfig.SPEC, "unyieldingquests-essentials.toml");
        container.registerConfig(ModConfig.Type.COMMON, dev.unyielding.quests.ultimine.UltimineConfig.SPEC, "unyieldingquests-ultimine.toml");
        Registration.register(modBus);
        modBus.addListener(Net::register);
        NeoForge.EVENT_BUS.register(new ServerEvents());
        NeoForge.EVENT_BUS.register(new dev.unyielding.quests.essentials.Essentials.Events());
        NeoForge.EVENT_BUS.register(new dev.unyielding.quests.ultimine.Ultimine.Events());
        if (FMLEnvironment.dist.isClient()) {
            dev.unyielding.quests.client.ClientInit.init(modBus, container);
        }
    }
}
