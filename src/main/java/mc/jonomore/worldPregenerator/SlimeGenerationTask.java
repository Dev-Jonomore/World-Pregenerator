package mc.jonomore.worldPregenerator;

import com.infernalsuite.asp.api.AdvancedSlimePaperAPI;
import com.infernalsuite.asp.api.exceptions.InvalidWorldException;
import com.infernalsuite.asp.api.exceptions.WorldAlreadyExistsException;
import com.infernalsuite.asp.api.exceptions.WorldLoadedException;
import com.infernalsuite.asp.api.exceptions.WorldTooBigException;
import com.infernalsuite.asp.api.world.SlimeWorld;
import com.infernalsuite.asp.api.loaders.SlimeLoader;
import com.infernalsuite.asp.loaders.file.FileLoader;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.scheduler.BukkitRunnable;
import org.popcraft.chunky.api.ChunkyAPI;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;

public class SlimeGenerationTask extends BukkitRunnable {

    private final WorldPregenerator plugin;
    private final Logger logger;
    private final List<String> seeds;
    private final ConfigManager config;
    private final SpawnAdjuster spawnAdjuster;
    private final CageBuilder cageBuilder;
    private final ChunkyAPI chunky;
    private final AdvancedSlimePaperAPI slimeAPI;

    private int currentSeedIndex = 0;
    private boolean interrupted = false;

    public SlimeGenerationTask(WorldPregenerator plugin, List<String> seeds, ChunkyAPI chunky) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.seeds = seeds;
        this.config = plugin.config;
        this.chunky = chunky;

