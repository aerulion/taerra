package net.aerulion.taerra.mod.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import net.aerulion.taerra.mod.web.model.MapConfig;
import net.aerulion.taerra.mod.web.model.Marker;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

class OverlayRasterTest {

  private static final MapConfig CONFIG =
      new MapConfig(64, 4, new MapConfig.Isometric(256, 4, 8), null);
  private static final Projection TOP_DOWN = Projection.topDown(CONFIG);

  private static @NotNull OverlayScene scene(final @NotNull Marker marker) {
    final @Nullable OverlayItem item = OverlayItem.of(marker, new MarkerShape(marker, TOP_DOWN));
    return new OverlayScene(0, "world", TOP_DOWN, List.of(item), Look.COLOR, 1, 4);
  }

  private static @NotNull Marker area(
      final double x0, final double z0, final double x1, final double z1) {
    return new Marker(
        "region",
        "Region",
        "#ff0000",
        null,
        "area",
        null,
        null,
        List.of(List.of(x0, z0), List.of(x1, z0), List.of(x1, z1), List.of(x0, z1)),
        0,
        256,
        false,
        false,
        false);
  }

  @Test
  void fillsTheInsideAndOutlinesTheEdge() {
    final int @Nullable [] pixels =
        OverlayRaster.pixels(scene(area(0, 0, 32, 32)), new OverlayKey(4, 0, 0));
    assertNotNull(pixels);
    assertEquals(0x1FFF0000, pixels[64 * 256 + 64], "fill alpha of the color look");
    assertEquals(0xFFFF0000, pixels[64 * 256 + 128], "the right edge, 2 px wide");
    assertEquals(0, pixels[64 * 256 + 200], "outside");
  }

  @Test
  void aTileTheRegionMissesStaysEmpty() {
    assertNull(OverlayRaster.pixels(scene(area(0, 0, 32, 32)), new OverlayKey(4, 5, 5)));
  }

  @Test
  void farAwayARegionIsOneDot() {
    final int @Nullable [] pixels =
        OverlayRaster.pixels(scene(area(0, 0, 4, 4)), new OverlayKey(0, 0, 0));
    assertNotNull(pixels);
    assertEquals(0xB3FF0000, pixels[0], "one dot in the far line color");
  }
}
