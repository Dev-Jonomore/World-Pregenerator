package mc.jonomore.worldPregenerator;

import mc.jonomore.worldPregenerator.config.ConfigManager;
import mc.jonomore.worldPregenerator.generation.FailedSeedEntry;
import mc.jonomore.worldPregenerator.generation.GenerationState;
import mc.jonomore.worldPregenerator.generation.GenerationTask;
import mc.jonomore.worldPregenerator.generation.SeedEntry;
import mc.jonomore.worldPregenerator.util.FileUtils;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;
import org.popcraft.chunky.api.ChunkyAPI;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;

public final class WorldPregenerator extends JavaPlugin {

  public ConfigManager config;
  public boolean running = false;
  GenerationState state = null;
  private GenerationTask task = null;

  public void start() {
    if (running) {
      getLogger().warning("Generation already running!");
      return;
    }

    if (task == null) {
      getLogger().info("No task found, creating a new one.");
      List<SeedEntry> allSeeds = readSeeds();
      if (allSeeds.isEmpty()) {
        getLogger().warning("No seeds found in file!");
        return;
      }
      
      java.util.Set<Long> completedSeeds = loadCompletedSeeds();
      List<SeedEntry> seedsToProcess = allSeeds.stream()
              .filter(s -> !completedSeeds.contains(s.seed()))
              .toList();
      
      if (seedsToProcess.isEmpty()) {
          getLogger().info("All seeds have already been completed according to completed.txt");
          return;
      }

      ChunkyAPI chunky = getServer().getServicesManager().load(ChunkyAPI.class);
      if (chunky == null) {
        getLogger().severe("Chunky API not found! Make sure Chunky plugin is installed.");
        return;
      }
      
      if (state == null) {
          state = new GenerationState();
      }

      state.setCurrentIndex(0);
      task = new GenerationTask(this, seedsToProcess, completedSeeds, chunky, state, false);
    }

    running = true;
    task.start();
  }

  public void retry() {
    if (running) {
      getLogger().warning("Generation already running!");
      return;
    }

    if (state == null || state.getFailedSeeds().isEmpty()) {
      getLogger().warning("No failed seeds to retry!");
      return;
    }

    List<SeedEntry> failedSeeds = state.getFailedSeeds().stream()
            .map(FailedSeedEntry::seedEntry)
            .toList();
    getLogger().info("Retrying generation for " + failedSeeds.size() + " failed seeds.");

    ChunkyAPI chunky = getServer().getServicesManager().load(ChunkyAPI.class);
    if (chunky == null) {
      getLogger().severe("Chunky API not found! Make sure Chunky plugin is installed.");
      return;
    }

    java.util.Set<Long> completedSeeds = loadCompletedSeeds();

    // Reset state for the retry run
    state.getFailedSeeds().clear();
    state.setCurrentIndex(0);
    state.setSuccessCount(0);
    state.setFailureCount(0);
    state.setStartTime(System.currentTimeMillis());
    state.setCurrentStep("RETRYING_FAILED");

    task = new GenerationTask(this, failedSeeds, completedSeeds, chunky, state, false);
    running = true;
    task.start();
  }

  private java.util.Set<Long> loadCompletedSeeds() {
      File completedFile = new File(getDataFolder(), GenerationConstants.COMPLETED_SEEDS_FILE_NAME);
      if (!completedFile.exists()) return new java.util.HashSet<>();
      
      try (java.util.stream.Stream<String> lines = Files.lines(completedFile.toPath())) {
          return lines.map(String::trim)
              .filter(line -> !line.isEmpty())
              .map(line -> {
                  try {
                      return Long.parseLong(line);
                  } catch (NumberFormatException e) {
                      getLogger().warning("Failed to parse seed in completed.txt: " + line);
                      return null;
                  }
              })
              .filter(java.util.Objects::nonNull)
              .collect(java.util.stream.Collectors.toSet());
      } catch (IOException e) {
          getLogger().log(Level.SEVERE, "Failed to load completed seeds", e);
          return new java.util.HashSet<>();
      }
  }

  public void stop() {
    if (!running) {
      getLogger().warning("No generation is running!");
    } else {
      running = false;
      if (task != null) {
        task.stop();
      }
    }
  }

  public void reset() {
      if (task != null) {
          task.reset();
          task = null;
          state = null;
          running = false;
          
          File stateFile = new File(getDataFolder(), GenerationConstants.STATE_FILE_NAME);
          if (stateFile.exists()) stateFile.delete();

          File completedFile = new File(getDataFolder(), GenerationConstants.COMPLETED_SEEDS_FILE_NAME);
          if (completedFile.exists()) completedFile.delete();
      } else {
          getLogger().warning("No task to reset!");
      }
  }

