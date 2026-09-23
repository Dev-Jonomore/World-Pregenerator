package mc.jonomore.worldPregenerator.config;

import mc.jonomore.worldPregenerator.WorldPregenerator;
import mc.jonomore.worldPregenerator.generation.SeedParser;
import org.bukkit.Bukkit;
import org.popcraft.chunky.api.ChunkyAPI;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public class ConfigValidator {
    private final ConfigManager config;

    public ConfigValidator(WorldPregenerator plugin) {
        this.config = plugin.config;
    }

    public List<String> validate() {
        List<String> results = new ArrayList<>();

        // 1. Config validation (already partially done in ConfigManager, but we can report it here)
        results.add("<gray>Checking configuration values...");
        results.add("<green> - generation-radius: " + config.getGenerationRadius());
        results.add("<green> - worlds-per-batch: " + config.getWorldsPerBatch());

        // 2. Seed file check
        results.add("<gray>Checking seed file...");
        File seedFile = new File(config.getSeedsFile());
        if (!seedFile.exists()) {
            results.add("<red> - FAILED: Seed file not found at " + seedFile.getAbsolutePath());
        } else if (!seedFile.canRead()) {
            results.add("<red> - FAILED: Seed file is not readable.");
        } else {
            try (Stream<String> lines = Files.lines(seedFile.toPath())) {
                SeedCount seeds = countSeeds(lines);
                long count = seeds.valid();
                if (count == 0) {
                    results.add("<red> - FAILED: No valid seeds found.");
                } else {
                    results.add("<green> - SUCCESS: Found " + count + " valid seeds.");
                }
                if (seeds.invalid() > 0) {
                    results.add("<yellow> - WARNING: " + seeds.invalid() + " line(s) don't match the seed format and will be skipped.");
                }

                // 3. Estimate disk space
                double perWorldGB = estimateWorldSizeGB(config.getGenerationRadius());
                results.add(String.format("<yellow> - Estimated size per world: ~%.2f GB", perWorldGB));
                results.add(String.format("<yellow> - Estimated disk space needed: ~%.2f GB", perWorldGB * count));
            } catch (Exception e) {
                results.add("<red> - FAILED: Could not read seed file: " + e.getMessage());
            }
        }

        // 3. Export path check
        results.add("<gray>Checking export path...");
        File exportDir = new File(config.getExportPath());
        if (!exportDir.exists() && !exportDir.mkdirs()) {
            results.add("<red> - FAILED: Export directory could not be created.");
        } else if (!exportDir.canWrite()) {
            results.add("<red> - FAILED: Export directory is not writable.");
        } else {
            results.add("<green> - SUCCESS: Export directory is ready.");
        }

        // 4. Dependencies check
        results.add("<gray>Checking dependencies...");
        
        ChunkyAPI chunky = Bukkit.getServicesManager().load(ChunkyAPI.class);
        if (chunky == null) {
            results.add("<red> - FAILED: Chunky API not found.");
        } else {
            results.add("<green> - SUCCESS: Chunky API found (Version " + chunky.version() + ")");
        }

        return results;
    }

    /** Number of valid and invalid non-blank lines in a seeds file. */
    record SeedCount(long valid, long invalid) {}

    static SeedCount countSeeds(Stream<String> lines) {
        long valid = 0;
        long invalid = 0;
        for (String line : (Iterable<String>) lines.filter(l -> !l.isBlank())::iterator) {
            try {
                SeedParser.parse(line);
                valid++;
            } catch (IllegalArgumentException e) {
                invalid++;
            }
        }
        return new SeedCount(valid, invalid);
    }

    /**
     * Average overworld region-file size per generated chunk, in KB (Minecraft 26.1.2),
     * taken from the world size calculator at https://onlinemo.de/world.
     */
    private static final double OVERWORLD_KB_PER_CHUNK = 9.82;

    /**
     * Estimates the on-disk size of one world pregenerated as a Chunky square of the given radius.
     * Only the overworld is generated, so the nether and end are not counted.
     */
    static double estimateWorldSizeGB(int radius) {
        double chunksPerSide = radius * 2 / 16.0;
        return chunksPerSide * chunksPerSide * OVERWORLD_KB_PER_CHUNK / 1024 / 1024;
    }
}
