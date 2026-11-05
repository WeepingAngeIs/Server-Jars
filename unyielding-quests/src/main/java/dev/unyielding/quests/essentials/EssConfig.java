package dev.unyielding.quests.essentials;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

/** Settings of the Essentials module (config/unyieldingquests-essentials.toml). */
public final class EssConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> DISABLED;
    public static final ModConfigSpec.IntValue HOME_MAX, HOME_COOLDOWN;
    public static final ModConfigSpec.IntValue BACK_MAX, BACK_COOLDOWN;
    public static final ModConfigSpec.BooleanValue BACK_ONLY_ON_DEATH;
    public static final ModConfigSpec.IntValue SPAWN_COOLDOWN, PLAYERSPAWN_COOLDOWN, WARP_COOLDOWN, TPA_COOLDOWN;
    public static final ModConfigSpec.IntValue RTP_COOLDOWN, RTP_MIN, RTP_MAX, RTP_TRIES;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> RTP_BLACKLIST, RTP_WHITELIST;
    public static final ModConfigSpec.IntValue NEAR_RADIUS;
    public static final ModConfigSpec.BooleanValue ADMINS_EXEMPT_COOLDOWNS;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        DISABLED = b.comment("Commands to switch off completely (needs a restart). Example: [\"rtp\", \"hat\", \"nick\"]")
                .defineListAllowEmpty("disabled_commands", List.of(), () -> "", o -> o instanceof String);
        ADMINS_EXEMPT_COOLDOWNS = b.comment("Operators ignore teleport cooldowns.").define("admins_exempt_cooldowns", true);
        b.push("home");
        HOME_MAX = b.comment("How many homes a player may set.").defineInRange("max", 1, 1, 1000);
        HOME_COOLDOWN = b.comment("Seconds between /home uses.").defineInRange("cooldown", 10, 0, 86400);
        b.pop();
        b.push("back");
        BACK_MAX = b.comment("How many past locations /back remembers.").defineInRange("max", 10, 1, 100);
        BACK_COOLDOWN = b.comment("Seconds between /back uses.").defineInRange("cooldown", 30, 0, 86400);
        BACK_ONLY_ON_DEATH = b.comment("If true /back only returns you to where you died.").define("only_on_death", false);
        b.pop();
        b.push("cooldowns");
        SPAWN_COOLDOWN = b.defineInRange("spawn", 10, 0, 86400);
        PLAYERSPAWN_COOLDOWN = b.defineInRange("playerspawn", 10, 0, 86400);
        WARP_COOLDOWN = b.defineInRange("warp", 10, 0, 86400);
        TPA_COOLDOWN = b.defineInRange("tpa", 10, 0, 86400);
        b.pop();
        b.push("rtp");
        RTP_COOLDOWN = b.comment("Seconds between /rtp uses.").defineInRange("cooldown", 600, 0, 86400);
        RTP_MIN = b.comment("Minimum distance from the world spawn.").defineInRange("min_distance", 500, 0, 1000000);
        RTP_MAX = b.comment("Maximum distance from the world spawn.").defineInRange("max_distance", 25000, 1, 1000000);
        RTP_TRIES = b.comment("How many random spots to try before giving up.").defineInRange("max_tries", 100, 1, 1000);
        RTP_BLACKLIST = b.comment("Dimensions where /rtp does not work.")
                .defineListAllowEmpty("dimension_blacklist", List.of("minecraft:the_end"), () -> "", o -> o instanceof String);
        RTP_WHITELIST = b.comment("If not empty /rtp only works in these dimensions.")
                .defineListAllowEmpty("dimension_whitelist", List.of(), () -> "", o -> o instanceof String);
        b.pop();
        NEAR_RADIUS = b.comment("Radius used by /near.").defineInRange("near_radius", 200, 1, 10000);
        SPEC = b.build();
    }

    private EssConfig() {
    }

    public static boolean disabled(String cmd) {
        for (String s : DISABLED.get()) if (s.equalsIgnoreCase(cmd)) return true;
        return false;
    }
}
