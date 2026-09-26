package mc.jonomore.worldPregenerator.generation;

import java.util.List;

/**
 * Represents a seed with the spawn points available in its world.
 */
public record SeedEntry(long seed, List<SpawnPoint> spawnPoints) {
    public SeedEntry(long seed, List<SpawnPoint> spawnPoints) {
        this.seed = seed;
        this.spawnPoints = List.copyOf(spawnPoints);
    }
}
