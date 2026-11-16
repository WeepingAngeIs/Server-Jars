package dev.unyielding.quests.jail;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import net.neoforged.neoforge.common.util.TriState;

@EventBusSubscriber(modid="unyieldingquests")
public final class JailQuarantineEvents {
    private JailQuarantineEvents(){}
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void pickup(ItemEntityPickupEvent.Pre event){if(event.getPlayer() instanceof ServerPlayer p&&JailCustodyService.quarantined(p))event.setCanPickup(TriState.FALSE);}
    @SubscribeEvent public static void use(LivingEntityUseItemEvent.Start event){if(event.getEntity() instanceof ServerPlayer p&&JailCustodyService.quarantined(p))event.setCanceled(true);}
    @SubscribeEvent public static void menu(PlayerContainerEvent.Open event){if(event.getEntity() instanceof ServerPlayer p&&JailCustodyService.quarantined(p))p.closeContainer();}
}
