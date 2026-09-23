package mc.jonomore.worldPregenerator.config;

import mc.jonomore.worldPregenerator.WorldPregenerator;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.yaml.NodeStyle;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.logging.Level;

public class ConfigManager {
  private final WorldPregenerator plugin;
  private final YamlConfigurationLoader loader;

  // Cached config values
  private int generationRadius;
  private String exportPath;
  private String serverId;
  private String seedsFile;
  private long worldDelayTicks;
  private int worldsPerBatch;
  private long pauseBetweenBatches;
  private List<String> structureFinderStructures;
  private boolean structureFinderWhitelist;
  private int structureFinderSearchRadius;

  public ConfigManager(WorldPregenerator plugin) {
    this.plugin = plugin;
    plugin.saveDefaultConfig();
    this.loader = YamlConfigurationLoader.builder()
        .path(plugin.getDataFolder().toPath().resolve("config.yml"))
        .nodeStyle(NodeStyle.BLOCK)
        .indent(2)
        .build();
    loadConfig();
  }

  /**
   * Loads and validates config.yml. Missing keys fall back to the defaults in {@link PluginConfig}.
   */
  public void loadConfig() {
    PluginConfig cfg;
    try {
      cfg = loader.load().get(PluginConfig.class);
    } catch (IOException e) {
      plugin.getLogger().log(Level.SEVERE, "Failed to load config.yml; using defaults", e);
      cfg = new PluginConfig();
    }

    generationRadius = cfg.generationRadius;
    if (generationRadius < 100 || generationRadius > 10000) {
        plugin.getLogger().warning("generation-radius out of range [100,10000]: " + generationRadius + ". Defaulting to 1200.");
        generationRadius = 1200;
    }

    exportPath = cfg.exportPath;
    serverId = cfg.serverId;
    File exportDir = new File(exportPath);
    if (!exportDir.exists() && !exportDir.mkdirs()) {
        plugin.getLogger().warning("export-path directory could not be created or found: " + exportPath);
    }

    seedsFile = cfg.seedsFile;
    File sFile = new File(seedsFile);
    if (!sFile.exists()) {
        plugin.getLogger().warning("seeds-file does not exist: " + seedsFile);
    }
    
    worldDelayTicks = cfg.worldDelayTicks;
    if (worldDelayTicks < 0) {
        plugin.getLogger().warning("world-delay-ticks cannot be negative: " + worldDelayTicks + ". Defaulting to 40.");
        worldDelayTicks = 40L;
    }

    worldsPerBatch = cfg.batchSettings.worldsPerBatch;
    if (worldsPerBatch <= 0) {
        plugin.getLogger().warning("worlds-per-batch must be positive: " + worldsPerBatch + ". Defaulting to 10.");
        worldsPerBatch = 10;
    }

    pauseBetweenBatches = cfg.batchSettings.pauseBetweenBatches;
    if (pauseBetweenBatches < 0) {
        plugin.getLogger().warning("pause-between-batches cannot be negative: " + pauseBetweenBatches + ". Defaulting to 60.");
        pauseBetweenBatches = 60L;
    }

    structureFinderStructures = List.copyOf(cfg.structureFinder.structures);
    structureFinderWhitelist = cfg.structureFinder.whitelist;
    structureFinderSearchRadius = cfg.structureFinder.searchRadius;
    if (structureFinderSearchRadius < 16 || structureFinderSearchRadius > 10000) {
        plugin.getLogger().warning("structure-finder.search-radius out of range [16,10000]: " + structureFinderSearchRadius + ". Defaulting to 1200.");
        structureFinderSearchRadius = 1200;
    }
  }

  /**
   * Sets a single value in config.yml and reloads. {@code path} is dot-separated, e.g.
   * {@code cage-building.cage-radius}. The value is stored as a string and converted to the
   * field's type on load. Configurate's YAML loader does not preserve comments, so saving
   * strips them from the file.
   */
  public void set(String path, String value) throws IOException {
    CommentedConfigurationNode root = loader.load();
    root.node((Object[]) path.split("\\.")).set(value);
    loader.save(root);
    loadConfig();
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