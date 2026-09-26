package mc.jonomore.worldPregenerator.logic;

import org.bukkit.util.BoundingBox;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BukkitBlockViewTest {

  private static BoundingBox box(double x1, double y1, double z1, double x2, double y2, double z2) {
    return new BoundingBox(x1, y1, z1, x2, y2, z2);
  }

  @Test
  void fullBlockCoversTopFace() {
    assertTrue(BukkitBlockView.coversTopFace(List.of(box(0, 0, 0, 1, 1, 1))));
  }

  @Test
  void topSlabCoversTopFace() {
    assertTrue(BukkitBlockView.coversTopFace(List.of(box(0, 0.5, 0, 1, 1, 1))));
  }

  @Test
  void bottomSlabDoesNotReachTop() {
    assertFalse(BukkitBlockView.coversTopFace(List.of(box(0, 0, 0, 1, 0.5, 1))));
  }

  @Test
  void farmlandIsSlightlyShort() {
    assertFalse(BukkitBlockView.coversTopFace(List.of(box(0, 0, 0, 1, 15 / 16.0, 1))));
  }

  @Test
  void upsideDownStairsCoverTopFace() {
    // Full top half plus a bottom quarter
    assertTrue(BukkitBlockView.coversTopFace(List.of(
        box(0, 0.5, 0, 1, 1, 1),
        box(0, 0, 0.5, 1, 0.5, 1)
    )));
  }

  @Test
  void twoHalvesTogetherCoverTopFace() {
    assertTrue(BukkitBlockView.coversTopFace(List.of(
        box(0, 0, 0, 0.5, 1, 1),
        box(0.5, 0, 0, 1, 1, 1)
    )));
  }

  @Test
  void uprightStairsLeaveHalfTheTopUncovered() {
    assertFalse(BukkitBlockView.coversTopFace(List.of(
        box(0, 0, 0, 1, 0.5, 1),
        box(0, 0.5, 0.5, 1, 1, 1)
    )));
  }

  @Test
  void fencePostIsTooNarrow() {
    assertFalse(BukkitBlockView.coversTopFace(List.of(box(0.375, 0, 0.375, 0.625, 1.5, 0.625))));
  }

  @Test
  void emptyShapeHasNoTopFace() {
    assertFalse(BukkitBlockView.coversTopFace(List.of()));
  }
}
