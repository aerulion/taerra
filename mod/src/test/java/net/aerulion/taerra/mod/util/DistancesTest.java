package net.aerulion.taerra.mod.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class DistancesTest {

  @Test
  void metersUntilAKilometerThenOneDecimalUntilTen() {
    assertEquals("0m", Distances.format(.4));
    assertEquals("999m", Distances.format(999.4));
    assertEquals("1.0km", Distances.format(999.6));
    assertEquals("2.5km", Distances.format(2468));
    assertEquals("12km", Distances.format(12_345));
  }
}
