package dev.unyielding.bundlesmoke;

import dev.unyielding.quests.client.ClientData;
import dev.unyielding.quests.client.ClientHandlers;
import dev.unyielding.quests.client.ConfigValuesScreen;
import dev.unyielding.quests.client.edit.EditLootTableScreen;
import dev.unyielding.quests.client.edit.EditQuestScreen;
import dev.unyielding.quests.client.edit.LootTablesScreen;
import dev.unyielding.quests.settings.ConfigEditor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Map;
import static dev.unyielding.bundlesmoke.BundleSmoke.require;

/** Render the actual bundled editors and verify drafts do not change server-owned data. */
@EventBusSubscriber(modid = BundleSmoke.MOD_ID, value = Dist.CLIENT)
public final class LootEditorClientSmoke {
    private static boolean checked;

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (checked || mc.player == null || ClientData.file == null || mc.getOverlay() != null
                || !Files.exists(Path.of("/tmp/uq-emi-quest-passed.json"))
                || !Files.exists(Path.of("/tmp/uq-loot-tables-passed.json"))) return;
        checked = true;
        Screen previous = mc.screen;
        CompoundTag original = ClientData.lootTables;
        int originalPermission = mc.player.getPermissionLevel();
        try {
            // Render-only fixture: earlier tests intentionally leave a normal-player client.
            // Server authorization was exercised separately; do not send any edits here.
            mc.player.setPermissionLevel(2);
            var report = BundleSmoke.verifyProductionJar();
            CompoundTag table = table();
            ListTag tables = new ListTag(); tables.add(table);
            CompoundTag state = new CompoundTag();
            state.putBoolean("allowed", true);
            state.putBoolean("enabled", true);
            state.putInt("revision", 17);
            state.put("tables", tables);
            ClientHandlers.onLootTables(state, true);
            require(mc.screen instanceof LootTablesScreen, "Server response did not open loot editor");
            var measurements = new ArrayList<Map<String, Object>>();
            for (int width : new int[]{640, 320}) {
                Screen list = mc.screen;
                draw(mc, list, width);
                require(has(list, "New loot table"), "Table create action missing");
                require(has(list, "Edit") && has(list, "Get crate") && has(list, "Delete"),
                        "Saved table actions missing");
                EditLootTableScreen edit = new EditLootTableScreen(list, table);
                draw(mc, edit, width);
                require(has(edit, "Add reward") && has(edit, "Save"), "Reward editor missing controls");
                var draftField = EditLootTableScreen.class.getDeclaredField("draft");
                draftField.setAccessible(true);
                CompoundTag draft = (CompoundTag)draftField.get(edit);
                draft.putString("name", "Unsaved draft");
                require(!table.getString("name").equals("Unsaved draft"), "Editing mutates synchronized table");
                EditLootTableScreen create = new EditLootTableScreen(list, null);
                draw(mc, create, width);
                CompoundTag blank = (CompoundTag)draftField.get(create);
                require(blank.getInt("rolls") == 1 && blank.getBoolean("crateEnabled"), "New table defaults invalid");
                draw(mc, new EditQuestScreen(list, ClientData.file.quests.get(0x05AAEB7B168BFD49L)), width);
                measurements.add(Map.of("width", width, "list_create_edit_quest_reward_forms", "rendered"));
            }
            require(LootTablesScreen.action("save").getInt("revision") == 17, "Stale write revision omitted");
            report.put("loot_list_create_edit_draft_isolation_and_revision", "passed");
            report.put("editor_sizes", measurements);

            CompoundTag row = new CompoundTag();
            row.putString("key", "quests.lucky_spin.basic_items");
            row.putString("value", "[\"minecraft:apple 1-3 x10\"]");
            row.putString("help", "Item reward pool entries");
            ListTag rows = new ListTag(); rows.add(row);
            CompoundTag config = new CompoundTag(); config.put("rows", rows);
            ConfigValuesScreen.receive(new ConfigEditor.Sync(config, true));
            Screen configList = mc.screen;
            require(configList instanceof ConfigValuesScreen, "Config values screen did not open");
            draw(mc, configList, 320);
            var valueClass = Class.forName("dev.unyielding.quests.client.ConfigValuesScreen$ValueScreen");
            var constructor = valueClass.getDeclaredConstructor(ConfigValuesScreen.class, CompoundTag.class);
            constructor.setAccessible(true);
            Screen value = (Screen)constructor.newInstance(configList, row);
            draw(mc, value, 320);
            require(has(value, "Add entry") && has(value, "Save"), "List config editor missing controls");
            report.put("config_values_and_list_editor_small_screen", "passed");
            SpinOddsClientSmoke.exercise(mc, report);
            BundleSmoke.write("/tmp/uq-loot-editor-client-passed.json", report);
        } catch (Throwable failure) {
            BundleSmoke.fail("/tmp/uq-loot-editor-client-failed.txt", failure);
        } finally {
            ClientData.lootTables = original;
            ClientData.lootTablesVersion++;
            mc.player.setPermissionLevel(originalPermission);
            mc.setScreen(previous);
        }
    }

    private static CompoundTag table() {
        CompoundTag table = new CompoundTag();
        table.putString("id", "cloud_ui_fixture"); table.putString("name", "Reward crate");
        table.putBoolean("enabled", true); table.putBoolean("crateEnabled", true); table.putInt("rolls", 2);
        ListTag entries = new ListTag();
        for (String type : new String[]{"item", "command", "pokemon", "lucky_spin"}) {
            CompoundTag entry = new CompoundTag(); entry.putString("type", type);
            entry.putString("value", type.equals("item") ? "minecraft:apple" : type.equals("pokemon") ? "pikachu" : "");
            entry.putInt("min", 1); entry.putInt("max", 3); entry.putInt("weight", 10); entries.add(entry);
        }
        table.put("entries", entries); return table;
    }

    private static boolean has(Screen screen, String label) {
        return screen.children().stream().anyMatch(child -> child instanceof AbstractWidget widget
                && widget.getMessage().getString().equals(label));
    }

    private static void draw(Minecraft mc, Screen screen, int width) {
        screen.init(mc, width, 400);
        GuiGraphics graphics = new GuiGraphics(mc, mc.renderBuffers().bufferSource());
        screen.render(graphics, -1000, -1000, 0f);
        graphics.flush();
    }
}
