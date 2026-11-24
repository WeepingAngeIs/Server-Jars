package dev.unyielding.jail.mixin;

import dev.unyielding.jail.JailRetainedCustody;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameRules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** The native clone copies its own retained inventory once after the bound corpse passes verification. */
@Mixin(ServerPlayer.class)
public abstract class JailRetainedCloneMixin {
    @Redirect(method="restoreFrom",at=@At(value="INVOKE",target="Lnet/minecraft/world/level/GameRules;getBoolean(Lnet/minecraft/world/level/GameRules$Key;)Z"),require=1)
    private boolean jail$retainDuringVerifiedClone(GameRules rules,GameRules.Key<GameRules.BooleanValue> key,ServerPlayer original,boolean keepEverything){
        if(key==GameRules.RULE_KEEPINVENTORY&&JailRetainedCustody.keepInventoryAtClone(original,(ServerPlayer)(Object)this))return true;
        return rules.getBoolean(key);
    }
}
