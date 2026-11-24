package dev.unyielding.jail.mixin;

import dev.unyielding.jail.JailRetainedCustody;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameRules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Per-invocation vanilla retention; never changes the world's keepInventory rule. */
@Mixin(Player.class)
public abstract class JailRetainedDropMixin {
    @Redirect(method="dropEquipment",at=@At(value="INVOKE",target="Lnet/minecraft/world/level/GameRules;getBoolean(Lnet/minecraft/world/level/GameRules$Key;)Z"),require=1)
    private boolean jail$retainDuringVerifiedDeath(GameRules rules,GameRules.Key<GameRules.BooleanValue> key){
        if(key==GameRules.RULE_KEEPINVENTORY&&(Object)this instanceof ServerPlayer player&&JailRetainedCustody.keepInventoryAtDrop(player))return true;
        return rules.getBoolean(key);
    }
}
