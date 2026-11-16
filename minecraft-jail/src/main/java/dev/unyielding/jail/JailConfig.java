package dev.unyielding.jail;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Separate config; never import an enabled admission switch from the old bundle. */
public final class JailConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue JAIL_CERTIFIED_ADMISSIONS;
    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.push("jail");
        JAIL_CERTIFIED_ADMISSIONS=b.comment("Allow only certified jail admission combinations. Default off. Unsupported runtimes, origin hooks, equipment and death stores are always refused; this is not a bypass.","Use console /jailintegrated check <player> before /jailintegrated mode supported. Existing custody recovery does not depend on this switch.").define("certified_admissions",false);
        b.pop();
        SPEC = b.build();
    }
    private JailConfig() {}
}
