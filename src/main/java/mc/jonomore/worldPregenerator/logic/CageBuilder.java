package mc.jonomore.worldPregenerator.logic;

import org.bukkit.Material;

public class CageBuilder {

  private final org.bukkit.Material cageMaterial;
  private final int cageRadius;
  private final int cageHeight;

  public CageBuilder(org.bukkit.Material cageMaterial, int cageRadius, int cageHeight) {
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
      boolean isWallX = x == minX || x == maxX;
      for (int z = minZ; z <= maxZ; ++z) {
        boolean isWall = isWallX || (z == minZ || z == maxZ);
        for (int y = floorY; y <= ceilingY; y += mc.jonomore.worldPregenerator.GenerationConstants.CAGE_Y_SKIP) {
        boolean isFloorOrCeiling = y == floorY || y == ceilingY;
          if (isFloorOrCeiling || isWall) {
            org.bukkit.block.Block block = world.getBlockAt(x, y, z);
            if ((block.isPassable() && block.getType() != cageMaterial) || block.getType() == Material.BAMBOO) {
              block.setType(cageMaterial, false);
            }
          }
        }
      }
    }
  }

}
