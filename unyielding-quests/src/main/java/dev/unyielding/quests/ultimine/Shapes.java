package dev.unyielding.quests.ultimine;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/** Works out which blocks one vein-mine hits. Used by the server (mining) and client (preview). */
public final class Shapes {
    public enum Shape {
        SHAPELESS("Shapeless"), SMALL_TUNNEL("Small tunnel"), LARGE_TUNNEL("Large tunnel"),
        SMALL_SQUARE("Small square"), LARGE_SQUARE("Large square"), SHAFT("Shaft");

        public final String label;

        Shape(String label) {
            this.label = label;
        }

        public static Shape of(int i) {
            Shape[] v = values();
            return v[Math.floorMod(i, v.length)];
        }
    }

    private Shapes() {
    }

    private static List<Pattern> patterns(List<? extends String> l) {
        List<Pattern> out = new ArrayList<>();
        for (String s : l) out.add(Pattern.compile(Pattern.quote(s).replace("*", "\\E.*\\Q")));
        return out;
    }

    private static boolean sharesTag(BlockState a, BlockState b, List<Pattern> pats) {
        if (pats.isEmpty()) return false;
        Set<String> ta = new HashSet<>();
        a.getTags().forEach(t -> ta.add(t.location().toString()));
        for (TagKey<Block> t : (Iterable<TagKey<Block>>) b.getTags()::iterator) {
            String id = t.location().toString();
            if (!ta.contains(id)) continue;
            for (Pattern p : pats) if (p.matcher(id).matches()) return true;
        }
        return false;
    }

    private static boolean matches(Level level, BlockPos pos, BlockState origin, boolean shaped, List<Pattern> merge) {
        BlockState s = level.getBlockState(pos);
        if (s.isAir() || !s.getFluidState().isEmpty() && s.getBlock() instanceof net.minecraft.world.level.block.LiquidBlock) return false;
        if (s.getDestroySpeed(level, pos) < 0) return false;
        if (s.getBlock() == origin.getBlock()) return true;
        return sharesTag(origin, s, merge);
    }

    /** All positions (origin first) that a vein-mine starting at origin would affect. */
    public static List<BlockPos> compute(Level level, BlockPos origin, Direction face, Shape shape, int max) {
        BlockState os = level.getBlockState(origin);
        List<BlockPos> out = new ArrayList<>();
        if (os.isAir()) return out;
        boolean shaped = shape != Shape.SHAPELESS;
        List<Pattern> merge = patterns(shaped ? UltimineConfig.MERGE_TAGS_SHAPED.get() : UltimineConfig.MERGE_TAGS.get());
        out.add(origin);
        if (shape == Shape.SHAPELESS) {
            Set<BlockPos> seen = new HashSet<>();
            ArrayDeque<BlockPos> q = new ArrayDeque<>();
            seen.add(origin);
            q.add(origin);
            int visited = 0;
            while (!q.isEmpty() && out.size() < max + 1 && visited < 20000) {
                BlockPos c = q.poll();
                visited++;
                for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;
                    BlockPos n = c.offset(dx, dy, dz);
                    if (!seen.add(n)) continue;
                    if (Math.abs(n.getX() - origin.getX()) > 24 || Math.abs(n.getY() - origin.getY()) > 24 || Math.abs(n.getZ() - origin.getZ()) > 24) continue;
                    if (!level.isLoaded(n) || !matches(level, n, os, false, merge)) continue;
                    q.add(n);
                    if (out.size() < max + 1) out.add(n);
                }
            }
            return out;
        }
        Direction into = face.getOpposite();
        Direction u, v;
        switch (face.getAxis()) {
            case X -> {
                u = Direction.UP;
                v = Direction.SOUTH;
            }
            case Y -> {
                u = Direction.EAST;
                v = Direction.SOUTH;
            }
            default -> {
                u = Direction.UP;
                v = Direction.EAST;
            }
        }
        int radius = switch (shape) {
            case SMALL_TUNNEL, SMALL_SQUARE -> 1;
            case LARGE_TUNNEL, LARGE_SQUARE -> 2;
            default -> 0;
        };
        int cross = (radius * 2 + 1) * (radius * 2 + 1);
        int depth = switch (shape) {
            case SMALL_SQUARE, LARGE_SQUARE -> 1;
            default -> Math.max(1, (max + 1) / cross);
        };
        for (int d = 0; d < depth; d++) {
            for (int a = -radius; a <= radius; a++) for (int b = -radius; b <= radius; b++) {
                BlockPos p = origin.relative(into, d).relative(u, a).relative(v, b);
                if (p.equals(origin) || !level.isLoaded(p) || !matches(level, p, os, true, merge)) continue;
                out.add(p);
            }
        }
        List<BlockPos> rest = new ArrayList<>(out.subList(1, out.size()));
        rest.sort(Comparator.comparingDouble(p -> p.distSqr(origin)));
        out.subList(1, out.size()).clear();
        for (BlockPos p : rest) {
            if (out.size() >= max + 1) break;
            out.add(p);
        }
        return out;
    }
}
