package mc.jonomore.worldPregenerator.util;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.util.Vector;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
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
public record ManhuntYaml(
    long seed,
    Vector spawn,
    Vector coordinateHint,
    String directionHint,
    int pregenRadius,
    // worlds section — overworld always present, nether/end may be null (pregenerator zips)
    String overworldFolderName,
    String netherFolderName,   // null if not present
    String endFolderName       // null if not present
) {

  public static final String FILE_NAME = "manhunt.yml";

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
      // Write to a temp file so YamlConfiguration can read it
      // (YamlConfiguration doesn't read from streams directly)
      Path temp = Files.createTempFile("manhunt", ".yml");
      try {
        try (InputStream in = zf.getInputStream(entry)) {
          Files.copy(in, temp, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        return fromFile(temp.toFile());
      } finally {
        Files.deleteIfExists(temp);
      }
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

    YamlConfiguration config = YamlConfiguration.loadConfiguration(file);

    long seed = config.getLong("seed");

    Vector spawn = new Vector(
        config.getInt("spawn.x"),
        config.getInt("spawn.y"),
        config.getInt("spawn.z")
    );

    Vector coordinateHint = new Vector(
        config.getInt("coordinate-hint.x"),
        0,
        config.getInt("coordinate-hint.z")
    );

    String directionHint = config.getString("direction-hint", "NORTH");

    int pregenRadius = config.getInt("pregen-radius", 0);

    // worlds section — overworld required, nether/end optional
    String overworldFolderName = config.getString("worlds.overworld");
    if (overworldFolderName == null || overworldFolderName.isBlank()) {
      throw new IOException("manhunt.yml is missing required field: worlds.overworld");
    }

    String netherFolderName = config.getString("worlds.nether", null);
    String endFolderName = config.getString("worlds.end", null);

    return new ManhuntYaml(
        seed, spawn, coordinateHint, directionHint, pregenRadius,
        overworldFolderName, netherFolderName, endFolderName
    );
  }

}