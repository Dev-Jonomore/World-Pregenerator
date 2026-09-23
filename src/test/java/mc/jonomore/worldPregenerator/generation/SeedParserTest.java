package mc.jonomore.worldPregenerator.generation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SeedParserTest {

  @Test
  void parsesSeedWithThreeSpawnPoints() {
    SeedEntry entry = SeedParser.parse("12345/100,64,200/-50,70,30/0,80,-120");

    assertEquals(12345L, entry.seed());
    assertEquals(List.of(
        new SpawnPoint(100, 64, 200),
        new SpawnPoint(-50, 70, 30),
        new SpawnPoint(0, 80, -120)
    ), entry.spawnPoints());
  }

  @Test
  void parsesSingleSpawnPoint() {
    SeedEntry entry = SeedParser.parse("42/1,2,3");

    assertEquals(42L, entry.seed());
    assertEquals(List.of(new SpawnPoint(1, 2, 3)), entry.spawnPoints());
  }

  @Test
  void parsesAnyNumberOfSpawnPoints() {
    SeedEntry entry = SeedParser.parse("7/1,1,1/2,2,2/3,3,3/4,4,4/5,5,5");

    assertEquals(5, entry.spawnPoints().size());
    assertEquals(new SpawnPoint(5, 5, 5), entry.spawnPoints().getLast());
  }

  @Test
  void parsesNegativeSeedAndCoordinates() {
    SeedEntry entry = SeedParser.parse("-8675309/-1,-64,-2");

    assertEquals(-8675309L, entry.seed());
    assertEquals(List.of(new SpawnPoint(-1, -64, -2)), entry.spawnPoints());
  }

  @Test
  void parsesFullRangeLongSeeds() {
    assertEquals(Long.MAX_VALUE, SeedParser.parse(Long.MAX_VALUE + "/0,0,0").seed());
    assertEquals(Long.MIN_VALUE, SeedParser.parse(Long.MIN_VALUE + "/0,0,0").seed());
  }

  @Test
  void toleratesWhitespace() {
    SeedEntry entry = SeedParser.parse("  12345 / 100, 64, 200 /-50 ,70 , 30\t");

    assertEquals(12345L, entry.seed());
    assertEquals(List.of(new SpawnPoint(100, 64, 200), new SpawnPoint(-50, 70, 30)), entry.spawnPoints());
  }

  @Test
  void spawnPointListIsImmutable() {
    SeedEntry entry = SeedParser.parse("1/1,2,3");

    assertThrows(UnsupportedOperationException.class, () -> entry.spawnPoints().add(new SpawnPoint(0, 0, 0)));
  }

  @ParameterizedTest
  @ValueSource(strings = {
      "",                          // blank
      "12345",                     // no spawn points
      "12345/",                    // trailing slash, no point
      "12345/1,2",                 // missing coordinate
      "12345/1,2,3,4",             // extra coordinate
      "12345/1,2,3/",              // trailing slash
      "12345/1,2,3/4,5",           // second point incomplete
      "12345/1.5,2,3",             // decimal coordinate
      "abc/1,2,3",                 // non-numeric seed
      "12345/1;2;3",               // wrong separator
      "- 12345 [100, ~ 200]",      // old format
  })
  void rejectsMalformedLines(String line) {
    assertThrows(IllegalArgumentException.class, () -> SeedParser.parse(line));
  }

  @Test
  void rejectsSeedOutsideLongRange() {
    IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> SeedParser.parse("9223372036854775808/1,2,3"));
    assertEquals(NumberFormatException.class, e.getCause().getClass());
  }

  @Test
  void rejectsCoordinateOutsideIntRange() {
    assertThrows(IllegalArgumentException.class, () -> SeedParser.parse("1/2147483648,0,0"));
  }
}
