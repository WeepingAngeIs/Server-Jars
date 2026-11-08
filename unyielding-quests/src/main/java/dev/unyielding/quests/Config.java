package dev.unyielding.quests;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

public final class Config {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue ALLOW_PARTIES;
    public static final ModConfigSpec.BooleanValue SHARE_QUEST_PROGRESS;
    public static final ModConfigSpec.BooleanValue TUTORIAL_ON_JOIN;
    public static final ModConfigSpec.IntValue MAX_PARTY_SIZE;
    public static final ModConfigSpec.IntValue MAX_NAME_LENGTH;
    public static final ModConfigSpec.BooleanValue ANNOUNCE_COMPLETION;
    public static final ModConfigSpec.BooleanValue QUEST_TOASTS;
    public static final ModConfigSpec.BooleanValue INVENTORY_BUTTONS;
    public static final ModConfigSpec.IntValue DETECTION_DELAY_OVERRIDE;
    public static final ModConfigSpec.ConfigValue<String> QUEST_BOOK_SOURCE;

    // ---- Lucky Spin -------------------------------------------------------
    public static final ModConfigSpec.BooleanValue SPIN_ENABLED;
    public static final ModConfigSpec.IntValue SPIN_BASIC;
    public static final ModConfigSpec.IntValue SPIN_GOOD;
    public static final ModConfigSpec.IntValue SPIN_PARADOX;
    public static final ModConfigSpec.IntValue SPIN_MYTHICAL;
    public static final ModConfigSpec.IntValue SPIN_LEGENDARY;
    public static final ModConfigSpec.IntValue SPIN_LEVEL_MIN;
    public static final ModConfigSpec.IntValue SPIN_LEVEL_MAX;
    public static final ModConfigSpec.BooleanValue SPIN_ANNOUNCE;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> SPIN_BASIC_ITEMS;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> SPIN_GOOD_ITEMS;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> SPIN_LEGENDARY_LABELS;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> SPIN_MYTHICAL_LABELS;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> SPIN_PARADOX_LABELS;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> SPIN_EXTRA_LEGENDARY;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> SPIN_EXTRA_MYTHICAL;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> SPIN_EXTRA_PARADOX;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> SPIN_EXCLUDE;

    // ---- Daily login --------------------------------------------------------
    public static final ModConfigSpec.BooleanValue DAILY_ENABLED;
    public static final ModConfigSpec.IntValue DAILY_BASIC;
    public static final ModConfigSpec.IntValue DAILY_GOOD;
    public static final ModConfigSpec.IntValue DAILY_PARADOX;
    public static final ModConfigSpec.IntValue DAILY_MYTHICAL;
    public static final ModConfigSpec.IntValue DAILY_LEGENDARY;
    public static final ModConfigSpec.IntValue DAILY_STREAK_EVERY;
    public static final ModConfigSpec.IntValue DAILY_STREAK_BONUS_SPINS;
    public static final ModConfigSpec.ConfigValue<String> DAILY_TIMEZONE;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.push("teams");
        ALLOW_PARTIES = b.comment("Can players create new party teams? Existing parties remain available. Quest progress sharing is controlled separately.")
                .define("allow_party_creation", true);
        SHARE_QUEST_PROGRESS = b.comment("Share quest completion and task progress between team members. False keeps progress personal even while in a party.",
                        "Existing progress is preserved when changing modes. Each player's reward claims persist across teams and modes.")
                .define("share_quest_progress", false);
        MAX_PARTY_SIZE = b.comment("Maximum players in a party (0 = unlimited).")
                .defineInRange("max_party_size", 0, 0, 1000);
        MAX_NAME_LENGTH = b.comment("Maximum length of a party name.")
                .defineInRange("max_name_length", 32, 3, 64);
        b.pop();
        b.push("tutorial");
        TUTORIAL_ON_JOIN = b.comment("Automatically open the player tutorial after origin selection until the player finishes or dismisses it. /tutorial remains available when this is false.")
                .define("show_on_join", true);
        b.pop();
        b.push("quests");
        ANNOUNCE_COMPLETION = b.comment("Send a chat message to the whole team when a quest is completed.")
                .define("announce_completion", true);
        QUEST_TOASTS = b.comment("Show a small quest-completion popup to the players whose progress was completed.")
                .define("completion_popups", true);
        INVENTORY_BUTTONS = b.comment("Show the Quest Book / Teams buttons on the inventory screen (players can move or hide them in their client settings).")
                .define("inventory_buttons", true);
        DETECTION_DELAY_OVERRIDE = b.comment("Ticks between quest detection scans (item / location tasks). 0 = use the value from the quest book (detection_delay in data.snbt).")
                .defineInRange("detection_delay_override", 0, 0, 1200);
        QUEST_BOOK_SOURCE = b.comment("Where the quest book comes from.",
                        "builtin = the quest book that ships inside this mod (always up to date when you update the jar).",
                        "config_folder = config/unyieldingquests/quests (use this if you edit the quest files yourself; it is created from FTB Quests data or the built-in book when missing).")
                .define("quest_book_source", "builtin", o -> o instanceof String s && (s.equals("builtin") || s.equals("config_folder")));
        b.pop();

