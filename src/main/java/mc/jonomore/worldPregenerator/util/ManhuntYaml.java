package mc.jonomore.worldPregenerator.util;

import org.jspecify.annotations.Nullable;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.objectmapping.meta.Setting;
import org.spongepowered.configurate.yaml.NodeStyle;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Schema and parser for {@code manhunt.yml} embedded in world zip archives.
 *
 * <p>Two zip types produce this file:
 *
 * <p><strong>Pregenerator zip</strong> — contains one world folder + {@code manhunt.yml}:
 * <pre>
 *   world_42/region/...
 *   manhunt.yml
 * </pre>
 * {@code worlds.overworld} will be {@code "world_42"} (the pregenerator's internal name).
 * {@code worlds.nether} and {@code worlds.end} are absent — WorldManager creates them fresh.
 *
 * <p><strong>Save state zip</strong> — contains all three world folders + {@code manhunt.yml}:
 * <pre>
 *   mh_overworld/region/...
 *   mh_the_nether/region/...
 *   mh_the_end/region/...
 *   manhunt.yml
 * </pre>
 * All three {@code worlds.*} keys are present. Names always match WorldManager's constants.
 *
 * <p>The import pipeline reads {@code worlds.overworld} to determine which folder to rename
 * to {@code mh_overworld}. If nether/end are absent, WorldManager creates them fresh with
 * the same seed.
 */
@ConfigSerializable
public record ManhuntYaml(
    @Setting("seed") long seed,
    @Setting("spawn-points") List<SpawnPoint> spawnPoints,
    @Setting("pregen-radius") int pregenRadius,
    @Setting("worlds") Worlds worlds
) {

  public static final String FILE_NAME = "manhunt.yml";

  /**
   * A spawn point, the nearest structure to it if one was found within the search radius, and how
   * spawn verification changed it (null if it was left as-is).
   */
  @ConfigSerializable
  public record SpawnPoint(
      @Setting("x") int x,
      @Setting("y") int y,
      @Setting("z") int z,
      @Setting("nearest-structure") @Nullable NearestStructure nearestStructure,
      @Setting("verification") @Nullable Verification verification
  ) {
    public SpawnPoint {
      // Configurate maps a missing section to an empty object rather than null
      if (nearestStructure != null && nearestStructure.type() == null) nearestStructure = null;
      if (verification != null && verification.status() == null) verification = null;
    }

    public SpawnPoint(int x, int y, int z, @Nullable NearestStructure nearestStructure) {
      this(x, y, z, nearestStructure, null);
    }
  }

  /**
   * @param status   what spawn verification did, e.g. {@code ADJUSTED}
   * @param original the point as given in the seeds file
   */
  @ConfigSerializable
  public record Verification(
      @Setting("status") String status,
      @Setting("original") Position original
  ) {}

  @ConfigSerializable
  public record Position(
      @Setting("x") int x,
      @Setting("y") int y,
      @Setting("z") int z
  ) {}

  /**
   * @param type      namespaced structure key, e.g. {@code minecraft:village_plains}
   * @param x         block x of the structure's center
   * @param z         block z of the structure's center
   * @param direction compass direction from the spawn point to the structure, e.g. {@code NORTHEAST}
   */
  @ConfigSerializable
  public record NearestStructure(
      @Setting("type") String type,
      @Setting("x") int x,
      @Setting("z") int z,
      @Setting("direction") String direction
  ) {}

  /**
   * World folder names. Overworld is always present; nether/end are null in pregenerator zips.
   */
  @ConfigSerializable
  public record Worlds(
      @Setting("overworld") String overworld,
      @Setting("nether") @Nullable String nether,
      @Setting("end") @Nullable String end
  ) {}

  // ---------------------------------------------------------------------------
  // Parsing
  // ---------------------------------------------------------------------------

  /**
   * Reads and parses {@code manhunt.yml} from a zip archive.
   *
   * @param zipPath path to the zip file
   * @return parsed {@link ManhuntYaml}
   * @throws IOException if the file is missing, unreadable, or malformed
   */
  public static ManhuntYaml fromZip(Path zipPath) throws IOException {
    try (ZipFile zf = new ZipFile(zipPath.toFile())) {
      ZipEntry entry = zf.getEntry(FILE_NAME);
      if (entry == null) {
        throw new IOException("manhunt.yml not found in zip: " + zipPath);
      }
      return load(baseLoader().source(() -> new BufferedReader(
          new InputStreamReader(zf.getInputStream(entry), StandardCharsets.UTF_8)
      )).build());
    }
  }

  /**
   * Reads and parses {@code manhunt.yml} from a file on disk.
   * Used by WorldManager when reading from an already-extracted folder.
   *
   * @param file the {@code manhunt.yml} file
   * @return parsed {@link ManhuntYaml}
   * @throws IOException if the file is missing or malformed
   */
  public static ManhuntYaml fromFile(File file) throws IOException {
    if (!file.exists()) {
      throw new IOException("manhunt.yml not found at: " + file.getAbsolutePath());
    }
    return load(baseLoader().path(file.toPath()).build());
  }

  private static ManhuntYaml load(YamlConfigurationLoader loader) throws IOException {
    ManhuntYaml yaml = loader.load().get(ManhuntYaml.class);
    if (yaml == null || yaml.worlds() == null || yaml.worlds().overworld() == null || yaml.worlds().overworld().isBlank()) {
      throw new IOException("manhunt.yml is missing required field: worlds.overworld");
    }
    return yaml;
  }

  // ---------------------------------------------------------------------------
  // Serialization
  // ---------------------------------------------------------------------------

  /**
   * Serializes this object to the YAML text written into world zips.
   */
  public String toYamlString() throws ConfigurateException {
    StringWriter out = new StringWriter();
    YamlConfigurationLoader loader = baseLoader().sink(() -> new BufferedWriter(out)).build();
    CommentedConfigurationNode root = loader.createNode();
    root.set(ManhuntYaml.class, this);
    loader.save(root);
    return out.toString();
  }

  private static YamlConfigurationLoader.Builder baseLoader() {
    return YamlConfigurationLoader.builder()
        .nodeStyle(NodeStyle.BLOCK)
        .indent(2);
  }
}
