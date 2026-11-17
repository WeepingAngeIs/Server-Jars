package dev.unyielding.jail;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Separate config; never import an enabled admission switch from the old bundle. */
public final class JailConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue JAIL_CERTIFIED_ADMISSIONS;
    public static final ModConfigSpec.BooleanValue JAIL_PACKED_WALLETS;
    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.push("jail");
        JAIL_CERTIFIED_ADMISSIONS=b.comment("Allow only certified jail admission combinations. Default off. Unsupported runtimes, origin hooks, equipment and death stores are always refused; this is not a bypass.","Use console /jailintegrated check <player> before /jailintegrated mode supported. Existing custody recovery does not depend on this switch.").define("certified_admissions",false);
        JAIL_PACKED_WALLETS=b.comment("Opt in to the bounded physical-wallet contract. Requires supported admissions and every existing safety check. Only one unequipped leather wallet in slots 9-35, six slots, no upgrades, auto-exchange off, ordinary gold coins. Equipped wallets and nonempty Pokemon parties remain blocked.").define("packed_wallet_admissions",false);
        b.pop();
        SPEC = b.build();
    }
    private JailConfig() {}
}
