package net.aerulion.taerra.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LayerScaleTest {

  @Test
  @DisplayName("a scale is the pixels one block takes or the blocks one pixel shows")
  void parses() {
    assertEquals(2, LayerScale.parse("4px"));
    assertEquals(0, LayerScale.parse("1px"));
    assertEquals(0, LayerScale.parse("1block"));
    assertEquals(-3, LayerScale.parse("8blocks"));
  }

  @Test
  @DisplayName("only powers of two inside the zoom range are scales")
  void refuses() {
    assertNull(LayerScale.parse("3px"));
    assertNull(LayerScale.parse("0px"));
    assertNull(LayerScale.parse("4"));
    assertNull(LayerScale.parse("512px"));
    assertNull(LayerScale.parse("8192blocks"));
  }

  @Test
  @DisplayName("every scale reads back as itself")
  void roundTrips() {
    for (int scale = LayerScale.MIN; scale <= LayerScale.MAX; scale++) {
      assertEquals(scale, LayerScale.parse(LayerScale.format(scale)));
    }
  }
}
