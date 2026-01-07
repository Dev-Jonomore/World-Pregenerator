package mc.jonomore.worldPregenerator;

public class ConfigManager {
  private final WorldPregenerator plugin;

  // Cached config values
  private int radius;
  private String exportPath;
  private String seedsFile;

  public ConfigManager(WorldPregenerator plugin) {
    this.plugin = plugin;
    plugin.saveDefaultConfig();
    loadConfig();
  }

  public void loadConfig() {
    radius = plugin.getConfig().getInt("radius");
    exportPath = plugin.getConfig().getString("export-path");
    seedsFile = plugin.getConfig().getString("seeds-file");
  }

  public int getRadius() {
    return radius;
  }

  public String getExportPath() {
    return exportPath;
  }

  public String getSeedsFile() {
    return seedsFile;
  }
}
