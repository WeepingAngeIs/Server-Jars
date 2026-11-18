package dev.unyielding.jail.mixin;

import dev.unyielding.jail.JailFoodProgress;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.ScoreAccess;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Scoreboard.class)
public abstract class OnionDeathScoreMixin {
    @Redirect(method="forAllObjectives",at=@At(value="INVOKE",target="Ljava/util/List;forEach(Ljava/util/function/Consumer;)V",remap=false),require=1)
    private void jail$foodCycle(List<Objective> objectives, Consumer<Objective> action, ObjectiveCriteria criteria,
                                ScoreHolder owner, Consumer<ScoreAccess> update) {
        if (criteria==ObjectiveCriteria.DEATH_COUNT && owner instanceof ServerPlayer player && JailFoodProgress.protectsScore(player)) {
            for (Objective objective : objectives) if (!objective.getName().equals("so_death")) action.accept(objective);
        } else objectives.forEach(action);
    }
}
