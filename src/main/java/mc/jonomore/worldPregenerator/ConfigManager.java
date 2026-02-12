package mc.jonomore.worldPregenerator;

import org.bukkit.Material;
import java.io.File;

public class
ConfigManager {
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
  private long worldDelayTicks;

  public ConfigManager(WorldPregenerator plugin) {
    this.plugin = plugin;
    plugin.saveDefaultConfig();
    loadConfig();
  }

  public void loadConfig() {
    plugin.reloadConfig();
    
    generationRadius = plugin.getConfig().getInt("generation-radius", 1200);
    if (generationRadius < 100 || generationRadius > 10000) {
        plugin.getLogger().warning("generation-radius out of range [100,10000]: " + generationRadius + ". Defaulting to 1200.");
        generationRadius = 1200;
    }

    exportPath = plugin.getConfig().getString("export-path", "exported_worlds");
    File exportDir = new File(exportPath);
    if (!exportDir.exists() && !exportDir.mkdirs()) {
        plugin.getLogger().warning("export-path directory could not be created or found: " + exportPath);
    }

    seedsFile = plugin.getConfig().getString("seeds-file", "seeds.txt");
    File sFile = new File(seedsFile);
    if (!sFile.exists()) {
        plugin.getLogger().warning("seeds-file does not exist: " + seedsFile);
    }

    maxSearchRadius = plugin.getConfig().getInt("spawn-adjustment.maxSearchRadius", 100);
    if (maxSearchRadius < 10 || maxSearchRadius > 256) {
        plugin.getLogger().warning("maxSearchRadius out of range [10,256]: " + maxSearchRadius + ". Defaulting to 100.");
        maxSearchRadius = 100;
    }

    maxVerticalScan = plugin.getConfig().getInt("spawn-adjustment.maxVerticalScan", 128);
    if (maxVerticalScan < 10 || maxVerticalScan > 256) {
        plugin.getLogger().warning("maxVerticalScan out of range [10,256]: " + maxVerticalScan + ". Defaulting to 128.");
        maxVerticalScan = 128;
    }

    String cageMaterialName = plugin.getConfig().getString("cage-building.cage-material", "PURPLE_STAINED_GLASS");
    Material parsedMaterial = Material.getMaterial(cageMaterialName);
    if (parsedMaterial == null) {
      plugin.getLogger().warning("Invalid cage material: '" + cageMaterialName + "'. Defaulting to 'PURPLE_STAINED_GLASS'");
      cageMaterial = Material.PURPLE_STAINED_GLASS;
    } else {
      cageMaterial = parsedMaterial;
    }

    cageRadius = plugin.getConfig().getInt("cage-building.cage-radius", 4);
    if (cageRadius < 2 || cageRadius > 10) {
        plugin.getLogger().warning("cage-radius out of range [2,10]: " + cageRadius + ". Defaulting to 4.");
        cageRadius = 4;
    }

    cageHeight = plugin.getConfig().getInt("cage-building.cage-height", 3);
    if (cageHeight < 3 || cageHeight > 9 || cageHeight % 2 == 0) {
        plugin.getLogger().warning("cage-height must be an odd number between 3-9: " + cageHeight + ". Defaulting to 3.");
        cageHeight = 3;
    }
    
    worldDelayTicks = plugin.getConfig().getLong("world-delay-ticks", 40L);
    if (worldDelayTicks < 0) {
        plugin.getLogger().warning("world-delay-ticks cannot be negative: " + worldDelayTicks + ". Defaulting to 40.");
        worldDelayTicks = 40L;
    }
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

  public long getWorldDelayTicks() { return worldDelayTicks; }

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
        "  cage-height: " + cageHeight + "\n" +
        "world-delay-ticks: " + worldDelayTicks;
  }
}