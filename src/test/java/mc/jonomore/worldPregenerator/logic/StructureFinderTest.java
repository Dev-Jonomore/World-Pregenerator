package mc.jonomore.worldPregenerator.logic;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StructureFinderTest {

  // Minecraft axes: +X is east, +Z is south
  @ParameterizedTest(name = "({0}, {1}) -> {2}")
  @CsvSource({
      "100,    0, EAST",
      "100,  100, SOUTHEAST",
      "  0,  100, SOUTH",
      "-100, 100, SOUTHWEST",
      "-100,   0, WEST",
      "-100,-100, NORTHWEST",
      "  0, -100, NORTH",
      "100, -100, NORTHEAST",
  })
  void compassDirections(double dx, double dz, String expected) {
    assertEquals(expected, StructureFinder.direction(0, 0, dx, dz));
  }

  @ParameterizedTest(name = "({0}, {1}) -> {2}")
  @CsvSource({
      // Just inside each side of the 22.5° boundary between EAST and SOUTHEAST
      "100, 41, EAST",      // ~22.3°
      "100, 42, SOUTHEAST", // ~22.8°
      // Just inside each side of the boundary between NORTH and NORTHEAST (292.5°)
      "41, -100, NORTH",     // ~292.3°
      "42, -100, NORTHEAST", // ~292.8°
  })
  void sectorBoundaries(double dx, double dz, String expected) {
    assertEquals(expected, StructureFinder.direction(0, 0, dx, dz));
  }

  @ParameterizedTest(name = "from ({0}, {1}) to ({2}, {3}) -> {4}")
  @CsvSource({
      "500, 500, 500, 100, NORTH",
      "-20, 300, -800, 300, WEST",
      "1000, -1000, 1500, -500, SOUTHEAST",
  })
  void isRelativeToOrigin(double fromX, double fromZ, double toX, double toZ, String expected) {
    assertEquals(expected, StructureFinder.direction(fromX, fromZ, toX, toZ));
  }
}
