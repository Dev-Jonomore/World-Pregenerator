package mc.jonomore.worldPregenerator.generation;

import java.util.List;

/**
 * Represents a seed with the spawn points available in its world.
 */
public class SeedEntry {
    private final long seed;
    private final List<SpawnPoint> spawnPoints;

    public SeedEntry(long seed, List<SpawnPoint> spawnPoints) {
        this.seed = seed;
        this.spawnPoints = List.copyOf(spawnPoints);
    }

    public long seed() { return seed; }
    public List<SpawnPoint> spawnPoints() { return spawnPoints; }
}
