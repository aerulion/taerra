package net.aerulion.taerra.rendering.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.awt.image.BufferedImage;
import java.util.List;
import java.util.UUID;
import net.aerulion.taerra.model.TileCoordinate;
import net.aerulion.taerra.rendering.sample.FogView;
import net.aerulion.taerra.rendering.tile.RenderedTile;
import net.aerulion.taerra.util.ImageUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FogLayersTest {

  private static final UUID FIRST = UUID.randomUUID();
  private static final UUID SECOND = UUID.randomUUID();
  private static final int FOG = 0xFFDDDDDD;
  private static final int GRASS = 0xFF336633;

  @Test
  @DisplayName("a clear tile is the base with every body laid over it, erasing where a body erases")
  void clearStacksBodies() {
    final TileCoordinate tile = TileCoordinate.topDown("w", 0, 0, 0);
    final BufferedImage base = ImageUtils.fromPixels(2, new int[] {FOG, FOG, FOG, GRASS});
    final BufferedImage first = ImageUtils.fromPixels(2, new int[] {GRASS, 0, 0, 0});
    final BufferedImage second = ImageUtils.fromPixels(2, new int[] {0, FogLayers.ERASE, 0, 0});

    final List<RenderedTile> clear =
        FogLayers.clear(
            List.of(
                new RenderedTile(tile, base, null),
                new RenderedTile(tile, FogView.body(FIRST), first, null),
                new RenderedTile(tile, FogView.spill(FIRST), first, null),
                new RenderedTile(tile, FogView.body(SECOND), second, null),
                new RenderedTile(TileCoordinate.topDown("w", 0, 1, 0), base, null)));

    assertEquals(1, clear.size());
    assertEquals(FogView.CLEAR, clear.getFirst().layer());
    final BufferedImage image = clear.getFirst().image();
    assertEquals(GRASS, image.getRGB(0, 0));
    assertEquals(0, image.getRGB(1, 0));
    assertEquals(FOG, image.getRGB(0, 1));
    assertEquals(GRASS, image.getRGB(1, 1));
  }
}
