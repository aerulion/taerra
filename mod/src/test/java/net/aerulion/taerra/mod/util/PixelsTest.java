package net.aerulion.taerra.mod.util;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PixelsTest {

  @Test
  void monochromeMatchesTheServer() {
    final int[] pixels = new int[Pixels.ICON * Pixels.ICON];
    assertFalse(Pixels.monochrome(pixels), "empty");
    pixels[0] = 0xFFFFFFFF;
    pixels[1] = 0x80FFFFFF;
    assertTrue(Pixels.monochrome(pixels), "white");
    pixels[2] = 0xFF000000;
    assertFalse(Pixels.monochrome(pixels), "white and black");
    pixels[0] = 0xFF000000;
    pixels[1] = 0x80000000;
    assertTrue(Pixels.monochrome(pixels), "black");
    pixels[3] = 0xFF010101;
    assertFalse(Pixels.monochrome(pixels), "nearly black");
  }
}
