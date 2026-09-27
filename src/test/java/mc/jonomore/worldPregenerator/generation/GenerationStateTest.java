package mc.jonomore.worldPregenerator.generation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * State files are read back with Gson, which leaves a missing field null rather than failing. A
 * state.json from an older version can therefore load "successfully" with only some fields set,
 * so callers have to guard each one they use rather than inferring it from a sibling.
 */
class GenerationStateTest {

  private static GenerationState load(Path dir, String json) throws Exception {
    File f = dir.resolve("state.json").toFile();
    Files.writeString(f.toPath(), json);
    return GenerationState.load(f);
  }

  @Test
  void loadsAStateFileWithEveryFieldPresent(@TempDir Path dir) throws Exception {
    GenerationState s = load(dir, """
        {"currentWorldName":"world_7","currentWorldFolder":"/srv/world_7","currentStep":"CLEANUP"}""");

    assertEquals("world_7", s.getCurrentWorldName());
    assertNotNull(s.getCurrentWorldFolder());
    assertEquals("/srv/world_7", s.getCurrentWorldFolder().getPath());
  }

  @Test
  void aMissingWorldFolderLoadsAsNullRatherThanFailing(@TempDir Path dir) throws Exception {
    // No currentWorldFolder key at all, as an older format would write it
    GenerationState s = load(dir, """
        {"currentWorldName":"world_7","currentStep":"CLEANUP"}""");

    assertEquals("world_7", s.getCurrentWorldName());
    assertNull(s.getCurrentWorldFolder(), "a name without a folder has to be representable, and guarded for");
  }

  @Test
  void anExplicitlyNullWorldFolderLoadsAsNull(@TempDir Path dir) throws Exception {
    GenerationState s = load(dir, """
        {"currentWorldName":"world_7","currentWorldFolder":null,"currentStep":"CLEANUP"}""");

    assertNull(s.getCurrentWorldFolder());
  }

  @Test
  void anIdleStateHasNeitherNameNorFolder(@TempDir Path dir) throws Exception {
    GenerationState s = load(dir, """
        {"currentStep":"IDLE"}""");

    assertNull(s.getCurrentWorldName());
    assertNull(s.getCurrentWorldFolder());
  }

  @Test
  void waitingForSeedsDefaultsToFalseWhenAbsent(@TempDir Path dir) throws Exception {
    // The field postdates existing state files, so its absence must not break a resume
    GenerationState s = load(dir, """
        {"currentStep":"CREATE_WORLD","currentIndex":3}""");

    assertEquals(3, s.getCurrentIndex());
    assertEquals(false, s.isWaitingForSeeds());
  }
}
