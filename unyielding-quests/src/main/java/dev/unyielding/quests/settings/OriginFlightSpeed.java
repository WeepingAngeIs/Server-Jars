package dev.unyielding.quests.settings;

import dev.unyielding.quests.Config;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.bus.api.SubscribeEvent;
import java.util.WeakHashMap;
import java.util.Map;

public final class OriginFlightSpeed {
    private static final Map<ServerPlayer, Integer> active = new WeakHashMap<>();
    private static final String BASE = "unyielding_origin_flight_base";
    private static final String LAST = "unyielding_origin_flight_last";

    public static void markActive(ServerPlayer player) { active.put(player, player.tickCount); }

    @SubscribeEvent public void tick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        var data = player.getPersistentData();
        var abilities = player.getAbilities();
        float speed = abilities.getFlyingSpeed();
        Integer seen = active.get(player);
        boolean applies = seen != null && player.tickCount - seen <= 1 && abilities.mayfly && abilities.flying && !player.isCreative() && !player.isSpectator();
        if (!applies && !data.contains(BASE)) return;
        float base = data.contains(BASE) && data.getFloat(LAST) == speed ? data.getFloat(BASE) : speed;
        float target = applies ? base * Config.ORIGIN_FLIGHT_SPEED.get().floatValue() : base;
        if (applies) {
            data.putFloat(BASE, base);
            data.putFloat(LAST, target);
        } else {
            data.remove(BASE);
            data.remove(LAST);
        }
        if (speed != target) {
            abilities.setFlyingSpeed(target);
            player.onUpdateAbilities();
        }
    }
}