        this.spawnAdjuster = new SpawnAdjuster(
                config.getMaxSearchRadius(),
                config.getMaxVerticalScan()
        );
        this.cageBuilder = new CageBuilder(
                config.getCageMaterial(),
                config.getCageRadius(),
                config.getCageHeight()
        );
        this.slimeAPI = AdvancedSlimePaperAPI.instance();
    }

    @Override
    public void run() {
        if (!plugin.running) { // Only start if plugin is not already running a task
            plugin.running = true;
            processNextSeed();
        } else {
            logger.warning("Slime world generation is already running. Skipping new task.");
            cancel();
        }
    }

    public void stop() {
        interrupted = true;
        logger.info("Slime world generation stopped.");
        plugin.running = false;
    }

    private void processNextSeed() {
        if (interrupted || currentSeedIndex >= seeds.size()) {
            if (!interrupted) {
                logger.info("All " + seeds.size() + " slime worlds generated successfully!");
            }
            // All seeds processed or interrupted, cancel this task
            cancel();
            return;
        }

        String seedString = seeds.get(currentSeedIndex);
        long seed;
        try {
            seed = Long.parseLong(seedString);
        } catch (NumberFormatException e) {
            logger.warning("Invalid seed format for '" + seedString + "'. Skipping.");
            moveOn();
            return;
        }

        logger.info("Processing seed " + seed + " (" + (currentSeedIndex + 1) + "/" + seeds.size() + ")");

        // Create a temporary vanilla world
        String tempWorldName = "temp_pregen_" + seed;
        WorldCreator creator = new WorldCreator(tempWorldName)
                .seed(seed)
                .environment(World.Environment.NORMAL);

        World tempWorld;
        try {
            // World creation must happen on the main thread
            tempWorld = Bukkit.getScheduler().callSyncMethod(plugin, creator::createWorld).get();
            if (tempWorld == null) {
                logger.log(Level.SEVERE, "Failed to create temporary world for seed " + seed);
                moveOn();
                return;
            }
        } catch (InterruptedException | ExecutionException e) {
            logger.log(Level.SEVERE, "Error creating temporary world for seed " + seed, e);
            moveOn();
            return;
        }

        // Apply spawn adjustment and cage building
        checkSpawn(tempWorld);
        buildCage(tempWorld);

        // Use Chunky to generate chunks
        logger.info("Starting chunk generation with Chunky for temporary world: " + tempWorldName);
        chunky.startTask(
                tempWorld.getName(),
                "square",
                Objects.requireNonNull(tempWorld.getSpawnLocation()).getX(),
                tempWorld.getSpawnLocation().getZ(),
                config.getGenerationRadius(),
                config.getGenerationRadius(),
                "concentric");

        chunky.onGenerationComplete(event -> {
            // Ensure this is the correct world and task was not interrupted
            if (event.world().equals(tempWorld.getName()) && !interrupted) {
                Bukkit.getScheduler().runTask(plugin, () -> { // Run on main thread
                    logger.info("Chunky generation completed for " + tempWorldName + ". Importing to Slime format.");
                    try {
                        // Import to Slime format
                        File tempWorldDirectory = tempWorld.getWorldFolder();
                        File exportDir = new File(config.getExportPath());
                        if (!exportDir.exists() && !exportDir.mkdirs()) {
                            logger.log(Level.SEVERE, "Failed to create export directory: " + exportDir.getAbsolutePath());
                            moveOn();
                            return;
                        }

                        // The FileLoader will save directly into the exportPath
                        // We give the loader the export directory, and the readVanillaWorld method will use the seed as the world name for the .slime file
                        SlimeLoader loader = new FileLoader(exportDir);

                        // readVanillaWorld will automatically save it to the loader
                        SlimeWorld slimeWorld = slimeAPI.readVanillaWorld(tempWorldDirectory, String.valueOf(seed), loader);
                        logger.info("Imported vanilla world '" + tempWorldName + "' to SlimeWorld: " + slimeWorld.getName());

                        // Unload and cleanup the temporary vanilla world
                        unloadAndDeleteVanillaWorld(tempWorld);
                        moveOn(); // Move to the next seed
                    } catch (IOException |
                             RuntimeException |
                             InvalidWorldException |
                             WorldTooBigException |
                             WorldAlreadyExistsException |
                             WorldLoadedException e
                    ) {
                        logger.log(Level.SEVERE, "Error importing/saving slime world for seed " + seed, e);
                        unloadAndDeleteVanillaWorld(tempWorld); // Attempt cleanup even on error
                        moveOn();
                    }
                });
            } else if (interrupted) {
                logger.info("Slime generation task interrupted during Chunky generation for " + tempWorldName);
                unloadAndDeleteVanillaWorld(tempWorld); // Cleanup on interruption
                moveOn();
            } else {
                logger.warning("Unexpected Chunky generation complete event for world: " + event.world() + ". Expected: " + tempWorldName);
            }
        });
    }

    private void moveOn() {
        currentSeedIndex++;
        // Schedule next seed processing with a delay to avoid spamming the server
        Bukkit.getScheduler().runTaskLater(plugin, this::processNextSeed, 40L); // 2 seconds delay (20 ticks/sec)
    }

    private void checkSpawn(World world) {
        if (!SpawnAdjuster.isSafeSpawn(Objects.requireNonNull(world.getSpawnLocation()))) {
            world.setSpawnLocation(spawnAdjuster.findSafeSpawn(world));
            logger.info("Spawn adjusted for world " + world.getName());
        }
    }

    private void buildCage(World world) {
        cageBuilder.buildCage(world);
        logger.info("Cage built for world " + world.getName());
    }

    private void unloadAndDeleteVanillaWorld(World world) {
        String worldName = world.getName();
        File worldFolder = world.getWorldFolder();

        // Unload world must happen on the main thread
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (Bukkit.unloadWorld(world, false)) {
                logger.info("Temporary world unloaded: " + worldName);
                // Delete world folder asynchronously to avoid blocking the main thread
                Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                    try {
                        Util.deleteDirectory(worldFolder);
                        logger.info("Temporary world folder deleted: " + worldName);
                    } catch (IOException e) {
                        logger.log(Level.SEVERE, "Failed to delete temporary world folder: " + worldName, e);
                    }
                });
            } else {
                logger.warning("Failed to unload temporary world: " + worldName);
            }
        });
    }
}
