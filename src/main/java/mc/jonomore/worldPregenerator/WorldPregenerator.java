package mc.jonomore.worldPregenerator;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;

public final class WorldPregenerator extends JavaPlugin {

  private ConfigManager config;
  private boolean running = false;

  public void start() {
    if (running) {
      this.getLogger().warning("Generation already running!");
    }
    else {
      running = true;
      // TODO: read seeds (method), loop through worlds
      ArrayList<Long> seeds = readSeeds();
      if (!seeds.isEmpty()) {
        // TODO: loop through seeds
      }
    }
  }

  public void stop() {
    running = false;
    // TODO: cleanup
  }

  private ArrayList<Long> readSeeds() {
    ArrayList<Long> seeds = new ArrayList<Long>();
    try {
      java.util.Scanner scanner = new java.util.Scanner(new File(config.getSeedsFile()));
      while (scanner.hasNextLine()) {
        seeds.add(scanner.nextLong());
      }
    } catch (FileNotFoundException e) {
      getLogger().severe("Seeds file not found: " + e.getMessage());
    }
    return seeds;
  }

  @Override
  public void onEnable() {
    // Plugin startup logic
    config = new ConfigManager(this);
  }

  @Override
  public void onDisable() {
    // Plugin shutdown logic
  }
}
