package mc.jonomore.worldPregenerator;

/**
 * Represents a seed that failed to generate, including the reason.
 */
public class FailedSeedEntry {
    private final SeedEntry seedEntry;
    private final String reason;

    public FailedSeedEntry(SeedEntry seedEntry, String reason) {
        this.seedEntry = seedEntry;
        this.reason = reason;
    }

    public SeedEntry seedEntry() { return seedEntry; }
    public String reason() { return reason; }
}
