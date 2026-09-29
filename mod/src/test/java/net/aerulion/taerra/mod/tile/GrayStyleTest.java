package net.aerulion.taerra.mod.tile;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class GrayStyleTest {

  @Test
  void mapsBrightnessOntoTheWebMapsStoneGrays() {
    assertEquals(0xFF1E1E1F, GrayStyle.apply(0xFF000000));
    assertEquals(0xFF8C8D90, GrayStyle.apply(0xFFFFFFFF));
  }

  @Test
  void keepsWaterSlateBlue() {
    assertEquals(0xFF455C80, GrayStyle.apply(0xFF0000FF));
  }

  @Test
  void leavesTransparentPixels() {
    assertEquals(0, GrayStyle.apply(0));
  }
}
