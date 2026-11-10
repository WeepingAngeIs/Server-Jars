package dev.unyielding.quests.settings;

import dev.unyielding.quests.Config;
import dev.unyielding.quests.UnyieldingQuests;
import net.minecraft.server.commands.TimeCommand;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** Small allowlist for inventory buttons; clients never supply commands or weather state. */
public final class AdminWorldControls {
    private AdminWorldControls() { }

    public static boolean handle(ServerPlayer player, String action) {
        if (!player.hasPermissions(2)) {
            reply(player, "Only server admins can use these controls.");
            return false;
        }
        if (!Config.ADMIN_INVENTORY_CONTROLS.get()) {
            reply(player, "Admin inventory controls are disabled in server settings.");
            return false;
        }
        switch (action) {
            case "day", "night" -> {
                TimeCommand.setTime(player.createCommandSourceStack(), action.equals("day") ? 1000 : 13000);
                reply(player, action.equals("day") ? "Set day in all dimensions." : "Set night in all dimensions.");
            }
            case "weather" -> {
                var level = player.serverLevel();
                if (!level.dimensionType().hasSkyLight()) {
                    reply(player, "Weather cannot be changed in this dimension. Use this control in the Overworld.");
                    return false;
                }
                // Read saved server weather flags: interpolation can lag immediately after a click.
                boolean rain = level.getLevelData().isRaining();
                boolean thunder = level.getLevelData().isThundering();
                String next;
                if (thunder) {
                    level.setWeatherParameters(12000, 0, false, false);
                    next = "clear";
                } else if (rain) {
                    level.setWeatherParameters(0, 12000, true, true);
                    next = "thunder";
                } else {
                    level.setWeatherParameters(0, 12000, true, false);
                    next = "rain";
                }
                reply(player, "Weather: " + next + " for ten minutes in this dimension.");
            }
            default -> {
                reply(player, "That admin inventory action is not available.");
                return false;
            }
        }
        UnyieldingQuests.LOGGER.info("{} used admin inventory control {}", player.getGameProfile().getName(), action);
        return true;
    }

    private static void reply(ServerPlayer player, String text) {
        player.displayClientMessage(Component.literal(text), true);
    }
}
