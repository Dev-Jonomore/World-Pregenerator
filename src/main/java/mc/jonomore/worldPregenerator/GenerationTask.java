package mc.jonomore.worldPregenerator;

import com.infernalsuite.asp.api.AdvancedSlimePaperAPI;
import com.infernalsuite.asp.api.exceptions.InvalidWorldException;
import com.infernalsuite.asp.api.exceptions.WorldAlreadyExistsException;
import com.infernalsuite.asp.api.exceptions.WorldLoadedException;
import com.infernalsuite.asp.api.exceptions.WorldTooBigException;
import com.infernalsuite.asp.api.loaders.SlimeLoader;
import com.infernalsuite.asp.api.world.SlimeWorld;
import com.infernalsuite.asp.loaders.file.FileLoader;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.scheduler.BukkitTask;
import org.popcraft.chunky.api.ChunkyAPI;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.logging.Level;

public class GenerationTask {
  private final WorldPregenerator plugin;
  private final java.util.logging.Logger logger;
  private final ConfigManager config;
  private final List<Long> seeds;
  private final ChunkyAPI chunky;
  private final AdvancedSlimePaperAPI slimeAPI;
  private final SpawnAdjuster spawnAdjuster;
  private final CageBuilder cageBuilder;

  private int currentIndex = 0;
  private boolean interrupted = false;
  private BukkitTask scheduledTask;
  private String currentWorldName = null;

  /**
   * Constructor for SlimeGenerationTask
   * @param plugin The main plugin instance
   * @param seeds List of seed values to generate worlds from
   * @param chunky ChunkyAPI instance for chunk generation
   */
  public GenerationTask(WorldPregenerator plugin, List<Long> seeds, ChunkyAPI chunky) {
    this.plugin = plugin;
    this.config = plugin.config;
    this.logger = plugin.getLogger();
    this.seeds = seeds;

    this.chunky = chunky;
    this.slimeAPI = AdvancedSlimePaperAPI.instance();

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

  /**
   * Starts or resumes the generation task
   */
  public void start() {
    interrupted = false;

    if (currentIndex == 0) {
      logger.info("Starting slime world generation for " + seeds.size() + " seeds");
    } else if (currentIndex < seeds.size()) {
      logger.info("Resuming slime world generation at seed " + (currentIndex + 1) + " of " + seeds.size());
    } else {
      logger.info("All slime worlds already generated!");
      plugin.running = false;
      return;
    }

    processNext();
  }

  /**
   * Stops the generation task, canceling any active Chunky tasks
   */
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

    logger.info("Slime world generation stopped at index " + currentIndex);
  }

  /**
   * Resets the generation progress to the beginning
   */
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

  /**
   * Processes the next seed in the list
   */
  private void processNext() {
    if (interrupted || currentIndex >= seeds.size()) {
      if (!interrupted) { // if not interrupted, that means we're done
        logger.info("All " + seeds.size() + " worlds generated successfully!");
      }
      plugin.running = false;
      return;
    }

    long seed = seeds.get(currentIndex);
    logger.info("Processing seed " + seed + " (" + (currentIndex + 1) + "/" + seeds.size() + ")");

    try {
      World tempworld = createWorld(seed);
      currentWorldName = tempworld.getName();

      generateChunks(tempworld, () -> {
        if (interrupted) return;

        try {
          checkSpawn(tempworld);
          buildCage(tempworld);

          saveAndExportToSlime(tempworld, seed, () -> {
            if (interrupted) return;

            unloadAndDeleteWorld(tempworld);
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

  /**
   * Advances to the next seed with a delay
   */
  private void moveOn() {
    currentIndex++;
    scheduledTask = Bukkit.getScheduler().runTaskLater(plugin, this::processNext, 40L);
  }

  /**
   * Creates a temporary vanilla world for chunk generation
   * @param seed The seed value for world generation
   * @return The created World object
   */
  private World createWorld(long seed) {
    String worldName = "world_" + currentIndex;

    World world = new WorldCreator(worldName)
                      .seed(seed)
                      .environment(World.Environment.NORMAL)
                      .createWorld();
    logger.info("Created world: " + worldName + " (seed: " + seed + ")");
    return world;
  }

  /**
   * Generates chunks using Chunky API
   * @param world The world to generate chunks in
   * @param onComplete Callback to run when generation completes
   */
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

  /**
   * Checks if spawn is safe and adjusts if necessary
   * @param world The world to check
   */
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

  /**
   * Builds a protective cage at spawn
   * @param world The world to build in
   */
  private void buildCage(World world) {
    cageBuilder.buildCage(world);
    logger.info("Cage built at spawn");
  }

  /**
   * Converts the vanilla world to Slime format and exports it
   * @param world The temporary vanilla world
   * @param seed The seed value (used for naming)
   * @param onComplete Callback to run when conversion completes
   */
  private void saveAndExportToSlime(World world, long seed, Runnable onComplete) {
    world.save();
    logger.info("World saved: " + world.getName());

    Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
      try {
        File tempWorldDir = world.getWorldFolder();
        File exportDir = new File(config.getExportPath());

        if (!exportDir.exists() && !exportDir.mkdirs()) {
          logger.log(Level.SEVERE, "Could not create export directory: " + exportDir.getAbsolutePath());
          Bukkit.getScheduler().runTask(plugin, onComplete);
          return;
        }

        SlimeLoader loader = new FileLoader(exportDir);
        SlimeWorld slimeWorld = slimeAPI.readVanillaWorld(tempWorldDir, String.valueOf(seed), loader);

        logger.info("Converted and exported SlimeWorld: " + slimeWorld.getName() + " to " + exportDir.getAbsolutePath());
        Bukkit.getScheduler().runTask(plugin, onComplete);
      } catch (IOException |
               RuntimeException |
               InvalidWorldException |
               WorldTooBigException |
               WorldAlreadyExistsException |
               WorldLoadedException e) {
        logger.log(java.util.logging.Level.SEVERE, "Failed to convert/export world for seed " + seed, e);
        Bukkit.getScheduler().runTask(plugin, onComplete);
      }
    });
  }

  /**
   * Unloads and deletes the temporary vanilla world
   * @param world The world to remove
   */
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