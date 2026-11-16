package dev.unyielding.jail;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Separate config; never import an enabled admission switch from the old bundle. */
public final class JailConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue JAIL_CERTIFIED_ADMISSIONS;
    public static final ModConfigSpec.BooleanValue PACKED_WALLET_ADMISSIONS;
    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.push("jail");
        JAIL_CERTIFIED_ADMISSIONS=b.comment("Allow only certified jail admission combinations. Default off. Unsupported runtimes, origin hooks, equipment and death stores are always refused; this is not a bypass.","Use console /jailintegrated check <player> before /jailintegrated mode supported. Existing custody recovery does not depend on this switch.").define("certified_admissions",false);
        PACKED_WALLET_ADMISSIONS=b.comment("Separately opt in to the bounded packed leather-wallet contract. One non-selected main-inventory wallet, no upgrades or bank access, only audited gold coins. Equipped wallets and nonempty Pokemon parties remain refused. Recovery of existing records does not depend on this switch.").define("packed_wallet_admissions",false);
        b.pop();
        SPEC = b.build();
    }
    private JailConfig() {}
}