  private List<SeedEntry> readSeeds() {
    // Expected format: "- XXX [X, ~ Z]" where XXX is seed, X is hintX, Z is hintZ
    // Example: "- 12345 [100, ~ 200]"
    java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("^-\\s*(-?\\d+)\\s*\\[\\s*(-?\\d+)\\s*,\\s*~\\s*(-?\\d+)\\s*]$");
    try (java.util.stream.Stream<String> lines = Files.lines(Paths.get(config.getSeedsFile()))) {
        return lines.map(String::trim)
            .filter(line -> !line.isEmpty())
            .map(line -> {
                java.util.regex.Matcher matcher = pattern.matcher(line);
                if (matcher.find()) {
                    try {
                        long seed = Long.parseLong(matcher.group(1));
                        int hintX = Integer.parseInt(matcher.group(2));
                        int hintZ = Integer.parseInt(matcher.group(3));
                        return new SeedEntry(seed, hintX, hintZ);
                    } catch (NumberFormatException e) {
                        getLogger().warning("Failed to parse numbers in line: " + line);
                    }
                } else {
                    getLogger().warning("Line does not match seed pattern: " + line);
                }
                return null;
            })
            .filter(java.util.Objects::nonNull)
            .toList();
    } catch (IOException e) {
      getLogger().severe("Error reading seeds: " + e.getMessage());
      return Collections.emptyList();
    }
  }

  @Override
  public void onEnable() {
    config = new ConfigManager(this);
    
    loadState();
    
    this.getLifecycleManager().registerEventHandler(
        LifecycleEvents.COMMANDS,
        commands -> commands.registrar().register(
            WPCommands.createCommand(this),
            List.of("wp", "worldpregen")
        )
    );

    if (state != null && state.getCurrentIndex() > 0 && !"COMPLETED".equals(state.getCurrentStep())) {
        getLogger().info("Found previous generation state. You can resume with /wp start");
        
        // Cleanup partial world if it exists
        if (state.getCurrentWorldName() != null) {
            String worldName = state.getCurrentWorldName();
            File worldFolder = new File(getServer().getWorldContainer(), worldName);
            if (worldFolder.exists()) {
                getLogger().info("Cleaning up partial world from previous run: " + worldName);
                Bukkit.getScheduler().runTaskLater(this, () -> {
                    World world = Bukkit.getWorld(worldName);
                    if (world != null) {
                        Bukkit.unloadWorld(world, false);
                    }
                    Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
                        try {
                            FileUtils.deleteDirectory(worldFolder);
                            getLogger().info("Successfully cleaned up " + worldName);
                        } catch (IOException e) {
                            getLogger().log(Level.SEVERE, "Failed to cleanup partial world " + worldName, e);
                        }
                    });
                    state.setCurrentStep("CREATE_WORLD");
                }, 20L); // Wait a bit for server to fully start
            }
        }
    }

    getLogger().info("WorldPregenerator enabled!");
  }

  @Override
  public void onDisable() {
    if (running && task != null) {
      getLogger().info("Stopping generation due to plugin disable...");
      task.stop();
      running = false;
    }
  }
  
  private void loadState() {
      File stateFile = new File(getDataFolder(), GenerationConstants.STATE_FILE_NAME);
      if (stateFile.exists()) {
          try {
              state = GenerationState.load(stateFile);
              getLogger().info("Loaded generation state from " + GenerationConstants.STATE_FILE_NAME);
          } catch (Exception e) {
              getLogger().log(Level.SEVERE, "Failed to load generation state. The state file might be corrupt or using an older format.", e);
              // We could potentially rename the corrupt file here to prevent infinite loop
              stateFile.renameTo(new File(getDataFolder(), GenerationConstants.STATE_FILE_NAME + ".corrupt"));
          }
      }
  }

  public void testOne(Long seedOverride) {
    if (running) {
      getLogger().warning("Generation already running!");
      return;
    }

    List<SeedEntry> allSeeds = readSeeds();
    SeedEntry testSeed;

    if (seedOverride != null) {
      testSeed = new SeedEntry(seedOverride, 0, 0);
    } else if (!allSeeds.isEmpty()) {
      testSeed = allSeeds.get(0);
    } else {
      testSeed = new SeedEntry(42L, 0, 0);
    }

    getLogger().info("Starting test-one for seed: " + testSeed.seed());

    ChunkyAPI chunky = getServer().getServicesManager().load(ChunkyAPI.class);
    if (chunky == null) {
      getLogger().severe("Chunky API not found!");
      return;
    }

    state = new GenerationState();
    task = new GenerationTask(this, List.of(testSeed), new java.util.HashSet<>(), chunky, state, true);
    running = true;
    task.start();
  }
}