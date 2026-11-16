package dev.unyielding.jail.mixin;

import dev.unyielding.jail.JailXp;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Suppress only the XP drop of the journal-bound player inside verified jail damage. */
@Mixin(LivingEntity.class)
public abstract class JailXpDropMixin {
    @Inject(method="dropExperience",at=@At("HEAD"),cancellable=true)
    private void retainedXp(Entity attacker,CallbackInfo ci){if((Object)this instanceof ServerPlayer p&&JailXp.suppressDeathDrop(p))ci.cancel();}
}
