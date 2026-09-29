package net.aerulion.taerra.rendering.style;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SunShadowTest {

  private static final int GROUND = 64;
  private static final int DISTANCE = 32;

  private static @NotNull HeightField towerAtOrigin(final int towerHeight) {
    return (blockX, blockZ) -> blockX == 0 && blockZ == 0 ? GROUND + towerHeight : GROUND;
  }

  @Test
  @DisplayName("flat ground is never in its own shadow")
  void flatGroundIsLit() {
    final @NotNull HeightField flat = (blockX, blockZ) -> GROUND;
    for (int x = -8; x <= 8; x++) {
      for (int z = -8; z <= 8; z++) {
        assertFalse(
            SunShadow.isShadowed(flat, x, z, GROUND, DISTANCE),
            "flat ground shadowed at " + x + "," + z);
      }
    }
  }

  @Test
  @DisplayName("a tower shadows the ground away from the sun, and never toward it")
  void towerShadowsDownsun() {
    final @NotNull HeightField field = towerAtOrigin(10);

    assertTrue(
        SunShadow.isShadowed(field, -4, 3, GROUND, DISTANCE),
        "ground downsun of the tower should be shadowed");
    assertFalse(
        SunShadow.isShadowed(field, 4, -3, GROUND, DISTANCE),
        "ground toward the sun must stay lit");
  }

  @Test
  @DisplayName("a taller tower casts a longer shadow, since the rays climb at a fixed rate")
  void tallerTowersCastLonger() {

    final int farX = -19;
    final int farZ = 15;
    assertFalse(
        SunShadow.isShadowed(towerAtOrigin(4), farX, farZ, GROUND, DISTANCE),
        "a four block tower cannot reach 25 blocks away");
    assertTrue(
        SunShadow.isShadowed(towerAtOrigin(40), farX, farZ, GROUND, DISTANCE),
        "a forty block tower reaches well past it");
  }

  @Test
  @DisplayName("the top of a tower is lit by its own height rather than shadowed by itself")
  void towerTopIsLit() {
    assertFalse(SunShadow.isShadowed(towerAtOrigin(10), 0, 0, GROUND + 10, DISTANCE));
  }

  @Test
  @DisplayName("shadows stop at the configured distance rather than running forever")
  void distanceBoundsTheShadow() {
    final @NotNull HeightField field = towerAtOrigin(200);
    assertTrue(SunShadow.isShadowed(field, -4, 3, GROUND, DISTANCE));
    assertFalse(
        SunShadow.isShadowed(field, -4, 3, GROUND, 0),
        "a distance of zero disables shadows entirely");
  }

  @Test
  @DisplayName("a shadow reaches about twice the height of what casts it")
  void shadowsReachTwiceTheCasterHeight() {

    assertTrue(SunShadow.isShadowed(towerAtOrigin(3), -4, 3, GROUND, DISTANCE));
    assertFalse(SunShadow.isShadowed(towerAtOrigin(2), -4, 3, GROUND, DISTANCE));
  }

  @Test
  @DisplayName("a column with nothing in it casts no shadow")
  void emptyColumnsAreTransparentToTheSun() {
    final @NotNull HeightField empty = (blockX, blockZ) -> SunShadow.NO_COLUMN;
    assertFalse(SunShadow.isShadowed(empty, 0, 0, GROUND, DISTANCE));
  }
}
