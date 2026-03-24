package mc.jonomore.worldPregenerator.generation;

/**
 * Represents a seed that failed to generate, including the reason.
 */
public record FailedSeedEntry(SeedEntry seedEntry, String reason) {
}