        b.comment("Lucky Spin: every quest gives one bonus prize roll per player when its rewards are claimed.",
                "The five chances below are weights; when they add up to 100 they are percentages.").push("lucky_spin");
        SPIN_ENABLED = b.comment("Turn the Lucky Spin on or off.").define("enabled", true);
        SPIN_BASIC = b.comment("Chance of a basic prize (Poke Balls, candies, potions...).").defineInRange("chance_basic", 50, 0, 1_000_000);
        SPIN_GOOD = b.comment("Chance of a good prize (golden apples, evolution stones, vitamins, rare held items...).").defineInRange("chance_good", 44, 0, 1_000_000);
        SPIN_PARADOX = b.comment("Chance of a random Paradox Pokemon.").defineInRange("chance_paradox", 3, 0, 1_000_000);
        SPIN_MYTHICAL = b.comment("Chance of a random Mythical Pokemon.").defineInRange("chance_mythical", 2, 0, 1_000_000);
        SPIN_LEGENDARY = b.comment("Chance of a random Legendary Pokemon.").defineInRange("chance_legendary", 1, 0, 1_000_000);
        SPIN_LEVEL_MIN = b.comment("Lowest level of a prize Pokemon (its stats, nature and ability are random).").defineInRange("pokemon_level_min", 20, 1, 100);
        SPIN_LEVEL_MAX = b.comment("Highest level of a prize Pokemon.").defineInRange("pokemon_level_max", 35, 1, 100);
        SPIN_ANNOUNCE = b.comment("Tell the whole server in chat when somebody wins a Pokemon.").define("announce_pokemon_wins", true);
        SPIN_BASIC_ITEMS = b.comment("Basic prizes. One per line: \"item_id amount\" or \"item_id min-max\", optionally followed by \"x<weight>\" (default weight 10).",
                        "Items that are not in the modpack are skipped.")
                .defineListAllowEmpty("basic_items", List.of(
                        "cobblemon:rare_candy 1-3 x10",
                        "cobblemon:poke_ball 5-10 x12",
                        "cobblemon:great_ball 3-6 x10",
                        "cobblemon:ultra_ball 2-4 x8",
                        "cobblemon:potion 2-4 x8",
                        "cobblemon:super_potion 2-3 x6",
                        "cobblemon:revive 1-2 x6",
                        "cobblemon:full_heal 1-2 x5",
                        "cobblemon:ether 1-2 x5",
                        "cobblemon:exp_candy_s 3-5 x8",
                        "cobblemon:exp_candy_m 2-4 x6",
                        "cobblemon:oran_berry 4-8 x6",
                        "cobblemon:sitrus_berry 2-4 x5",
                        "cobblemon:lure_ball 1-3 x3",
                        "cobblemon:dive_ball 1-3 x3",
                        "cobblemon:dusk_ball 1-3 x3",
                        "cobblemon:quick_ball 1-3 x3",
                        "cobblemon:timer_ball 1-3 x3",
                        "cobblemon:net_ball 1-3 x3",
                        "cobblemon:heal_ball 1-3 x3",
                        "cobblemon:nest_ball 1-3 x3",
                        "cobblemon:repeat_ball 1-3 x3",
                        "cobblemon:luxury_ball 1-2 x2",
                        "cobblemon:premier_ball 1-3 x3",
                        "minecraft:cooked_beef 8-16 x5",
                        "minecraft:golden_carrot 4-8 x5",
                        "minecraft:bread 8-16 x4",
                        "minecraft:iron_ingot 4-8 x6",
                        "minecraft:gold_ingot 3-6 x5",
                        "minecraft:emerald 2-4 x5",
                        "minecraft:experience_bottle 4-8 x6",
                        "minecraft:ender_pearl 2-4 x4",
                        "minecraft:diamond 1-2 x4",
                        "minecraft:torch 16-32 x4",
                        "minecraft:arrow 16-32 x3",
                        "minecraft:lapis_lazuli 8-16 x4",
                        "minecraft:bone_meal 8-16 x3",
                        "minecraft:honey_bottle 2-4 x3"), o -> o instanceof String);
        SPIN_GOOD_ITEMS = b.comment("Good prizes, same format as basic_items.")
                .defineListAllowEmpty("good_items", List.of(
                        "minecraft:golden_apple 2-4 x12",
                        "minecraft:enchanted_golden_apple 1 x2",
                        "minecraft:diamond 4-8 x10",
                        "minecraft:emerald_block 1-2 x4",
                        "minecraft:netherite_scrap 2-4 x5",
                        "minecraft:netherite_ingot 1-2 x3",
                        "minecraft:ancient_debris 2-4 x3",
                        "minecraft:netherite_upgrade_smithing_template 1 x2",
                        "minecraft:totem_of_undying 1 x3",
                        "minecraft:shulker_shell 2-4 x2",
                        "minecraft:heart_of_the_sea 1 x1",
                        "minecraft:nether_star 1 x1",
                        "minecraft:elytra 1 x1",
                        "minecraft:trident 1 x1",
                        "minecraft:name_tag 1-2 x3",
                        "minecraft:saddle 1 x2",
                        "minecraft:dragon_breath 2-4 x2",
                        "minecraft:experience_bottle 16-32 x6",
                        "cobblemon:rare_candy 5-10 x10",
                        "cobblemon:exp_candy_l 3-5 x6",
                        "cobblemon:exp_candy_xl 2-4 x6",
                        "cobblemon:master_ball 1 x1",
                        "cobblemon:ultra_ball 8-16 x6",
                        "cobblemon:max_revive 1-2 x5",
                        "cobblemon:max_potion 2-3 x4",
                        "cobblemon:full_restore 1-2 x4",
                        "cobblemon:max_ether 1-2 x3",
                        "cobblemon:pp_max 1 x3",
                        "cobblemon:pp_up 1-2 x4",
                        "cobblemon:hp_up 2-4 x4",
                        "cobblemon:protein 2-4 x4",
                        "cobblemon:iron 2-4 x4",
                        "cobblemon:calcium 2-4 x4",
                        "cobblemon:zinc 2-4 x4",
                        "cobblemon:carbos 2-4 x4",
                        "cobblemon:ability_capsule 1 x5",
                        "cobblemon:ability_patch 1 x2",
                        "cobblemon:soothe_bell 1 x2",
                        "cobblemon:everstone 1-2 x2",
                        "cobblemon:lucky_egg 1 x3",
                        "cobblemon:exp_share 1 x3",
                        "cobblemon:leftovers 1 x4",
                        "cobblemon:life_orb 1 x3",
                        "cobblemon:focus_sash 1 x3",
                        "cobblemon:assault_vest 1 x3",
                        "cobblemon:choice_band 1 x2",
                        "cobblemon:choice_specs 1 x2",
                        "cobblemon:choice_scarf 1 x2",
                        "cobblemon:eviolite 1 x2",
                        "cobblemon:rocky_helmet 1 x2",
                        "cobblemon:fire_stone 1 x3",
                        "cobblemon:water_stone 1 x3",
                        "cobblemon:thunder_stone 1 x3",
                        "cobblemon:leaf_stone 1 x3",
                        "cobblemon:moon_stone 1 x3",
                        "cobblemon:sun_stone 1 x3",
                        "cobblemon:shiny_stone 1 x3",
                        "cobblemon:dusk_stone 1 x3",
                        "cobblemon:dawn_stone 1 x3",
                        "cobblemon:ice_stone 1 x3",
                        "cobblemon:adamant_mint 1 x2",
                        "cobblemon:jolly_mint 1 x2",
                        "cobblemon:timid_mint 1 x2",
                        "cobblemon:modest_mint 1 x2",
                        "cobblemon:bold_mint 1 x2",
                        "cobblemon:impish_mint 1 x2",
                        "cobblemon:calm_mint 1 x2",
                        "cobblemon:careful_mint 1 x2",
                        "cobblemon:big_root 1 x2",
                        "cobblemon:black_sludge 1 x2",
                        "cobblemon:expert_belt 1 x2",
                        "cobblemon:muscle_band 1 x2",
                        "cobblemon:wise_glasses 1 x2",
                        "cobblemon:quick_claw 1 x2",
                        "cobblemon:kings_rock 1 x2",
                        "cobblemon:razor_claw 1 x2",
                        "cobblemon:razor_fang 1 x2",
                        "cobblemon:metal_coat 1 x2",
                        "cobblemon:upgrade 1 x2",
                        "cobblemon:dubious_disc 1 x1",
                        "cobblemon:dragon_scale 1 x2",
                        "cobblemon:protector 1 x2",
                        "cobblemon:electirizer 1 x2",
                        "cobblemon:magmarizer 1 x2",
                        "cobblemon:reaper_cloth 1 x2",
                        "cobblemon:prism_scale 1 x2",
                        "cobblemon:whipped_dream 1 x2",
                        "cobblemon:sachet 1 x2",
                        "cobblemon:oval_stone 1 x2",
                        "cobblemon:deep_sea_tooth 1 x2",
                        "cobblemon:deep_sea_scale 1 x2",
                        "cobblemon:link_cable 1 x3"), o -> o instanceof String);
        SPIN_LEGENDARY_LABELS = b.comment("Species labels that count as Legendary (Cobblemon labels, e.g. legendary, restricted, ultra_beast).")
                .defineListAllowEmpty("legendary_labels", List.of("legendary"), o -> o instanceof String);
        SPIN_MYTHICAL_LABELS = b.comment("Species labels that count as Mythical.")
                .defineListAllowEmpty("mythical_labels", List.of("mythical"), o -> o instanceof String);
        SPIN_PARADOX_LABELS = b.comment("Species labels that count as Paradox.")
                .defineListAllowEmpty("paradox_labels", List.of("paradox"), o -> o instanceof String);
        SPIN_EXTRA_LEGENDARY = b.comment("Extra species ids added to the Legendary prizes (e.g. cobblemon:mewtwo, or a custom addon Pokemon).")
                .defineListAllowEmpty("extra_legendary", List.of(), o -> o instanceof String);
        SPIN_EXTRA_MYTHICAL = b.comment("Extra species ids added to the Mythical prizes.")
                .defineListAllowEmpty("extra_mythical", List.of(), o -> o instanceof String);
        SPIN_EXTRA_PARADOX = b.comment("Extra species ids added to the Paradox prizes.")
                .defineListAllowEmpty("extra_paradox", List.of(), o -> o instanceof String);
        SPIN_EXCLUDE = b.comment("Species ids that are never given as a prize (e.g. cobblemon:arceus).")
                .defineListAllowEmpty("never_give", List.of(), o -> o instanceof String);
        b.pop();

