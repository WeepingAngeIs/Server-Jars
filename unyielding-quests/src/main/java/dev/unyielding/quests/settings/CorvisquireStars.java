package dev.unyielding.quests.settings;

import com.cobblemon.mod.common.api.pokemon.evolution.PassiveEvolution;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.pathfinder.Path;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import java.util.Comparator;
import java.util.Map;
import java.util.WeakHashMap;

/** Dropped Nether Stars belong to the individual Corvisquire that eats them. */
public final class CorvisquireStars {
    private static final String COUNT = "unyielding_corvisquire_nether_stars";
    private static final int GOAL = 120;
    private record Bite(ItemStack item, int finish) {}
    private static final Map<PokemonEntity, Bite> BITES = new WeakHashMap<>();
    private static final Map<PokemonEntity, Path> PATHS = new WeakHashMap<>();
    private static final Map<PokemonEntity, Integer> CRIES = new WeakHashMap<>();
    public static int count(Pokemon pokemon) { return Math.max(0, pokemon.getPersistentData().getInt(COUNT)); }
    private static void stop(PokemonEntity entity) {
        Path path = PATHS.remove(entity);
        if (path != null && path == entity.getNavigation().getPath()) entity.getNavigation().stop();
    }
    private static boolean walk(PokemonEntity entity, net.minecraft.world.entity.Entity target) {
        if (!entity.getNavigation().moveTo(target, 1.15)) return false;
        PATHS.put(entity, entity.getNavigation().getPath());
        return true;
    }
    @SubscribeEvent public void onTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof PokemonEntity entity) || !(entity.level() instanceof ServerLevel level)) return;
        if (!entity.getPokemon().getSpecies().getName().equalsIgnoreCase("corvisquire")) return;
        if (!entity.isAlive() || entity.isBusy() || entity.isBattling() || entity.isPassenger() || entity.isVehicle()) { stop(entity); return; }
        Bite bite = BITES.get(entity);
        if (bite != null) {
            if (entity.tickCount >= bite.finish()) BITES.remove(entity);
            else if (entity.tickCount % 6 == 0) {
                level.broadcastEntityEvent(entity, (byte)45);
                entity.playSound(entity.getEatingSound(bite.item()), 0.8f, 1f);
                var mouth = entity.getEyePosition().add(entity.getLookAngle().scale(.25));
                level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, bite.item()), mouth.x, mouth.y, mouth.z, 4, .08, .06, .08, .015);
            }
            return;
        }
        if (entity.tickCount % 10 != 0) return;
        Pokemon pokemon = entity.getPokemon();
        if (count(pokemon) >= GOAL) { stop(entity); return; }
        ServerPlayer lure = null;
        double nearest = 64;
        for (ServerPlayer player : level.players()) {
            double distance = entity.distanceToSqr(player);
            if (distance > nearest || player.isSpectator() || !player.isAlive() || !entity.hasLineOfSight(player)) continue;
            for (ItemStack hand : player.getHandSlots()) if (hand.is(Items.NETHER_STAR)) { lure = player; nearest = distance; break; }
        }
        if (lure != null && entity.tickCount - CRIES.getOrDefault(entity, -10000) >= 200) {
            entity.cry(); CRIES.put(entity, entity.tickCount);
        }
        var drops = level.getEntitiesOfClass(ItemEntity.class, entity.getBoundingBox().inflate(8, 3, 8), drop ->
                drop.isAlive() && drop.getItem().is(Items.NETHER_STAR) && drop.getOwner() instanceof ServerPlayer);
        drops.sort(Comparator.comparingDouble(entity::distanceToSqr));
        for (ItemEntity drop : drops) {
            if (entity.distanceToSqr(drop) > 2.25 || !entity.hasLineOfSight(drop)) { if (walk(entity, drop)) return; else continue; }
            if (!(drop.getOwner() instanceof ServerPlayer feeder)) continue;
            ItemStack food = drop.getItem().copyWithCount(1);
            int next = Math.min(GOAL, count(pokemon) + 1);
            pokemon.getPersistentData().putInt(COUNT, next);
            stop(entity); entity.getLookControl().setLookAt(drop); entity.take(drop, 1);
            ItemStack remaining = drop.getItem().copy(); remaining.shrink(1);
            if (remaining.isEmpty()) drop.discard(); else drop.setItem(remaining);
            BITES.put(entity, new Bite(food, entity.tickCount + 30));
            feeder.sendSystemMessage(Component.literal("Corvisquire ate a Nether Star: " + next + "/" + GOAL));
            if (next == GOAL) for (var evolution : pokemon.getForm().getEvolutions())
                if (evolution.getId().equals("corvisquire_covislight") && evolution instanceof PassiveEvolution passive) passive.attemptEvolution(pokemon);
            return;
        }
        if (lure != null) { entity.getLookControl().setLookAt(lure); if (entity.distanceToSqr(lure) > 4 && walk(entity, lure)) return; }
        stop(entity);
    }
}
