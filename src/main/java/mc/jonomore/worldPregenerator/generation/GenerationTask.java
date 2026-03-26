package mc.jonomore.worldPregenerator.generation;

import mc.jonomore.worldPregenerator.GenerationConstants;
import mc.jonomore.worldPregenerator.WorldPregenerator;
import mc.jonomore.worldPregenerator.config.ConfigManager;
import mc.jonomore.worldPregenerator.logic.CageBuilder;
import mc.jonomore.worldPregenerator.logic.SpawnAdjuster;
import mc.jonomore.worldPregenerator.util.FileUtils;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.scheduler.BukkitTask;
import org.popcraft.chunky.api.ChunkyAPI;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

public class GenerationTask {
  private final WorldPregenerator plugin;
  private final java.util.logging.Logger logger;
  private final ConfigManager config;
  private final List<SeedEntry> seeds;
  private final Set<Long> completedSeeds;
  private final ChunkyAPI chunky;
  private final SpawnAdjuster spawnAdjuster;
  private final CageBuilder cageBuilder;
  private final ErrorHandler errorHandler;
  private final GenerationState state;
  private final File stateFile;
  private final File completedSeedsFile;

  private int retryCount = 0;
  private int worldsInCurrentBatch = 0;
  private boolean interrupted = false;
  private BukkitTask scheduledTask = null;
  private CompletableFuture<World> chunkyFuture = null;

  /**
   * Constructor for GenerationTask
   * @param plugin The main plugin instance
   * @param seeds List of seed entries to generate worlds from
   * @param completedSeeds Set of already completed seeds for duplicate check
   * @param chunky ChunkyAPI instance for chunk generation
   * @param state The generation state to use/resume
   */
  public GenerationTask(WorldPregenerator plugin, List<SeedEntry> seeds, Set<Long> completedSeeds, ChunkyAPI chunky, GenerationState state) {
    this.plugin = plugin;
    this.config = plugin.config;
    this.logger = plugin.getLogger();
    this.seeds = seeds;
    this.completedSeeds = completedSeeds;
    this.state = state;
    this.stateFile = new File(plugin.getDataFolder(), GenerationConstants.STATE_FILE_NAME);
    this.completedSeedsFile = new File(plugin.getDataFolder(), GenerationConstants.COMPLETED_SEEDS_FILE_NAME);

    this.chunky = chunky;
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
      logger.info("Starting world generation for " + seeds.size() + " seeds");
    } else if (state.getCurrentIndex() < seeds.size()) {
      logger.info("Resuming world generation at seed " + (state.getCurrentIndex() + 1) + " of " + seeds.size());
    } else {
      logger.info("All worlds already generated!");
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
    logger.info("World generation stopped at index " + state.getCurrentIndex());
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
        .thenAcceptAsync(w -> {
          World world = w;
          try {
            state.setCurrentStep("ADJUSTING_SPAWN");
            checkSpawn(world);
            
            state.setCurrentStep("CREATING_METADATA");
            String direction = calculateDirection(seedEntry, world.getSpawnLocation());
            createManhuntYml(world, seedEntry, direction);

            state.setCurrentStep("BUILDING_CAGE");
            buildCage(world);
            
            state.setCurrentStep("UNLOADING");
            String worldName = world.getName();
            world.save();
            Bukkit.unloadWorld(world, true);
            logger.info("World saved and unloaded: " + worldName);
          } finally {
            // Fix 1: Memory Leak - Null out world reference
            world = null;
          }
          saveState();
        }, Bukkit.getScheduler().getMainThreadExecutor(plugin))
        .thenCompose(v -> zipWorld(seedEntry))
        .thenAcceptAsync(v -> {
            state.setCurrentStep("SAVING_SEED");
            writeCompletedSeed(seedEntry);
        }, Bukkit.getScheduler().getMainThreadExecutor(plugin))
        .handle((v, ex) -> {
            // Fix 7: CompletableFuture Error Handling - Ensure cleanup runs even on failure
            if (ex != null) {
                logger.log(Level.SEVERE, "Error in generation pipeline, proceeding to cleanup", ex);
                return cleanup().thenCompose(cleanupResult -> CompletableFuture.<Void>failedFuture(ex));
            }
            return cleanup();
        })
        .thenCompose(f -> f)
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
      try {
        state.save(stateFile);
      } catch (IOException ignored) {}
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
      
      double angle = Math.toDegrees(Math.atan2(deltaZ, deltaX));
      if (angle < 0) angle += 360;
      
      if (angle >= 337.5 || angle < 22.5) return "EAST";
      if (angle >= 22.5 && angle < 67.5) return "SOUTHEAST";
      if (angle >= 67.5 && angle < 112.5) return "SOUTH";
      if (angle >= 112.5 && angle < 157.5) return "SOUTHWEST";
      if (angle >= 157.5 && angle < 202.5) return "WEST";
      if (angle >= 202.5 && angle < 247.5) return "NORTHWEST";
      if (angle >= 247.5 && angle < 292.5) return "NORTH";
      if (angle >= 292.5 && angle < 337.5) return "NORTHEAST";
      
      return "Unknown";
  }

