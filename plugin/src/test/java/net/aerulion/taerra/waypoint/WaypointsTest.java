package net.aerulion.taerra.waypoint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

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
}
