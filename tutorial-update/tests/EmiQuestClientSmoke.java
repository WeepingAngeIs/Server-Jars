package dev.unyielding.bundlesmoke;

import dev.emi.emi.config.EmiConfig;
import dev.emi.emi.runtime.EmiReloadManager;
import dev.emi.emi.screen.RecipeScreen;
import dev.unyielding.quests.client.ClientData;
import dev.unyielding.quests.client.QuestScreen;
import dev.unyielding.quests.quest.QuestFile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.item.Items;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.lwjgl.glfw.GLFW;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import static dev.unyielding.bundlesmoke.BundleSmoke.require;

/** Exercise actual quest-screen input and actual EMI, not a mocked recipe callback. */
@EventBusSubscriber(modid = BundleSmoke.MOD_ID, value = Dist.CLIENT)
public final class EmiQuestClientSmoke {
    private static boolean checked;

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (checked || mc.player == null || ClientData.file == null || mc.getOverlay() != null
                || !EmiReloadManager.isLoaded()
                || !Files.exists(Path.of("/tmp/uq-compact-tasks-passed.json"))
                || !Files.exists(Path.of("/tmp/uq-admin-features-client-passed.json"))) return;
        checked = true;
        Screen previous = mc.screen;
        var recipes = EmiConfig.viewRecipes.boundKeys;
        var uses = EmiConfig.viewUses.boundKeys;
        boolean enabled = EmiConfig.enabled;
        var task = ClientData.file.quests.get(0x05AAEB7B168BFD49L).tasks.getFirst();
        var item = task.item;
        var icon = task.icon;
        try {
            var report = BundleSmoke.verifyProductionJar();
            EmiConfig.enabled = true;
            EmiConfig.viewRecipes.setKey(List.of("key.keyboard.r", "key.mouse.left"));
            EmiConfig.viewUses.setKey(List.of("key.keyboard.u", "key.mouse.right"));
            QuestScreen screen = new QuestScreen();
            mc.setScreen(screen);
            screen.debugSelect(0x05AAEB7B168BFD49L);
            field("detailAnim").setFloat(screen, 1f);
            int[] pos = hoverFirstTask(mc, screen);
            require(screen.keyPressed(GLFW.GLFW_KEY_R, 0, 0), "Quest R input was ignored");
            back(mc, screen);
            require(field("selected").get(screen) != null, "Returning lost selected quest");
            require(field("detailScroll").getInt(screen) == pos[2], "Returning lost quest scroll");
            report.put("real_pokeball_recipe_R_and_escape_return", "passed");

            // A known vanilla ingredient guarantees both a recipe and uses in the isolated pack.
            task.item = QuestFile.ItemRef.of("minecraft:oak_planks");
            task.icon = null;
            pos = hoverFirstTask(mc, screen);
            require(screen.stackAt(pos[0], pos[1]).is(Items.OAK_PLANKS), "Wrong task under recipe cursor");
            require(screen.keyPressed(GLFW.GLFW_KEY_U, 0, 0), "Quest U input was ignored");
            back(mc, screen);
            hoverFirstTask(mc, screen);
            require(screen.mouseClicked(pos[0], pos[1], 0), "Quest recipe click ignored");
            back(mc, screen);
            hoverFirstTask(mc, screen);
            require(screen.mouseClicked(pos[0], pos[1], 1), "Quest uses click ignored");
            back(mc, screen);
            report.put("uses_U_left_recipe_right_uses", "passed");

            EmiConfig.viewRecipes.setKey(List.of("key.keyboard.o"));
            hoverFirstTask(mc, screen);
            screen.keyPressed(GLFW.GLFW_KEY_R, 0, 0);
            require(mc.screen == screen, "Recipe shortcut is hardcoded to R");
            require(screen.keyPressed(GLFW.GLFW_KEY_O, 0, 0), "Remapped EMI recipe key ignored");
            back(mc, screen);
            report.put("custom_EMI_recipe_binding", "passed");

            draw(mc, screen, 0, 0);
            screen.keyPressed(GLFW.GLFW_KEY_O, 0, 0);
            require(mc.screen == screen, "Empty space opens a recipe");
            require(screen.stackAt(pos[0], 20).isEmpty(), "Clipped items respond above task viewport");
            hoverFirstTask(mc, screen);
            EmiConfig.enabled = false;
            screen.keyPressed(GLFW.GLFW_KEY_O, 0, 0);
            require(mc.screen == screen, "Disabled EMI still handles recipes");
            report.put("empty_clipped_and_disabled_inputs_ignored", "passed");
            BundleSmoke.write("/tmp/uq-emi-quest-passed.json", report);
        } catch (Throwable failure) {
            BundleSmoke.fail("/tmp/uq-emi-quest-failed.txt", failure);
        } finally {
            task.item = item;
            task.icon = icon;
            EmiConfig.viewRecipes.boundKeys = recipes;
            EmiConfig.viewUses.boundKeys = uses;
            EmiConfig.enabled = enabled;
            mc.setScreen(previous);
        }
    }

    private static java.lang.reflect.Field field(String name) throws Exception {
        var field = QuestScreen.class.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static int[] hoverFirstTask(Minecraft mc, QuestScreen screen) throws Exception {
        for (int scroll = 0; scroll < 2000; scroll += 20) {
            field("detailScroll").setInt(screen, scroll);
            draw(mc, screen, -1000, -1000);
            var slots = (List<?>)field("slots").get(screen);
            require(!slots.isEmpty(), "Quest rendered no task item slots");
            Object slot = slots.getFirst();
            var xMethod = slot.getClass().getDeclaredMethod("x");
            var yMethod = slot.getClass().getDeclaredMethod("y");
            xMethod.setAccessible(true);
            yMethod.setAccessible(true);
            int x = (int)xMethod.invoke(slot) + 9, y = (int)yMethod.invoke(slot) + 9;
            if (!screen.stackAt(x, y).isEmpty()) {
                draw(mc, screen, x, y);
                return new int[]{x, y, field("detailScroll").getInt(screen)};
            }
        }
        throw new AssertionError("First quest task never becomes hoverable");
    }

    private static void draw(Minecraft mc, QuestScreen screen, int x, int y) {
        GuiGraphics graphics = new GuiGraphics(mc, mc.renderBuffers().bufferSource());
        screen.render(graphics, x, y, 0f);
        graphics.flush();
    }

    private static void back(Minecraft mc, QuestScreen expected) {
        require(mc.screen instanceof RecipeScreen, "EMI did not open its actual recipe screen");
        mc.screen.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0);
        require(mc.screen == expected, "Escape did not return to the original quest book");
    }
}
