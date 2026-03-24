package mc.jonomore.worldPregenerator.generation;

/**
 * Represents a seed with its coordinate hints.
 */
public class SeedEntry {
    private final long seed;
    private final int hintX;
    private final int hintZ;

    public SeedEntry(long seed, int hintX, int hintZ) {
        this.seed = seed;
        this.hintX = hintX;
        this.hintZ = hintZ;
    }

    public long seed() { return seed; }
    public int hintX() { return hintX; }
    public int hintZ() { return hintZ; }
}
