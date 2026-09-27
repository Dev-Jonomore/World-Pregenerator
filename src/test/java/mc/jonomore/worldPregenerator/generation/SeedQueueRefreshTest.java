package mc.jonomore.worldPregenerator.generation;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@link GenerationTask#newSeeds} — what a re-read of the seeds file adds to a live queue. */
class SeedQueueRefreshTest {

  private static SeedEntry seed(long value) {
    return new SeedEntry(value, List.of(new SpawnPoint(0, 64, 0)));
  }

  private static List<Long> values(List<SeedEntry> entries) {
    return entries.stream().map(SeedEntry::seed).toList();
  }

  @Test
  void addsOnlySeedsNotAlreadyQueued() {
    List<SeedEntry> queued = List.of(seed(1), seed(2));
    List<SeedEntry> fresh = List.of(seed(1), seed(2), seed(3), seed(4));

    assertEquals(List.of(3L, 4L), values(GenerationTask.newSeeds(queued, Set.of(), fresh)));
  }

  @Test
  void skipsSeedsAlreadyExported() {
    List<SeedEntry> queued = List.of(seed(1));
    List<SeedEntry> fresh = List.of(seed(1), seed(2), seed(3));

    // 2 is in completed.txt from an earlier run, so re-listing it must not regenerate it
    assertEquals(List.of(3L), values(GenerationTask.newSeeds(queued, Set.of(2L), fresh)));
  }

  @Test
  void takesARepeatedSeedOnlyOnce() {
    List<SeedEntry> fresh = List.of(seed(7), seed(7), seed(8));

    assertEquals(List.of(7L, 8L), values(GenerationTask.newSeeds(List.of(), Set.of(), fresh)));
  }

  @Test
  void matchesBySeedValueNotBySpawnPoints() {
    List<SeedEntry> queued = List.of(new SeedEntry(5L, List.of(new SpawnPoint(0, 64, 0))));
    // Same seed, different points: one zip per seed, so it is not queued again
    List<SeedEntry> fresh = List.of(new SeedEntry(5L, List.of(new SpawnPoint(900, 70, -900))));

    assertTrue(GenerationTask.newSeeds(queued, Set.of(), fresh).isEmpty());
  }

  @Test
  void unchangedFileAddsNothing() {
    List<SeedEntry> queued = List.of(seed(1), seed(2), seed(3));

    assertTrue(GenerationTask.newSeeds(queued, Set.of(), queued).isEmpty());
  }

  @Test
  void keepsTheOrderTheFileListsThem() {
    List<SeedEntry> fresh = List.of(seed(30), seed(10), seed(20));

    assertEquals(List.of(30L, 10L, 20L), values(GenerationTask.newSeeds(List.of(), Set.of(), fresh)));
  }

  @Test
  void aShrunkFileNeverRemovesQueuedSeeds() {
    List<SeedEntry> queued = List.of(seed(1), seed(2), seed(3));

    // Truncating the file must not retract work: refresh only ever appends
    assertTrue(GenerationTask.newSeeds(queued, Set.of(), List.of(seed(1))).isEmpty());
  }
}
