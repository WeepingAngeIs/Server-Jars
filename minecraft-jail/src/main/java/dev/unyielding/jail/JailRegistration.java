package dev.unyielding.jail;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.PushReaction;

public final class JailRegistration {
    // Keep the old ID so existing vault blocks are not renamed or discarded.
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks("unyieldingquests");
    public static final DeferredBlock<JailCustodyBlock> CUSTODY_VAULT = BLOCKS.register("custody_vault",
            () -> new JailCustodyBlock(BlockBehaviour.Properties.of().strength(-1, 3600000)
                    .noLootTable().pushReaction(PushReaction.BLOCK)));
    private JailRegistration() {}
    public static void register(IEventBus bus) { BLOCKS.register(bus); }
}
