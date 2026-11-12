package dev.unyielding.balance.mixin;

import com.cobblemon.mod.common.api.pokemon.stats.Stat;
import com.cobblemon.mod.common.api.pokemon.stats.Stats;
import com.cobblemon.mod.common.pokemon.IVs;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.necro.raid.dens.common.data.raid.RaidBoss;
import com.necro.raid.dens.common.data.raid.RaidTier;
import com.uwustudio.ivrebalance.IvTools;
import com.uwustudio.ivrebalance.RebalanceConfig;
import dev.unyielding.balance.BalanceConfig;
import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = RaidBoss.class, remap = false)
public abstract class RaidBossMixin {
    @Inject(method = "getRewardPokemon", at = @At("RETURN"), remap = false, require = 0)
    private void cubalance$roulette(ServerPlayer player, CallbackInfoReturnable<Pokemon> result) {
        Pokemon reward = result.getReturnValue();
        if (reward == null || !BalanceConfig.RAID_ENABLED.get()) return;
        RaidTier tier = ((RaidBoss)(Object)this).getTier();
        ThreadLocalRandom random = ThreadLocalRandom.current();
        if (tier == RaidTier.TIER_SEVEN) {
            if (random.nextDouble() < RebalanceConfig.TIER_SEVEN_BONUS_CHANCE.get())
                IvTools.ensureMaxIvs(reward, RebalanceConfig.TIER_SEVEN_BONUS_MAX_IVS.get());
            return;
        }
        int count = BalanceConfig.rollPerfectIvs(tier == null ? 1 : tier.ordinal() + 1, random.nextDouble() * 100);
        int cap = BalanceConfig.NORMAL_IV_MAX.get();
        IVs fresh = IVs.createRandomIVs(0);
        IVs target = reward.getIvs();
        var stats = new ArrayList<Stat>(Stats.Companion.getPERMANENT());
        for (Stat stat : stats) {
            Integer value = fresh.get(stat);
            target.set(stat, value == null ? 0 : Math.min(value, cap));
        }
        Collections.shuffle(stats);
        for (int i = 0; i < Math.min(count, stats.size()); i++) target.set(stats.get(i), 31);
    }
}
