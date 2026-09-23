package mc.jonomore.worldPregenerator.logic;

import mc.jonomore.worldPregenerator.generation.SpawnPoint;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Checks that players respawning at a spawn point would be placed on dry land, by running
 * vanilla's respawn placement ({@code PlayerSpawnFinder}) over every column it could pick.
 *
 * <p>When a player respawns at the world spawn, vanilla tries every column within the
 * {@code respawn_radius} game rule of it, in a scrambled order, and places the player in the first
 * column that passes its test. If none pass, the player is left at the spawn itself, which for an
 * ocean spawn means floating on the water. A point is valid when at least
 * {@code minValidFraction} of those columns pass. An invalid point is moved to the nearest valid
 * spot within {@code snapRadius}.
 */
public final class SpawnVerifier {

  public enum Status { OK, ADJUSTED, FAILED }

  /**
   * @param point        the point to use: the original if OK, the new one if ADJUSTED, null if FAILED
   * @param validColumns landing columns vanilla would accept around the returned point (or the
   *                     original, if FAILED)
   * @param totalColumns columns vanilla picks from
   */
  public record Result(Status status, @Nullable SpawnPoint point, SpawnPoint original, int validColumns, int totalColumns) {}

  static final int NO_LANDING = Integer.MIN_VALUE;

  private final int respawnRadius;
  private final int snapRadius;
  private final int requiredColumns;
  /** Center offsets within {@code snapRadius}, nearest first. */
  private final List<int[]> snapOffsets = new ArrayList<>();

  public SpawnVerifier(int respawnRadius, double minValidFraction, int snapRadius) {
    this.respawnRadius = respawnRadius;
    this.snapRadius = snapRadius;
    int side = 2 * respawnRadius + 1;
    this.requiredColumns = Math.max(1, (int) Math.ceil(minValidFraction * side * side));

    long snapSq = (long) snapRadius * snapRadius;
    for (int dx = -snapRadius; dx <= snapRadius; dx++) {
      for (int dz = -snapRadius; dz <= snapRadius; dz++) {
        if ((long) dx * dx + (long) dz * dz <= snapSq) snapOffsets.add(new int[]{dx, dz});
      }
    }
    snapOffsets.sort(Comparator.comparingLong(o -> (long) o[0] * o[0] + (long) o[1] * o[1]));
  }

  public Result verify(BlockView view, SpawnPoint original) {
    int r = respawnRadius;
    int side = 2 * r + 1;
    int total = side * side;

    int valid = 0;
    for (int dx = -r; dx <= r; dx++) {
      for (int dz = -r; dz <= r; dz++) {
        if (landingY(view, original.x() + dx, original.z() + dz) != NO_LANDING) valid++;
      }
    }
    if (valid >= requiredColumns) {
      return new Result(Status.OK, original, original, valid, total);
    }

    // Test every column that any candidate center's area could include
    int span = snapRadius + r;
    int n = 2 * span + 1;
    int[][] landing = new int[n][n];
    int[][] prefix = new int[n + 1][n + 1];
    for (int i = 0; i < n; i++) {
      for (int j = 0; j < n; j++) {
        landing[i][j] = landingY(view, original.x() - span + i, original.z() - span + j);
        int pass = landing[i][j] != NO_LANDING ? 1 : 0;
        prefix[i + 1][j + 1] = pass + prefix[i][j + 1] + prefix[i + 1][j] - prefix[i][j];
      }
    }

    // Nearest valid center; ties go to the one whose landing heights are closest to the original y,
    // so a high / sea-level / in-between point keeps its role
    int bestDx = 0, bestDz = 0, bestValid = 0, bestMedian = 0;
    long bestDistSq = Long.MAX_VALUE;
    int bestYDiff = Integer.MAX_VALUE;
    for (int[] offset : snapOffsets) {
      int dx = offset[0];
      int dz = offset[1];
      long distSq = (long) dx * dx + (long) dz * dz;
      if (distSq > bestDistSq) break; // nearest-first, so nothing closer remains
      int ci = dx + span;
      int cj = dz + span;
      int count = windowSum(prefix, ci - r, cj - r, ci + r, cj + r);
      if (count < requiredColumns) continue;

      int median = medianLanding(landing, ci - r, cj - r, ci + r, cj + r);
      int yDiff = Math.abs(median - original.y());
      if (distSq < bestDistSq || yDiff < bestYDiff) {
        bestDistSq = distSq;
        bestYDiff = yDiff;
        bestDx = dx;
        bestDz = dz;
        bestValid = count;
        bestMedian = median;
      }
    }
    if (bestDistSq == Long.MAX_VALUE) {
      return new Result(Status.FAILED, null, original, valid, total);
    }

    int centerLanding = landing[bestDx + span][bestDz + span];
    int y = centerLanding != NO_LANDING ? centerLanding : bestMedian;
    SpawnPoint moved = new SpawnPoint(original.x() + bestDx, y, original.z() + bestDz);
    return new Result(Status.ADJUSTED, moved, original, bestValid, total);
  }

  /**
   * Vanilla's respawn test for one column ({@code PlayerSpawnFinder.getLevelRespawnPos} followed
   * by {@code noCollisionNoLiquid}).
   *
   * @return the y a respawning player would stand at in this column, or {@link #NO_LANDING}
   */
  static int landingY(BlockView view, int x, int z) {
    int top = view.height(BlockView.Heightmap.MOTION_BLOCKING, x, z);
    if (top < view.minY()) return NO_LANDING;

    // The top block is water (or other fluid) rather than ground
    int surface = view.height(BlockView.Heightmap.WORLD_SURFACE, x, z);
    if (surface <= top && surface > view.height(BlockView.Heightmap.OCEAN_FLOOR, x, z)) return NO_LANDING;

    for (int y = top + 1; y >= view.minY(); y--) {
      if (view.hasFluid(x, y, z)) return NO_LANDING;
      if (view.hasFullTopFace(x, y, z)) {
        int feet = y + 1;
        // The player's 1.8-block box spans the feet block and the one above it
        boolean clear = !view.playerCollides(x, feet, z)
            && !view.hasFluid(x, feet, z)
            && !view.hasFluid(x, feet + 1, z);
        return clear ? feet : NO_LANDING;
      }
    }
    return NO_LANDING;
  }

  /** Sum of the pass grid over the inclusive index rectangle [i0..i1] x [j0..j1]. */
  static int windowSum(int[][] prefix, int i0, int j0, int i1, int j1) {
    return prefix[i1 + 1][j1 + 1] - prefix[i0][j1 + 1] - prefix[i1 + 1][j0] + prefix[i0][j0];
  }

  private static int medianLanding(int[][] landing, int i0, int j0, int i1, int j1) {
    List<Integer> ys = new ArrayList<>();
    for (int i = i0; i <= i1; i++) {
      for (int j = j0; j <= j1; j++) {
        if (landing[i][j] != NO_LANDING) ys.add(landing[i][j]);
      }
    }
    Collections.sort(ys);
    return ys.get(ys.size() / 2);
  }
}
