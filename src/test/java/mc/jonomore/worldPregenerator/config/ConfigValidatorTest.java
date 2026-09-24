package mc.jonomore.worldPregenerator.config;

import org.junit.jupiter.api.Test;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ConfigValidatorTest {

  /**
   * Measured on Paper 26.3: a test-one of seed 12345 at generation-radius 1200 produced a
   * 214.24 MB export zip (297 MB unzipped).
   */
  private static final double MEASURED_ZIP_MB_AT_1200 = 214.24;
  private static final double LEEWAY_MB = 50;

  @Test
  void worldSizeEstimateMatchesMeasuredExport() {
    double estimateMB = ConfigValidator.estimateWorldSizeGB(1200) * 1024;

    assertEquals(MEASURED_ZIP_MB_AT_1200, estimateMB, LEEWAY_MB,
        "estimate for radius 1200 was " + estimateMB + " MB");
  }

  @Test
  void worldSizeEstimateScalesWithArea() {
    double ratio = ConfigValidator.estimateWorldSizeGB(2400) / ConfigValidator.estimateWorldSizeGB(1200);

    assertEquals(4.0, ratio, 1e-9);
  }

  @Test
  void countsOnlyValidSeedLines() {
    ConfigValidator.SeedCount count = ConfigValidator.countSeeds(Stream.of(
        "12345/0,70,0/300,70,-200",
        "",
        "   ",
        "-42/1,2,3",
        "not a valid line",
        "- 12345 [100, ~ 200]"
    ));

    assertEquals(new ConfigValidator.SeedCount(2, 2), count);
  }

  @Test
  void emptyFileHasNoSeeds() {
    ConfigValidator.SeedCount count = ConfigValidator.countSeeds(Stream.empty());

    assertEquals(new ConfigValidator.SeedCount(0, 0), count);
  }
}
