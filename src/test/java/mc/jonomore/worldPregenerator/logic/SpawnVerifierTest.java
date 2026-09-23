package mc.jonomore.worldPregenerator.logic;

import mc.jonomore.worldPregenerator.generation.SpawnPoint;
import mc.jonomore.worldPregenerator.logic.FakeBlockView.Block;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Random;

import static mc.jonomore.worldPregenerator.logic.FakeBlockView.land;
import static mc.jonomore.worldPregenerator.logic.FakeBlockView.ocean;
import static mc.jonomore.worldPregenerator.logic.FakeBlockView.with;
import static mc.jonomore.worldPregenerator.logic.SpawnVerifier.NO_LANDING;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SpawnVerifierTest {

  // Vanilla defaults: respawn_radius 10 gives a 21x21 square; 25% of 441 is 111 columns
  private final SpawnVerifier verifier = new SpawnVerifier(10, 0.25, 80);

  private static final int GROUND = 64;
  private static final int SEA = 62;

  // --- Vanilla column test ---------------------------------------------------------------------

  @Test
  void landsOnTopOfSolidGround() {
    FakeBlockView view = new FakeBlockView((x, z) -> land(GROUND));

    assertEquals(GROUND + 1, SpawnVerifier.landingY(view, 0, 0));
  }

  @Test
  void rejectsWaterSurface() {
    FakeBlockView view = new FakeBlockView((x, z) -> ocean(40, SEA));

    assertEquals(NO_LANDING, SpawnVerifier.landingY(view, 0, 0));
  }

  @Test
  void rejectsLavaPool() {
    FakeBlockView view = new FakeBlockView((x, z) -> with(land(GROUND), GROUND + 1, Block.LAVA));

    assertEquals(NO_LANDING, SpawnVerifier.landingY(view, 0, 0));
  }

  @Test
  void rejectsWaterloggedFloor() {
    FakeBlockView view = new FakeBlockView((x, z) -> with(land(GROUND), GROUND + 1, Block.WATERLOGGED_SLAB));

    assertEquals(NO_LANDING, SpawnVerifier.landingY(view, 0, 0));
  }

  @Test
  void rejectsKelpWithoutWaterloggedProperty() {
    // Water whose top block is kelp still reads as fluid all the way up
    FakeBlockView view = new FakeBlockView((x, z) -> with(ocean(40, SEA), SEA, Block.KELP));

    assertEquals(NO_LANDING, SpawnVerifier.landingY(view, 0, 0));
  }

  @Test
  void rejectsLandingSpotBlockedByPartialBlock() {
    // A bottom slab has no full top face, so the player lands on the stone under it, inside the slab
    FakeBlockView view = new FakeBlockView((x, z) -> with(land(GROUND), GROUND + 1, Block.BOTTOM_SLAB));

    assertEquals(NO_LANDING, SpawnVerifier.landingY(view, 0, 0));
  }

  @Test
  void landsOnTopOfOverhang() {
    // Vanilla scans down from the top of the column, so the gap under the overhang is never reached
    Block[] column = land(GROUND);
    column[GROUND + 2] = Block.STONE;
    FakeBlockView view = new FakeBlockView((x, z) -> column);

    assertEquals(GROUND + 3, SpawnVerifier.landingY(view, 0, 0));
  }

  @Test
  void landsOnTreetopsLikeVanilla() {
    FakeBlockView view = new FakeBlockView((x, z) -> with(land(GROUND), GROUND + 6, Block.LEAVES));

    assertEquals(GROUND + 7, SpawnVerifier.landingY(view, 0, 0));
  }

  @Test
  void rejectsEmptyColumn() {
    FakeBlockView view = new FakeBlockView((x, z) -> emptyColumn());

    assertEquals(NO_LANDING, SpawnVerifier.landingY(view, 0, 0));
  }

  private static Block[] emptyColumn() {
    Block[] column = new Block[FakeBlockView.HEIGHT];
    Arrays.fill(column, Block.AIR);
    return column;
  }

  // --- Verifying and snapping ------------------------------------------------------------------

  @Test
  void landPointIsOk() {
    FakeBlockView view = new FakeBlockView((x, z) -> land(GROUND));
    SpawnPoint point = new SpawnPoint(100, 70, -40);

    SpawnVerifier.Result result = verifier.verify(view, point);

    assertEquals(SpawnVerifier.Status.OK, result.status());
    assertEquals(point, result.point());
    assertEquals(441, result.validColumns());
    assertEquals(441, result.totalColumns());
  }

  @Test
  void oceanPointSnapsToNearestValidSpot() {
    // Land starts at x = 30. A center needs 6 land columns (126 >= 111) in its 21-wide square,
    // so the nearest valid center is x = 25
    FakeBlockView view = new FakeBlockView((x, z) -> x >= 30 ? land(GROUND) : ocean(40, SEA));

    SpawnVerifier.Result result = verifier.verify(view, new SpawnPoint(0, SEA, 0));

    assertEquals(SpawnVerifier.Status.ADJUSTED, result.status());
    // x = 25 is itself water, so y is the median landing height of its square
    assertEquals(new SpawnPoint(25, GROUND + 1, 0), result.point());
    assertEquals(126, result.validColumns());
    assertEquals(new SpawnPoint(0, SEA, 0), result.original());
  }

  @Test
  void coastlinePointWithTooLittleLandIsMovedInland() {
    // Only 3 land columns (63 < 111) in the square around x = 22
    FakeBlockView view = new FakeBlockView((x, z) -> x >= 30 ? land(GROUND) : ocean(40, SEA));

    SpawnVerifier.Result result = verifier.verify(view, new SpawnPoint(22, SEA, 0));

    assertEquals(SpawnVerifier.Status.ADJUSTED, result.status());
    assertEquals(25, result.point().x());
    assertEquals(0, result.point().z());
  }

  @Test
  void pointWithEnoughLandIsNotMoved() {
    // 8 land columns (168 >= 111) around x = 27
    FakeBlockView view = new FakeBlockView((x, z) -> x >= 30 ? land(GROUND) : ocean(40, SEA));

    assertEquals(SpawnVerifier.Status.OK, verifier.verify(view, new SpawnPoint(27, SEA, 0)).status());
  }

  @Test
  void movedPointLandsOnGroundWhenCenterIsLand() {
    FakeBlockView view = new FakeBlockView((x, z) -> x >= 30 ? land(GROUND) : ocean(40, SEA));
    SpawnVerifier strict = new SpawnVerifier(10, 1.0, 80);

    SpawnVerifier.Result result = strict.verify(view, new SpawnPoint(0, SEA, 0));

    // Every column must be land, so the whole square starts at x = 30: center x = 40
    assertEquals(new SpawnPoint(40, GROUND + 1, 0), result.point());
  }

  @Test
  void failsWhenNoValidSpotWithinSnapRadius() {
    FakeBlockView view = new FakeBlockView((x, z) -> x >= 200 ? land(GROUND) : ocean(40, SEA));

    SpawnVerifier.Result result = verifier.verify(view, new SpawnPoint(0, SEA, 0));

    assertEquals(SpawnVerifier.Status.FAILED, result.status());
    assertNull(result.point());
    assertEquals(0, result.validColumns());
  }

  @Test
  void snapRadiusZeroNeverMoves() {
    FakeBlockView view = new FakeBlockView((x, z) -> x >= 30 ? land(GROUND) : ocean(40, SEA));

    SpawnVerifier.Result result = new SpawnVerifier(10, 0.25, 0).verify(view, new SpawnPoint(0, SEA, 0));

    assertEquals(SpawnVerifier.Status.FAILED, result.status());
  }

  @Test
  void equidistantTieGoesToHeightClosestToOriginal() {
    // Low land east of x = 30, high land west of x = -30, both 25 blocks from the nearest valid center
    int high = 100;
    FakeBlockView view = new FakeBlockView((x, z) ->
        x >= 30 ? land(GROUND) : x <= -30 ? land(high) : ocean(40, SEA));

    SpawnVerifier.Result wantsHigh = verifier.verify(view, new SpawnPoint(0, high, 0));
    SpawnVerifier.Result wantsLow = verifier.verify(view, new SpawnPoint(0, SEA, 0));

    assertEquals(new SpawnPoint(-25, high + 1, 0), wantsHigh.point());
    assertEquals(new SpawnPoint(25, GROUND + 1, 0), wantsLow.point());
  }

  @Test
  void snapsAlongZToo() {
    FakeBlockView view = new FakeBlockView((x, z) -> z <= -40 ? land(GROUND) : ocean(40, SEA));

    SpawnVerifier.Result result = verifier.verify(view, new SpawnPoint(0, SEA, 0));

    assertEquals(new SpawnPoint(0, GROUND + 1, -35), result.point());
  }

  @Test
  void windowSumMatchesBruteForce() {
    Random random = new Random(42);
    int n = 40;
    int[][] pass = new int[n][n];
    int[][] prefix = new int[n + 1][n + 1];
    for (int i = 0; i < n; i++) {
      for (int j = 0; j < n; j++) {
        pass[i][j] = random.nextBoolean() ? 1 : 0;
        prefix[i + 1][j + 1] = pass[i][j] + prefix[i][j + 1] + prefix[i + 1][j] - prefix[i][j];
      }
    }

    for (int trial = 0; trial < 200; trial++) {
      int i0 = random.nextInt(n), i1 = i0 + random.nextInt(n - i0);
      int j0 = random.nextInt(n), j1 = j0 + random.nextInt(n - j0);
      int expected = 0;
      for (int i = i0; i <= i1; i++) {
        for (int j = j0; j <= j1; j++) expected += pass[i][j];
      }
      assertEquals(expected, SpawnVerifier.windowSum(prefix, i0, j0, i1, j1));
    }
  }
}
