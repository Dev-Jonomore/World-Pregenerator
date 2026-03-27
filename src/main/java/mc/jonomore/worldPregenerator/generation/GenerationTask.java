package mc.jonomore.worldPregenerator.generation;

import mc.jonomore.worldPregenerator.GenerationConstants;
import mc.jonomore.worldPregenerator.WorldPregenerator;
import mc.jonomore.worldPregenerator.config.ConfigManager;
import mc.jonomore.worldPregenerator.logic.CageBuilder;
import mc.jonomore.worldPregenerator.logic.SpawnAdjuster;
import mc.jonomore.worldPregenerator.util.FileUtils;
import org.bukkit.*;
import org.bukkit.scheduler.BukkitTask;
import org.popcraft.chunky.api.ChunkyAPI;
import org.popcraft.chunky.api.event.task.GenerationCompleteEvent;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;

public class GenerationTask {

  public enum Step {
    CREATE_WORLD,
    GENERATE_CHUNKS,
    PREPARE_WORLD,
    ZIP_EXPORT,
    WRITE_COMPLETED,
    CLEANUP,
    COMPLETE
  }

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

  public GenerationTask(
      WorldPregenerator plugin,
      List<SeedEntry> seeds,
      Set<Long> completedSeeds,
      ChunkyAPI chunky,
      GenerationState state
  ) {
    this.plugin = plugin;
    config = plugin.config;
    logger = plugin.getLogger();
    this.seeds = seeds;
    this.completedSeeds = completedSeeds;
    this.state = state;
    stateFile = new File(plugin.getDataFolder(), GenerationConstants.STATE_FILE_NAME);
    completedSeedsFile = new File(plugin.getDataFolder(), GenerationConstants.COMPLETED_SEEDS_FILE_NAME);
    this.chunky = chunky;
    errorHandler = new ErrorHandler(logger);
    spawnAdjuster = new SpawnAdjuster(config.getMaxSearchRadius(), config.getMaxVerticalScan());
    cageBuilder = new CageBuilder(config.getCageMaterial(), config.getCageRadius(), config.getCageHeight());
    this.state.setTotalSeeds(seeds.size());

    // Ensure starting step is set if it's IDLE or invalid
    if (state.getCurrentStep() == null || "IDLE".equals(state.getCurrentStep())) {
      state.setCurrentStep(Step.CREATE_WORLD.name());
    }
  }

  public void start() {
    interrupted = false;
    if (state.getStartTime() == 0) {
      state.setStartTime(System.currentTimeMillis());
    }
    logger.info("Starting/Resuming generation at seed index " + state.getCurrentIndex() + " (seed: " + seeds.get(state.getCurrentIndex()).seed() + ")");
    dispatch();
  }

  public void stop() {
    interrupted = true;
    if (scheduledTask != null) {
      scheduledTask.cancel();
      scheduledTask = null;
    }
    if (state.getCurrentWorldName() != null) {
      chunky.cancelTask(state.getCurrentWorldName());
    }
    saveState();
    logger.info("Generation stopped by user.");
  }

  public void reset() {
    stop();
    if (state.getCurrentWorldName() != null) {
      String worldName = state.getCurrentWorldName();
      World world = Bukkit.getWorld(worldName);
      if (world != null) Bukkit.unloadWorld(world, false);
      File worldFolder = state.getCurrentWorldFolder();
      if (worldFolder != null && worldFolder.exists()) {
        FileUtils.deleteDirectoryWithRetry(worldFolder, logger, null);
      }
    }
    state.setCurrentIndex(0);
    state.setSuccessCount(0);
    state.setFailureCount(0);
    state.setStartTime(0);
    state.getFailedSeeds().clear();
    state.setCurrentStep(Step.CREATE_WORLD.name());
    state.setCurrentWorldName(null);
    state.setCurrentWorldFolder(null);
    saveState();
    logger.info("Generation reset.");
  }

