package net.aerulion.taerra.rendering.style;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MapStyleTest {

  @Test
  @DisplayName("a column with open ground around it is not darkened at all")
  void openGroundIsUntouched() {
    assertEquals(1.0f, MapStyle.occlusion(0));
    assertEquals(1.0f, MapStyle.occlusion(1));
  }

  @Test
  @DisplayName("occlusion only ever darkens, and never below a floor")
  void occlusionOnlyDarkens() {
    float previous = 1.0f;
    for (int taller = 0; taller <= 8; taller++) {
      final float factor = MapStyle.occlusion(taller);
      assertTrue(factor <= previous, "occlusion must not brighten at " + taller);
      assertTrue(factor >= 0.5f, "occlusion must not black out at " + taller);
      previous = factor;
    }
  }

  @Test
  @DisplayName("occlusion is quantized, so it reads as contact shading rather than a gradient")
  void occlusionIsQuantized() {
    long distinct = 0;
    float previous = Float.NaN;
    for (int taller = 0; taller <= 8; taller++) {
      final float factor = MapStyle.occlusion(taller);
      if (factor != previous) {
        distinct++;
        previous = factor;
      }
    }
    assertTrue(
        distinct <= 5,
        "a smooth falloff would put a gradient back into a flat map, got " + distinct + " steps");
  }

  @Test
  @DisplayName("counts beyond the eight surrounding columns are clamped rather than overflowing")
  void occlusionClamps() {
    assertEquals(MapStyle.occlusion(8), MapStyle.occlusion(99));
  }

  @Test
  @DisplayName("shading leaves alpha alone and never overflows a channel")
  void shadingPreservesAlpha() {
    assertEquals(0xFF000000, MapStyle.shade(0xFF000000, 2.0f) & 0xFF000000);
    assertEquals(0xFFFFFFFF, MapStyle.shade(0xFFFFFFFF, 4.0f));
    assertEquals(0x80000000, MapStyle.shade(0x80FFFFFF, 0.0f));
  }

  @Test
  @DisplayName("shallow water is left alone, so a river keeps its plain surface color")
  void shallowWaterIsUntinted() {
    for (int depth = -5; depth < MapStyle.DEPTH_BAND_BLOCKS; depth++) {
      assertEquals(
          0xFF3050A0,
          MapStyle.depthTinted(0xFF3050A0, depth),
          "depth " + depth + " should be untinted");
    }
  }

  @Test
  @DisplayName("depth reads in bands rather than as a gradient, and stops once it is simply deep")
  void depthIsQuantizedAndClamped() {
    int distinct = 0;
    int previous = MapStyle.depthTinted(0xFF3050A0, 0);
    for (int depth = 0;
        depth <= MapStyle.DEPTH_BAND_BLOCKS * MapStyle.MAX_DEPTH_BANDS * 2;
        depth++) {
      final int tinted = MapStyle.depthTinted(0xFF3050A0, depth);
      if (tinted != previous) {
        distinct++;
        previous = tinted;
      }
    }
    assertEquals(MapStyle.MAX_DEPTH_BANDS, distinct, "one step per band and no more");
    assertEquals(
        MapStyle.depthTinted(0xFF3050A0, MapStyle.DEPTH_BAND_BLOCKS * MapStyle.MAX_DEPTH_BANDS),
        MapStyle.depthTinted(0xFF3050A0, 9999),
        "past the last band the depth stops mattering");
  }

  @Test
  @DisplayName("deep water loses its warm channels first, so it goes blue rather than gray")
  void depthShiftsTowardBlue() {
    final int surface = 0xFF808080;
    final int deep =
        MapStyle.depthTinted(surface, MapStyle.DEPTH_BAND_BLOCKS * MapStyle.MAX_DEPTH_BANDS);
    final int red = (deep >> 16) & 0xFF;
    final int green = (deep >> 8) & 0xFF;
    final int blue = deep & 0xFF;

    assertTrue(
        red < green && green < blue, "expected a blue shift, got " + Integer.toHexString(deep));
    assertTrue(blue < 0x80, "deep water should still be darker than its surface");
    assertEquals(0xFF000000, deep & 0xFF000000, "alpha is left alone");
  }

  @Test
  @DisplayName("blending keeps the first color's alpha and hits both endpoints exactly")
  void blendingHitsEndpoints() {
    assertEquals(0xFF102030, MapStyle.blend(0xFF102030, 0xFFAABBCC, 0.0f));
    assertEquals(0xFFAABBCC, MapStyle.blend(0xFF102030, 0xFFAABBCC, 1.0f));
    assertEquals(0x80000000, MapStyle.blend(0x80000000, 0xFF000000, 0.5f) & 0xFF000000);
  }

  @Test
  @DisplayName("only water one block deep gets the shallow glint")
  void shallowGlintOnlyAtOneBlock() {
    final int water = 0xFF3F76E4;
    assertTrue(MapStyle.shallow(water, 1) != water);
    assertEquals(water, MapStyle.shallow(water, 2));
    assertEquals(water, MapStyle.shallow(water, 0));
  }
}
