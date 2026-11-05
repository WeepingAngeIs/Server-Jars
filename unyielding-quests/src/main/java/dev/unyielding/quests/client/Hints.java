package dev.unyielding.quests.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.unyielding.quests.UnyieldingQuests;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;

import java.io.Reader;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** "How to get it" lines for quest items: crafting stations from the live recipe list, otherwise a bundled hint. */
public final class Hints {
    private static final Map<String, String> STATIONS = new HashMap<>();

    static {
        STATIONS.put("minecraft:crafting", "Crafting Table");
        STATIONS.put("minecraft:smelting", "Furnace");
        STATIONS.put("minecraft:blasting", "Blast Furnace");
        STATIONS.put("minecraft:smoking", "Smoker");
        STATIONS.put("minecraft:campfire_cooking", "Campfire");
        STATIONS.put("minecraft:stonecutting", "Stonecutter");
        STATIONS.put("minecraft:smithing", "Smithing Table");
        STATIONS.put("farmersdelight:cooking", "Cooking Pot");
        STATIONS.put("farmersdelight:cutting", "Cutting Board");
        STATIONS.put("cobblemon:cooking_pot", "Campfire Pot");
        STATIONS.put("cobblemon:brewing_stand", "Brewing Stand");
        STATIONS.put("aether:freezing", "Freezer");
        STATIONS.put("aether:enchanting", "Altar");
        STATIONS.put("aether:incubation", "Incubator");
        STATIONS.put("iceandfire:dragonforge", "Dragon Forge");
        STATIONS.put("eternal_starlight:alloy", "Alloy Furnace");
    }

    private static Map<String, String> bundled;
    private static WeakReference<RecipeManager> indexedFor = new WeakReference<>(null);
    private static final Map<Item, Set<String>> MADE_AT = new HashMap<>();

    private Hints() {
    }

    /** Called on resource reload. */
    public static void reload() {
        bundled = null;
    }

    private static Map<String, String> bundled() {
        if (bundled != null) return bundled;
        Map<String, String> m = new HashMap<>();
        try {
            ResourceLocation rl = ResourceLocation.fromNamespaceAndPath(UnyieldingQuests.MODID, "ui/item_hints.json");
            var res = Minecraft.getInstance().getResourceManager().getResource(rl);
            if (res.isPresent()) {
                try (Reader r = res.get().openAsReader()) {
                    JsonObject o = JsonParser.parseReader(r).getAsJsonObject();
                    for (var e : o.entrySet()) m.put(e.getKey(), e.getValue().getAsString());
                }
            }
        } catch (Exception e) {
            UnyieldingQuests.LOGGER.warn("Could not read item hints: {}", e.toString());
        }
        bundled = m;
        return m;
    }

    private static void index() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        RecipeManager rm = mc.level.getRecipeManager();
        if (indexedFor.get() == rm) return;
        indexedFor = new WeakReference<>(rm);
        MADE_AT.clear();
        for (RecipeHolder<?> h : rm.getRecipes()) {
            try {
                ItemStack out = h.value().getResultItem(mc.level.registryAccess());
                if (out.isEmpty()) continue;
                ResourceLocation type = BuiltInRegistries.RECIPE_TYPE.getKey(h.value().getType());
                String station = type == null ? "a crafting station" : STATIONS.getOrDefault(type.toString(), pretty(type.getPath()));
                MADE_AT.computeIfAbsent(out.getItem(), k -> new LinkedHashSet<>()).add(station);
            } catch (Exception ignored) {
                // some modded recipes cannot report a result outside their machine
            }
        }
    }

    private static String pretty(String path) {
        String s = path.replace('_', ' ');
        StringBuilder sb = new StringBuilder();
        boolean up = true;
        for (char c : s.toCharArray()) {
            sb.append(up ? Character.toUpperCase(c) : c);
            up = c == ' ';
        }
        return sb.toString();
    }

    /** One short line telling the player how to get this item, or "" when nothing is known. */
    public static String howToGet(ItemStack stack) {
        if (stack.isEmpty()) return "";
        index();
        Set<String> at = MADE_AT.get(stack.getItem());
        if (at != null && !at.isEmpty()) {
            StringBuilder sb = new StringBuilder("Made at ");
            int i = 0;
            for (String s : at) {
                if (i == 2) break;
                if (i > 0) sb.append(" or ");
                sb.append("AEIOU".indexOf(Character.toUpperCase(s.charAt(0))) >= 0 ? "an " : "a ").append(s);
                i++;
            }
            return sb.append(". Hover it and press R for the recipe.").toString();
        }
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        String h = bundled().get(id.toString());
        return h == null ? "" : h;
    }
}
