package mc.jonomore.worldPregenerator.config;

import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.objectmapping.meta.Setting;

import java.util.ArrayList;
import java.util.List;

/**
 * Schema for {@code config.yml}. Field values are the defaults used when a key is missing, and
 * should match the bundled {@code config.yml}. Range checks live in {@link ConfigManager}.
 */
@ConfigSerializable
public class PluginConfig {

  @Setting("generation-radius")
  public int generationRadius = 1200;

  @Setting("server-id")
  public String serverId = "server1";

  @Setting("export-path")
  public String exportPath = "/mnt/worlds";

  @Setting("seeds-file")
  public String seedsFile = "/mnt/seeds/seeds.txt";

  @Setting("spawn-adjustment")
  public SpawnAdjustment spawnAdjustment = new SpawnAdjustment();

  @Setting("cage-building")
  public CageBuilding cageBuilding = new CageBuilding();

  @Setting("batch-settings")
  public BatchSettings batchSettings = new BatchSettings();

  @Setting("structure-finder")
  public StructureFinder structureFinder = new StructureFinder();

  @Setting("world-delay-ticks")
  public long worldDelayTicks = 40L;

  @ConfigSerializable
  public static class SpawnAdjustment {
    @Setting("maxSearchRadius")
    public int maxSearchRadius = 100;

    @Setting("maxVerticalScan")
    public int maxVerticalScan = 128;
  }

  @ConfigSerializable
  public static class CageBuilding {
    @Setting("cage-material")
    public String cageMaterial = "PURPLE_STAINED_GLASS";

    @Setting("cage-radius")
    public int cageRadius = 4;

    @Setting("cage-height")
    public int cageHeight = 3;
  }

  @ConfigSerializable
  public static class BatchSettings {
    @Setting("worlds-per-batch")
    public int worldsPerBatch = 10;

    @Setting("pause-between-batches")
    public long pauseBetweenBatches = 60L;
  }

  @ConfigSerializable
  public static class StructureFinder {
    @Setting("whitelist")
    public boolean whitelist = true;

    @Setting("search-radius")
    public int searchRadius = 1200;

    @Setting("structures")
    public List<String> structures = new ArrayList<>(List.of(
        "village_plains",
        "village_desert",
        "village_savanna",
        "village_snowy",
        "village_taiga",
        "desert_pyramid",
        "jungle_pyramid",
        "pillager_outpost",
        "shipwreck",
        "shipwreck_beached",
        "ruined_portal",
        "ruined_portal_desert",
        "ruined_portal_jungle",
        "ruined_portal_mountain",
        "ruined_portal_ocean",
        "ruined_portal_swamp"
    ));
  }
}
