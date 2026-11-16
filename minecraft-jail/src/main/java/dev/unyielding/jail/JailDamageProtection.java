package dev.unyielding.jail;

import dev.unyielding.jail.MinecraftJail;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/** Certified custody prevents outside deaths; only a verified, scoped escape attempt may apply damage. */
@EventBusSubscriber(modid=MinecraftJail.MODID)
public final class JailDamageProtection {
    private JailDamageProtection(){}
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void incoming(LivingIncomingDamageEvent event){
        if(event.getEntity() instanceof ServerPlayer p&&JailAdmissions.protectedCustody(p)&&!JailEscapeCustody.ownDamage(p))event.setCanceled(true);
    }
    @SubscribeEvent(priority=EventPriority.LOWEST,receiveCanceled=true) public static void death(LivingDeathEvent event){
        if(event.getEntity() instanceof ServerPlayer p&&JailAdmissions.protectedCustody(p)&&!JailEscapeCustody.ownDamage(p)){
            // A direct die() call can bypass incoming-damage hooks. Do not revive an
            // already accepted escape corpse, and never grant or copy any items.
            if(p.getHealth()<=0&&!JailEscapeCustody.acceptedCorpse(p))p.setHealth(Math.min(1,p.getMaxHealth()));
            event.setCanceled(true);
        }
    }
}
