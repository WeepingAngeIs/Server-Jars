package dev.unyielding.quests.spin;

import dev.unyielding.quests.Config;
import dev.unyielding.quests.UnyieldingQuests;
import dev.unyielding.quests.quest.QuestFile;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.items.ItemHandlerHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * The Lucky Spin: one bonus prize roll per quest and player, rolled on the server when the quest's rewards are claimed.
 * Tiers: basic items, good items, a Paradox, a Mythical or a Legendary Pokemon.
 */
public final class LuckySpin {
    /** Prize tiers in the order of the config / network ids. */
    public enum Tier {
        BASIC("Basic prize", 0xFFB8C0D0),
        GOOD("Good prize", 0xFF6EDB8A),
        PARADOX("Paradox Pokémon", 0xFFC27BFF),
        MYTHICAL("Mythical Pokémon", 0xFFFF7AD9),
        LEGENDARY("Legendary Pokémon", 0xFFFFCE40);

        public final String label;
        public final int color;

        Tier(String label, int color) {
            this.label = label;
            this.color = color;
        }

        public boolean isPokemon() {
            return this != BASIC && this != GOOD;
        }

        public static Tier of(int i) {
            Tier[] v = values();
            return v[Math.max(0, Math.min(v.length - 1, i))];
        }
    }

    /** One slot of the spinning strip (the stack is only for display). */
    public record Entry(Tier tier, ItemStack stack) {
    }

    /** One finished spin: the prize and the strip it was drawn from (the prize sits at {@code index}). */
    public record Spin(Tier tier, boolean pokemon, String name, int index, List<Entry> strip) {
    }

    /** Where the prize sits in the strip. */
    public static final int STRIP_LEN = 30;
    public static final int STRIP_INDEX = 24;

    private static final Random RNG = new Random();

    private LuckySpin() {
    }

    /** Id of the virtual "Lucky Spin" reward of a quest (stored in the player's claimed set like a normal reward id). */
    public static long spinId(long questId) {
        long h = questId * 0x9E3779B97F4A7C15L;
        return Long.rotateLeft(h, 29) ^ 0x5ACE5ACE5ACE5ACEL;
    }

    public static boolean enabled() {
        try {
            return Config.SPIN_ENABLED.get();
        } catch (IllegalStateException e) {
            return false;
        }
    }

    /** The five weights in tier order. */
    public static int[] weights() {
        return new int[]{Config.SPIN_BASIC.get(), Config.SPIN_GOOD.get(), Config.SPIN_PARADOX.get(), Config.SPIN_MYTHICAL.get(), Config.SPIN_LEGENDARY.get()};
    }

    // ---- item pools ---------------------------------------------------------

    private record Prize(Item item, int min, int max, int weight) {
        ItemStack roll(Random r) {
            int n = min >= max ? min : min + r.nextInt(max - min + 1);
            return new ItemStack(item, Math.max(1, n));
        }
    }

    private static List<? extends String> basicSrc, goodSrc;
    private static List<Prize> basicPool = List.of(), goodPool = List.of();

    private static synchronized List<Prize> pool(Tier t) {
        List<? extends String> src = t == Tier.BASIC ? Config.SPIN_BASIC_ITEMS.get() : Config.SPIN_GOOD_ITEMS.get();
        if (t == Tier.BASIC) {
            if (src != basicSrc) {
                basicSrc = src;
                basicPool = parse(src);
            }
            return basicPool;
        }
        if (src != goodSrc) {
            goodSrc = src;
            goodPool = parse(src);
        }
        return goodPool;
    }

    /** "item_id amount|min-max [x<weight>]" */
    static List<Prize> parse(List<? extends String> lines) {
        List<Prize> out = new ArrayList<>();
        for (String raw : lines) {
            String[] p = raw.trim().split("\\s+");
            if (p.length == 0 || p[0].isEmpty()) continue;
            ResourceLocation rl = ResourceLocation.tryParse(p[0]);
            Item item = rl == null ? null : BuiltInRegistries.ITEM.getOptional(rl).orElse(null);
            if (item == null || item == Items.AIR) continue;
            int min = 1, max = 1, weight = 10;
            for (int i = 1; i < p.length; i++) {
                String s = p[i].toLowerCase(Locale.ROOT);
                try {
                    if (s.startsWith("x")) {
                        weight = Math.max(0, Integer.parseInt(s.substring(1)));
                    } else if (s.contains("-")) {
                        String[] mm = s.split("-", 2);
                        min = Integer.parseInt(mm[0]);
                        max = Integer.parseInt(mm[1]);
                    } else {
                        min = max = Integer.parseInt(s);
                    }
                } catch (NumberFormatException ignored) {
                }
            }
            if (max < min) max = min;
            if (weight > 0) out.add(new Prize(item, min, max, weight));
        }
        return out;
    }

    private static Prize pick(List<Prize> pool, Random r) {
        int total = 0;
        for (Prize p : pool) total += p.weight;
        if (total <= 0) return null;
        int roll = r.nextInt(total);
        for (Prize p : pool) {
            roll -= p.weight;
            if (roll < 0) return p;
        }
        return pool.get(pool.size() - 1);
    }

    // ---- rolling --------------------------------------------------------------

    private static Tier rollTier(Random r, int[] w) {
        int total = 0;
        for (int x : w) total += Math.max(0, x);
        if (total <= 0) return Tier.BASIC;
        int roll = r.nextInt(total);
        for (int i = 0; i < w.length; i++) {
            roll -= Math.max(0, w[i]);
            if (roll < 0) return Tier.of(i);
        }
        return Tier.BASIC;
    }