        b.comment("Daily login reward: one free Lucky Spin per real-world day when a player logs in (uses the prize lists above).").push("daily_login");
        DAILY_ENABLED = b.comment("Turn daily login rewards on or off.").define("enabled", true);
        DAILY_BASIC = b.comment("Daily spin: chance of a basic prize.").defineInRange("chance_basic", 40, 0, 1_000_000);
        DAILY_GOOD = b.comment("Daily spin: chance of a good prize.").defineInRange("chance_good", 50, 0, 1_000_000);
        DAILY_PARADOX = b.comment("Daily spin: chance of a Paradox Pokemon.").defineInRange("chance_paradox", 5, 0, 1_000_000);
        DAILY_MYTHICAL = b.comment("Daily spin: chance of a Mythical Pokemon.").defineInRange("chance_mythical", 3, 0, 1_000_000);
        DAILY_LEGENDARY = b.comment("Daily spin: chance of a Legendary Pokemon.").defineInRange("chance_legendary", 2, 0, 1_000_000);
        DAILY_STREAK_EVERY = b.comment("Every this many days in a row the player gets bonus spins (0 = no streak bonus).").defineInRange("streak_bonus_every_days", 7, 0, 365);
        DAILY_STREAK_BONUS_SPINS = b.comment("How many extra spins the streak bonus gives.").defineInRange("streak_bonus_spins", 2, 1, 20);
        DAILY_TIMEZONE = b.comment("Time zone that decides when a new day starts, e.g. America/Chicago or UTC. Blank = the server's own clock.").define("timezone", "America/Chicago");
        b.pop();
        SPEC = b.build();
    }

    private Config() {
    }
}
