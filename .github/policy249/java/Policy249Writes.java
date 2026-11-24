package dev.unyielding.bundlesmoke;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.world.entity.player.Player;

/** Passive counters. They never cancel, redirect or supply a production action. */
public final class Policy249Writes {
    private static UUID owner;
    private static final Map<String,Integer> direct = new LinkedHashMap<>();
    private static final Map<String,Integer> nativeClone = new LinkedHashMap<>();
    private static final Map<String,Integer> ports = new LinkedHashMap<>();
    private Policy249Writes() {}
    public static void arm(UUID id) { owner=id; direct.clear(); nativeClone.clear(); ports.clear(); }
    public static void inventory(Player player,String operation) {
        if (owner==null || !owner.equals(player.getUUID()) || player.level().isClientSide()) return;
        var trace=Thread.currentThread().getStackTrace();
        for(var frame:trace) {
            if (frame.getClassName().equals("net.minecraft.server.level.ServerPlayer") && frame.getMethodName().equals("restoreFrom")) {
                nativeClone.merge(operation,1,Integer::sum);
                return;
            }
            if (frame.getClassName().startsWith("dev.unyielding.jail.")) {
                direct.merge(operation,1,Integer::sum);
                return;
            }
        }
    }
    public static void port(String operation) {
        if(owner!=null) ports.merge(operation,1,Integer::sum);
    }
    static Map<String,Object> snapshot() {
        return Map.of("direct_jail_inventory_writes",new LinkedHashMap<>(direct),
                "native_respawn_inventory_copies",new LinkedHashMap<>(nativeClone),
                "confiscation_or_return_calls",new LinkedHashMap<>(ports));
    }
    static void noJailWrites() {
        PlayerOnlySmoke.require(direct.isEmpty() && ports.isEmpty(),
                "Retained-inventory session invoked Jail inventory mutation or return staging: "+snapshot());
    }
}
