package net.aerulion.taerra.storage;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;
import net.aerulion.taerra.model.MapRotation;
import net.aerulion.taerra.model.MapView;
import net.aerulion.taerra.model.TileCoordinate;
import net.aerulion.taerra.rendering.sample.FogView;
import net.aerulion.taerra.util.ImageUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

abstract class TileStorageContract {

  static final Logger LOGGER = Logger.getLogger("test");

  private static final UUID SEVEN = UUID.fromString("00000000-0000-0000-0000-000000000007");
  private static final UUID TWO = UUID.fromString("00000000-0000-0000-0000-000000000002");

  final List<TileStorageImpl> opened = new ArrayList<>();

  abstract TileStore store() throws IOException;

  abstract StorageSettings settings();

  abstract File dataDirectory();

  TileStorageImpl open() throws IOException {
    final TileStorageImpl storage =
        new TileStorageImpl(store(), settings(), dataDirectory(), LOGGER, 64);
    this.opened.add(storage);
    return storage;
  }

  @AfterEach
  void closeStorages() {
    this.opened.forEach(TileStorageImpl::close);
  }

  @Test
  @DisplayName("deleting a view of a world leaves its other view and other worlds alone")
  void deletesView() throws IOException {
    final TileStorageImpl storage = open();
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

    assertEquals(0, storage.lastModified(iso));
    assertEquals(List.of(), storage.tilesAt(iso));
    assertTrue(storage.readEncodedTile(iso).isEmpty());
    assertTrue(storage.lastModified(top) > 0);
    assertTrue(storage.lastModified(other) > 0);
    storage.saveTile(iso, image);
    assertEquals(List.of(iso), storage.tilesAt(iso));
  }

  @Test
  @DisplayName("fog layers sit beside their tile, are listed, and go when the tile goes")
  void storesLayers() throws IOException {
    final TileStorageImpl storage = open();
    final TileCoordinate tile = TileCoordinate.topDown("minecraft:overworld", 0, 3, 12);
    final BufferedImage image = ImageUtils.createEmptyTile(4);

    storage.saveTile(tile, image);
    assertEquals(List.of(), storage.layers(tile));
    storage.saveTile(tile, FogView.spill(SEVEN), image);
    storage.saveTile(tile, FogView.body(SEVEN), image);
    storage.saveTile(
        TileCoordinate.topDown("minecraft:overworld", 0, 3, 1), FogView.body(TWO), image);

    assertEquals(List.of(FogView.body(SEVEN), FogView.spill(SEVEN)), storage.layers(tile));
    assertEquals(List.of(FogView.body(SEVEN), FogView.spill(SEVEN)), open().layers(tile));
    assertEquals(
        List.of(tile), storage.tilesAt(tile).stream().filter(found -> found.z() == 12).toList());
    assertTrue(storage.deleteTile(tile));
    assertEquals(List.of(), storage.layers(tile));
    assertTrue(open().readEncodedTile(tile, FogView.body(SEVEN)).isEmpty());
    assertEquals(
        List.of(), storage.tilesAt(tile).stream().filter(found -> found.z() == 12).toList());

    storage.deleteLayers("minecraft:overworld");
    assertEquals(List.of(), open().layers(TileCoordinate.topDown("minecraft:overworld", 0, 3, 1)));
    assertTrue(
        open()
            .readEncodedTile(
                TileCoordinate.topDown("minecraft:overworld", 0, 3, 1), FogView.body(TWO))
            .isEmpty());
  }

  @Test
  @DisplayName("a saved tile is created or changed, an unchanged one only touched and not stale")
  void touchesUnchangedTiles() throws IOException, InterruptedException {
    final TileStorageImpl storage = open();
    final TileCoordinate kept = TileCoordinate.topDown("minecraft:overworld", 1, 0, 0);
    final TileCoordinate stale =
        TileCoordinate.isometric("minecraft:overworld", MapRotation.ofIndex(3), 1, -4, 5);
    final BufferedImage image = ImageUtils.createEmptyTile(4);
    assertEquals(TileStorage.Saved.CREATED, storage.saveTile(kept, ImageUtils.createEmptyTile(8)));
    assertEquals(TileStorage.Saved.CHANGED, storage.saveTile(kept, image));
    assertEquals(TileStorage.Saved.CREATED, storage.saveTile(stale, image));
    Thread.sleep(20);
    final long cutoff = System.currentTimeMillis();
    Thread.sleep(20);

    assertEquals(TileStorage.Saved.UNCHANGED, storage.saveTile(kept, image));
    assertTrue(storage.lastModified(kept) >= cutoff);
    assertEquals(List.of(stale), storage.tilesOlderThan("minecraft:overworld", cutoff));
    assertEquals(4, open().loadTile(stale).orElseThrow().getWidth());
  }

  @Test
  @DisplayName("touching a tile the store doesn't hold fails, so saving writes it instead")
  void touchFailsWithoutTile() throws IOException {
    final TileStore store = store();
    try {
      assertThrows(
          IOException.class,
          () -> store.touch(TileCoordinate.topDown("minecraft:overworld", 0, 9, 9), FogView.BASE));
    } finally {
      store.close();
    }
  }

  @Test
  @DisplayName("counting counts the tiles, layers and picks of the given worlds only")
  void countsTiles() throws IOException {
    final TileStorageImpl storage = open();
    final TileCoordinate tile = TileCoordinate.topDown("minecraft:overworld", 0, 1, 1);
    storage.saveTile(tile, ImageUtils.createEmptyTile(4));
    storage.saveTile(tile, FogView.body(SEVEN), ImageUtils.createEmptyTile(4));
    storage.savePick(tile, new byte[] {1});
    storage.saveTile(
        TileCoordinate.topDown("minecraft:the_end", 0, 1, 1), ImageUtils.createEmptyTile(4));
    final TileStore store = store();
    try {
      assertEquals(3, store.count(Set.of("minecraft:overworld")));
    } finally {
      store.close();
    }
  }

  @Test
  @DisplayName("pick buffers are kept per layer and go with their tile")
  void storesPicks() throws IOException {
    final TileStorageImpl storage = open();
    final TileCoordinate tile = TileCoordinate.topDown("minecraft:overworld", 0, 7, 7);
    final byte[] base = {1, 2, 3};
    final byte[] body = {4, 5, 6};
    storage.saveTile(tile, ImageUtils.createEmptyTile(4));
    storage.savePick(tile, base);
    storage.savePick(tile, FogView.body(SEVEN), body);

    assertArrayEquals(base, open().readPick(tile).orElseThrow());
    assertArrayEquals(body, open().readPick(tile, FogView.body(SEVEN)).orElseThrow());
    storage.deleteLayer(tile, FogView.BASE);
    assertTrue(open().readPick(tile).isEmpty());
  }
}
