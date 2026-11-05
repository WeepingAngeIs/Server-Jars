package dev.unyielding.quests.ultimine;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

/** Settings of the Ultimine module (config/unyieldingquests-ultimine.toml). */
public final class UltimineConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue ENABLED;
    public static final ModConfigSpec.IntValue MAX_BLOCKS;
    public static final ModConfigSpec.DoubleValue EXHAUSTION;
    public static final ModConfigSpec.BooleanValue REQUIRE_TOOL;
    public static final ModConfigSpec.IntValue PREVENT_TOOL_BREAK;
    public static final ModConfigSpec.BooleanValue RIGHT_CLICK_AXE, RIGHT_CLICK_HOE, RIGHT_CLICK_SHOVEL;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> MERGE_TAGS, MERGE_TAGS_SHAPED;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        ENABLED = b.comment("Master switch for vein mining.").define("enabled", true);
        MAX_BLOCKS = b.comment("Most blocks mined at once (besides the one you hit).").defineInRange("max_blocks", 64, 1, 1024);
        EXHAUSTION = b.comment("Hunger cost per extra block, in multiples of a normal block break (0 = free).").defineInRange("exhaustion_per_block", 20.0, 0.0, 1000.0);
        REQUIRE_TOOL = b.comment("Only vein-mine while holding something in your hand.").define("require_tool", false);
        PREVENT_TOOL_BREAK = b.comment("Stop vein-mining when the tool has this much durability left (0 = never stop early).").defineInRange("prevent_tool_break", 0, 0, 1000);
        b.push("right_click");
        RIGHT_CLICK_AXE = b.comment("Strip all connected logs at once with an axe.").define("axe", true);
        RIGHT_CLICK_HOE = b.comment("Till a whole patch of dirt at once with a hoe.").define("hoe", true);
        RIGHT_CLICK_SHOVEL = b.comment("Make paths on a whole patch at once with a shovel.").define("shovel", true);
        b.pop();
        MERGE_TAGS = b.comment("Blocks sharing one of these tags count as the same kind in shapeless mode. * is a wildcard.")
                .defineListAllowEmpty("merge_tags", List.of("minecraft:base_stone_overworld", "c:ores/*", "forge:ores/*"), () -> "", o -> o instanceof String);
        MERGE_TAGS_SHAPED = b.comment("Same as merge_tags but for tunnel and square shapes.")
                .defineListAllowEmpty("merge_tags_shaped", List.of("*"), () -> "", o -> o instanceof String);
        SPEC = b.build();
    }

    private UltimineConfig() {
    }
}
