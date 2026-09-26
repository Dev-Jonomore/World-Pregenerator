package mc.jonomore.worldPregenerator.logic;

/**
 * Read-only access to the parts of a world that {@link SpawnVerifier} needs, so its rules can be
 * tested without a server.
 */
public interface BlockView {

  /** The vanilla heightmaps used by respawn placement. */
  enum Heightmap {
    /** Highest block that blocks motion or holds fluid. */
    MOTION_BLOCKING,
    /** Highest non-air block. */
    WORLD_SURFACE,
    /** Highest block that blocks motion (fluids excluded). */
    OCEAN_FLOOR
  }

  int minY();

  /** Y of the highest block in the column counted by {@code heightmap}, or {@code minY() - 1} if none. */
  int height(Heightmap heightmap, int x, int z);

  /** Whether the block holds any fluid, including waterlogged blocks and plants like kelp. */
  boolean hasFluid(int x, int y, int z);

  /** Whether the block's collision shape covers its entire top face. */
  boolean hasFullTopFace(int x, int y, int z);

  /** Whether a player standing at the bottom center of the block would collide with anything. */
  boolean playerCollides(int x, int y, int z);
}
