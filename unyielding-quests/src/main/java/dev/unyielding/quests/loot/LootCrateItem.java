package dev.unyielding.quests.loot;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** A server-issued item whose payload names a server-owned reward table. */
public final class LootCrateItem extends Item {
    public LootCrateItem(Properties properties) { super(properties); }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (level.isClientSide) return InteractionResultHolder.success(held);
        if (!(player instanceof ServerPlayer serverPlayer) || player.getCooldowns().isOnCooldown(this))
            return InteractionResultHolder.fail(held);
        boolean opened = LootTables.openCrate(serverPlayer, held);
        if (opened) player.getCooldowns().addCooldown(this, 10);
        // Opening can empty this slot and immediately place a prize into it. Returning
        // the old (now empty) crate would make the interaction overwrite that prize.
        ItemStack currentHand = player.getItemInHand(hand);
        return opened ? InteractionResultHolder.consume(currentHand) : InteractionResultHolder.fail(currentHand);
    }
}
