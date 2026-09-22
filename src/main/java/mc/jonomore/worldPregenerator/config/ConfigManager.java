package mc.jonomore.worldPregenerator.config;

import mc.jonomore.worldPregenerator.WorldPregenerator;
import org.bukkit.Material;
import java.io.File;
import java.util.List;

public class
ConfigManager {
  private final WorldPregenerator plugin;

  // Cached config values
  private int generationRadius;
  private String exportPath;
  private String serverId;
  private String seedsFile;
  private int maxSearchRadius;
  private int maxVerticalScan;
  private Material cageMaterial;
  private int cageRadius;
  private int cageHeight;
  private long worldDelayTicks;
  private int worldsPerBatch;
  private long pauseBetweenBatches;
  private List<String> structureFinderStructures;
  private boolean structureFinderWhitelist;
  private int structureFinderSearchRadius;

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
    serverId = plugin.getConfig().getString("server-id", "default");
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

    worldsPerBatch = plugin.getConfig().getInt("batch-settings.worlds-per-batch", 10);
    if (worldsPerBatch <= 0) {
        plugin.getLogger().warning("worlds-per-batch must be positive: " + worldsPerBatch + ". Defaulting to 10.");
        worldsPerBatch = 10;
    }

    pauseBetweenBatches = plugin.getConfig().getLong("batch-settings.pause-between-batches", 60L);
    if (pauseBetweenBatches < 0) {
        plugin.getLogger().warning("pause-between-batches cannot be negative: " + pauseBetweenBatches + ". Defaulting to 60.");
        pauseBetweenBatches = 60L;
    }

    structureFinderStructures = plugin.getConfig().getStringList("structure-finder.structures");
    structureFinderWhitelist = plugin.getConfig().getBoolean("structure-finder.whitelist", true);
    structureFinderSearchRadius = plugin.getConfig().getInt("structure-finder.search-radius", 1200);
    if (structureFinderSearchRadius < 16 || structureFinderSearchRadius > 10000) {
        plugin.getLogger().warning("structure-finder.search-radius out of range [16,10000]: " + structureFinderSearchRadius + ". Defaulting to 1200.");
        structureFinderSearchRadius = 1200;
    }
  }

  public int getGenerationRadius() {
    return generationRadius;
  }

  public String getExportPath() {
    return exportPath;
  }

  public String getServerId() {
    return serverId;
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

  public int getWorldsPerBatch() { return worldsPerBatch; }

  public long getPauseBetweenBatches() { return pauseBetweenBatches; }

  public List<String> getStructureFinderStructures() { return structureFinderStructures; }

  public boolean isStructureFinderWhitelist() { return structureFinderWhitelist; }

  public int getStructureFinderSearchRadius() { return structureFinderSearchRadius; }

  @Override
  public String toString() {
    return "generation-radius: " + generationRadius + "\n" +
        "server-id: " + serverId + "\n" +
        "export-path: " + exportPath + "\n" +
        "seeds-file: " + seedsFile + "\n" +
        "spawn-adjustment:\n" +
        "  maxSearchRadius: " + maxSearchRadius + "\n" +
        "  maxVerticalScan: " + maxVerticalScan + "\n" +
        "cage-building:\n" +
        "  cage-material: " + cageMaterial + "\n" +
        "  cage-radius: " + cageRadius + "\n" +
        "  cage-height: " + cageHeight + "\n" +
        "batch-settings:\n" +
        "  worlds-per-batch: " + worldsPerBatch + "\n" +
        "  pause-between-batches: " + pauseBetweenBatches + "\n" +
        "structure-finder:\n" +
        "  whitelist: " + structureFinderWhitelist + "\n" +
        "  search-radius: " + structureFinderSearchRadius + "\n" +
        "  structures: " + structureFinderStructures + "\n" +
        "world-delay-ticks: " + worldDelayTicks;
  }
}