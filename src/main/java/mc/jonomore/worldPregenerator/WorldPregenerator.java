package mc.jonomore.worldPregenerator;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
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
    }
  }

  public void stop() {
    running = stop;
    // TODO: cleanup
  }

  private ArrayList<long> readSeeds() {
    ArrayList<long> seeds;
    try {
      java.uitl.Scanner scanner = new java.util.Scanner(new File(config.getSeedsFile()))
      while (scanner.hasNextLine()) {
        seeds.add(scanner.getLong())
      }
    }
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
