package dev.unyielding.jail;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

public final class JailRegistration {
    // Keep the old ID so existing vault blocks are not renamed or discarded.
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks("unyieldingquests");
    public static final DeferredBlock<JailCustodyBlock> CUSTODY_VAULT = BLOCKS.register("custody_vault",
            () -> new JailCustodyBlock(BlockBehaviour.Properties.of().strength(-1, 3600000)
                    .noLootTable().pushReaction(PushReaction.BLOCK)));
    private static final DeferredRegister.Blocks DECOR = DeferredRegister.createBlocks(MinecraftJail.MODID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MinecraftJail.MODID);
    private static final List<DeferredBlock<? extends Block>> PALETTE = new ArrayList<>();
    static final DeferredBlock<Block> ASHLAR = block("pearl_ashlar", SoundType.STONE, 0);
    static final DeferredBlock<Block> CHESTNUT = block("smoked_chestnut", SoundType.WOOD, 0);
    static final DeferredBlock<Block> INLAY = block("rose_inlay", SoundType.CALCITE, 0);
    static final DeferredBlock<Block> LAMP = block("petal_lantern", SoundType.LANTERN, 15);
    static final DeferredBlock<Block> BLOSSOM = block("ceramic_blossom", SoundType.DECORATED_POT, 0);
    static final DeferredBlock<RosecourtGrille> GRILLE = item("ward_grille", DECOR.register("ward_grille",
            () -> new RosecourtGrille(BlockBehaviour.Properties.of().strength(5, 8).sound(SoundType.METAL).noOcclusion())));
    static final List<DeferredBlock<Block>> PLATES = IntStream.rangeClosed(1, DetentionPlan.CELLS)
            .mapToObj(n -> block("cell_plate_" + n, SoundType.METAL, 0)).toList();

    private static DeferredBlock<Block> block(String name, SoundType sound, int light) {
        return item(name, DECOR.registerSimpleBlock(name, BlockBehaviour.Properties.of()
                .strength(3, 7).sound(sound).lightLevel(state -> light)));
    }
    private static <B extends Block> DeferredBlock<B> item(String name, DeferredBlock<B> block) {
        ITEMS.registerSimpleBlockItem(name, block);
        PALETTE.add(block);
        return block;
    }
    private static void creative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().equals(CreativeModeTabs.BUILDING_BLOCKS))
            for (var block : PALETTE) event.accept(block.get().asItem());
    }
    private JailRegistration() {}
    public static void register(IEventBus bus) { BLOCKS.register(bus); DECOR.register(bus); ITEMS.register(bus); bus.addListener(JailRegistration::creative); }
}
