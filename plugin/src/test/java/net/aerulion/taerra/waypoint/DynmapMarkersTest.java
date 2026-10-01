package net.aerulion.taerra.waypoint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DynmapMarkersTest {

  @Test
  @DisplayName("imported labels lose characters waypoint names do not allow")
  void cleansLabels() {
    assertEquals("Shop Main St.", DynmapMarkers.clean("\uD83D\uDED2 Shop | Main St."));
    assertEquals("", DynmapMarkers.clean("\u2605\u2605"));
    assertEquals("", DynmapMarkers.clean(null));
  }

  private static final @NotNull String MARKERS =
      """
      sets:
        markers:
          label: Markers
          hide: false
          markers:
            _spawn_world:
              world: world
              x: 0.0
              y: 64.0
              z: 0.0
              label: Spawn
              markup: false
              icon: world
            shop1:
              world: world
              x: 100.5
              y: 70.0
              z: -20.5
              label: Shop
              markup: false
              icon: redflag
            shop2:
              world: world_nether
              x: 12.0
              y: 40.0
              z: 8.0
              label: Shop
              markup: false
              icon: house
          areas:
            plaza:
              world: world
              x: [0.0, 10.0]
              z: [0.0, 10.0]
              label: Plaza
        staff_events:
          label: Staff Events
          hide: true
          markers:
            party:
              world: world
              x: 5.0
              y: 65.0
              z: 5.0
              label: '<b>Summer</b> &amp; <i>Party</i>'
              markup: true
              icon: pinkflag
          lines:
            route:
              world: world
              x: [0.0, 5.0]
              y: [64.0, 64.0]
              z: [0.0, 5.0]
          circles:
            arena:
              world: world
              x: 0.0
              y: 64.0
              z: 0.0
              xr: 5.0
              zr: 5.0
      """;

  @Test
  @DisplayName("points become waypoints, sets become groups, shapes are only counted")
  void parsesMarkers() throws Exception {
    final @NotNull YamlConfiguration yaml = new YamlConfiguration();
    yaml.loadFromString(MARKERS);

    final DynmapMarkers.@NotNull Parsed parsed = DynmapMarkers.parse(yaml);

    assertEquals(3, parsed.shapes());
    final @NotNull List<DynmapMarkers.Found> points = parsed.points();
    assertEquals(
        List.of("Shop", "Shop 2", "Summer & Party"),
        points.stream().map(DynmapMarkers.Found::name).toList());

    final DynmapMarkers.@NotNull Found shop = points.get(0);
    assertEquals("world", shop.world());
    assertEquals(100.5, shop.x());
    assertEquals(0xB02E26, shop.rgb(), "a colored flag keeps its color");
    assertNull(shop.group(), "the default set lands in the plain waypoints layer");

    assertEquals("world_nether", points.get(1).world());
    assertEquals(DynmapMarkers.color("house"), points.get(1).rgb());
    assertEquals("Staff Events", points.get(2).group());
    assertEquals(Set.of("Staff Events"), parsed.hiddenGroups(), "dynmap hid that set by default");
  }

  @Test
  @DisplayName("markers sharing an icon share a color, the plain icon keeps the default")
  void iconColors() {
    assertEquals(0x4FA3E0, DynmapMarkers.color("default"));
    assertEquals(0xF38BAA, DynmapMarkers.color("pinkflag"));
    assertEquals(DynmapMarkers.color("anchor"), DynmapMarkers.color("Anchor"));
    assertNotEquals(DynmapMarkers.color("anchor"), DynmapMarkers.color("tree"));
  }

  @Test
  @DisplayName("repeated names are numbered within the 32-character limit")
  void uniqueNames() {
    final @NotNull Map<String, Integer> seen = new HashMap<>();
    final @NotNull String long32 = "A".repeat(40);
    assertEquals("A".repeat(32), DynmapMarkers.unique(long32, seen));
    assertEquals("A".repeat(30) + " 2", DynmapMarkers.unique(long32, seen));
    assertEquals("shop", DynmapMarkers.unique("shop", seen));
    assertEquals("SHOP 2", DynmapMarkers.unique("SHOP", seen), "names differing in case collide");
  }
}
