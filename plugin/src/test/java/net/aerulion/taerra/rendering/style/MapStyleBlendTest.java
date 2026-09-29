package net.aerulion.taerra.rendering.style;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MapStyleBlendTest {

  private static final int TOP = 0xFF4080C0;
  private static final int SIDE = 0xFF204060;
  private static final float WEIGHT = 2.0f;

  private static int brightness(final int argb) {
    return ((argb >> 16) & 0xFF) + ((argb >> 8) & 0xFF) + (argb & 0xFF);
  }

  @Test
  @DisplayName("level ground keeps its own color untouched")
  void levelGroundIsUntouched() {
    assertEquals(TOP, MapStyle.blendFaces(TOP, SIDE, 0, 0, WEIGHT));
    assertEquals(
        TOP, MapStyle.blendFaces(TOP, SIDE, -3, -2, WEIGHT), "rising ground is never brightened");
  }

  @Test
  @DisplayName("a drop mixes in the faces it exposes, and a deeper one mixes in more")
  void deeperDropsShowMoreSide() {
    final int shallow = MapStyle.blendFaces(TOP, SIDE, 1, 0, WEIGHT);
    final int deep = MapStyle.blendFaces(TOP, SIDE, 6, 0, WEIGHT);

    assertTrue(brightness(shallow) < brightness(TOP));
    assertTrue(brightness(deep) < brightness(shallow));
  }

  @Test
  @DisplayName("the drop that counts is capped, so a sheer cliff does not swamp everything")
  void dropsAreCapped() {
    assertEquals(
        MapStyle.blendFaces(TOP, SIDE, MapStyle.MAX_EDGE_DROP, 0, WEIGHT),
        MapStyle.blendFaces(TOP, SIDE, 5000, 0, WEIGHT));
  }

  @Test
  @DisplayName("the two directions are shaded apart, as the two visible faces are")
  void directionsDifferer() {
    assertTrue(
        brightness(MapStyle.blendFaces(TOP, SIDE, 0, 3, WEIGHT))
            < brightness(MapStyle.blendFaces(TOP, SIDE, 3, 0, WEIGHT)));
  }

  @Test
  @DisplayName("a heavier weight leaves more of the surface's own color")
  void weightControlsTheMix() {
    assertTrue(
        brightness(MapStyle.blendFaces(TOP, SIDE, 2, 0, 1.0f))
            < brightness(MapStyle.blendFaces(TOP, SIDE, 2, 0, 4.0f)));
  }

  @Test
  @DisplayName("alpha survives the mix")
  void alphaSurvives() {
    assertEquals(0xFF000000, MapStyle.blendFaces(TOP, SIDE, 4, 4, WEIGHT) & 0xFF000000);
  }
}
