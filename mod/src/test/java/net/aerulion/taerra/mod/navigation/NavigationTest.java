package net.aerulion.taerra.mod.navigation;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class NavigationTest {

  private static Navigation.Target target(final String world) {
    return new Navigation.Target("Home", world, 800, 70, -160, 0xFFFFFFFF, null);
  }

  @Test
  void aTargetInTheOverworldIsAnEighthAsFarInTheNether() {
    assertArrayEquals(
        new double[] {100, Double.NaN, -20},
        target("minecraft:overworld").in("minecraft:the_nether"));
    assertArrayEquals(
        new double[] {6400, Double.NaN, -1280},
        target("minecraft:the_nether").in("minecraft:overworld"));
  }

  @Test
  void aTargetKeepsItsHeightInItsOwnWorld() {
    assertArrayEquals(
        new double[] {800, 70, -160}, target("minecraft:overworld").in("minecraft:overworld"));
  }

  @Test
  void theEndAndCustomWorldsHaveNoWayThere() {
    assertNull(target("minecraft:overworld").in("minecraft:the_end"));
    assertNull(target("survival:mining").in("minecraft:overworld"));
  }
}
