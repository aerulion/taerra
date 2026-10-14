package net.aerulion.taerra.web.http;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SkyListTest {

  @Test
  @DisplayName("the day clock splits into day, sunset, night and sunrise")
  void phase() {
    assertEquals(Map.of("phase", "day", "progress", 0.0, "time", 0), SkyList.phase(0));
    assertEquals(Map.of("phase", "day", "progress", 0.5, "time", 6000), SkyList.phase(6000));
    assertEquals(Map.of("phase", "sunset", "progress", 0.75, "time", 12750), SkyList.phase(12750));
    assertEquals(Map.of("phase", "night", "progress", 0.0, "time", 13000), SkyList.phase(13000));
    assertEquals(Map.of("phase", "night", "progress", 0.5, "time", 18000), SkyList.phase(18000));
    assertEquals(Map.of("phase", "sunrise", "progress", 0.99, "time", 23990), SkyList.phase(23990));
  }
}
