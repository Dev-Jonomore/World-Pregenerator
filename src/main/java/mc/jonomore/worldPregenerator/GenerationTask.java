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
  private final ErrorHandler errorHandler;
  private final GenerationState state;
  private final File stateFile;

  private int retryCount = 0;
  private boolean interrupted = false;
  private BukkitTask scheduledTask = null;

  /**
   * Constructor for GenerationTask
   * @param plugin The main plugin instance
   * @param seeds List of seed values to generate worlds from
   * @param chunky ChunkyAPI instance for chunk generation
   * @param state The generation state to use/resume
   */
  public GenerationTask(WorldPregenerator plugin, List<Long> seeds, ChunkyAPI chunky, GenerationState state) {
    this.plugin = plugin;
    this.config = plugin.config;
    this.logger = plugin.getLogger();
    this.seeds = seeds;
    this.state = state;
    this.stateFile = new File(plugin.getDataFolder(), GenerationConstants.STATE_FILE_NAME);

    this.chunky = chunky;
    this.slimeAPI = AdvancedSlimePaperAPI.instance();
    this.errorHandler = new ErrorHandler(logger);

    this.spawnAdjuster = new SpawnAdjuster(
        config.getMaxSearchRadius(),
        config.getMaxVerticalScan()
    );
    this.cageBuilder = new CageBuilder(
        config.getCageMaterial(),
        config.getCageRadius(),
        config.getCageHeight()
    );

    this.state.setTotalSeeds(seeds.size());
  }

  /**
   * Starts or resumes the generation task
   */
  public void start() {
    interrupted = false;

    if (state.getStartTime() == 0) {
      state.setStartTime(System.currentTimeMillis());
    }

    if (state.getCurrentIndex() == 0) {
      logger.info("Starting slime world generation for " + seeds.size() + " seeds");
    } else if (state.getCurrentIndex() < seeds.size()) {
      logger.info("Resuming slime world generation at seed " + (state.getCurrentIndex() + 1) + " of " + seeds.size());
    } else {
      logger.info("All slime worlds already generated!");
      plugin.running = false;
      return;
    }

    saveState();
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

    if (state.getCurrentWorldName() != null) {
      chunky.cancelTask(state.getCurrentWorldName());
      logger.info("Cancelled Chunky task for: " + state.getCurrentWorldName());
    }

    state.setCurrentStep("STOPPED");
    saveState();
    logger.info("Slime world generation stopped at index " + state.getCurrentIndex());
  }

  /**
   * Resets the generation progress to the beginning
   */
  public void reset() {
    stop();

    // If a world was in the middle of processing, delete it.
    if (state.getCurrentWorldName() != null) {
      World worldToDelete = Bukkit.getWorld(state.getCurrentWorldName());
      if (worldToDelete != null) {
        logger.info("Resetting: Deleting partially generated world " + state.getCurrentWorldName());
        unloadAndDeleteWorld(worldToDelete, (success) -> {
            if (!success) {
                logger.warning("Failed to delete world " + state.getCurrentWorldName() + " during reset.");
            }
        });
      }
      state.setCurrentWorldName(null);
    }

    state.setCurrentIndex(0);
    state.setSuccessCount(0);
    state.setFailureCount(0);
    state.setStartTime(0);
    state.getFailedSeeds().clear();
    state.setCurrentStep("IDLE");
    saveState();
    logger.info("Generation progress reset");
  }

  /**
   * Processes the next seed in the list
   */
  private void processNext() {
    if (interrupted || state.getCurrentIndex() >= seeds.size()) {
      if (!interrupted) { // if not interrupted, that means we're done
        logger.info("All " + seeds.size() + " worlds generated successfully!");
        state.setCurrentStep("COMPLETED");
        saveState();
      }
      plugin.running = false;
      return;
    }

    long seed = seeds.get(state.getCurrentIndex());
    logger.info("Processing seed " + seed + " (" + (state.getCurrentIndex() + 1) + "/" + seeds.size() + ")");

    try {
      state.setCurrentStep("CREATING_WORLD");
      World tempworld = createWorld(seed);
      state.setCurrentWorldName(tempworld.getName());
      saveState();

      generateChunks(tempworld, () -> {
        if (interrupted) return;

        try {
          state.setCurrentStep("ADJUSTING_SPAWN");
          checkSpawn(tempworld);
          
          state.setCurrentStep("BUILDING_CAGE");
          buildCage(tempworld);
          saveState();

          state.setCurrentStep("EXPORTING");
          saveAndExportToSlime(tempworld, seed, () -> {
            if (interrupted) return;

            state.setCurrentStep("CLEANING_UP");
            unloadAndDeleteWorld(tempworld, (success) -> {
                if (success) {
                    state.setSuccessCount(state.getSuccessCount() + 1);
                    state.setCurrentWorldName(null);
                    retryCount = 0;
                    moveOn();
                } else {
                    logger.severe("Cleanup failed for " + tempworld.getName() + ". Stopping to prevent corrupt state.");
                    plugin.running = false;
                }
            });
          });

        } catch (Exception e) {
          handleError(e, "post-generation steps");
        }
      });

    } catch (Exception e) {
      handleError(e, "creating/generating world");
    }
  }

  private void handleError(Exception e, String context) {
    ErrorHandler.ErrorCategory category = errorHandler.categorize(e);
    errorHandler.handleError(e, context);

    if (category == ErrorHandler.ErrorCategory.FATAL) {
      logger.severe("Fatal error encountered. Stopping generation.");
      plugin.running = false;
      state.setCurrentStep("ERROR_FATAL");
      saveState();
    } else if (category == ErrorHandler.ErrorCategory.RECOVERABLE && retryCount < GenerationConstants.MAX_RETRIES_PER_SEED) {
      retryCount++;
      logger.info("Recoverable error. Retrying seed " + seeds.get(state.getCurrentIndex()) + " (Attempt " + (retryCount + 1) + ")");
      state.setCurrentStep("RETRYING");
      saveState();
      scheduledTask = Bukkit.getScheduler().runTaskLater(plugin, this::processNext, config.getWorldDelayTicks());
    } else {
      state.setFailureCount(state.getFailureCount() + 1);
      state.addFailedSeed(seeds.get(state.getCurrentIndex()));
      logger.warning("Skipping seed " + seeds.get(state.getCurrentIndex()) + " due to " + category + " error.");
      retryCount = 0;
      moveOn();
    }
  }

  /**
   * Advances to the next seed with a delay
   */
  private void moveOn() {
    state.setCurrentIndex(state.getCurrentIndex() + 1);
    saveState();
    scheduledTask = Bukkit.getScheduler().runTaskLater(plugin, this::processNext, config.getWorldDelayTicks());
  }

  /**
   * Creates a temporary vanilla world for chunk generation
   * @param seed The seed value for world generation
   * @return The created World object
   */
  private World createWorld(long seed) {
    String worldName = "world_" + state.getCurrentIndex();

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
    int radius = config.getGenerationRadius();

    logger.info("Starting chunk generation (radius: " + radius + ")");
    state.setCurrentStep("GENERATING_CHUNKS");
    saveState();
    
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
   * @param callback Callback with success status
   */
  public void unloadAndDeleteWorld(World world, java.util.function.Consumer<Boolean> callback) {
    String worldName = world.getName();
    File worldFolder = world.getWorldFolder();

    Bukkit.unloadWorld(world, false);
    logger.info("World unloaded: " + worldName);

    Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
        FileUtils.deleteDirectoryWithRetry(worldFolder, logger, (success) -> {
            Bukkit.getScheduler().runTask(plugin, () -> callback.accept(success));
        });
    });
  }

  private void saveState() {
    try {
      state.save(stateFile);
    } catch (IOException e) {
      logger.log(Level.SEVERE, "Failed to save generation state", e);
    }
  }

  public GenerationState getState() {
    return state;
  }
}
