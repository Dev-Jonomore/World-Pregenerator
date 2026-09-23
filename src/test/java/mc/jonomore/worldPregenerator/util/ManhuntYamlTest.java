package mc.jonomore.worldPregenerator.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ManhuntYamlTest {

  @TempDir
  Path tempDir;

  static ManhuntYaml sample() {
    return new ManhuntYaml(
        12345L,
        List.of(
            new ManhuntYaml.SpawnPoint(100, 64, 200,
                new ManhuntYaml.NearestStructure("minecraft:village_plains", 300, 0, -80, "NORTHEAST")),
            new ManhuntYaml.SpawnPoint(-50, 70, 30, null)
        ),
        1200,
        new ManhuntYaml.Worlds("world_0", null, null)
    );
  }

  /** Writes a zip containing a world file and the given manhunt.yml text (omitted if null). */
  static Path zipWith(Path dir, String manhuntYml) throws IOException {
    Path zip = dir.resolve("world.zip");
    try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zip))) {
      zos.putNextEntry(new ZipEntry("world_0/level.dat"));
      zos.write(new byte[]{1, 2, 3});
      zos.closeEntry();
      if (manhuntYml != null) {
        zos.putNextEntry(new ZipEntry(ManhuntYaml.FILE_NAME));
        zos.write(manhuntYml.getBytes(StandardCharsets.UTF_8));
        zos.closeEntry();
      }
    }
    return zip;
  }

  @Test
  void roundTripsThroughFile() throws IOException {
    Path file = tempDir.resolve(ManhuntYaml.FILE_NAME);
    Files.writeString(file, sample().toYamlString());

    assertEquals(sample(), ManhuntYaml.fromFile(file.toFile()));
  }

  @Test
  void roundTripsThroughZip() throws IOException {
    Path zip = zipWith(tempDir, sample().toYamlString());

    assertEquals(sample(), ManhuntYaml.fromZip(zip));
  }

  @Test
  void writesExpectedKeys() throws IOException {
    String yaml = sample().toYamlString();

    assertTrue(yaml.contains("seed: 12345\n"), yaml);
    assertTrue(yaml.contains("spawn-points:\n"), yaml);
    assertTrue(yaml.contains("nearest-structure:\n"), yaml);
    assertTrue(yaml.contains("type: minecraft:village_plains\n"), yaml);
    assertTrue(yaml.contains("direction: NORTHEAST\n"), yaml);
    assertTrue(yaml.contains("pregen-radius: 1200\n"), yaml);
    assertTrue(yaml.contains("overworld: world_0\n"), yaml);
  }

  @Test
  void omitsNullSections() throws IOException {
    ManhuntYaml noStructures = new ManhuntYaml(
        1L,
        List.of(new ManhuntYaml.SpawnPoint(0, 64, 0, null)),
        1200,
        new ManhuntYaml.Worlds("world_0", null, null)
    );
    String yaml = noStructures.toYamlString();

    assertFalse(yaml.contains("nearest-structure"), yaml);
    assertFalse(yaml.contains("nether"), yaml);
    assertFalse(yaml.contains("end:"), yaml);
  }

  @Test
  void missingNearestStructureReadsAsNull() throws IOException {
    Path zip = zipWith(tempDir, """
        seed: 1
        spawn-points:
        - x: 1
          y: 2
          z: 3
        worlds:
          overworld: world_0
        """);

    ManhuntYaml yaml = ManhuntYaml.fromZip(zip);

    assertEquals(1, yaml.spawnPoints().size());
    assertNull(yaml.spawnPoints().getFirst().nearestStructure());
  }

  @Test
  void readsSaveStateWorlds() throws IOException {
    Path zip = zipWith(tempDir, """
        seed: 1
        spawn-points:
        - x: 1
          y: 2
          z: 3
        worlds:
          overworld: mh_overworld
          nether: mh_the_nether
          end: mh_the_end
        """);

    assertEquals(new ManhuntYaml.Worlds("mh_overworld", "mh_the_nether", "mh_the_end"), ManhuntYaml.fromZip(zip).worlds());
  }

  @Test
  void rejectsMissingOverworld() throws IOException {
    Path zip = zipWith(tempDir, """
        seed: 1
        spawn-points:
        - x: 1
          y: 2
          z: 3
        """);

    IOException e = assertThrows(IOException.class, () -> ManhuntYaml.fromZip(zip));
    assertTrue(e.getMessage().contains("worlds.overworld"), e.getMessage());
  }

  @Test
  void rejectsBlankOverworld() throws IOException {
    Path zip = zipWith(tempDir, "worlds:\n  overworld: ' '\n");

    assertThrows(IOException.class, () -> ManhuntYaml.fromZip(zip));
  }

  @Test
  void rejectsZipWithoutManhuntYml() throws IOException {
    Path zip = zipWith(tempDir, null);

    assertThrows(IOException.class, () -> ManhuntYaml.fromZip(zip));
  }

  @Test
  void rejectsMissingFile() {
    assertThrows(IOException.class, () -> ManhuntYaml.fromFile(tempDir.resolve("nope.yml").toFile()));
  }
}
