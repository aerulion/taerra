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
import java.util.UUID;
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

  private static final UUID SEVEN = UUID.fromString("00000000-0000-0000-0000-000000000007");
  private static final UUID TWO = UUID.fromString("00000000-0000-0000-0000-000000000002");

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

  private Plugin plugin() {
    final File dataFolder = this.folder.toFile();
    return (Plugin)
        Proxy.newProxyInstance(
            Plugin.class.getClassLoader(),
            new Class<?>[] {Plugin.class},
            (proxy, method, args) ->
                switch (method.getName()) {
                  case "getDataFolder" -> dataFolder;
                  case "getLogger" -> Logger.getLogger("test");
                  default -> throw new UnsupportedOperationException(method.getName());
                });
  }

  @Test
  @DisplayName("deleting a view of a world leaves its other view and other worlds alone")
  void deletesView() throws IOException {
    final TileStorageImpl storage = new TileStorageImpl(plugin(), 64);
    final BufferedImage image = ImageUtils.createEmptyTile(4);
    final TileCoordinate top = TileCoordinate.topDown("minecraft:overworld", 0, 1, 2);
    final TileCoordinate iso =
        TileCoordinate.isometric("minecraft:overworld", MapRotation.ofIndex(2), 0, 1, 2);
    final TileCoordinate other =
        TileCoordinate.isometric("minecraft:the_end", MapRotation.ofIndex(2), 0, 1, 2);
    storage.saveTile(top, image);
    storage.saveTile(iso, image);
    storage.saveTile(other, image);

    storage.deleteView("minecraft:overworld", MapView.ISOMETRIC);
    storage.deleteView("minecraft:nether", MapView.ISOMETRIC);

    assertFalse(storage.tileFile(iso).exists());
    assertEquals(List.of(), storage.tilesAt(iso));
    assertTrue(storage.readEncodedTile(iso).isEmpty());
    assertTrue(storage.tileFile(top).exists());
    assertTrue(storage.tileFile(other).exists());
    storage.saveTile(iso, image);
    assertEquals(List.of(iso), storage.tilesAt(iso));
  }

  @Test
  @DisplayName("fog layers sit beside their tile, are listed, and go when the tile goes")
  void storesLayers() throws IOException {
    final Plugin plugin = plugin();
    final TileStorageImpl storage = new TileStorageImpl(plugin, 64);
    final TileCoordinate tile = TileCoordinate.topDown("minecraft:overworld", 0, 3, 12);
    final BufferedImage image = ImageUtils.createEmptyTile(4);

    storage.saveTile(tile, image);
    assertEquals(List.of(), storage.layers(tile));
    storage.saveTile(tile, FogView.spill(SEVEN), image);
    storage.saveTile(tile, FogView.body(SEVEN), image);
    storage.saveTile(
        TileCoordinate.topDown("minecraft:overworld", 0, 3, 1), FogView.body(TWO), image);

    assertEquals(List.of(FogView.body(SEVEN), FogView.spill(SEVEN)), storage.layers(tile));
    assertEquals(
        List.of(FogView.body(SEVEN), FogView.spill(SEVEN)),
        new TileStorageImpl(plugin, 64).layers(tile));
    assertTrue(storage.deleteTile(tile));
    assertEquals(List.of(), storage.layers(tile));
    assertFalse(storage.tileFile(tile, FogView.body(SEVEN)).exists());
    assertEquals(
        List.of(), storage.tilesAt(tile).stream().filter(found -> found.z() == 12).toList());
  }
}
