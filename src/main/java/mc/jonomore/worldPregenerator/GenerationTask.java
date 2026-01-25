package mc.jonomore.worldPregenerator;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.scheduler.BukkitTask;
import org.popcraft.chunky.api.ChunkyAPI;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
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
    currentIndex = 0;
    logger.info("Starting world generation for " + seeds.size() + " seeds");
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
      currentWorldName = null;
    }

    logger.info("World generation stopped at index " + currentIndex);
  }

  public boolean isRunning() {
    return !interrupted && currentIndex < seeds.size();
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

      generateWorldChunks(world, () -> {
        if (interrupted) return;

        try {
          adjustSpawn(world);
          buildCage(world);

          saveAndExportWorld(world, () -> {
            if (interrupted) return;

            unloadAndDeleteWorld(world);
            currentWorldName = null;
            currentIndex++;
            scheduledTask = Bukkit.getScheduler().runTaskLater(plugin, this::processNext, 40L);
          });

        } catch (Exception e) {
          logger.severe("Error in post-generation steps for seed " + seed + ": " + e.getMessage());
          logger.log(java.util.logging.Level.SEVERE, "Stack trace:", e);
          handleError();
        }
      });

    } catch (Exception e) {
      logger.severe("Error creating/generating world for seed " + seed + ": " + e.getMessage());
      logger.log(java.util.logging.Level.SEVERE, "Stack trace:", e);
      handleError();
    }
  }

  private void handleError() {
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

  private void generateWorldChunks(World world, Runnable onComplete) {
    int radius = config.getRadius();

    if (chunky.version() == 0) {
      logger.info("Starting chunk generation (radius: " + radius + ")");
      chunky.startTask(world.getName(), "square", 0, 0, radius, radius, "concentric");

      chunky.onGenerationComplete(event -> {
        if (event.world().equals(world.getName())) {
          logger.info("Chunk generation completed for " + event.world());
          Bukkit.getScheduler().runTask(plugin, onComplete);
        }
      });
    } else {
      logger.warning("Chunky API version mismatch. Expected 0, got " + chunky.version());
      onComplete.run();
    }
  }

  private void adjustSpawn(World world) {
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

  private void buildCage(World world) {
    cageBuilder.buildCage(world);
    logger.info("Cage built at spawn");
  }

  private void saveAndExportWorld(World world, Runnable onComplete) {
    world.save();
    logger.info("World saved: " + world.getName());

    File worldFolder = world.getWorldFolder();
    File exportFolder = new File(config.getExportPath(), world.getName());

    Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
      try {
        copyDirectory(worldFolder, exportFolder);
        logger.info("World exported to: " + exportFolder);
        Bukkit.getScheduler().runTask(plugin, onComplete);
      } catch (IOException e) {
        logger.severe("Failed to export world: " + e.getMessage());
        logger.log(java.util.logging.Level.SEVERE, "Stack trace:", e);
        Bukkit.getScheduler().runTask(plugin, onComplete);
      }
    });
  }

  private void copyDirectory(File source, File destination) throws IOException {
    if (!destination.exists()) {
      destination.mkdirs();
    }

    File[] files = source.listFiles();
    if (files != null) {
      for (File file : files) {
        File destFile = new File(destination, file.getName());

        if (file.isDirectory()) {
          copyDirectory(file, destFile);
        } else {
          Files.copy(file.toPath(), destFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
      }
    }
  }

  private void unloadAndDeleteWorld(World world) {
    String worldName = world.getName();
    File worldFolder = world.getWorldFolder();

    Bukkit.unloadWorld(world, false);
    logger.info("World unloaded: " + worldName);

    Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
      try {
        deleteDirectory(worldFolder);
        logger.info("World folder deleted: " + worldName);
      } catch (IOException e) {
        logger.severe("Failed to delete world folder: " + e.getMessage());
        logger.log(java.util.logging.Level.SEVERE, "Stack trace:", e);
      }
    });
  }

  private void deleteDirectory(File directory) throws IOException {
    if (directory.exists()) {
      File[] files = directory.listFiles();
      if (files != null) {
        for (File file : files) {
          if (file.isDirectory()) {
            deleteDirectory(file);
          } else {
            if (!file.delete()) {
              throw new IOException("Failed to delete file: " + file);
            }
          }
        }
      }
      if (!directory.delete()) {
        throw new IOException("Failed to delete directory: " + directory);
      }
    }
  }
}