    private static boolean cobblemon() {
        return ModList.get().isLoaded("cobblemon");
    }

    public static boolean starterReady(ServerPlayer p) {
        return !cobblemon() || SpinCobblemon.starterReady(p);
    }

    /** A tier that can actually pay out (falls back to items when a Pokemon pool is empty). */
    private static Tier usable(Tier t) {
        if (t.isPokemon()) {
            if (cobblemon() && !SpinCobblemon.pool(t).isEmpty()) return t;
            t = Tier.GOOD;
        }
        if (t == Tier.GOOD && pool(Tier.GOOD).isEmpty()) t = Tier.BASIC;
        return t;
    }

    /** Display-only roll used for the strip (never gives anything). */
    private static Entry decoy(Random r, int[] w) {
        Tier t = usable(rollTier(r, w));
        if (t.isPokemon()) {
            String sp = SpinCobblemon.pick(t, r);
            return new Entry(t, pokemonStack(sp, false));
        }
        List<Prize> pool = pool(t);
        if (pool.isEmpty()) return new Entry(Tier.BASIC, new ItemStack(Items.BREAD));
        return new Entry(t, pick(pool, r).roll(r));
    }

    /**
     * Rolls a spin for the player, gives the prize and returns what to show.
     *
     * @param questTitle plain title of the quest (for chat)
     */
    public static Spin roll(ServerPlayer p, String questTitle) {
        return rollWith(p, questTitle, weights());
    }

    /** Same as {@link #roll} with explicit tier weights (used by the daily login reward). */
    public static Spin rollWith(ServerPlayer p, String questTitle, int[] w) {
        if (!starterReady(p)) throw new IllegalStateException("Choose your starter before using Lucky Spin");
        Tier tier = usable(rollTier(RNG, w));
        ItemStack shown;
        String name;
        boolean pokemon = false;
        if (tier.isPokemon()) {
            String species = SpinCobblemon.pick(tier, RNG);
            int lo = Config.SPIN_LEVEL_MIN.get(), hi = Config.SPIN_LEVEL_MAX.get();
            int level = Math.min(lo, hi) + RNG.nextInt(Math.abs(hi - lo) + 1);
            SpinCobblemon.Given g = SpinCobblemon.give(p, species, level);
            if (g == null) {
                tier = usable(Tier.GOOD);
                Prize pr = pick(pool(tier), RNG);
                shown = pr == null ? new ItemStack(Items.BREAD) : pr.roll(RNG);
                ItemHandlerHelper.giveItemToPlayer(p, shown.copy());
                name = shown.getCount() + " × " + shown.getHoverName().getString();
            } else {
                pokemon = true;
                shown = pokemonStack(species, g.shiny());
                name = (g.shiny() ? "Shiny " : "") + g.name() + " (Lv. " + g.level() + ")";
            }
        } else {
            Prize pr = pick(pool(tier), RNG);
            shown = pr == null ? new ItemStack(Items.BREAD) : pr.roll(RNG);
            ItemHandlerHelper.giveItemToPlayer(p, shown.copy());
            name = shown.getCount() + " × " + shown.getHoverName().getString();
        }
        List<Entry> strip = new ArrayList<>(STRIP_LEN);
        for (int i = 0; i < STRIP_LEN; i++) strip.add(i == STRIP_INDEX ? new Entry(tier, shown.copy()) : decoy(RNG, w));
        Spin spin = new Spin(tier, pokemon, name, STRIP_INDEX, strip);
        final int col = tier.color & 0xFFFFFF;
        final String tierLabel = tier.label;

        Component line = Component.literal("Lucky Spin: ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal(tierLabel + " — ").withStyle(s -> s.withColor(col)))
                .append(Component.literal(name).withStyle(ChatFormatting.WHITE));
        p.sendSystemMessage(line);
        if (pokemon && Config.SPIN_ANNOUNCE.get() && p.getServer() != null) {
            Component all = Component.literal(p.getGameProfile().getName()).withStyle(ChatFormatting.YELLOW)
                    .append(Component.literal(" won a ").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(tierLabel.replace(" Pokémon", "") + " " + name).withStyle(s -> s.withColor(col)))
                    .append(Component.literal(" from the Lucky Spin! (" + questTitle + ")").withStyle(ChatFormatting.GRAY));
            for (ServerPlayer o : p.getServer().getPlayerList().getPlayers()) if (o != p) o.sendSystemMessage(all);
        }
        UnyieldingQuests.LOGGER.info("[spin] {} won {} ({}) for quest {}", p.getGameProfile().getName(), name, tier, questTitle);
        return spin;
    }

    /** A Cobblemon "Pokemon model" item showing a species (Poke Ball when that item is missing). */
    public static ItemStack pokemonStack(String species, boolean shiny) {
        QuestFile.ItemRef r = QuestFile.ItemRef.of("cobblemon:pokemon_model");
        CompoundTag c = new CompoundTag(), pi = new CompoundTag();
        pi.putString("species", species.contains(":") ? species : "cobblemon:" + species);
        ListTag aspects = new ListTag();
        if (shiny) aspects.add(StringTag.valueOf("shiny"));
        pi.put("aspects", aspects);
        c.put("cobblemon:pokemon_item", pi);
        r.components = c;
        if (!r.exists()) {
            Item ball = BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse("cobblemon:poke_ball")).orElse(Items.ENDER_EYE);
            return new ItemStack(ball);
        }
        return r.stack();
    }
}
