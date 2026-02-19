package mc.jonomore.worldPregenerator;

import org.bukkit.Material;

public class CageBuilder {

  private final Material cageMaterial;
  private final int cageRadius;
  private final int cageHeight;

  public CageBuilder(Material cageMaterial, int cageRadius, int cageHeight) {
    this.cageMaterial = cageMaterial;
    this.cageRadius = cageRadius;
    this.cageHeight = cageHeight;
  }

  /**
   * Builds the cage using an iterative approach.
   * Memory complexity: O(1)
   */
  public void buildCage(org.bukkit.World world) {
    org.bukkit.Location spawnLoc = world.getSpawnLocation();
    int centerX = spawnLoc.getBlockX();
    int centerY = spawnLoc.getBlockY();
    int centerZ = spawnLoc.getBlockZ();

    int minX = centerX - cageRadius;
    int maxX = centerX + cageRadius;
    int minZ = centerZ - cageRadius;
    int maxZ = centerZ + cageRadius;

    int floorY = centerY - 1;
    int ceilingY = centerY + cageHeight;

    // Loop through the bounds efficiently
    for (int x = minX; x <= maxX; ++x) {
      for (int y = floorY; y <= ceilingY; y += GenerationConstants.CAGE_Y_SKIP) {
        boolean isFloorOrCeiling = y == floorY || y == ceilingY;
        for (int z = minZ; z <= maxZ; ++z) {

          boolean isWall = (x == minX || x == maxX || z == minZ || z == maxZ);

          if (isFloorOrCeiling || isWall) {
            org.bukkit.block.Block block = world.getBlockAt(x, y, z);
            boolean passable = block.getCollisionShape().getBoundingBoxes().isEmpty();
            if (block.getType() != cageMaterial && passable) {
              block.setType(cageMaterial, false);
            }
          }
        }
      }
    }
  }

}
