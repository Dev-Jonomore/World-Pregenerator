package mc.jonomore.worldPregenerator;

public class ConfigManager {
  private final WorldPregenerator plugin;

  public ConfigManager(WorldPregenerator plugin) {
    this.plugin = plugin;
    plugin.saveDefaultConfig();
  }

  public int getRadius() {
    return plugin.getConfig().getInt("chunk-radius");
  }

  public String getExportPath() {
    return plugin.getConfig().getString("export-path");
  }

  public String getSeedsFile() {
    return plugin.getConfig().getString("seeds-file");
  }
}
