package net.aerulion.taerra.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FormatsTest {

  @Test
  @DisplayName("durations pick a unit that stays readable at every scale")
  void durationsScale() {
    assertEquals("0ms", Formats.duration(0));
    assertEquals("750ms", Formats.duration(750));
    assertEquals("1.5s", Formats.duration(1500));
    assertEquals("1m 30s", Formats.duration(90_000));
    assertEquals("2h 5m", Formats.duration(7_500_000));
  }

  @Test
  @DisplayName("an unknown duration says so rather than reporting a negative time")
  void unknownDuration() {
    assertEquals("unknown", Formats.duration(-1));
  }

  @Test
  @DisplayName("a progress bar fills in proportion and clamps out-of-range fractions")
  void barFillsAndClamps() {
    assertEquals(0, Formats.filled(0.0, 10));
    assertEquals(5, Formats.filled(0.5, 10));
    assertEquals(10, Formats.filled(1.0, 10));
    assertEquals(10, Formats.filled(5.0, 10));
    assertEquals(0, Formats.filled(-5.0, 10));
  }

  @Test
  @DisplayName("percentages and rates are formatted independently of the server's locale")
  void formattingIsLocaleIndependent() {
    assertEquals("12.5%", Formats.percent(0.125));
    assertEquals("3.05/s", Formats.rate(3.05));
    assertEquals("34.0ms", Formats.millis(34.0));
  }
}
