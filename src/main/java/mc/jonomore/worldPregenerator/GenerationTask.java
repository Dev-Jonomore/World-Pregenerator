package mc.jonomore.worldPregenerator;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.scheduler.BukkitTask;
import org.popcraft.chunky.api.ChunkyAPI;

import java.io.File;
import java.io.IOException;
import java.util.List;

public class GenerationTask {
  private final WorldPregenerator plugin;
  private final java.util.logging.Logger logger;
  private final List<Long> seeds;
  private final ChunkyAPI chunky;
  private final ConfigManager config;
  private final SpawnAdjuster spawnAdjuster;
  private final CageBuilder cageBuilder;

  private int currentIndex = 0;
  private boolean interrupted = false;
  private BukkitTask scheduledTask;
  private String currentWorldName = null;

  // Constructor
  public GenerationTask(WorldPregenerator plugin, List<Long> seeds, ChunkyAPI chunky) {
    this.plugin = plugin;
    this.logger = plugin.getLogger();
    this.seeds = seeds;
    this.chunky = chunky;
    this.config = plugin.config;

    // Pre-create these objects to avoid recreating them for each world
    this.spawnAdjuster = new SpawnAdjuster(
        config.getMaxSearchRadius(),
        config.getMaxVerticalScan()
    );
    this.cageBuilder = new CageBuilder(
        config.getCageMaterial(),
        config.getCageRadius(),
        config.getCageHeight()
    );
  }

  public void start() {
    interrupted = false;

    if (currentIndex == 0) {
      logger.info("Starting world generation for " + seeds.size() + " seeds");
    } else if (currentIndex < seeds.size()) {
      logger.info("Resuming world generation at seed " + (currentIndex + 1) + " of " + seeds.size());
    } else {
      logger.info("All worlds already generated!");
      plugin.running = false;
      return;
    }

    processNext();
  }

  public void stop() {
    interrupted = true;

    if (scheduledTask != null) {
      scheduledTask.cancel();
      scheduledTask = null;
    }

    if (currentWorldName != null) {
      chunky.cancelTask(currentWorldName);
      logger.info("Cancelled Chunky task for: " + currentWorldName);
    }

    logger.info("World generation stopped at index " + currentIndex);
  }

  public void reset() {
    stop();

    // If a world was in the middle of processing, delete it.
    if (currentWorldName != null) {
      World worldToDelete = Bukkit.getWorld(currentWorldName);
      if (worldToDelete != null) {
        logger.info("Resetting: Deleting partially generated world " + currentWorldName);
        unloadAndDeleteWorld(worldToDelete);
      }
      currentWorldName = null;
    }

    currentIndex = 0;
    logger.info("Generation progress reset");
  }

  private void processNext() {
    if (interrupted || currentIndex >= seeds.size()) {
      if (!interrupted) {
        logger.info("All " + seeds.size() + " worlds generated successfully!");
      }
      plugin.running = false;
      return;
    }

    long seed = seeds.get(currentIndex);
    logger.info("Processing seed " + seed + " (" + (currentIndex + 1) + "/" + seeds.size() + ")");

    try {
      World world = createWorld(seed);
      currentWorldName = world.getName();

      generateChunks(world, () -> {
        if (interrupted) return;

        try {
          checkSpawn(world);
          buildCage(world);

          saveAndExportWorld(world, () -> {
            if (interrupted) return;

            unloadAndDeleteWorld(world);
            currentWorldName = null;
            moveOn();
          });

        } catch (Exception e) {
          logger.log(java.util.logging.Level.SEVERE, "Error in post-generation steps for seed " + seed, e);
          moveOn();
        }
      });

    } catch (Exception e) {
      logger.log(java.util.logging.Level.SEVERE, "Error creating/generating world for seed " + seed, e);
      moveOn();
    }
  }

  private void moveOn() {
    currentIndex++;
    scheduledTask = Bukkit.getScheduler().runTaskLater(plugin, this::processNext, 40L);
  }

  private World createWorld(long seed) {
    String worldName = "world_" + currentIndex;
    WorldCreator creator = new WorldCreator(worldName);
    creator.seed(seed);
    creator.environment(World.Environment.NORMAL);

    World world = creator.createWorld();
    logger.info("Created world: " + worldName + " (seed: " + seed + ")");
    return world;
  }

  private void generateChunks(World world, Runnable onComplete) {
    int radius = config.getRadius();

    logger.info("Starting chunk generation (radius: " + radius + ")");
    chunky.startTask(
        world.getName(),
        "square",
        world.getSpawnLocation().getX(),
        world.getSpawnLocation().getZ(),
        radius,
        radius,
        "concentric");

    chunky.onGenerationComplete(event -> {
      if (event.world().equals(world.getName())) {
        logger.info("Chunk generation completed for " + event.world());
        Bukkit.getScheduler().runTask(plugin, onComplete);
      }
    });
  }

  private void checkSpawn(World world) {
    if (!SpawnAdjuster.isSafeSpawn(world.getSpawnLocation())) {
      Location safeSpawn = spawnAdjuster.findSafeSpawn(world);

      if (safeSpawn != null) {
        world.setSpawnLocation(safeSpawn);
        logger.info("Spawn adjusted to: " +
            safeSpawn.getBlockX() + ", " +
            safeSpawn.getBlockY() + ", " +
            safeSpawn.getBlockZ());
      } else {
        logger.warning("Could not find safe spawn location, using default");
      }
    }
  }

  private void buildCage(World world) {
    cageBuilder.buildCage(world);
    logger.info("Cage built at spawn");
  }

  private void saveAndExportWorld(World world, Runnable onComplete) {
    world.save();
    logger.info("World saved: " + world.getName());

    Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
      try {
        File worldFolder = world.getWorldFolder();
        File exportFolder = new File(config.getExportPath(), world.getName());
        util.copyDirectory(worldFolder, exportFolder);
        logger.info("World exported to: " + exportFolder);
        Bukkit.getScheduler().runTask(plugin, onComplete);
      } catch (IOException e) {
        logger.log(java.util.logging.Level.SEVERE, "Failed to export world", e);
        Bukkit.getScheduler().runTask(plugin, onComplete);
      }
    });
  }

  public void unloadAndDeleteWorld(World world) {
    String worldName = world.getName();
    File worldFolder = world.getWorldFolder();

    Bukkit.unloadWorld(world, false);
    logger.info("World unloaded: " + worldName);

    Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
      try {
        util.deleteDirectory(worldFolder);
        logger.info("World folder deleted: " + worldName);
      } catch (IOException e) {
        logger.log(java.util.logging.Level.SEVERE, "Failed to delete world folder", e);
      }
    });
  }
}