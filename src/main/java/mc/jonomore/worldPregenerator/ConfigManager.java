package mc.jonomore.worldPregenerator;

import org.bukkit.Material;

public class ConfigManager {
  private final WorldPregenerator plugin;

  // Cached config values
  private int generationRadius;
  private String exportPath;
  private String seedsFile;
  private int maxSearchRadius;
  private int maxVerticalScan;
  private Material cageMaterial;
  private int cageRadius;
  private int cageHeight;

  public ConfigManager(WorldPregenerator plugin) {
    this.plugin = plugin;
    plugin.saveDefaultConfig();
    loadConfig();
  }

  public void loadConfig() {
    plugin.reloadConfig();
    generationRadius = plugin.getConfig().getInt("generation-radius");
    exportPath = plugin.getConfig().getString("export-path");
    seedsFile = plugin.getConfig().getString("seeds-file");
    maxSearchRadius = plugin.getConfig().getInt("spawn-adjustment.maxSearchRadius");
    maxVerticalScan = plugin.getConfig().getInt("spawn-adjustment.maxVerticalScan");

    String cageMaterialName = plugin.getConfig().getString("cage-building.cage-material", "PURPLE_STAINED_GLASS");
    Material parsedMaterial = Material.getMaterial(cageMaterialName);
    if (parsedMaterial == null) {
      plugin.getLogger().warning("Invalid cage material: '" + cageMaterialName + "'. Defaulting to 'PURPLE_STAINED_GLASS'");
      cageMaterial = Material.PURPLE_STAINED_GLASS;
    } else {
      cageMaterial = parsedMaterial;
    }

    cageRadius = plugin.getConfig().getInt("cage-building.cage-radius");
    cageHeight = plugin.getConfig().getInt("cage-building.cage-height");
  }

  public int getGenerationRadius() {
    return generationRadius;
  }

  public String getExportPath() {
    return exportPath;
  }

  public String getSeedsFile() {
    return seedsFile;
  }

  public int getMaxSearchRadius() { return maxSearchRadius; }

  public int getMaxVerticalScan() { return maxVerticalScan; }

  public Material getCageMaterial() { return cageMaterial; }

  public int getCageRadius() { return cageRadius; }

  public int getCageHeight() { return cageHeight; }

  @Override
  public String toString() {
    return "generation-radius: " + generationRadius + "\n" +
        "export-path: " + exportPath + "\n" +
        "seeds-file: " + seedsFile + "\n" +
        "spawn-adjustment:\n" +
        "  maxSearchRadius: " + maxSearchRadius + "\n" +
        "  maxVerticalScan: " + maxVerticalScan + "\n" +
        "cage-building:\n" +
        "  cage-material: " + cageMaterial + "\n" +
        "  cage-radius: " + cageRadius + "\n" +
        "  cage-height: " + cageHeight;
  }
}
