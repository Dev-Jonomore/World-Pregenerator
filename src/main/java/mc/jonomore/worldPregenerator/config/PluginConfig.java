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

  @Setting("generation-area")
  public String generationArea = "world-spawn";

  @Setting("spawn-points-margin")
  public int spawnPointsMargin = 128;

  @Setting("export-path")
  public String exportPath = "/mnt/worlds";

  @Setting("seeds-file")
  public String seedsFile = "/mnt/seeds/seeds.txt";

  @Setting("batch-settings")
  public BatchSettings batchSettings = new BatchSettings();

  @Setting("structure-finder")
  public StructureFinder structureFinder = new StructureFinder();

  @Setting("spawn-verification")
  public SpawnVerification spawnVerification = new SpawnVerification();

  @Setting("world-delay-ticks")
  public long worldDelayTicks = 40L;

  @ConfigSerializable
  public static class BatchSettings {
    @Setting("worlds-per-batch")
    public int worldsPerBatch = 10;

    @Setting("pause-between-batches")
    public long pauseBetweenBatches = 60L;
  }

  @ConfigSerializable
  public static class SpawnVerification {
    @Setting("enabled")
    public boolean enabled = true;

    @Setting("respawn-radius")
    public int respawnRadius = 10;

    @Setting("min-valid-fraction")
    public double minValidFraction = 0.5;

    @Setting("snap-radius")
    public int snapRadius = 80;
  }

  @ConfigSerializable
  public static class StructureFinder {
    @Setting("whitelist")
    public boolean whitelist = true;

    @Setting("search-radius")
    public int searchRadius = 200;

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
