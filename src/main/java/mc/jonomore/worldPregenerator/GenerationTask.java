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
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

public class GenerationTask {
  private final WorldPregenerator plugin;
  private final java.util.logging.Logger logger;
  private final ConfigManager config;
  private final List<SeedEntry> seeds;
  private final ChunkyAPI chunky;
  private final AdvancedSlimePaperAPI slimeAPI;
  private final SpawnAdjuster spawnAdjuster;
  private final CageBuilder cageBuilder;
  private final ErrorHandler errorHandler;
  private final GenerationState state;
  private final File stateFile;
  private final File metadataFile;

  private int retryCount = 0;
  private int worldsInCurrentBatch = 0;
  private boolean interrupted = false;
  private BukkitTask scheduledTask = null;
  private CompletableFuture<World> chunkyFuture = null;

  /**
   * Constructor for GenerationTask
   * @param plugin The main plugin instance
   * @param seeds List of seed entries to generate worlds from
   * @param chunky ChunkyAPI instance for chunk generation
   * @param state The generation state to use/resume
   */
  public GenerationTask(WorldPregenerator plugin, List<SeedEntry> seeds, ChunkyAPI chunky, GenerationState state) {
    this.plugin = plugin;
    this.config = plugin.config;
    this.logger = plugin.getLogger();
    this.seeds = seeds;
    this.state = state;
    this.stateFile = new File(plugin.getDataFolder(), GenerationConstants.STATE_FILE_NAME);
    this.metadataFile = new File(plugin.getDataFolder(), GenerationConstants.METADATA_FILE_NAME);

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

    // Initialize metadata file with header if it doesn't exist
    if (!metadataFile.exists()) {
        try {
            java.nio.file.Files.writeString(metadataFile.toPath(), "seed,coordinate_hint,direction_hint,times_played,difficulty\n");
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Failed to create metadata file", e);
        }
    }

    // Register Chunky listener once
    this.chunky.onGenerationComplete(event -> {
        if (chunkyFuture != null && state.getCurrentWorldName() != null && event.world().equals(state.getCurrentWorldName())) {
            logger.info("Chunk generation completed for " + event.world());
            CompletableFuture<World> future = chunkyFuture;
            chunkyFuture = null;
            Bukkit.getScheduler().runTask(plugin, () -> {
                World world = Bukkit.getWorld(event.world());
                if (world != null) {
                    future.complete(world);
                } else {
                    future.completeExceptionally(new RuntimeException("World " + event.world() + " not found after generation"));
                }
            });
        }
    });
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
      if (chunkyFuture != null) {
          chunkyFuture.completeExceptionally(new RuntimeException("Generation stopped by user"));
          chunkyFuture = null;
      }
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
      File worldFolder = state.getCurrentWorldFolder();
      if (worldFolder != null && worldFolder.exists()) {
        logger.info("Resetting: Deleting partially generated world folder " + worldFolder.getAbsolutePath());
        // Unload first if loaded
        World world = Bukkit.getWorld(state.getCurrentWorldName());
        if (world != null) {
            Bukkit.unloadWorld(world, false);
        }
        FileUtils.deleteDirectoryWithRetry(worldFolder, logger, (success) -> {
            if (!success) {
                logger.warning("Failed to delete world folder during reset.");
            }
        });
      }
      state.setCurrentWorldName(null);
      state.setCurrentWorldFolder(null);
    }

