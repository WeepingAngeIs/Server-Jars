package dev.unyielding.bundlesmoke;

import com.google.gson.Gson;
import dev.unyielding.quests.Config;
import dev.unyielding.quests.settings.AdminSettings;
import dev.unyielding.quests.settings.ConfigEditor;
import dev.unyielding.quests.spin.LuckySpin;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.ModConfigSpec;
import java.nio.file.Files;
import java.util.*;
import static dev.unyielding.bundlesmoke.BundleSmoke.require;

/** Exercises the real combined JAR's odds transaction, persistence and selection. */
final class SpinOddsSmoke {
    private static final Gson JSON = new Gson();
    private static ModConfigSpec.IntValue[] values(boolean daily) {
        return daily ? new ModConfigSpec.IntValue[]{Config.DAILY_BASIC, Config.DAILY_GOOD, Config.DAILY_PARADOX, Config.DAILY_MYTHICAL, Config.DAILY_LEGENDARY}
                : new ModConfigSpec.IntValue[]{Config.SPIN_BASIC, Config.SPIN_GOOD, Config.SPIN_PARADOX, Config.SPIN_MYTHICAL, Config.SPIN_LEGENDARY};
    }
    private static int[] snapshot(boolean daily) { return Arrays.stream(values(daily)).mapToInt(v -> v.get()).toArray(); }
    private static String disk() throws Exception {
        return Files.readString(FMLPaths.CONFIGDIR.get().resolve("unyieldingquests-common.toml"));
    }
    private static void restore(boolean daily, int[] before) {
        var values = values(daily);
        for (int i = 0; i < 5; i++) values[i].set(before[i]);
    }
    static void exercise(ServerPlayer player, Map<String, Object> report) throws Exception {
        var players = player.server.getPlayerList();
        var profile = player.getGameProfile();
        boolean wasOp = players.isOp(profile);
        int[] questBefore = snapshot(false), dailyBefore = snapshot(true);
        try {
            for (boolean daily : new boolean[]{false, true}) {
                String group = daily ? "daily" : "quest";
                String expected = JSON.toJson(snapshot(daily));
                String veryRare = "[6000,3984,10,5,1]";
                players.deop(profile);
                reject(player, group, expected, veryRare);
                players.op(profile);
                for (String bad : List.of("[]", "[0,0,0,0,0]", "[-1,1,1,1,1]", "[1000001,1,1,1,1]",
                        "[1,1,1,1]", "[1,1,1,1,1,1]", "[1,1,1,1,0.5]", "[1,1,1,1,\"2\"]", "null", "{}"))
                    reject(player, group, expected, bad);
                int[] other = snapshot(!daily);
                ConfigEditor.handle(player, new ConfigEditor.Action("__odds:" + group, expected, veryRare));
                require(Arrays.equals(snapshot(daily), new int[]{6000,3984,10,5,1}), "Odds transaction did not apply " + group);
                require(Arrays.equals(snapshot(!daily), other), "Editing " + group + " changed the other spin group");
                String text = disk();
                String section = daily ? "daily_login" : "lucky_spin";
                int start = text.indexOf("[" + section + "]"), end = text.indexOf("\n[", start + 1);
                String block = text.substring(start, end < 0 ? text.length() : end);
                require(block.matches("(?s).*chance_basic\\s*=\\s*6000.*") && block.matches("(?s).*chance_legendary\\s*=\\s*1(?:\\s|$).*"),
                        "Atomic odds not persisted in " + section);
                reject(player, group, expected, "[6000,4000,0,0,0]");
                ConfigEditor.handle(player, new ConfigEditor.Action("__odds:" + group, veryRare, "[6000,4000,0,0,0]"));
                require(Arrays.equals(snapshot(daily), new int[]{6000,4000,0,0,0}), "Items-only preset did not disable Pokemon");
                players.deop(profile);
                reject(player, group, "[6000,4000,0,0,0]", veryRare);
            }
            players.op(profile);
            reject(player, "unknown", "[]", "[6000,4000,0,0,0]");
            verifySelection();
            verifyPoolRefresh();
            report.put("spin_odds_admin_only_atomic_groups_stale_rejection_and_persistence", "passed");
            report.put("spin_odds_exact_10000_roll_distribution_and_zero_tiers", "passed");
            report.put("spin_extra_species_edits_refresh_without_restart", "passed");
        } finally {
            restore(false, questBefore); restore(true, dailyBefore); Config.SPEC.save();
            if (wasOp) players.op(profile); else players.deop(profile);
            AdminSettings.publishChanges();
        }
        require(Arrays.equals(snapshot(false), questBefore) && Arrays.equals(snapshot(true), dailyBefore), "Spin test failed to restore settings");
    }
    private static void reject(ServerPlayer player, String group, String expected, String next) throws Exception {
        int[] quest = snapshot(false), daily = snapshot(true);
        String beforeDisk = disk();
        ConfigEditor.handle(player, new ConfigEditor.Action("__odds:" + group, expected, next));
        require(Arrays.equals(quest, snapshot(false)) && Arrays.equals(daily, snapshot(true)) && beforeDisk.equals(disk()),
                "Rejected odds edit changed state: " + group + " " + next);
    }
    private static void verifySelection() throws Exception {
        var method = LuckySpin.class.getDeclaredMethod("rollTier", Random.class, int[].class);
        method.setAccessible(true);
        for (int[] weights : new int[][]{{6000,3984,10,5,1}, {6000,4000,0,0,0}}) {
            int[] counts = new int[5];
            for (int roll = 0; roll < 10000; roll++) {
                final int selected = roll;
                Random random = new Random(0) { @Override public int nextInt(int bound) {
                    require(bound == 10000, "Wrong odds denominator"); return selected;
                }};
                LuckySpin.Tier result = (LuckySpin.Tier)method.invoke(null, random, weights);
                counts[result.ordinal()]++;
            }
            require(Arrays.equals(counts, weights), "Tier boundaries do not match configured probabilities");
        }
    }
    private static void verifyPoolRefresh() throws Exception {
        var labels = Config.SPIN_PARADOX_LABELS.get(); var extras = Config.SPIN_EXTRA_PARADOX.get(); var exclude = Config.SPIN_EXCLUDE.get();
        var pool = Class.forName("dev.unyielding.quests.spin.SpinCobblemon").getDeclaredMethod("pool", LuckySpin.Tier.class);
        pool.setAccessible(true);
        try {
            Config.SPIN_PARADOX_LABELS.set(List.of()); Config.SPIN_EXTRA_PARADOX.set(List.of()); Config.SPIN_EXCLUDE.set(List.of());
            require(((List<?>)pool.invoke(null, LuckySpin.Tier.PARADOX)).isEmpty(), "Empty species fixture is not empty");
            Config.SPIN_EXTRA_PARADOX.set(List.of("cobblemon:pikachu"));
            require(((List<?>)pool.invoke(null, LuckySpin.Tier.PARADOX)).contains("cobblemon:pikachu"), "Added species not refreshed");
            Config.SPIN_EXTRA_PARADOX.set(List.of());
            require(((List<?>)pool.invoke(null, LuckySpin.Tier.PARADOX)).isEmpty(), "Removed species not refreshed");
        } finally {
            Config.SPIN_PARADOX_LABELS.set(labels); Config.SPIN_EXTRA_PARADOX.set(extras); Config.SPIN_EXCLUDE.set(exclude);
            pool.invoke(null, LuckySpin.Tier.PARADOX);
        }
    }
}
