package dev.unyielding.jail;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Separate config; never import an enabled admission switch from the old bundle. */
public final class JailConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue JAIL_CERTIFIED_ADMISSIONS;
    public static final ModConfigSpec.BooleanValue JAIL_PACKED_WALLETS;
    public static final ModConfigSpec.BooleanValue JAIL_CONFISCATE_INVENTORY;
    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.push("jail");
        JAIL_CONFISCATE_INVENTORY=b.comment("Confiscate inventory on new admissions. Default on. Each admission keeps its original policy through config changes and restarts.","Off retains only reviewed safe inventory and still requires every independent confinement, runtime, origin and player-state check. Existing held items are returned normally; enabling this later never confiscates an active retained-inventory session.").define("confiscate_inventory",true);
        JAIL_CERTIFIED_ADMISSIONS=b.comment("Allow only certified jail admission combinations. Default off. Unsupported runtimes, origin hooks, equipment and death stores are always refused; this is not a bypass.","Use console /jailintegrated check <player> before /jailintegrated mode supported. Existing custody recovery does not depend on this switch.").define("certified_admissions",false);
        JAIL_PACKED_WALLETS=b.comment("Opt in to the bounded physical-wallet contract. Requires supported admissions and every existing safety check. Only one unequipped leather wallet in slots 9-35, six slots, no upgrades, auto-exchange off, ordinary gold coins. Equipped wallets remain blocked. Pokemon are outside Jail custody and are left to their ordinary mod behavior.").define("packed_wallet_admissions",false);
        b.pop();
        SPEC = b.build();
    }
    private JailConfig() {}
}

