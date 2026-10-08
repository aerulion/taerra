package net.aerulion.taerra.waypoint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class WaypointsTest {

  @Test
  @DisplayName("group names differing in case or punctuation share one layer")
  void groupLayerIds() {
    assertEquals("waypoints.build_event", Waypoints.groupLayerId("Build Event"));
    assertEquals("waypoints.build_event", Waypoints.groupLayerId("  build-event! "));
    assertEquals("waypoints.event_2026", Waypoints.groupLayerId("Event #2026"));
    assertEquals("waypoints.wassergrundstucke", Waypoints.groupLayerId("Wassergrundstücke"));
    assertEquals("waypoints.strasse", Waypoints.groupLayerId("Straße"));
    assertNull(Waypoints.groupLayerId(null));
    assertNull(Waypoints.groupLayerId(" -- "), "a name with no letters is the default layer");
  }

  @Test
  @DisplayName("an outline keeps only its corners")
  void outlineKeepsCorners() {
    assertEquals(
        List.of(
            new Vector(0, 0, 0), new Vector(32, 0, 0), new Vector(32, 0, 16), new Vector(0, 0, 16)),
        Waypoints.outline(
            List.of(
                new Vector(0, 0, 0),
                new Vector(16, 0, 0),
                new Vector(32, 0, 0),
                new Vector(32, 0, 16),
                new Vector(32, 0, 16),
                new Vector(16, 0, 16),
                new Vector(0, 0, 16))));
  }
}
