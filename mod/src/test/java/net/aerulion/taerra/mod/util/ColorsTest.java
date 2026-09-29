package net.aerulion.taerra.mod.util;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

class ColorsTest {

  @Test
  void hsvMatchesTheWebEditor() {
    assertEquals(0xFF0000, Colors.fromHsv(0, 1, 1));
    assertEquals(0x00FF00, Colors.fromHsv(120, 1, 1));
    assertEquals(0x808080, Colors.fromHsv(200, 0, 128 / 255.0));
    assertArrayEquals(new double[] {240, 1, 1}, Colors.toHsv(0x0000FF), 1e-9);
  }

  @Test
  void hexRoundTripsThroughHsv() {
    for (final int rgb : new int[] {0x3ab3da, 0xb02e26, 0x1d1d21, 0xf9fffe, 0x000000}) {
      final double @NotNull [] hsv = Colors.toHsv(rgb);
      assertEquals(rgb, Colors.fromHsv(hsv[0], hsv[1], hsv[2]), Colors.hex(rgb));
    }
  }
}
