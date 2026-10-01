package net.aerulion.taerra.rendering.color;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ColormapsTest {

  private static final int TOLERANCE = 3;

  private static int pixel(final int[] pixels, final double temperature, final double downfall) {
    final int column = (int) ((1.0 - temperature) * 255.0);
    final int row = (int) ((1.0 - downfall * temperature) * 255.0);
    return pixels[row << 8 | column];
  }

  private static void assertNear(final int expectedRgb, final int actual, final String what) {
    assertEquals(0xFF, actual >>> 24, what + " is opaque");
    for (final int shift : new int[] {16, 8, 0}) {
      final int expected = expectedRgb >> shift & 0xFF;
      final int channel = actual >> shift & 0xFF;
      assertTrue(
          Math.abs(expected - channel) <= TOLERANCE,
          what
              + ": expected #"
              + Integer.toHexString(expectedRgb)
              + ", got #"
              + Integer.toHexString(actual & 0xFFFFFF));
    }
  }

  @Test
  @DisplayName("the shipped colormaps reproduce the vanilla tints of familiar biomes")
  void reproducesVanillaTints() throws IOException {
    final Map<String, Map<String, double[]>> colormaps = Colormaps.read();
    final int[] grass = Colormaps.pixels(colormaps.get("grass"));
    final int[] foliage = Colormaps.pixels(colormaps.get("foliage"));
    final int[] dryFoliage = Colormaps.pixels(colormaps.get("dry_foliage"));

    assertNear(0x91BD59, pixel(grass, 0.8, 0.4), "plains grass");
    assertNear(0x77AB2F, pixel(foliage, 0.8, 0.4), "plains foliage");
    assertNear(0xA37546, pixel(dryFoliage, 0.8, 0.4), "plains dry foliage");
    assertNear(0x79C05A, pixel(grass, 0.7, 0.8), "forest grass");
    assertNear(0x59AE30, pixel(foliage, 0.7, 0.8), "forest foliage");
    assertNear(0x59C93C, pixel(grass, 0.95, 0.9), "jungle grass");
    assertNear(0x30BB0B, pixel(foliage, 0.95, 0.9), "jungle foliage");
    assertNear(0x80B497, pixel(grass, 0.0, 0.5), "snowy grass");
    assertNear(0x60A17B, pixel(foliage, 0.0, 0.5), "snowy foliage");
    assertNear(0x8F7A5A, pixel(dryFoliage, 0.0, 0.5), "snowy dry foliage");
  }

  @Test
  @DisplayName("a table covers the whole 256 by 256 lookup and clamps to valid channels")
  void coversTheWholeLookup() {
    final double[] overflowing = {300.0, 0.0, 0.0, -400.0, 0.0, 0.0};
    final int[] pixels =
        Colormaps.pixels(Map.of("red", overflowing, "green", overflowing, "blue", overflowing));
    assertEquals(256 * 256, pixels.length);
    assertEquals(0xFFFFFFFF, pixels[0]);
    assertEquals(0xFF000000, pixels[255]);
  }
}
