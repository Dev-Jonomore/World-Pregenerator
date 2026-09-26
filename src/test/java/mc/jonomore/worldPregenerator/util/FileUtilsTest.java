package mc.jonomore.worldPregenerator.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileUtilsTest {

  @TempDir
  Path tempDir;

  @Test
  void verifyZipAcceptsValidExport() throws IOException {
    Path zip = ManhuntYamlTest.zipWith(tempDir, ManhuntYamlTest.sample().toYamlString());

    assertDoesNotThrow(() -> FileUtils.verifyZip(zip));
  }

  @Test
  void verifyZipRejectsMissingFile() {
    assertThrows(IOException.class, () -> FileUtils.verifyZip(tempDir.resolve("missing.zip")));
  }

  @Test
  void verifyZipRejectsNonZip() throws IOException {
    Path notZip = Files.writeString(tempDir.resolve("fake.zip"), "not a zip");

    assertThrows(IOException.class, () -> FileUtils.verifyZip(notZip));
  }

  @Test
  void verifyZipRejectsEmptyZip() throws IOException {
    Path zip = tempDir.resolve("empty.zip");
    new ZipOutputStream(Files.newOutputStream(zip)).close();

    assertThrows(IOException.class, () -> FileUtils.verifyZip(zip));
  }

  @Test
  void verifyZipRejectsMissingManhuntYml() throws IOException {
    Path zip = ManhuntYamlTest.zipWith(tempDir, null);

    IOException e = assertThrows(IOException.class, () -> FileUtils.verifyZip(zip));
    assertTrue(e.getMessage().contains("manhunt.yml"), e.getMessage());
  }

  @Test
  void verifyZipRejectsNoSpawnPoints() throws IOException {
    ManhuntYaml noSpawns = new ManhuntYaml(1L, List.of(), 1200, new ManhuntYaml.Worlds("world_0", null, null));
    Path zip = ManhuntYamlTest.zipWith(tempDir, noSpawns.toYamlString());

    IOException e = assertThrows(IOException.class, () -> FileUtils.verifyZip(zip));
    assertTrue(e.getMessage().contains("no spawn points"), e.getMessage());
  }

  @Test
  void deleteDirectoryRemovesNestedContents() throws IOException {
    Path root = tempDir.resolve("world");
    Files.createDirectories(root.resolve("region/deep"));
    Files.writeString(root.resolve("level.dat"), "x");
    Files.writeString(root.resolve("region/deep/r.0.0.mca"), "x");

    FileUtils.deleteDirectory(root);

    assertFalse(Files.exists(root));
  }

  @Test
  void deleteDirectoryIgnoresMissingPath() {
    assertDoesNotThrow(() -> FileUtils.deleteDirectory(tempDir.resolve("missing")));
  }

  @Test
  void deleteDirectoryRejectsFile() throws IOException {
    Path file = Files.writeString(tempDir.resolve("file.txt"), "x");

    assertThrows(IOException.class, () -> FileUtils.deleteDirectory(file));
  }

  @Test
  void deleteDirectoryWithRetryReportsSuccess() throws IOException {
    Path root = Files.createDirectories(tempDir.resolve("world"));
    boolean[] result = {false};

    FileUtils.deleteDirectoryWithRetry(root, java.util.logging.Logger.getAnonymousLogger(), ok -> result[0] = ok);

    assertTrue(result[0]);
    assertFalse(Files.exists(root));
  }
}
