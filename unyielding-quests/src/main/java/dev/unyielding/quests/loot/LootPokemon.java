package dev.unyielding.quests.loot;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/** Kept separate so item-only tables work when Cobblemon is absent. */
final class LootPokemon {
    static boolean exists(String id) {
        var key = ResourceLocation.tryParse(id.contains(":") ? id : "cobblemon:" + id);
        return key != null && PokemonSpecies.getByIdentifier(key) != null;
    }

    static void give(ServerPlayer player, String id, int level) {
        PokemonProperties properties = new PokemonProperties();
        properties.setSpecies(id);
        properties.setLevel(level);
        var pokemon = properties.create(player);
        boolean added = Cobblemon.INSTANCE.getStorage().getParty(player).add(pokemon);
        if (!added) added = Cobblemon.INSTANCE.getStorage().getPC(player).add(pokemon);
        if (!added) throw new IllegalStateException("Pokemon storage is full");
    }
}
