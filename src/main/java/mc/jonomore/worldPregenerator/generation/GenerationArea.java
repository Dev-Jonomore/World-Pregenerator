package mc.jonomore.worldPregenerator.generation;

import java.util.List;

/**
 * The rectangle Chunky pregenerates, in blocks.
 *
 * @param shape Chunky shape name: {@code square} or {@code rectangle}
 */
public record GenerationArea(String shape, double centerX, double centerZ, double radiusX, double radiusZ) {

  /** A square of {@code radius} around the world spawn. */
  public static GenerationArea aroundWorldSpawn(double spawnX, double spawnZ, int radius) {
    return new GenerationArea("square", spawnX, spawnZ, radius, radius);
  }

  /** The smallest rectangle containing every spawn point, grown by {@code margin} on each side. */
  public static GenerationArea aroundSpawnPoints(List<SpawnPoint> points, int margin) {
    if (points.isEmpty()) throw new IllegalArgumentException("No spawn points");
    int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
    for (SpawnPoint p : points) {
      minX = Math.min(minX, p.x());
      maxX = Math.max(maxX, p.x());
      minZ = Math.min(minZ, p.z());
      maxZ = Math.max(maxZ, p.z());
    }
    return new GenerationArea("rectangle",
        (minX + maxX) / 2.0, (minZ + maxZ) / 2.0,
        (maxX - minX) / 2.0 + margin, (maxZ - minZ) / 2.0 + margin);
  }
}
