package mc.jonomore.worldPregenerator.logic;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;

/**
 * A {@link BlockView} made of columns of simple blocks, with heightmaps computed the way vanilla
 * defines them. The player's box is treated as staying inside its own column.
 */
class FakeBlockView implements BlockView {

  static final int HEIGHT = 128;

  enum Block {
    AIR(false, false, false),
    STONE(true, false, true),
    LEAVES(true, false, true), // full collision, so vanilla lands players on treetops
    BOTTOM_SLAB(true, false, false),
    WATERLOGGED_SLAB(true, true, false),
    WATER(false, true, false),
    LAVA(false, true, false),
    KELP(false, true, false); // water without a Waterlogged property

    final boolean blocksMotion;
    final boolean fluid;
    final boolean fullTop;

    Block(boolean blocksMotion, boolean fluid, boolean fullTop) {
      this.blocksMotion = blocksMotion;
      this.fluid = fluid;
      this.fullTop = fullTop;
    }

    boolean collides() {
      return blocksMotion;
    }
  }

  private final BiFunction<Integer, Integer, Block[]> generator;
  private final Map<Long, Block[]> columns = new HashMap<>();

  FakeBlockView(BiFunction<Integer, Integer, Block[]> generator) {
    this.generator = generator;
  }

  /** Stone from y 0 up to and including {@code top}. */
  static Block[] land(int top) {
    Block[] column = new Block[HEIGHT];
    Arrays.fill(column, Block.AIR);
    Arrays.fill(column, 0, top + 1, Block.STONE);
    return column;
  }

  /** Stone up to {@code floor}, then water up to {@code seaLevel}. */
  static Block[] ocean(int floor, int seaLevel) {
    Block[] column = land(floor);
    Arrays.fill(column, floor + 1, seaLevel + 1, Block.WATER);
    return column;
  }

  static Block[] with(Block[] column, int y, Block block) {
    Block[] copy = column.clone();
    copy[y] = block;
    return copy;
  }

  private Block[] column(int x, int z) {
    return columns.computeIfAbsent(((long) x << 32) ^ (z & 0xffffffffL), k -> generator.apply(x, z));
  }

  private Block block(int x, int y, int z) {
    return y < 0 || y >= HEIGHT ? Block.AIR : column(x, z)[y];
  }

  @Override
  public int minY() {
    return 0;
  }

  @Override
  public int height(Heightmap heightmap, int x, int z) {
    Block[] column = column(x, z);
    for (int y = HEIGHT - 1; y >= 0; y--) {
      Block b = column[y];
      boolean counts = switch (heightmap) {
        case MOTION_BLOCKING -> b.blocksMotion || b.fluid;
        case WORLD_SURFACE -> b != Block.AIR;
        case OCEAN_FLOOR -> b.blocksMotion;
      };
      if (counts) return y;
    }
    return -1;
  }

  @Override
  public boolean hasFluid(int x, int y, int z) {
    return block(x, y, z).fluid;
  }

  @Override
  public boolean hasFullTopFace(int x, int y, int z) {
    return block(x, y, z).fullTop;
  }

  @Override
  public boolean playerCollides(int x, int y, int z) {
    return block(x, y, z).collides() || block(x, y + 1, z).collides();
  }
}