  private void dispatch() {
    if (interrupted) return;

    if (state.getCurrentIndex() >= seeds.size()) {
      logger.info("All seeds processed!");
      plugin.running = false;
      state.setCurrentStep("COMPLETED");
      saveState();
      return;
    }

    Step step;
    try {
      step = Step.valueOf(state.getCurrentStep());
    } catch (IllegalArgumentException | NullPointerException e) {
      step = Step.CREATE_WORLD;
      state.setCurrentStep(step.name());
    }

    switch (step) {
      case CREATE_WORLD -> handleCreateWorld();
      case GENERATE_CHUNKS -> handleGenerateChunks();
      case PREPARE_WORLD -> handlePrepareWorld();
      case ZIP_EXPORT -> handleZipExport();
      case WRITE_COMPLETED -> handleWriteCompleted();
      case CLEANUP -> handleCleanup();
      case COMPLETE -> handleComplete();
    }
  }

  private void advance(Step next) {
    state.setCurrentStep(next.name());
    saveState();
    dispatch();
  }

  private void handleCreateWorld() {
    Bukkit.getScheduler().runTask(plugin, () -> {
      try {
        SeedEntry seedEntry = seeds.get(state.getCurrentIndex());
        String worldName = "world_" + state.getCurrentIndex();

        logger.info("Step: CREATE_WORLD for " + worldName);

        World world = Bukkit.getWorld(worldName);
        if (world == null) {
          world = new WorldCreator(worldName)
              .seed(seedEntry.seed())
              .environment(World.Environment.NORMAL)
              .createWorld();
        }

        if (world == null) throw new RuntimeException("Failed to create world " + worldName);

        world.setDifficulty(Difficulty.EASY);
        state.setCurrentWorldName(world.getName());
        state.setCurrentWorldFolder(world.getWorldFolder());

        advance(Step.GENERATE_CHUNKS);
      } catch (Exception e) {
        handleError(e);
      }
    });
  }

  private void handleGenerateChunks() {
    final String targetWorld = state.getCurrentWorldName();
    if (targetWorld == null) {
      state.setCurrentStep(Step.CREATE_WORLD.name());
      dispatch();
      return;
    }

    logger.info("Step: GENERATE_CHUNKS for " + targetWorld);

    // Register a fresh listener per generation call using Chunky's Consumer API.
    // Capturing targetWorld as a final local to ensure the correct world is tracked.
    chunky.onGenerationComplete(new java.util.function.Consumer<>() {
      private boolean active = true;
      @Override
      public void accept(GenerationCompleteEvent event) {
        if (!active || !event.world().equals(targetWorld)) return;
        active = false; // Ensure this listener only triggers once for its target world

        if (interrupted) return;

        logger.info("Generation complete for " + targetWorld);
        // Advance to the next step on the main thread
        Bukkit.getScheduler().runTask(plugin, () -> advance(Step.PREPARE_WORLD));
      }
    });

    Bukkit.getScheduler().runTask(plugin, () -> {
      World world = Bukkit.getWorld(targetWorld);
      if (world == null) {
        handleError(new RuntimeException("World " + targetWorld + " not found for generation"));
        return;
      }

      if (!chunky.isRunning(targetWorld)) {
        chunky.startTask(
            targetWorld,
            "square",
            world.getSpawnLocation().getX(),
            world.getSpawnLocation().getZ(),
            config.getGenerationRadius(),
            config.getGenerationRadius(),
            "concentric"
        );
      }
    });
  }

