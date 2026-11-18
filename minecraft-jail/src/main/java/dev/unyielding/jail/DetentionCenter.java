package dev.unyielding.jail;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;

final class DetentionCenter {
    static final int GEOMETRY = 4, CELL_COUNT = DetentionPlan.CELLS;
    static Loc cell(Loc first, int number) {
        return new Loc(first.dim(), first.x() + DetentionPlan.centerX(number) - 3, first.y(),
                first.z() + DetentionPlan.centerZ(number) - 3, first.yaw(), first.pitch());
    }
    static int number(Loc first, Loc dest) {
        if (first != null && dest != null)
            for (int n = 1; n <= CELL_COUNT; n++) if (cell(first, n).equals(dest)) return n;
        return 0;
    }
    static List<BlockPos> vaultCandidates(Loc first, int cell) {
        BlockPos base = BlockPos.containing(first.x(), first.y(), first.z()).offset(-3, -1, -3);
        return List.of(base.offset(DetentionPlan.vaultX(cell), 1, DetentionPlan.centerZ(cell)));
    }

    static BlockState state(int x, int y, int z) {
        return switch (DetentionPlan.tile(x, y, z)) {
            case AIR -> Blocks.AIR.defaultBlockState();
            case ASHLAR -> JailRegistration.ASHLAR.get().defaultBlockState();
            case CHESTNUT -> JailRegistration.CHESTNUT.get().defaultBlockState();
            case INLAY -> JailRegistration.INLAY.get().defaultBlockState();
            case LAMP -> JailRegistration.LAMP.get().defaultBlockState();
            case BLOSSOM -> JailRegistration.BLOSSOM.get().defaultBlockState();
            case SLATE -> Blocks.POLISHED_DEEPSLATE.defaultBlockState();
            case GLASS -> Blocks.GLASS.defaultBlockState();
            case CHERRY_TRUNK -> Blocks.STRIPPED_CHERRY_LOG.defaultBlockState();
            case BARS_NS -> JailRegistration.GRILLE.get().defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_AXIS, Direction.Axis.Z);
            case BARS_EW -> JailRegistration.GRILLE.get().defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_AXIS, Direction.Axis.X);
            case BENCH_EAST -> bench(Direction.WEST);
            case BENCH_WEST -> bench(Direction.EAST);
            case BENCH_NORTH -> bench(Direction.SOUTH);
            case BENCH_SOUTH -> bench(Direction.NORTH);
            case VAULT -> JailRegistration.CUSTODY_VAULT.get().defaultBlockState();
            case PLATE_1 -> JailRegistration.PLATES.get(0).get().defaultBlockState();
            case PLATE_2 -> JailRegistration.PLATES.get(1).get().defaultBlockState();
            case PLATE_3 -> JailRegistration.PLATES.get(2).get().defaultBlockState();
            case PLATE_4 -> JailRegistration.PLATES.get(3).get().defaultBlockState();
            case PLATE_5 -> JailRegistration.PLATES.get(4).get().defaultBlockState();
            case PLATE_6 -> JailRegistration.PLATES.get(5).get().defaultBlockState();
        };
    }
    private static BlockState bench(Direction back) { return Blocks.DARK_OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, back); }
    private DetentionCenter() { }
}
