package dev.unyielding.jail;

/** Bounds refer to the feet position in the validated 7x7x7 jail template. */
public final class JailBoundary {
    private JailBoundary() {}
    public static boolean contains(String dimension, double x, double y, double z, String jailDimension, double jx, double jy, double jz) {
        return dimension != null && dimension.equals(jailDimension)
                && Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z)
                && Double.isFinite(jx) && Double.isFinite(jy) && Double.isFinite(jz)
                && Math.abs(x - jx) < 2.2 && Math.abs(z - jz) < 2.2
                && y >= jy && y < jy + 4.0;
    }
}
