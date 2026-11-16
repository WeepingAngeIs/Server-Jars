package dev.unyielding.jail;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;

/** Intentionally no BlockEntity, Container, item handler, loot or physical escrow. */
@EventBusSubscriber(modid=MinecraftJail.MODID)
public final class JailCustodyBlock extends Block {
    public JailCustodyBlock(Properties properties){super(properties);}
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit){if(player instanceof ServerPlayer server)JailCustodyService.open(server,pos);return InteractionResult.sidedSuccess(level.isClientSide);}
    @SubscribeEvent public static void breaking(BlockEvent.BreakEvent event){if(event.getState().getBlock() instanceof JailCustodyBlock)event.setCanceled(true);}
    @SubscribeEvent public static void placing(BlockEvent.EntityPlaceEvent event){if(event.getBlockSnapshot().getState().getBlock() instanceof JailCustodyBlock)event.setCanceled(true);}
    @SubscribeEvent public static void explosion(ExplosionEvent.Detonate event){event.getAffectedBlocks().removeIf(pos->event.getLevel().hasChunkAt(pos)&&event.getLevel().getBlockState(pos).getBlock() instanceof JailCustodyBlock);}
}
