package dev.unyielding.jail;

/** Rosecourt is drawn north to south. All coordinates here are relative to its northwest floor tile. */
final class DetentionPlan {
    static final int WIDTH = 19, HEIGHT = 10, DEPTH = 30;
    static final int CELLS = 6;
    enum Tile {
        AIR, ASHLAR, CHESTNUT, INLAY, LAMP, BLOSSOM, SLATE, GLASS, BARS_NS, BARS_EW,
        BENCH_EAST, BENCH_WEST, BENCH_NORTH, BENCH_SOUTH, CHERRY_TRUNK, VAULT,
        PLATE_1, PLATE_2, PLATE_3, PLATE_4, PLATE_5, PLATE_6
    }
    static int centerX(int cell) { checkCell(cell); return cell % 2 == 1 ? 3 : 15; }
    static int centerZ(int cell) { checkCell(cell); return 3 + ((cell - 1) / 2) * 6; }
    static int vaultX(int cell) { checkCell(cell); return cell % 2 == 1 ? 7 : 11; }
    private static void checkCell(int cell) {
        if (cell < 1 || cell > CELLS) throw new IllegalArgumentException("Cell must be 1 through " + CELLS);
    }
    private static final Tile[] PLATES = {Tile.PLATE_1, Tile.PLATE_2, Tile.PLATE_3, Tile.PLATE_4, Tile.PLATE_5, Tile.PLATE_6};
    static Tile tile(int x, int y, int z) {
        if (z >= 19) return court(x, y, z);
        if (x >= 7 && x <= 11) return gallery(x, y, z);
        int side = x <= 6 ? x : 18 - x;
        int bay = z % 6;
        // Each wing has a flat, trimmed parapet and a continuous six-block-high cell shell.
        if (y == 7 && (side == 0 || z == 0 || z == 18)) return Tile.SLATE;
        if (y > 6) return Tile.AIR;
        if (y == 6) return side == 6 || bay == 0 ? Tile.CHESTNUT : Tile.SLATE;
        if (y == 0) return side == 3 && bay == 3 ? Tile.INLAY : Tile.ASHLAR;
        if (side == 6 && bay == 3 && y == 4) return PLATES[z / 6 * 2 + (x < 7 ? 0 : 1)];
        if (bay == 0) {
            if (side == 0 || side == 6 || y == 5) return Tile.CHESTNUT;
            if ((z == 0 || z == 18) && side >= 2 && side <= 4 && y >= 2 && y <= 3) return Tile.BARS_EW;
            return Tile.ASHLAR;
        }
        if (side == 0 || side == 6) {
            if (y == 5) return Tile.CHESTNUT;
            if (side == 6 && y <= 4 && bay >= 2 && bay <= 4) return Tile.BARS_NS;
            if (side == 0 && y >= 3 && y <= 4 && bay >= 2 && bay <= 4) return Tile.BARS_NS;
            return y == 1 && (bay == 1 || bay == 5) ? Tile.INLAY : Tile.ASHLAR;
        }
        if (y == 1 && side == 1 && bay >= 2 && bay <= 4) return x < 7 ? Tile.BENCH_EAST : Tile.BENCH_WEST;
        if (y == 5 && side == 3 && bay == 3) return Tile.LAMP;
        return Tile.AIR;
    }

    private static Tile gallery(int x, int y, int z) {
        int side = Math.abs(x - 9);
        int roof = 9 - side;
        if (y > roof) return Tile.AIR;
        if (y == roof) return side == 0 && z % 6 != 0 ? Tile.GLASS : Tile.SLATE;
        if (y == 0) return side == 1 || z % 6 == 0 ? Tile.INLAY : Tile.ASHLAR;
        if (side == 2 && z % 6 == 3 && y == 1) return Tile.VAULT;
        if (z % 6 == 0) {
            if (side == 2 && y <= 6 || y == roof - 1) return Tile.CHESTNUT;
            if (z == 0 || z == 18) {
                if (y >= 5) return side == 0 && y <= 7 ? Tile.GLASS : Tile.ASHLAR;
                if (side == 1 && y == 4) return Tile.INLAY;
            }
        }
        if (side == 0 && z % 6 == 3 && y == 8) return Tile.LAMP;
        return Tile.AIR;
    }

    private static Tile court(int x, int y, int z) {
        int edge = Math.min(x, 18 - x);
        if (y == 0) {
            if (z == 20 || z == 27 || edge == 6 || edge == 8) return Tile.INLAY;
            return Tile.ASHLAR;
        }
        if (edge == 0 || z == 29) {
            boolean pier = edge == 0 && (z == 19 || z == 24 || z == 29) || z == 29 && x % 6 == 0;
            if (y <= 2) return pier ? Tile.CHESTNUT : y == 1 ? Tile.ASHLAR : edge == 0 ? Tile.BARS_NS : Tile.BARS_EW;
            if (pier && y == 3) return Tile.LAMP;
            return Tile.AIR;
        }
        // Ornamental cherry trees have permanent, non-ticking blossoms. The backup does not drift as leaves decay.
        int dx = Math.abs(edge - 4), dz = Math.abs(z - 24);
        if (dx == 0 && dz == 0 && y <= 4 || y == 4 && dx + dz == 1) return Tile.CHERRY_TRUNK;
        if (y >= 4 && y <= 7) {
            int radius = y == 4 || y == 7 ? 1 : 2;
            if (dx <= radius && dz <= radius && dx + dz <= radius + 1) return Tile.BLOSSOM;
        }
        if (y == 1 && edge >= 3 && edge <= 5) {
            if (z == 21) return Tile.BENCH_SOUTH;
            if (z == 27) return Tile.BENCH_NORTH;
        }
        return Tile.AIR;
    }
    private DetentionPlan() { }
}
