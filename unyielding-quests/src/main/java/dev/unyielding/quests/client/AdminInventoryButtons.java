package dev.unyielding.quests.client;

import dev.unyielding.quests.net.Net;
import dev.unyielding.quests.client.ui.UiConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.ScreenEvent;

/** Operator-only shortcuts, with server-side authorization for every click. */
public final class AdminInventoryButtons {
    private static final java.util.Map<net.minecraft.client.gui.screens.Screen, java.util.List<WorldButton>> BUTTONS = new java.util.WeakHashMap<>();
    private static final java.util.Map<net.minecraft.client.gui.screens.Screen, int[]> AREAS = new java.util.WeakHashMap<>();
    private AdminInventoryButtons() { }

    public static int[] onInit(ScreenEvent.Init.Post event, int[] occupied) {
        var screen = event.getScreen();
        if (!(screen instanceof InventoryScreen) && !(screen instanceof CreativeModeInventoryScreen)) return occupied;
        // A horizontal row beside the container would cover armor/crafting slots.
        boolean topLeft = occupied != null && UiConfig.invButtons() == UiConfig.InvButtons.TOP_LEFT;
        int x = topLeft ? occupied[0] : 4;
        int y = topLeft ? occupied[1] + occupied[3] + 3 : 4;
        int width = Math.min(52, (screen.width - 12) / 3);
        x = Math.max(0, Math.min(x, screen.width - (width * 3 + 4)));
        y = Math.max(0, Math.min(y, screen.height - 20));
        String[] actions = {"day", "night", "weather"};
        String[] names = {"Day", "Night", "Weather"};
        var buttons = new java.util.ArrayList<WorldButton>();
        for (int i = 0; i < actions.length; i++) {
            WorldButton button = new WorldButton(x + i * (width + 2), y, width, names[i], actions[i]);
            buttons.add(button);
            event.addListener(button);
        }
        BUTTONS.put(screen, buttons);
        AREAS.put(screen, new int[]{x, y, width * 3 + 4, 20});
        return occupied;
    }

    public static void tick() {
        var buttons = BUTTONS.get(Minecraft.getInstance().screen);
        if (buttons != null) buttons.forEach(WorldButton::updateState);
    }

    public static int[] area(net.minecraft.client.gui.screens.Screen screen, int[] occupied) {
        int[] controls = AREAS.get(screen);
        if (!WorldButton.allowed() || controls == null) return occupied;
        int x = controls[0], y = controls[1], width = (controls[2] - 4) / 3;
        if (occupied == null) return new int[]{x, y, width * 3 + 4, 20};
        int right = Math.max(x + width * 3 + 4, occupied[0] + occupied[2]);
        int bottom = Math.max(y + 20, occupied[1] + occupied[3]);
        int left = Math.min(x, occupied[0]), top = Math.min(y, occupied[1]);
        return new int[]{left, top, right - left, bottom - top};
    }

    private static final class WorldButton extends Button {
        private final String action;
        private String tip = "";

        private WorldButton(int x, int y, int width, String title, String action) {
            super(x, y, width, 20, Component.literal(title),
                    ignored -> Net.sendToServer(new Net.AdminWorldAction(action)), DEFAULT_NARRATION);
            this.action = action;
            updateState();
        }

        private static boolean allowed() {
            return ClientData.adminInventoryControls && AdminSettingsScreen.canManage();
        }

        private void updateState() {
            visible = active = allowed();
            if (visible) {
                String next = switch (action) {
                    case "day" -> "Set day (1000) in all dimensions";
                    case "night" -> "Set night (13000) in all dimensions";
                    default -> weatherTip();
                };
                if (!next.equals(tip)) {
                    tip = next;
                    setTooltip(Tooltip.create(Component.literal(next)));
                }
            }
        }

        private static String weatherTip() {
            var level = Minecraft.getInstance().level;
            if (level == null || !level.dimensionType().hasSkyLight()) return "Weather is unavailable in this dimension";
            String next = level.isThundering() ? "clear" : level.getLevelData().isRaining() ? "thunder" : "rain";
            return "Next: " + next + " for ten minutes in this dimension";
        }

        @Override public void onPress() {
            if (allowed()) super.onPress();
        }
    }
}
