package dev.unyielding.bundlesmoke;

import dev.unyielding.quests.client.AdminSettingsScreen;
import dev.unyielding.quests.client.ConfigValuesScreen;
import dev.unyielding.quests.client.SpinOddsScreen;
import dev.unyielding.quests.settings.ConfigEditor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import java.util.Arrays;
import java.util.Map;
import static dev.unyielding.bundlesmoke.BundleSmoke.require;

final class SpinOddsClientSmoke {
    static void exercise(Minecraft mc, Map<String, Object> report) throws Exception {
        CompoundTag data = fixture();
        data.putBoolean("oddsOpen", true);
        ConfigValuesScreen.receive(new ConfigEditor.Sync(data, true));
        require(mc.screen instanceof SpinOddsScreen, "Odds shortcut did not open percentage editor");
        for (int[] size : new int[][]{{640,400}, {320,240}}) {
            var screen = new SpinOddsScreen(null, data);
            mc.setScreen(screen);
            draw(mc, screen, size[0], size[1]);
            require(fields(screen).length == 5, "Expected five percentage fields");
            require(Arrays.equals(values(screen), new String[]{"50","44","3","2","1"}), "Quest percentages incorrect");
            press(screen, "Very rare");
            require(Arrays.equals(values(screen), new String[]{"60","39.84","0.1","0.05","0.01"}), "Rare preset is not precise");
            press(screen, "Daily spins");
            require(Arrays.equals(values(screen), new String[]{"40","50","5","3","2"}), "Daily tab did not retain its own values");
            press(screen, "Items only");
            require(Arrays.equals(values(screen), new String[]{"60","40","0","0","0"}), "Items-only preset is not zero Pokemon");
            press(screen, "Quest spins");
            require(values(screen)[4].equals("0.01"), "Switching tabs lost draft");
            screen.receive(fixture(), false);
            require(values(screen)[4].equals("0.01"), "Unrelated server update overwrote unsaved draft");
            fields(screen)[0].setValue("59.99");
            press(screen, "Save");
            var pending = SpinOddsScreen.class.getDeclaredField("pendingGroup");
            pending.setAccessible(true);
            require(pending.getInt(screen) == -1, "Invalid total submitted a save");
            fields(screen)[0].setValue("60");
            draw(mc, screen, size[0], size[1]);
            // Only layout and draft changes; server transaction is covered by SpinOddsSmoke.
            for (var child : screen.children()) if (child instanceof AbstractWidget widget) {
                require(widget.getX() >= 0 && widget.getX() + widget.getWidth() <= size[0], "Odds control exceeds narrow screen");
            }
            var admin = new AdminSettingsScreen(null);
            draw(mc, admin, size[0], size[1]);
            require(admin.children().stream().anyMatch(w -> w instanceof Button b && b.getMessage().getString().equals("Spin odds")),
                    "Admin menu has no odds shortcut");
        }
        var rounding = SpinOddsScreen.class.getDeclaredMethod("percentages", int[].class);
        rounding.setAccessible(true);
        require(Arrays.stream((int[])rounding.invoke(null, (Object)new int[]{1,1,1,1,1})).sum() == 10000, "Percentage rounding changes total");
        report.put("spin_odds_percentage_presets_separate_drafts_sync_safety_and_invalid_total", "passed");
        report.put("spin_odds_and_admin_shortcut_render_at_640x400_and_320x240", "passed");
    }
    private static CompoundTag fixture() {
        ListTag rows = new ListTag();
        String[] keys = {"basic","good","paradox","mythical","legendary"};
        int[][] values = {{50,44,3,2,1},{40,50,5,3,2}};
        for (int group = 0; group < 2; group++) for (int i = 0; i < 5; i++) {
            CompoundTag row = new CompoundTag();
            row.putString("key", "quests." + (group == 0 ? "lucky_spin" : "daily_login") + ".chance_" + keys[i]);
            row.putString("value", Integer.toString(values[group][i])); rows.add(row);
        }
        CompoundTag data = new CompoundTag(); data.put("rows", rows); return data;
    }
    private static EditBox[] fields(Screen screen) {
        return screen.children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast).toArray(EditBox[]::new);
    }
    private static String[] values(Screen screen) { return Arrays.stream(fields(screen)).map(EditBox::getValue).toArray(String[]::new); }
    private static void press(Screen screen, String label) {
        Button button = screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                .filter(b -> b.getMessage().getString().equals(label) || b.getMessage().getString().equals(label + " *"))
                .findFirst().orElseThrow(() -> new IllegalStateException("Missing button " + label));
        button.onPress();
    }
    private static void draw(Minecraft mc, Screen screen, int width, int height) {
        screen.init(mc, width, height);
        GuiGraphics graphics = new GuiGraphics(mc, mc.renderBuffers().bufferSource());
        screen.render(graphics, -1000, -1000, 0f); graphics.flush();
    }
}
