package net.aerulion.taerra.command;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RenderReportTest {

  @Test
  @DisplayName("durations pick a unit that stays readable at every scale")
  void durationsScale() {
    assertEquals("0ms", RenderReport.duration(0));
    assertEquals("750ms", RenderReport.duration(750));
    assertEquals("1.5s", RenderReport.duration(1500));
    assertEquals("1m 30s", RenderReport.duration(90_000));
    assertEquals("2h 5m", RenderReport.duration(7_500_000));
  }

  @Test
  @DisplayName("an unknown duration says so rather than reporting a negative time")
  void unknownDuration() {
    assertEquals("unknown", RenderReport.duration(-1));
  }

  @Test
  @DisplayName("a progress bar fills in proportion and clamps out-of-range fractions")
  void barFillsAndClamps() {
    assertEquals(0, RenderReport.filled(0.0, 10));
    assertEquals(5, RenderReport.filled(0.5, 10));
    assertEquals(10, RenderReport.filled(1.0, 10));
    assertEquals(10, RenderReport.filled(5.0, 10));
    assertEquals(0, RenderReport.filled(-5.0, 10));
  }

  @Test
  @DisplayName("percentages and rates are formatted independently of the server's locale")
  void formattingIsLocaleIndependent() {
    assertEquals("12.5%", RenderReport.percent(0.125));
    assertEquals("3.05/s", RenderReport.rate(3.05));
    assertEquals("34.0ms", RenderReport.millis(34.0));
  }
}
