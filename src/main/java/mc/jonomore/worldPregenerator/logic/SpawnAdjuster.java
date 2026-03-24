package mc.jonomore.worldPregenerator.logic;

import mc.jonomore.worldPregenerator.GenerationConstants;
import mc.jonomore.worldPregenerator.util.CoordinateUtils;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;

import static mc.jonomore.worldPregenerator.util.CoordinateUtils.pack;
import static mc.jonomore.worldPregenerator.util.CoordinateUtils.unpackX;
import static mc.jonomore.worldPregenerator.util.CoordinateUtils.unpackZ;

/**
 * Uses BFS to find a safe spawn location in a Minecraft world.
 * Searches the x-z plane and scans vertically to find valid ground.
 */
public class SpawnAdjuster {

  private final int maxSearchRadius;
  private final int maxVerticalScan;
  private World world;
  private int hintY;

  /**
   * Creates a SpawnAdjuster with configuration values.
   *
   * @param maxSearchRadius Maximum horizontal search distance
   * @param maxVerticalScan Maximum vertical scan distance
   */
  public SpawnAdjuster(int maxSearchRadius, int maxVerticalScan) {
    this.maxSearchRadius = maxSearchRadius;
    this.maxVerticalScan = maxVerticalScan;
  }

  /**
   * Checks if a location is safe for spawning.
   *
   * @param location The location to check
   * @return true if safe, false otherwise
   */
  public static boolean isSafeSpawn(Location location) {
    World world = location.getWorld();
    int x = location.getBlockX();
    int y = location.getBlockY();
    int z = location.getBlockZ();

    // bounds + get blocks
    if (y - 1 >= world.getMinHeight() && y + 1 <= world.getMaxHeight()) {

      Block ground = world.getBlockAt(x, y - 1, z);
      Block feet = world.getBlockAt(x, y, z);
      Block head = world.getBlockAt(x, y + 1, z);
      Material groundMat = ground.getType();

      // checks: feet/head passable, ground not air/liquid/leaves, ground has collision
      return feet.isPassable()
          && head.isPassable()
          && !groundMat.isAir()
          && groundMat != Material.WATER
          && groundMat != Material.LAVA
          && !Tag.LEAVES.isTagged(groundMat)
          && !ground.getCollisionShape().getBoundingBoxes().isEmpty();
    } else return false;
  }

  /**
   * Finds a safe spawn location starting from the world's default spawn.
   *
   * @param world The world to search in
   * @return A safe spawn location, or null if none found
   */
  public Location findSafeSpawn(World world) {
    Location defaultSpawn = world.getSpawnLocation();
    int startX = defaultSpawn.getBlockX();
    int startZ = defaultSpawn.getBlockZ();
    this.hintY = defaultSpawn.getBlockY();
    this.world = world;

    return search(pack(startX, startZ));
  }

  /**
   * Bread-First Search algorithm.
   *
   * @param start n0 (x, z coordinate)
   * @return A safe spawn location or null if none found
   */
  private Location search(long start) {
    java.util.Queue<Long> points = new java.util.LinkedList<>();
    java.util.Set<Long> breadcrumbs = new java.util.HashSet<>(); // discovered points map to parents

    // Discover start
    breadcrumbs.add(start);
    if (isGoalPoint(start)) {
      return findGroundLevel(start);
    }
    points.add(start);

    while (!points.isEmpty()) {
      long point = points.poll();
      // Visit n (point)
      for (long newPoint : reachable(point)) {
        if (Math.abs(unpackX(newPoint) - unpackX(start)) <= maxSearchRadius &&
            Math.abs(unpackZ(newPoint) - unpackZ(start)) <= maxSearchRadius &&
            !breadcrumbs.contains(newPoint)
        ) {
          // Discover n' (newPoint)
          breadcrumbs.add(newPoint);
          if (isGoalPoint(newPoint)) {
            return findGroundLevel(newPoint);
          }
          points.add(newPoint);
        }
      }
    }

    return null; // FAIL
  }

  /**
   * Tests whether a point is a goal point.
   * A goal point is an (x, z) position with safe ground at the correct y level.
   *
   * @param p The point to test
   * @return true if this is a safe spawn location
   */
  private boolean isGoalPoint(long p) {
    Location groundLevel = findGroundLevel(p);
    return groundLevel != null && isSafeSpawn(groundLevel);
  }

  /**
   * Returns all valid points reachable from the given point.
   * In our 2D grid, these are the 4 cardinal directions: N, E, S, W.
   *
   * @param p The current point
   * @return Array of reachable neighbor points
   */
  private long[] reachable(long p) {
    int x = unpackX(p), z = unpackZ(p);
    return new long[]{
        pack(x, z - 1),  // North
        pack(x + 1, z),  // East
        pack(x, z + 1),  // South
        pack(x - 1, z)   // West
    };
  }

  /**
   * Scans vertically at (x, z) to find ground level.
   * Strategy: Start at hintY, go down if in ground, go up if in air.
   *
   * @param p the Point
   * @return Location at ground level, or null if not found
   */
  private Location findGroundLevel(long p) {
    int maxY = Math.min(hintY + maxVerticalScan, world.getMaxHeight());
    int minY = Math.max(hintY - maxVerticalScan, world.getMinHeight());

    int x = unpackX(p);
    int z = unpackZ(p);

    Material startMat = world.getBlockAt(x, hintY, z).getType();

    double offset = GenerationConstants.LOCATION_CENTER_OFFSET;

    // If in air or leaves, scan DOWN to find ground
    if (startMat.isAir() || Tag.LEAVES.isTagged(startMat)) {
      for (int y = hintY; y > minY; y--) {
        Material mat = world.getBlockAt(x, y, z).getType();

        if (!mat.isAir() && !Tag.LEAVES.isTagged(mat)) {
          return new Location(world, x + offset, y + 1, z + offset);
        }
      }
    }
    // If in solid ground (not leaves), scan UP to find surface
    else {
      for (int y = hintY; y < maxY; y++) {
        Material currentMat = world.getBlockAt(x, y, z).getType();
        Material aboveMat = world.getBlockAt(x, y + 1, z).getType();

        if (!currentMat.isAir() && !Tag.LEAVES.isTagged(currentMat)
            && (aboveMat.isAir() || Tag.LEAVES.isTagged(aboveMat))) {
          return new Location(world, x + offset, y + 1, z + offset);
        }
      }
    }

    return null;
  }
}