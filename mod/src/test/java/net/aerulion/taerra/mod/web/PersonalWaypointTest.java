package net.aerulion.taerra.mod.web;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import java.util.Arrays;
import java.util.List;
import net.aerulion.taerra.mod.web.model.PersonalWaypoint;
import net.aerulion.taerra.mod.web.model.Waypoint;
import org.junit.jupiter.api.Test;

class PersonalWaypointTest {

  private static final Gson GSON = new Gson();

  @Test
  void aPointIsCenteredOnItsBlock() {
    final PersonalWaypoint home =
        GSON.fromJson(
            "{\"name\":\"Home\",\"world\":\"minecraft:overworld\",\"type\":\"point\","
                + "\"x\":10,\"y\":64,\"z\":-3,\"color\":\"#f9801d\"}",
            PersonalWaypoint.class);
    assertTrue(home.point());
    assertArrayEquals(new double[] {10.5, 64, -2.5}, home.center());
    final Waypoint editable = home.editable();
    assertTrue(editable.personal());
    assertEquals("Home", editable.previous());
    assertEquals("", editable.description());
  }

  @Test
  void anAreaIsCenteredOnItsBoundsAndEditedWithoutHeights() {
    final PersonalWaypoint field =
        GSON.fromJson(
            "{\"name\":\"Field\",\"world\":\"minecraft:overworld\",\"type\":\"area\","
                + "\"points\":[[0,0],[20,0],[20,10],[0,10]],\"hidden\":true}",
            PersonalWaypoint.class);
    assertTrue(field.hidden());
    final double[] center = field.center();
    assertEquals(10, center[0]);
    assertTrue(Double.isNaN(center[1]));
    assertEquals(5, center[2]);
    final List<List<Double>> points = field.editable().points();
    assertEquals(Arrays.asList(20.0, null, 10.0), points.get(2));
    assertNull(field.editable().y());
    assertEquals(Waypoint.DEFAULT_COLOR, field.editable().color());
  }
}
