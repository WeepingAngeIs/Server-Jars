package dev.unyielding.quests.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.unyielding.quests.Config;
import dev.unyielding.quests.UnyieldingQuests;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.glfw.GLFW;

/** Client-only setup: key bindings, inventory buttons, cleanup on disconnect. */
public final class ClientInit {
    public static final String CATEGORY = "key.categories.unyieldingquests";
    public static final KeyMapping OPEN_QUESTS = new KeyMapping("key.unyieldingquests.quests", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_J, CATEGORY);
    public static final KeyMapping OPEN_TEAMS = new KeyMapping("key.unyieldingquests.teams", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, CATEGORY);

    private static boolean AUTOTEST = System.getProperty("uq.autotest", "").equals("1");

    private ClientInit() {
    }

    public static void init(IEventBus modBus, net.neoforged.fml.ModContainer container) {
        container.registerConfig(net.neoforged.fml.config.ModConfig.Type.CLIENT, dev.unyielding.quests.client.ui.UiConfig.SPEC, "unyieldingquests-client.toml");
        container.registerExtensionPoint(net.neoforged.neoforge.client.gui.IConfigScreenFactory.class, net.neoforged.neoforge.client.gui.ConfigurationScreen::new);
        modBus.addListener(ClientInit::registerKeys);
        modBus.addListener((net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent e) ->
                e.registerReloadListener((net.minecraft.server.packs.resources.ResourceManagerReloadListener) rm -> {
                    dev.unyielding.quests.client.ui.Theme.reload();
                    Hints.reload();
                    QuestScreen.clearArt();
                }));
        UltimineClient.init(modBus);
        NeoForge.EVENT_BUS.register(new Events());
    }

    private static void registerKeys(RegisterKeyMappingsEvent e) {
        e.register(OPEN_QUESTS);
        e.register(OPEN_TEAMS);
    }

    public static void openQuests() {
        Minecraft mc = Minecraft.getInstance();
        if (ClientData.file == null) {
            if (mc.player != null) mc.player.displayClientMessage(Component.literal("The quest book has not been received from the server yet."), true);
            return;
        }
        mc.setScreen(new QuestScreen());
    }

    public static final class Events {
        @SubscribeEvent
        public void onTick(ClientTickEvent.Post e) {
            Minecraft mc = Minecraft.getInstance();
            dev.unyielding.quests.client.tutorial.TutorialClient.tick();
            if (mc.player == null) return;
            if (AUTOTEST) {
                try {
                    Class.forName("dev.unyielding.quests.devtest.AutoTest").getMethod("tick").invoke(null);
                } catch (ReflectiveOperationException ex) {
                    UnyieldingQuests.LOGGER.error("autotest failed", ex);
                    AUTOTEST = false;
                }
            }
            while (OPEN_QUESTS.consumeClick()) {
                if (mc.screen == null) openQuests();
            }
            while (OPEN_TEAMS.consumeClick()) {
                if (mc.screen == null) mc.setScreen(new TeamScreen());
            }
        }

        @SubscribeEvent
        public void onInventory(ScreenEvent.Init.Post e) {
            InvButtons.onInit(e);
        }

        @SubscribeEvent
        public void onLogout(ClientPlayerNetworkEvent.LoggingOut e) {
            dev.unyielding.quests.client.tutorial.TutorialClient.reset();
            ClientData.clear();
        }
    }
}
