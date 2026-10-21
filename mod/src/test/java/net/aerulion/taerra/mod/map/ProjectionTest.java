package net.aerulion.taerra.mod.map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import net.aerulion.taerra.mod.web.model.MapConfig;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

class ProjectionTest {

  private static final MapConfig CONFIG =
      new MapConfig(64, 4, new MapConfig.Isometric(256, 4, 8), null, null);

  @Test
  void topDownMatchesTheWebMapScale() {
    assertArrayEquals(new double[] {16, -32}, Projection.topDown(CONFIG).toMap(64, 70, -128));
  }

  @Test
  void isometricMatchesTheWebMapProjection() {
    assertArrayEquals(
        new double[] {(10 - 4) * 4 / 16.0, ((10 + 4) * 2 - 64 * 4) / 16.0},
        Projection.isometric(CONFIG, 0).toMap(10, 65, 4),
        1e-9);
  }

  @Test
  void toBlockInvertsToMapInEveryRotation() {
    for (int rotation = 0; rotation < 4; rotation++) {
      final @NotNull Projection projection = Projection.isometric(CONFIG, rotation);
      final double @NotNull [] map = projection.toMap(-37.5, 80, 212.25);
      assertArrayEquals(
          new double[] {-37.5, 212.25},
          projection.toBlock(map[0], map[1], 80),
          1e-9,
          "r" + rotation);
    }
  }

  @Test
  void northIsUpOnTheTopDownMap() {
    assertEquals(0, Projection.topDown(CONFIG).bearing(180), 1e-9);
    assertEquals(Math.PI / 2, Projection.topDown(CONFIG).bearing(-90), 1e-9);
  }

  @Test
  void isometricRotationsLookTowardsTheirViewNames() {
    final float @NotNull [] yaws = {135, 225, 315, 45};
    for (int rotation = 0; rotation < 4; rotation++) {
      assertEquals(
          0, Projection.isometric(CONFIG, rotation).bearing(yaws[rotation]), 1e-9, "r" + rotation);
    }
  }
}
