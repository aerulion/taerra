package net.aerulion.taerra.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Logger;
import net.aerulion.taerra.model.MapRotation;
import net.aerulion.taerra.model.MapView;
import net.aerulion.taerra.model.TileCoordinate;
import net.aerulion.taerra.rendering.sample.FogView;
import net.aerulion.taerra.util.ImageUtils;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TileStorageImplTest {

  @Test
  @DisplayName("stored tile paths parse back to their coordinates, anything else is ignored")
  void parsesStoredTilePaths() {
    assertEquals(
        TileCoordinate.topDown("minecraft:overworld", 2, -3, 7),
        TileStorageImpl.parseTile("minecraft:overworld", MapView.TOP_DOWN, Path.of("2/-3/7.png")));
    assertEquals(
        TileCoordinate.isometric("minecraft:overworld", MapRotation.ofIndex(1), 0, 4, -5),
        TileStorageImpl.parseTile(
            "minecraft:overworld",
            MapView.ISOMETRIC,
            Path.of(MapRotation.ofIndex(1).id() + "/0/4/-5.png")));

    assertNull(TileStorageImpl.parseTile("w", MapView.TOP_DOWN, Path.of("0/1/x.png")));
    assertNull(TileStorageImpl.parseTile("w", MapView.TOP_DOWN, Path.of("0/1.png")));
    assertNull(TileStorageImpl.parseTile("w", MapView.ISOMETRIC, Path.of("9/0/1/2.png")));
  }

  @TempDir Path folder;

  @Test
  @DisplayName("fog layers sit beside their tile, are listed, and go when the tile goes")
  void storesLayers() throws IOException {
    final File dataFolder = this.folder.toFile();
    final Plugin plugin =
        (Plugin)
            Proxy.newProxyInstance(
                Plugin.class.getClassLoader(),
                new Class<?>[] {Plugin.class},
                (proxy, method, args) ->
                    switch (method.getName()) {
                      case "getDataFolder" -> dataFolder;
                      case "getLogger" -> Logger.getLogger("test");
                      default -> throw new UnsupportedOperationException(method.getName());
                    });
    final TileStorageImpl storage = new TileStorageImpl(plugin, 64);
    final TileCoordinate tile = TileCoordinate.topDown("minecraft:overworld", 0, 3, 12);
    final BufferedImage image = ImageUtils.createEmptyTile(4);

    storage.saveTile(tile, image);
    assertEquals(List.of(), storage.layers(tile));
    storage.saveTile(tile, FogView.spill(7), image);
    storage.saveTile(tile, FogView.body(7), image);
    storage.saveTile(
        TileCoordinate.topDown("minecraft:overworld", 0, 3, 1), FogView.body(2), image);

    assertEquals(List.of(FogView.body(7), FogView.spill(7)), storage.layers(tile));
    assertEquals(
        List.of(FogView.body(7), FogView.spill(7)), new TileStorageImpl(plugin, 64).layers(tile));
    assertTrue(storage.deleteTile(tile));
    assertEquals(List.of(), storage.layers(tile));
    assertFalse(storage.tileFile(tile, FogView.body(7)).exists());
    assertEquals(
        List.of(), storage.tilesAt(tile).stream().filter(found -> found.z() == 12).toList());
  }
}
