package dev.unyielding.jail;

/** Outer extent of the original cell and the prisoner half of later templates. */
public final class JailEscapeBoundary {
    private JailEscapeBoundary() {}

    /** True only when the player's entire box is beyond at least one outer cell face. */
    public static boolean fullyOutside(double minX, double minY, double minZ,
                                       double maxX, double maxY, double maxZ,
                                       double jx, double jy, double jz) {
        if (!Double.isFinite(minX) || !Double.isFinite(minY) || !Double.isFinite(minZ)
                || !Double.isFinite(maxX) || !Double.isFinite(maxY) || !Double.isFinite(maxZ)
                || !Double.isFinite(jx) || !Double.isFinite(jy) || !Double.isFinite(jz)) return false;
        return maxX <= jx - 3.5 || minX >= jx + 3.5
                || maxZ <= jz - 3.5 || minZ >= jz + 3.5
                || maxY <= jy - 1.0 || minY >= jy + 6.0;
    }
}
