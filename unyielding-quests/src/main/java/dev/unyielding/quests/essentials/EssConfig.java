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
    public static final ModConfigSpec.BooleanValue ADMIN_CLAIM_BYPASS;
    public static final ModConfigSpec.BooleanValue HOME_ENABLED, BACK_ENABLED, WARP_ENABLED, RTP_ENABLED;
    public static final ModConfigSpec.BooleanValue TPA_ENABLED, NICK_ENABLED, RECORDING_ENABLED, NEAR_ENABLED;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        DISABLED = b.comment("Commands to switch off completely (needs a restart). Example: [\"rtp\", \"hat\", \"nick\"]")
                .worldRestart().defineListAllowEmpty("disabled_commands", List.of(), () -> "", o -> o instanceof String);
        ADMINS_EXEMPT_COOLDOWNS = b.comment("Operators ignore teleport cooldowns.").define("admins_exempt_cooldowns", true);
        ADMIN_CLAIM_BYPASS = b.comment("Automatically bypass claim interaction protection for online operators. Turning OFF removes this automatic bypass; existing OPAC manual modes remain separate. Requires Open Parties and Claims.").define("admin_claim_bypass", false);
        b.push("home");
        HOME_ENABLED = b.comment("Allow /home, /sethome, /delhome and /listhomes. Turning off preserves all saved homes. Changes apply immediately.").define("enabled", true);
        HOME_MAX = b.comment("How many homes a player may set.").defineInRange("max", 1, 1, 1000);
        HOME_COOLDOWN = b.comment("Seconds between /home uses.").defineInRange("cooldown", 10, 0, 86400);
        b.pop();
        b.push("back");
        BACK_ENABLED = b.comment("Allow /back. Turning off preserves existing destination history; recording continues. Changes apply immediately.").define("enabled", true);
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
        RTP_ENABLED = b.comment("Allow random teleportation with /rtp. Turning off avoids new random-location searches. Changes apply immediately.").define("enabled", true);
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
        b.push("warps");
        WARP_ENABLED = b.comment("Allow /warp, /setwarp, /delwarp and /listwarps. Saved warps are preserved while disabled. Changes apply immediately.").define("enabled", true);
        b.pop();
        b.push("teleport_requests");
        TPA_ENABLED = b.comment("Allow /tpa, /tpahere, /tpaccept and /tpdeny. Turning off cancels pending requests. Changes apply immediately.").define("enabled", true);
        b.pop();
        b.push("nicknames");
        NICK_ENABLED = b.comment("Allow /nick and display saved nicknames. Turning off shows real names without deleting saved nicknames. Changes apply immediately.").define("enabled", true);
        b.pop();
        b.push("recording");
        RECORDING_ENABLED = b.comment("Allow /rec and show recording tags. Turning off hides tags without deleting saved recording states. Changes apply immediately.").define("enabled", true);
        b.pop();
        b.push("near");
        NEAR_ENABLED = b.comment("Allow /near to list nearby players. Changes apply immediately.").define("enabled", true);
        b.pop();
        SPEC = b.build();
    }

    private EssConfig() {
    }

    /** Live switches are independent of the restart-only command-removal list. */
    public static ModConfigSpec.BooleanValue featureSwitch(String command) {
        return switch (command) {
            case "home", "sethome", "delhome", "listhomes" -> HOME_ENABLED;
            case "back" -> BACK_ENABLED;
            case "warp", "setwarp", "delwarp", "listwarps" -> WARP_ENABLED;
            case "rtp" -> RTP_ENABLED;
            case "tpa", "tpahere", "tpaccept", "tpdeny" -> TPA_ENABLED;
            case "nick" -> NICK_ENABLED;
            case "rec" -> RECORDING_ENABLED;
            case "near" -> NEAR_ENABLED;
            default -> null;
        };
    }

    public static boolean disabled(String cmd) {
        for (String s : DISABLED.get()) if (s.equalsIgnoreCase(cmd)) return true;
        return false;
    }
}
