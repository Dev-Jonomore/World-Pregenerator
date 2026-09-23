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
  /** What Chunky pregenerates: a square around the world spawn, or a rectangle around the seed's spawn points. */
  public enum GenerationAreaMode { WORLD_SPAWN, SPAWN_POINTS }

  private final WorldPregenerator plugin;
  private final YamlConfigurationLoader loader;

  // Cached config values
  private int generationRadius;
  private GenerationAreaMode generationArea;
  private int spawnPointsMargin;
  private String exportPath;
  private String serverId;
  private String seedsFile;
  private long worldDelayTicks;
  private int worldsPerBatch;
  private long pauseBetweenBatches;
  private List<String> structureFinderStructures;
  private boolean structureFinderWhitelist;
  private int structureFinderSearchRadius;
  private boolean spawnVerificationEnabled;
  private int respawnRadius;
  private double minValidFraction;
  private int snapRadius;

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

    try {
        generationArea = GenerationAreaMode.valueOf(cfg.generationArea.trim().toUpperCase().replace('-', '_'));
    } catch (IllegalArgumentException | NullPointerException e) {
        plugin.getLogger().warning("generation-area must be world-spawn or spawn-points: " + cfg.generationArea + ". Defaulting to world-spawn.");
        generationArea = GenerationAreaMode.WORLD_SPAWN;
    }
    spawnPointsMargin = cfg.spawnPointsMargin;
    if (spawnPointsMargin < 16 || spawnPointsMargin > 2000) {
        plugin.getLogger().warning("spawn-points-margin out of range [16,2000]: " + spawnPointsMargin + ". Defaulting to 128.");
        spawnPointsMargin = 128;
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
        plugin.getLogger().warning("structure-finder.search-radius out of range [16,10000]: " + structureFinderSearchRadius + ". Defaulting to 200.");
        structureFinderSearchRadius = 200;
    }

    spawnVerificationEnabled = cfg.spawnVerification.enabled;
    respawnRadius = cfg.spawnVerification.respawnRadius;
    // Vanilla tries at most 1024 columns, which covers the whole square only up to radius 15
    if (respawnRadius < 0 || respawnRadius > 15) {
        plugin.getLogger().warning("spawn-verification.respawn-radius out of range [0,15]: " + respawnRadius + ". Defaulting to 10.");
        respawnRadius = 10;
    }
    minValidFraction = cfg.spawnVerification.minValidFraction;
    if (!(minValidFraction > 0 && minValidFraction <= 1)) {
        plugin.getLogger().warning("spawn-verification.min-valid-fraction out of range (0,1]: " + minValidFraction + ". Defaulting to 0.25.");
        minValidFraction = 0.25;
    }
    snapRadius = cfg.spawnVerification.snapRadius;
    if (snapRadius < 0 || snapRadius > 256) {
        plugin.getLogger().warning("spawn-verification.snap-radius out of range [0,256]: " + snapRadius + ". Defaulting to 80.");
        snapRadius = 80;
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

  public GenerationAreaMode getGenerationArea() { return generationArea; }

  public int getSpawnPointsMargin() { return spawnPointsMargin; }

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

  public boolean isSpawnVerificationEnabled() { return spawnVerificationEnabled; }

  public int getRespawnRadius() { return respawnRadius; }

  public double getMinValidFraction() { return minValidFraction; }

  public int getSnapRadius() { return snapRadius; }

  @Override
  public String toString() {
    return "generation-radius: " + generationRadius + "\n" +
        "generation-area: " + generationArea.name().toLowerCase().replace('_', '-') + "\n" +
        "spawn-points-margin: " + spawnPointsMargin + "\n" +
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
        "spawn-verification:\n" +
        "  enabled: " + spawnVerificationEnabled + "\n" +
        "  respawn-radius: " + respawnRadius + "\n" +
        "  min-valid-fraction: " + minValidFraction + "\n" +
        "  snap-radius: " + snapRadius + "\n" +
        "world-delay-ticks: " + worldDelayTicks;
  }
}