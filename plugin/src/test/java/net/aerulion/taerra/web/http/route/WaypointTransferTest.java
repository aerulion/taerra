package net.aerulion.taerra.web.http.route;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.lang.reflect.Proxy;
import net.aerulion.taerra.api.marker.Marker;
import net.aerulion.taerra.api.marker.PointMarker;
import net.aerulion.taerra.waypoint.PersonalWaypoints;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class WaypointTransferTest {

  private static @NotNull World world() {
    final @NotNull WorldBorder border =
        (WorldBorder)
            Proxy.newProxyInstance(
                WorldBorder.class.getClassLoader(),
                new Class<?>[] {WorldBorder.class},
                (proxy, method, args) -> {
                  if (!method.getName().equals("isInside")) {
                    throw new UnsupportedOperationException(method.getName());
                  }
                  return true;
                });
    return (World)
        Proxy.newProxyInstance(
            World.class.getClassLoader(),
            new Class<?>[] {World.class},
            (proxy, method, args) ->
                switch (method.getName()) {
                  case "getWorldBorder" -> border;
                  case "getKey" -> NamespacedKey.minecraft("overworld");
                  case "getMinHeight" -> -64;
                  case "getMaxHeight" -> 320;
                  case "equals" -> proxy == args[0];
                  case "hashCode" -> System.identityHashCode(proxy);
                  default -> throw new UnsupportedOperationException(method.getName());
                });
  }

  @Test
  @DisplayName("an exported point is imported with its icon")
  void keepsIcons() {
    final @NotNull NamespacedKey icon = new NamespacedKey("towns", "spawn");
    final @NotNull Marker marker =
        Marker.point(
                PersonalWaypoints.markerId("Home"),
                NamespacedKey.minecraft("overworld"),
                new Vector(1.5, 64, .5),
                "Home")
            .withIcon(icon);

    final PersonalWaypoints.Saved imported =
        WaypointTransfer.saved(
            WaypointTransfer.json(new PersonalWaypoints.Saved(marker, false)), world());
    assertNotNull(imported);
    assertEquals(icon, ((PointMarker) imported.marker()).icon());
  }
}
