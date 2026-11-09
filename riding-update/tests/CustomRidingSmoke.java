package dev.unyielding.bundlesmoke;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.CobblemonEntities;
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.api.riding.RidingStyle;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static dev.unyielding.bundlesmoke.BundleSmoke.require;

/** Uses the real connected cloud player and the actual release JAR, with no external pack. */
@EventBusSubscriber(modid = BundleSmoke.MOD_ID)
public final class CustomRidingSmoke {
    private static boolean checked, started;
    private static int index, phase, ticks;
    private static Pokemon original;
    private static Vec3 position;
    private static PokemonEntity current;
    private static Map<String, Object> currentRow;
    private static final List<Map<String, Object>> rows = new ArrayList<>();
    private static final List<String> SPECIES = List.of("mawonna", "mawonna_flower", "covislight",
            "fairy_sliggoo", "inkyeon", "lunavee", "buzzeon", "ghost_jirachi", "creepyeon");
    private static final Map<String, Float> FRIEND_SCALES = Map.of(
            "lunavee", .5f, "buzzeon", .4f, "ghost_jirachi", .4f, "creepyeon", .4f);

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        // Complete the existing interactive checks before temporarily mounting their player.
        if (checked || !Files.exists(Path.of("/tmp/uq-balance-live-passed.json"))) return;
        var player = event.getServer().getPlayerList().getPlayerByName("Dev");
        if (player == null) return;
        var party = Cobblemon.INSTANCE.getStorage().getParty(player);
        try {
            if (!started) {
                original = party.get(0);
                position = player.position();
                started = true;
                require(!Files.exists(event.getServer().getWorldPath(LevelResource.DATAPACK_DIR).resolve("Mawonna.zip")),
                        "External datapack masks bundled definitions");
            }
            String id = SPECIES.get(index / 2);
            boolean shiny = index % 2 != 0;
            if (phase == 0) {
                var species = PokemonSpecies.getByIdentifier(ResourceLocation.parse("cobblemon:" + id));
                require(species != null, "Bundled species failed to load: " + id);
                Pokemon pokemon = new Pokemon();
                pokemon.setSpecies(species);
                pokemon.setLevel(1);
                pokemon.setShiny(shiny);
                pokemon.setScaleModifier(1f);
                pokemon.heal();
                var riding = pokemon.getRiding();
                require(riding != null && !riding.getBehaviours().isEmpty(), "Missing riding behavior: " + id);
                require(riding.getSeats().size() == 1 && "middle".equals(riding.getSeats().getFirst().getLocator()),
                        "Incorrect custom saddle locator: " + id);
                boolean flying = List.of("covislight", "fairy_sliggoo", "buzzeon", "ghost_jirachi").contains(id);
                require(riding.getBehaviours().containsKey(flying ? RidingStyle.AIR : RidingStyle.LAND),
                        "Incorrect riding movement mode: " + id);
                int firstLevelMoves = pokemon.getForm().getMoves().getLevelUpMovesUpTo(1).size();
                if (FRIEND_SCALES.containsKey(id)) {
                    require(Math.abs(pokemon.getForm().getBaseScale() - FRIEND_SCALES.get(id)) < .0001,
                            "Incorrect size for " + id + ": " + pokemon.getForm().getBaseScale());
                    require(firstLevelMoves >= 1 && firstLevelMoves <= 4, "Too many level-one moves: " + id + "=" + firstLevelMoves);
                    require(pokemon.getForm().getMoves().getLevelUpMovesUpTo(100).size() > firstLevelMoves,
                            "Later levels do not unlock moves: " + id);
                }
                party.set(0, pokemon);
                current = new PokemonEntity(player.serverLevel(), pokemon, CobblemonEntities.POKEMON);
                current.setNoAi(true);
                current.setPos(player.getX(), player.getY(), player.getZ());
                current.setOnGround(true);
                require(player.serverLevel().addFreshEntity(current), "Unable to spawn riding fixture: " + id);
                currentRow = new LinkedHashMap<>(Map.of("species", id, "shiny", shiny,
                        "movement_modes", riding.getBehaviours().keySet().toString(),
                        "saddle", "middle", "level_one_moves", firstLevelMoves,
                        "base_scale", pokemon.getForm().getBaseScale()));
                phase = 1;
                ticks = 0;
                return;
            }
            // Allow native controller initialization and Minecraft's 60-tick remount cooldown.
            // After mounting, retain passenger state for twenty actual server ticks.
            if (++ticks < (phase == 1 ? 70 : 20)) return;
            require(current != null && current.isAlive(), "Riding fixture disappeared: " + id);
            if (phase == 1) {
                require(current.getRidingController() != null && current.getRidingController().getBehaviour() != null,
                        "Riding controller failed to initialize: " + id);
                require(current.getOwner() == player, "Fixture ownership did not resolve: " + id);
                require(current.isRideableFor(player), "Normal riding eligibility rejected: " + id);
                require(current.tryRidingPokemon(player), "Mount interaction rejected: " + id);
                require(player.getVehicle() == current, "Player did not mount: " + id);
                phase = 2;
                ticks = 0;
                return;
            }
            require(player.getVehicle() == current && current.getPassengers().contains(player),
                    "Mounted passenger state did not persist: " + id);
            require(current.getSeatForPassenger(player) != null, "Passenger saddle assignment failed: " + id);
            currentRow.put("mounted_for_ticks", ticks);
            rows.add(currentRow);
            cleanup(player);
            if (++index == SPECIES.size() * 2) {
                var report = new LinkedHashMap<>(BundleSmoke.verifyProductionJar());
                report.put("custom_pokemon_riding", rows);
                report.put("embedded_data_without_external_pack", true);
                report.put("scope", "Live owned-Pokemon mount, passenger and saddle state for nine species, normal and shiny, for twenty ticks each; no sustained player-controlled flight test");
                BundleSmoke.write("/tmp/uq-custom-riding-passed.json", report);
                checked = true;
            }
            phase = 0;
        } catch (Throwable failure) {
            checked = true;
            cleanup(player);
            BundleSmoke.fail("/tmp/uq-custom-riding-failed.txt", failure);
        }
    }

    private static void cleanup(ServerPlayer player) {
        player.stopRiding();
        if (current != null) current.discard();
        current = null;
        if (started) {
            Cobblemon.INSTANCE.getStorage().getParty(player).set(0, original);
            player.setPos(position);
        }
    }
}
