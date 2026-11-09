package dev.unyielding.quests;

import dev.unyielding.quests.commands.ModCommands;
import dev.unyielding.quests.quest.QuestManager;
import dev.unyielding.quests.team.TeamManager;
import dev.unyielding.quests.tutorial.TutorialManager;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

public class ServerEvents {
    @SubscribeEvent
    public void onStarted(ServerStartedEvent e) {
        dev.unyielding.quests.settings.PokemonEditor.start(e.getServer());
    }
    @SubscribeEvent
    public void onStarting(ServerStartingEvent e) {
        TeamManager.onServerStart(e.getServer());
        QuestManager.onServerStart(e.getServer());
        dev.unyielding.quests.loot.LootTables.start(e.getServer());
        dev.unyielding.quests.spin.DailyLogin.onServerStart(e.getServer());
        TutorialManager.onServerStart();
        dev.unyielding.quests.settings.AdminSettings.onServerStart(e.getServer());
    }

    @SubscribeEvent
    public void onStopped(ServerStoppedEvent e) {
        dev.unyielding.quests.settings.PokemonEditor.stop();
        dev.unyielding.quests.loot.LootTables.stop();
        QuestManager.onServerStop();
        dev.unyielding.quests.spin.DailyLogin.onServerStop();
        TeamManager.onServerStop();
        TutorialManager.onServerStop();
        dev.unyielding.quests.settings.AdminSettings.onServerStop();
    }

    @SubscribeEvent
    public void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) {
            TeamManager.onLogin(p);
            dev.unyielding.quests.settings.AdminSettings.sync(p, false, "", false);
            QuestManager.onLogin(p);
            dev.unyielding.quests.spin.DailyLogin.onLogin(p);
            TutorialManager.onLogin(p);
        }
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) {
            dev.unyielding.quests.settings.PokemonEditor.logout(p);
            TeamManager.onLogout(p);
            dev.unyielding.quests.spin.DailyLogin.onLogout(p);
            TutorialManager.onLogout(p);
        }
    }

    @SubscribeEvent
    public void onTick(ServerTickEvent.Post e) {
        dev.unyielding.quests.settings.AdminSettings.onTick(e.getServer());
        QuestManager.onServerTick();
        dev.unyielding.quests.spin.DailyLogin.onTick(e.getServer());
        TutorialManager.onTick(e.getServer());
    }

    @SubscribeEvent
    public void onDeath(LivingDeathEvent e) {
        if (e.getSource().getEntity() instanceof ServerPlayer p && e.getEntity() != p) {
            QuestManager.onKill(p, e.getEntity());
        }
    }

    @SubscribeEvent
    public void onCommands(RegisterCommandsEvent e) {
        ModCommands.register(e.getDispatcher());
        dev.unyielding.quests.loot.LootTables.registerCommands(e.getDispatcher());
    }
}
