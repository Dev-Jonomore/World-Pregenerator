package mc.jonomore.worldPregenerator;

public class CoordinateUtils {
    /**
     * Packs two 32-bit integers into a single 64-bit long.
     * Useful for coordinate storage in maps or sets.
     *
     * @param x The X coordinate
     * @param z The Z coordinate
     * @return The packed long
     */
    public static long pack(int x, int z) {
        return (((long) x) << 32) | (z & 0xffffffffL);
    }

    /**
     * Unpacks the X coordinate from a packed long.
     *
     * @param packed The packed long
     * @return The X coordinate
     */
    public static int unpackX(long packed) {
        return (int) (packed >> 32);
    }

    /**
     * Unpacks the Z coordinate from a packed long.
     *
     * @param packed The packed long
     * @return The Z coordinate
     */
    public static int unpackZ(long packed) {
        return (int) packed;
    }
}