  private void createManhuntYml(World world, SeedEntry seed, String direction) {
      File manhuntFile = new File(world.getWorldFolder(), GenerationConstants.MANHUNT_YML_FILE_NAME);
      StringBuilder yaml = new StringBuilder();
      yaml.append("seed: ").append(seed.seed()).append("\n");
      yaml.append("spawn:\n");
      yaml.append("  x: ").append(world.getSpawnLocation().getBlockX()).append("\n");
      yaml.append("  y: ").append(world.getSpawnLocation().getBlockY()).append("\n");
      yaml.append("  z: ").append(world.getSpawnLocation().getBlockZ()).append("\n");
      yaml.append("coordinate-hint:\n");
      yaml.append("  x: ").append(seed.hintX()).append("\n");
      yaml.append("  z: ").append(seed.hintZ()).append("\n");
      yaml.append("direction-hint: ").append(direction).append("\n");
      yaml.append("pregen-radius: ").append(config.getGenerationRadius()).append("\n");

      try {
          Files.writeString(manhuntFile.toPath(), yaml.toString());
          logger.info("Created manhunt.yml for world " + world.getName());
      } catch (IOException e) {
          logger.log(Level.SEVERE, "Failed to create manhunt.yml for world " + world.getName(), e);
      }
  }

  private void writeCompletedSeed(SeedEntry seed) {
      synchronized (completedSeedsFile) {
          if (completedSeeds.contains(seed.seed())) return;
          
          try {
              Files.writeString(completedSeedsFile.toPath(), seed.seed() + "\n", 
                  StandardOpenOption.APPEND, StandardOpenOption.CREATE);
              completedSeeds.add(seed.seed());
          } catch (IOException e) {
              logger.log(Level.SEVERE, "Failed to write completed seed " + seed.seed(), e);
          }
      }
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
    
    if (world != null) {
        world.setDifficulty(Difficulty.EASY);
        logger.info("Created world: " + worldName + " (seed: " + seedEntry.seed() + ", difficulty: EASY)");
    }
    
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
    double centerX = world.getSpawnLocation().getX();
    double centerZ = world.getSpawnLocation().getZ();
    String worldName = world.getName();

    logger.info("Starting chunk generation (radius: " + radius + ")");
    state.setCurrentStep("GENERATING_CHUNKS");
    saveState();
    
    chunkyFuture = new CompletableFuture<>();
    
    // Fix 3: Watchdog Thread Dumps - Run chunky.startTask asynchronously
    CompletableFuture.runAsync(() -> {
        long start = System.currentTimeMillis();
        chunky.startTask(
            worldName,
            "square",
            centerX,
            centerZ,
            radius,
            radius,
            "concentric");
        long duration = System.currentTimeMillis() - start;
        if (duration > 100) {
            logger.warning("Chunky startTask took " + duration + "ms");
        }
    }, Bukkit.getScheduler().getMainThreadExecutor(plugin));
    
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
   * Zips the world folder and moves it to the export directory
   * @param seedEntry The seed entry (used for naming)
   * @return Future that completes when zipping is done
   */
  private CompletableFuture<Void> zipWorld(SeedEntry seedEntry) {
    state.setCurrentStep("ZIPPING");
    saveState();

    CompletableFuture<Void> future = new CompletableFuture<>();
    Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
      try {
        File worldFolder = state.getCurrentWorldFolder();
        File exportDir = new File(config.getExportPath());

        if (!exportDir.exists() && !exportDir.mkdirs()) {
          future.completeExceptionally(new IOException("Could not create export directory: " + exportDir.getAbsolutePath()));
          return;
        }

        String fileName = seedEntry.seed() + "_" + config.getServerId() + ".zip";
        File zipFile = new File(exportDir, fileName);
        Set<String> exclusions = Set.of("session.lock", "uid.dat");
        
        logger.info("Zipping world " + worldFolder.getName() + " to " + zipFile.getAbsolutePath());
        FileUtils.zipDirectory(worldFolder, zipFile, exclusions);

        // Fix 6: Zip Integrity Validation - Verify zip before source deletion
        if (!FileUtils.validateZipFile(zipFile)) {
            future.completeExceptionally(new IOException("Zip validation failed for: " + zipFile.getAbsolutePath()));
            return;
        }

        logger.info("Successfully zipped and validated world: " + zipFile.getName());
        future.complete(null);
      } catch (IOException e) {
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

    if (worldFolder == null || !worldFolder.exists()) {
        future.complete(null);
        return future;
    }

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
      // Fix 5: State File Save Failures - Stop on failure
      logger.log(Level.SEVERE, "CRITICAL: State persistence failure - cannot continue safely", e);
      stop();
      throw new RuntimeException("State persistence failure", e);
    }
  }

  public GenerationState getState() {
    return state;
  }
}
