package mc.jonomore.worldPregenerator;

import org.bukkit.Material;

import java.util.Objects;

public class ConfigManager {
  private final WorldPregenerator plugin;

  // Cached config values
  private int generation_radius;
  private String exportPath;
  private String seedsFile;
  private int maxSearchRadius;
  private int maxVerticalScan;
  private Material cage_material;
  private int cage_radius;
  private int cage_height;

  public ConfigManager(WorldPregenerator plugin) {
    this.plugin = plugin;
    plugin.saveDefaultConfig();
    loadConfig();
  }

  public void loadConfig() {
    plugin.reloadConfig();
    generation_radius = plugin.getConfig().getInt("generation-radius");
    exportPath = plugin.getConfig().getString("export-path");
    seedsFile = plugin.getConfig().getString("seeds-file");
    maxSearchRadius = plugin.getConfig().getInt("spawn-adjustment.maxSearchRadius");
    maxVerticalScan = plugin.getConfig().getInt("spawn-adjustment.maxVerticalScan");

    String cageMaterialName = plugin.getConfig().getString("cage-building.cage-material", "PURPLE_STAINED_GLASS");
    Material parsedMaterial = Material.getMaterial(cageMaterialName);
    if (parsedMaterial == null) {
      plugin.getLogger().warning("Invalid cage material: '" + cageMaterialName + "'. Defaulting to 'PURPLE_STAINED_GLASS'");
      cage_material = Material.PURPLE_STAINED_GLASS;
    } else {
      cage_material = parsedMaterial;
    }

    cage_radius = plugin.getConfig().getInt("cage-building.cage-radius");
    cage_height = plugin.getConfig().getInt("cage-building.cage-height");
  }

  public int getRadius() {
    return generation_radius;
  }

  public String getExportPath() {
    return exportPath;
  }

  public String getSeedsFile() {
    return seedsFile;
  }

  public int getMaxSearchRadius() { return maxSearchRadius; }

  public int getMaxVerticalScan() { return maxVerticalScan; }

  public Material getCageMaterial() { return cage_material; }

  public int getCageRadius() { return cage_radius; }

  public int getCageHeight() { return cage_height; }

  @Override
  public String toString() {
    return "generation-radius: " + generation_radius + "\n" +
        "export-path: " + exportPath + "\n" +
        "seeds-file: " + seedsFile + "\n" +
        "spawn-adjustment:\n" +
        "  maxSearchRadius: " + maxSearchRadius + "\n" +
        "  maxVerticalScan: " + maxVerticalScan + "\n" +
        "cage-building:\n" +
        "  cage-material: " + cage_material + "\n" +
        "  cage-radius: " + cage_radius + "\n" +
        "  cage-height: " + cage_height;
  }
}
