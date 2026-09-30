package net.aerulion.taerra.web.marker;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import net.aerulion.taerra.api.marker.Marker;
import net.aerulion.taerra.api.marker.MarkerRegistry;
import net.aerulion.taerra.marker.MarkerRegistryImpl;
import net.aerulion.taerra.marker.TestPlugins;
import org.bukkit.Color;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MarkerPayloadSerializerTest {

  @Test
  @DisplayName("a world's payload lists only the layers and markers in that world")
  void filtersByWorld() {
    final @NotNull MarkerRegistry registry = new MarkerRegistryImpl();
    final @NotNull NamespacedKey overworld = NamespacedKey.minecraft("overworld");
    final @NotNull Plugin taerra = TestPlugins.named("Taerra");
    registry
        .layer(taerra, "paths", "Paths", false)
        .set(
            Marker.line(
                    "road", overworld, List.of(new Vector(0, 64, 0), new Vector(10, 70, 5)), "Road")
                .smoothed()
                .withDetails(Map.of("Built by", "Alex"))
                .withColor(Color.fromRGB(0x4FA3E0)));
    registry
        .layer(taerra, "nether", "Nether")
        .set(
            Marker.point(
                "portal", NamespacedKey.minecraft("the_nether"), new Vector(0, 64, 0), null));

    final @NotNull List<Map<String, Object>> layers =
        MarkerPayloadSerializer.world(
            registry, "minecraft:overworld", layer -> true, layer -> false);

    assertEquals(1, layers.size());
    assertEquals("paths", layers.getFirst().get("id"));
    assertEquals(false, layers.getFirst().get("shown"));
    final Map<?, ?> road = (Map<?, ?>) ((List<?>) layers.getFirst().get("markers")).getFirst();
    assertEquals("line", road.get("type"));
    assertEquals(true, road.get("smooth"));
    assertEquals("#4fa3e0", road.get("color"));
    assertEquals(List.of(List.of(0.0, 64.0, 0.0), List.of(10.0, 70.0, 5.0)), road.get("points"));
    assertEquals(List.of(List.of("Built by", "Alex")), road.get("details"));
  }
}