  private void handlePrepareWorld() {
    Bukkit.getScheduler().runTask(plugin, () -> {
      String worldName = state.getCurrentWorldName();
      if (worldName == null) {
        state.setCurrentStep(Step.CREATE_WORLD.name());
        dispatch();
        return;
      }

      logger.info("Step: PREPARE_WORLD for " + worldName);
      try {
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
          world = new WorldCreator(worldName).createWorld();
        }
        if (world == null) throw new RuntimeException("World " + worldName + " not loaded for preparation");

        // Spawn Adjustment
        if (!SpawnAdjuster.isSafeSpawn(world.getSpawnLocation())) {
          Location safeSpawn = spawnAdjuster.findSafeSpawn(world);
          if (safeSpawn != null) {
            world.setSpawnLocation(safeSpawn);
            logger.info("Spawn adjusted to: " + safeSpawn.getBlockX() + ", " + safeSpawn.getBlockY() + ", " + safeSpawn.getBlockZ());
          }
        }

        // Metadata
        SeedEntry seedEntry = seeds.get(state.getCurrentIndex());
        createManhuntYml(world, seedEntry);

        // Cage
        cageBuilder.buildCage(world);

        // Save and Unload
        world.save();
        Bukkit.unloadWorld(world, true);
        logger.info("World " + worldName + " prepared and unloaded.");

        advance(Step.ZIP_EXPORT);
      } catch (Exception e) {
        handleError(e);
      }
    });
  }

  private void handleZipExport() {
    logger.info("Step: ZIP_EXPORT for " + state.getCurrentWorldName());
    Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
      try {
        SeedEntry seedEntry = seeds.get(state.getCurrentIndex());
        File worldFolder = state.getCurrentWorldFolder();
        if (worldFolder == null || !worldFolder.exists()) {
          throw new IOException("World folder not found for zipping: " + state.getCurrentWorldName());
        }

        File exportDir = new File(config.getExportPath());
        if (!exportDir.exists() && !exportDir.mkdirs()) throw new IOException("Failed to create export dir");

        String zipFileName = seedEntry.seed() + "_" + config.getServerId() + ".zip";
        File zipFile = new File(exportDir, zipFileName);

        // Idempotency: Validate existing zip
        if (zipFile.exists()) {
          if (FileUtils.validateZipFile(zipFile)) {
            logger.info("Valid zip already exists, skipping zip step.");
            Bukkit.getScheduler().runTask(plugin, () -> advance(Step.WRITE_COMPLETED));
            return;
          } else {
            logger.warning("Existing zip invalid, deleting and re-zipping.");
            zipFile.delete();
          }
        }

        FileUtils.zipDirectory(worldFolder, zipFile, Set.of("session.lock", "uid.dat"));

        if (!FileUtils.validateZipFile(zipFile)) {
          throw new IOException("Zip validation failed after creation.");
        }

        logger.info("Successfully zipped and validated: " + zipFileName);
        Bukkit.getScheduler().runTask(plugin, () -> advance(Step.WRITE_COMPLETED));
      } catch (Exception e) {
        // Delete partial zip on error
        try {
          SeedEntry seedEntry = seeds.get(state.getCurrentIndex());
          File zipFile = new File(config.getExportPath(), seedEntry.seed() + "_" + config.getServerId() + ".zip");
          if (zipFile.exists()) zipFile.delete();
        } catch (Exception ignored) {}

        Bukkit.getScheduler().runTask(plugin, () -> handleError(e));
      }
    });
  }

  private void handleWriteCompleted() {
    logger.info("Step: WRITE_COMPLETED");
    final int index = state.getCurrentIndex();
    final SeedEntry seedEntry = seeds.get(index);
    Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
      synchronized (completedSeeds) {
        try {
          if (!completedSeeds.contains(seedEntry.seed())) {
            Files.writeString(
                completedSeedsFile.toPath(),
                seedEntry.seed() + "\n",
                StandardOpenOption.APPEND,
                StandardOpenOption.CREATE
            );
            completedSeeds.add(seedEntry.seed());
          }
          Bukkit.getScheduler().runTask(plugin, () -> advance(Step.CLEANUP));
        } catch (IOException e) {
          Bukkit.getScheduler().runTask(plugin, () -> handleError(e));
        }
      }
    });
  }

  private void handleCleanup() {
    logger.info("Step: CLEANUP for " + state.getCurrentWorldName());
    Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
      File worldFolder = state.getCurrentWorldFolder();
      if (worldFolder != null && worldFolder.exists()) {
        FileUtils.deleteDirectoryWithRetry(worldFolder, logger, success ->
          Bukkit.getScheduler().runTask(plugin, () -> {
            if (success) advance(Step.COMPLETE);
            else handleError(new RuntimeException("Failed to delete world folder"));
          })
        );
      } else {
        Bukkit.getScheduler().runTask(plugin, () -> advance(Step.COMPLETE));
      }
    });
  }

  private void handleComplete() {
    logger.info("Step: COMPLETE for seed index " + state.getCurrentIndex());
    state.setSuccessCount(state.getSuccessCount() + 1);
    state.setCurrentIndex(state.getCurrentIndex() + 1);
    state.setCurrentWorldName(null);
    state.setCurrentWorldFolder(null);
    state.setCurrentStep(Step.CREATE_WORLD.name());
    worldsInCurrentBatch++;
    retryCount = 0;
    saveState();

    if (state.getCurrentIndex() >= seeds.size()) {
      logger.info("All worlds generated successfully!");
      plugin.running = false;
      state.setCurrentStep("COMPLETED");
      saveState();
      return;
    }

    long delay;
    if (worldsInCurrentBatch >= config.getWorldsPerBatch()) {
      worldsInCurrentBatch = 0;
      delay = config.getPauseBetweenBatches() * 20L;
      logger.info("Batch complete. Pausing for " + config.getPauseBetweenBatches() + " seconds...");
    } else {
      delay = config.getWorldDelayTicks();
    }

    scheduledTask = Bukkit.getScheduler().runTaskLater(plugin, this::dispatch, delay);
  }

  private void handleError(Exception e) {
    if (interrupted) return;
    ErrorHandler.ErrorCategory category = errorHandler.categorize(e);
    errorHandler.handleError(e, "generation pipeline step: " + state.getCurrentStep());

    if (category == ErrorHandler.ErrorCategory.FATAL) {
      logger.severe("Fatal error. Stopping generation.");
      plugin.running = false;
      saveState();
    } else if (category == ErrorHandler.ErrorCategory.RECOVERABLE && retryCount < GenerationConstants.MAX_RETRIES_PER_SEED) {
      retryCount++;
      logger.info("Recoverable error. Retry " + retryCount + "/" + GenerationConstants.MAX_RETRIES_PER_SEED + " for seed index " + state.getCurrentIndex());
      scheduledTask = Bukkit.getScheduler().runTaskLater(plugin, this::dispatch, config.getWorldDelayTicks());
    } else {
      state.setFailureCount(state.getFailureCount() + 1);
      SeedEntry seedEntry = seeds.get(state.getCurrentIndex());
      state.addFailedSeed(new FailedSeedEntry(seedEntry, e.getMessage() != null ? e.getMessage() : e.toString()));
      logger.warning("Skipping seed " + seedEntry.seed() + " due to persistent errors.");

      // Advance to cleanup to remove any partial files before moving to next seed
      state.setCurrentStep(Step.CLEANUP.name());
      saveState();
      dispatch();
    }
  }

  private void createManhuntYml(World world, SeedEntry seed) {
    File manhuntFile = new File(world.getWorldFolder(), GenerationConstants.MANHUNT_YML_FILE_NAME);
    String direction = calculateDirection(seed, world.getSpawnLocation());
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
      logger.log(Level.SEVERE, "Failed to create manhunt.yml", e);
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

  private void saveState() {
    try {
      state.save(stateFile);
    } catch (IOException e) {
      logger.log(Level.SEVERE, "CRITICAL: State persistence failure - cannot continue safely", e);
      stop();
      throw new RuntimeException("State persistence failure", e);
    }
  }

  public GenerationState getState() {
    return state;
  }
}