    state.setCurrentIndex(0);
    state.setSuccessCount(0);
    state.setFailureCount(0);
    state.setStartTime(0);
    state.getFailedSeeds().clear();
    state.setCurrentStep("IDLE");
    worldsInCurrentBatch = 0;
    saveState();
    logger.info("Generation progress reset");
  }

  /**
   * Processes the next seed in the list
   */
  private void processNext() {
    if (interrupted || state.getCurrentIndex() >= seeds.size()) {
      if (!interrupted) {
        logger.info("All " + seeds.size() + " worlds generated successfully!");
        state.setCurrentStep("COMPLETED");
        saveState();
      }
      plugin.running = false;
      return;
    }

    SeedEntry seedEntry = seeds.get(state.getCurrentIndex());
    logger.info("Processing seed " + seedEntry.seed() + " (" + (state.getCurrentIndex() + 1) + "/" + seeds.size() + ")");

    CompletableFuture.runAsync(() -> {
          state.setCurrentStep("CREATING_WORLD");
          World tempworld = createWorld(seedEntry);
          state.setCurrentWorldName(tempworld.getName());
          state.setCurrentWorldFolder(tempworld.getWorldFolder());
          saveState();
        }, Bukkit.getScheduler().getMainThreadExecutor(plugin))
        .thenCompose(v -> generateChunks())
        .thenAcceptAsync(world -> {
          state.setCurrentStep("ADJUSTING_SPAWN");
          checkSpawn(world);
          
          // Calculate and save metadata
          String direction = calculateDirection(seedEntry, world.getSpawnLocation());
          saveMetadata(seedEntry, direction);

          state.setCurrentStep("BUILDING_CAGE");
          buildCage(world);
          saveState();

          state.setCurrentStep("UNLOADING");
          Bukkit.unloadWorld(world, true);
          logger.info("World saved and unloaded: " + world.getName());
        }, Bukkit.getScheduler().getMainThreadExecutor(plugin))
        .thenCompose(v -> exportToSlime(seedEntry))
        .thenCompose(v -> cleanup())
        .thenRun(this::moveOn)
        .exceptionally(ex -> {
          if (ex instanceof Exception e) {
            handleError(e);
          } else {
            handleError(new RuntimeException(ex));
          }
          return null;
        });
  }

  private void handleError(Exception e) {
    if (interrupted) return;

    ErrorHandler.ErrorCategory category = errorHandler.categorize(e);
    errorHandler.handleError(e, "generation pipeline");

    if (category == ErrorHandler.ErrorCategory.FATAL) {
      logger.severe("Fatal error encountered. Stopping generation.");
      plugin.running = false;
      state.setCurrentStep("ERROR_FATAL");
      saveState();
    } else if (category == ErrorHandler.ErrorCategory.RECOVERABLE && retryCount < GenerationConstants.MAX_RETRIES_PER_SEED) {
      retryCount++;
      logger.info("Recoverable error. Retrying seed " + seeds.get(state.getCurrentIndex()).seed() + " (Attempt " + (retryCount + 1) + ")");
      state.setCurrentStep("RETRYING");
      saveState();
      scheduledTask = Bukkit.getScheduler().runTaskLater(plugin, this::processNext, config.getWorldDelayTicks());
    } else {
      state.setFailureCount(state.getFailureCount() + 1);
      SeedEntry seedEntry = seeds.get(state.getCurrentIndex());
      state.addFailedSeed(new FailedSeedEntry(seedEntry, e.getMessage() != null ? e.getMessage() : e.toString()));
      logger.warning("Skipping seed " + seedEntry.seed() + " due to " + category + " error.");
      retryCount = 0;
      moveOn();
    }
  }

  private String calculateDirection(SeedEntry seed, Location spawn) {
      double deltaX = seed.hintX() - spawn.getX();
      double deltaZ = seed.hintZ() - spawn.getZ();
      
      if (Math.abs(deltaX) > Math.abs(deltaZ)) {
          return deltaX > 0 ? "East" : "West";
      } else {
          return deltaZ > 0 ? "South" : "North";
      }
  }

  private void saveMetadata(SeedEntry seed, String direction) {
      // Format: id (seed), coordinate_hint, direction_hint, times_played, difficulty
      String hint = String.format("[%d, ~ %d]", seed.hintX(), seed.hintZ());
      String record = String.format("%d,\"%s\",%s,0,Easy\n", seed.seed(), hint, direction);
      
      Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
          try {
              java.nio.file.Files.writeString(metadataFile.toPath(), record, 
                  java.nio.file.StandardOpenOption.APPEND, java.nio.file.StandardOpenOption.CREATE);
          } catch (IOException e) {
              logger.log(Level.SEVERE, "Failed to save metadata for seed " + seed.seed(), e);
          }
      });
  }

  /**
   * Advances to the next seed with a delay, respecting batch settings
   */
  private void moveOn() {
    state.setCurrentIndex(state.getCurrentIndex() + 1);
    worldsInCurrentBatch++;
    saveState();

    long delay;
    if (worldsInCurrentBatch >= config.getWorldsPerBatch()) {
        worldsInCurrentBatch = 0;
        delay = config.getPauseBetweenBatches() * 20L; // Convert seconds to ticks
        logger.info("Batch complete. Pausing for " + config.getPauseBetweenBatches() + " seconds...");
        state.setCurrentStep("BATCH_PAUSE");
    } else {
        delay = config.getWorldDelayTicks();
        state.setCurrentStep("WAITING");
    }
    saveState();

    scheduledTask = Bukkit.getScheduler().runTaskLater(plugin, this::processNext, delay);
  }

  /**
   * Creates a temporary vanilla world for chunk generation
   * @param seedEntry The seed entry for world generation
   * @return The created World object
   */
  private World createWorld(SeedEntry seedEntry) {
    String worldName = "world_" + state.getCurrentIndex();

    World world = new WorldCreator(worldName)
                      .seed(seedEntry.seed())
                      .environment(World.Environment.NORMAL)
                      .createWorld();
    logger.info("Created world: " + worldName + " (seed: " + seedEntry.seed() + ")");
    return world;
  }

  /**
   * Generates chunks using Chunky API
   * @return Future that completes when generation is done
   */
  private CompletableFuture<World> generateChunks() {
    World world = Bukkit.getWorld(state.getCurrentWorldName());
    if (world == null) {
        return CompletableFuture.failedFuture(new RuntimeException("World not found: " + state.getCurrentWorldName()));
    }

    int radius = config.getGenerationRadius();
    logger.info("Starting chunk generation (radius: " + radius + ")");
    state.setCurrentStep("GENERATING_CHUNKS");
    saveState();
    
    chunkyFuture = new CompletableFuture<>();
    
    chunky.startTask(
        world.getName(),
        "square",
        world.getSpawnLocation().getX(),
        world.getSpawnLocation().getZ(),
        radius,
        radius,
        "concentric");
    
    return chunkyFuture;
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
   * @param seedEntry The seed entry (used for naming)
   * @return Future that completes when conversion is done
   */
  private CompletableFuture<Void> exportToSlime(SeedEntry seedEntry) {
    state.setCurrentStep("EXPORTING");
    saveState();

    CompletableFuture<Void> future = new CompletableFuture<>();
    Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
      try {
        File tempWorldDir = state.getCurrentWorldFolder();
        File exportDir = new File(config.getExportPath());

        if (!exportDir.exists() && !exportDir.mkdirs()) {
          future.completeExceptionally(new IOException("Could not create export directory: " + exportDir.getAbsolutePath()));
          return;
        }

        SlimeLoader loader = new FileLoader(exportDir);
        SlimeWorld slimeWorld = slimeAPI.readVanillaWorld(tempWorldDir, String.valueOf(seedEntry.seed()), loader);
        slimeAPI.saveWorld(slimeWorld);

        logger.info("Converted and exported SlimeWorld: " + slimeWorld.getName() + " to " + exportDir.getAbsolutePath());
        future.complete(null);
      } catch (IOException |
               RuntimeException |
               InvalidWorldException |
               WorldTooBigException |
               WorldAlreadyExistsException |
               WorldLoadedException e) {
        future.completeExceptionally(e);
      }
    });
    return future;
  }

  /**
   * Deletes the temporary vanilla world folder
   * @return Future that completes when deletion is done
   */
  private CompletableFuture<Void> cleanup() {
    state.setCurrentStep("CLEANING_UP");
    saveState();

    CompletableFuture<Void> future = new CompletableFuture<>();
    String worldName = state.getCurrentWorldName();
    File worldFolder = state.getCurrentWorldFolder();

    Bukkit.getScheduler().runTaskAsynchronously(plugin, () ->
        FileUtils.deleteDirectoryWithRetry(worldFolder, logger, (success) ->
            Bukkit.getScheduler().runTask(plugin, () -> {
              if (success) {
                state.setSuccessCount(state.getSuccessCount() + 1);
                state.setCurrentWorldName(null);
                state.setCurrentWorldFolder(null);
                retryCount = 0;
                future.complete(null);
              } else {
                future.completeExceptionally(new RuntimeException("Cleanup failed for " + worldName));
              }
            })
        )
    );
    return future;
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
