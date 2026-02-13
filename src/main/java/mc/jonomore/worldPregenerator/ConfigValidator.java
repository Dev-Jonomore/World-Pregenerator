package mc.jonomore.worldPregenerator;

import com.infernalsuite.asp.api.AdvancedSlimePaperAPI;
import org.bukkit.Bukkit;
import org.popcraft.chunky.api.ChunkyAPI;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

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
        results.add("<green> - cage-radius: " + config.getCageRadius());
        results.add("<green> - worlds-per-batch: " + config.getWorldsPerBatch());

        // 2. Seed file check
        results.add("<gray>Checking seed file...");
        File seedFile = new File(config.getSeedsFile());
        if (!seedFile.exists()) {
            results.add("<red> - FAILED: Seed file not found at " + seedFile.getAbsolutePath());
        } else if (!seedFile.canRead()) {
            results.add("<red> - FAILED: Seed file is not readable.");
        } else {
            try {
                long count = Files.lines(seedFile.toPath()).count();
                results.add("<green> - SUCCESS: Found " + count + " seeds.");
                
                // 5. Estimate disk space (radius × seeds × ~50MB)
                // Assuming 50MB is for a standard radius (e.g. 1000). 
                // Let's scale it slightly based on radius squared.
                double scale = Math.pow(config.getGenerationRadius() / 1000.0, 2);
                long estimatedMB = (long) (count * 50 * scale);
                results.add("<yellow> - Estimated disk space needed: ~" + estimatedMB + " MB");
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

        try {
            AdvancedSlimePaperAPI.instance();
            results.add("<green> - SUCCESS: AdvancedSlimePaper API found.");
        } catch (NoClassDefFoundError | Exception e) {
            results.add("<red> - FAILED: AdvancedSlimePaper API not found.");
        }

        return results;
    }
}
