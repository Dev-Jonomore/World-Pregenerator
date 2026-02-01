package mc.jonomore.worldPregenerator;

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.plugin.java.JavaPlugin;
import org.popcraft.chunky.api.ChunkyAPI;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;

public final class WorldPregenerator extends JavaPlugin {

  ConfigManager config;
  boolean running = false;
  GenerationTask task = null;

  public void start() {
    if (running) {
      getLogger().warning("Generation already running!");
      return;
    }

    if (task == null) {
      getLogger().info("No task found, creating a new one.");
      List<Long> seeds = readSeeds();
      if (seeds.isEmpty()) {
        getLogger().warning("No seeds found in file!");
        return;
      }
      ChunkyAPI chunky = getServer().getServicesManager().load(ChunkyAPI.class);
      if (chunky == null) {
        getLogger().severe("Chunky API not found! Make sure Chunky plugin is installed.");
        return;
      }
      task = new GenerationTask(this, seeds, chunky);
    }

    running = true;
    task.start();
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

  private List<Long> readSeeds() {
    List<Long> seeds = new ArrayList<>();
    try {
      java.util.Scanner scanner = new java.util.Scanner(new File(config.getSeedsFile()));
      while (scanner.hasNextLong()) {
        seeds.add(scanner.nextLong());
      }
    } catch (FileNotFoundException e) {
      getLogger().severe("Seeds file not found: " + e.getMessage());
    }
    getLogger().info("Loaded " + seeds.size() + " seeds from file");
    return seeds;
  }

  @Override
  public void onEnable() {
    config = new ConfigManager(this);
    this.getLifecycleManager().registerEventHandler(
        LifecycleEvents.COMMANDS,
        commands -> commands.registrar().register(
            WPCommands.createCommand(this),
            List.of("wp", "worldpregen")
        )
    );
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
}
