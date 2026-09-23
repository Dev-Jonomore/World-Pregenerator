package mc.jonomore.worldPregenerator.logic;

import org.bukkit.Fluid;
import org.bukkit.HeightMap;
import org.bukkit.World;
import org.bukkit.util.BoundingBox;

import java.util.Collection;
import java.util.List;
import java.util.TreeSet;

/**
 * {@link BlockView} over a loaded Bukkit world. Reading an ungenerated chunk generates it, so this
 * must be used on the main thread.
 */
public final class BukkitBlockView implements BlockView {

  // EntityType.PLAYER's dimensions
  private static final double PLAYER_HALF_WIDTH = 0.3;
  private static final double PLAYER_HEIGHT = 1.8;
  private static final double EPSILON = 1.0E-7;

  private final World world;

  public BukkitBlockView(World world) {
    this.world = world;
  }

  @Override
  public int minY() {
    return world.getMinHeight();
  }

  @Override
  public int height(Heightmap heightmap, int x, int z) {
    HeightMap type = switch (heightmap) {
      case MOTION_BLOCKING -> HeightMap.MOTION_BLOCKING;
      case WORLD_SURFACE -> HeightMap.WORLD_SURFACE;
      case OCEAN_FLOOR -> HeightMap.OCEAN_FLOOR;
    };
    return world.getHighestBlockYAt(x, z, type);
  }

  @Override
  public boolean hasFluid(int x, int y, int z) {
    return world.getFluidData(x, y, z).getFluidType() != Fluid.EMPTY;
  }

  @Override
  public boolean hasFullTopFace(int x, int y, int z) {
    // Vanilla tests the collision shape (Block.isFaceFull). BlockData#isFaceSturdy uses the support
    // shape instead, which differs for e.g. leaves, so it can't be used here.
    return coversTopFace(world.getBlockAt(x, y, z).getCollisionShape().getBoundingBoxes());
  }

  @Override
  public boolean playerCollides(int x, int y, int z) {
    double cx = x + 0.5;
    double cz = z + 0.5;
    return world.hasCollisionsIn(new BoundingBox(
        cx - PLAYER_HALF_WIDTH, y, cz - PLAYER_HALF_WIDTH,
        cx + PLAYER_HALF_WIDTH, y + PLAYER_HEIGHT, cz + PLAYER_HALF_WIDTH
    ));
  }

  /**
   * Whether boxes in block-local coordinates (0 to 1) cover the whole top face: the parts of the
   * boxes that reach y = 1 must together span the full 1x1 square.
   */
  static boolean coversTopFace(Collection<BoundingBox> boxes) {
    List<BoundingBox> top = boxes.stream().filter(b -> b.getMaxY() >= 1 - EPSILON).toList();
    if (top.isEmpty()) return false;

    // Split the face into cells along every box edge and check that each cell is covered
    TreeSet<Double> xs = new TreeSet<>(List.of(0.0, 1.0));
    TreeSet<Double> zs = new TreeSet<>(List.of(0.0, 1.0));
    for (BoundingBox b : top) {
      xs.add(Math.clamp(b.getMinX(), 0, 1));
      xs.add(Math.clamp(b.getMaxX(), 0, 1));
      zs.add(Math.clamp(b.getMinZ(), 0, 1));
      zs.add(Math.clamp(b.getMaxZ(), 0, 1));
    }
    Double[] xa = xs.toArray(Double[]::new);
    Double[] za = zs.toArray(Double[]::new);
    for (int i = 0; i + 1 < xa.length; i++) {
      for (int j = 0; j + 1 < za.length; j++) {
        double mx = (xa[i] + xa[i + 1]) / 2;
        double mz = (za[j] + za[j + 1]) / 2;
        boolean covered = top.stream().anyMatch(b ->
            b.getMinX() <= mx && mx <= b.getMaxX() && b.getMinZ() <= mz && mz <= b.getMaxZ());
        if (!covered) return false;
      }
    }
    return true;
  }
}
