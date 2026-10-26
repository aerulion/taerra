package net.aerulion.taerra.storage;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import net.aerulion.taerra.model.MapRotation;
import net.aerulion.taerra.model.TileCoordinate;
import net.aerulion.taerra.rendering.sample.FogView;
import net.aerulion.taerra.util.ImageUtils;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.containers.JdbcDatabaseContainer;

abstract class SqlTileStoreContract extends TileStorageContract {

  @TempDir Path folder;

  abstract JdbcDatabaseContainer<?> database();

  abstract String type();

  @Override
  StorageSettings settings() {
    return new StorageSettings(
        type(),
        database().getHost(),
        database().getMappedPort(3306),
        database().getDatabaseName(),
        database().getUsername(),
        database().getPassword());
  }

  @Override
  File dataDirectory() {
    return this.folder.toFile();
  }

  @Override
  TileStore store() throws IOException {
    return SqlTileStore.open(settings(), LOGGER);
  }

  @BeforeEach
  void emptyTables() throws IOException, SQLException {
    store().close();
    try (final Connection connection =
            DriverManager.getConnection(
                "jdbc:mariadb://"
                    + database().getHost()
                    + ":"
                    + database().getMappedPort(3306)
                    + "/"
                    + database().getDatabaseName(),
                database().getUsername(),
                database().getPassword());
        final Statement statement = connection.createStatement()) {
      statement.executeUpdate("delete from taerra_tiles");
      statement.executeUpdate("delete from taerra_maps");
    }
  }

  @Test
  @DisplayName(
      "moving from files copies the tiles, layers and picks of the given worlds, then deletes"
          + " their files; the rest move once their world is given")
  void movesFromFiles() throws IOException, InterruptedException {
    final TileStorageImpl storage =
        new TileStorageImpl(
            new FileTileStore(this.folder.toFile(), LOGGER),
            StorageSettings.of(new YamlConfiguration()),
            this.folder.toFile(),
            LOGGER,
            64);
    final UUID region = UUID.fromString("00000000-0000-0000-0000-000000000003");
    final TileCoordinate top = TileCoordinate.topDown("minecraft:overworld", 0, -2, 5);
    final TileCoordinate iso =
        TileCoordinate.isometric("minecraft:the_end", MapRotation.ofIndex(1), 2, 3, -4);
    storage.saveTile(top, ImageUtils.createEmptyTile(4));
    storage.saveTile(top, FogView.body(region), ImageUtils.createEmptyTile(4));
    storage.savePick(top, new byte[] {9, 8, 7});
    storage.saveTile(iso, ImageUtils.createEmptyTile(8));
    final TileCoordinate left = TileCoordinate.topDown("minecraft:the_nether", 0, 1, 1);
    storage.saveTile(left, ImageUtils.createEmptyTile(4));

    storage.move(storage.prepare(settings()), Set.of("minecraft:overworld", "minecraft:the_end"));
    awaitMoved(storage);
    assertNull(storage.move());

    final TileStorageImpl moved = open();
    assertTrue(moved.lastModified(top) > 0);
    assertEquals(List.of(FogView.body(region)), moved.layers(top));
    assertArrayEquals(new byte[] {9, 8, 7}, moved.readPick(top).orElseThrow());
    assertEquals(8, moved.loadTile(iso).orElseThrow().getWidth());
    assertTrue(moved.readEncodedTile(left).isEmpty());
    assertEquals(settings(), storage.settings());
    storage.close();
    for (int i = 0; i < 200 && pngs() > 1; i++) {
      Thread.sleep(50);
    }
    assertEquals(1, pngs());

    final TileStorageImpl resumed = open();
    resumed.resume(Set.of("minecraft:the_nether"));
    awaitMoved(resumed);
    assertEquals(4, open().loadTile(left).orElseThrow().getWidth());
    for (int i = 0; i < 200 && pngs() > 0; i++) {
      Thread.sleep(50);
    }
    assertEquals(0, pngs());
  }

  @Test
  @DisplayName("moving to files copies more than one batch per column, of the given worlds only")
  void movesToFiles() throws IOException, InterruptedException {
    final TileStorageImpl storage = open();
    final BufferedImage image = ImageUtils.createEmptyTile(4);
    final int count = 450;
    for (int z = 0; z < count; z++) {
      storage.saveTile(TileCoordinate.topDown("minecraft:overworld", 0, 0, z), image);
    }
    storage.savePick(TileCoordinate.topDown("minecraft:overworld", 0, 0, 7), new byte[] {1, 2});
    final TileCoordinate iso =
        TileCoordinate.isometric("minecraft:overworld", MapRotation.ofIndex(3), 1, -5, 2);
    storage.saveTile(iso, image);
    final TileCoordinate other = TileCoordinate.topDown("minecraft:the_end", 0, 0, 0);
    storage.saveTile(other, image);

    final StorageSettings files = StorageSettings.of(new YamlConfiguration());
    storage.move(storage.prepare(files), Set.of("minecraft:overworld"));
    awaitMoved(storage);
    storage.close();

    final TileStorageImpl moved =
        new TileStorageImpl(
            new FileTileStore(this.folder.toFile(), LOGGER),
            files,
            this.folder.toFile(),
            LOGGER,
            64);
    this.opened.add(moved);
    assertEquals(
        count, moved.tilesAt(TileCoordinate.topDown("minecraft:overworld", 0, 0, 0)).size());
    assertArrayEquals(
        new byte[] {1, 2},
        moved.readPick(TileCoordinate.topDown("minecraft:overworld", 0, 0, 7)).orElseThrow());
    assertTrue(moved.lastModified(iso) > 0);
    assertEquals(0, moved.lastModified(other));
    assertTrue(open().lastModified(other) > 0);
  }

  @Test
  @DisplayName("moving back to a database replaces the tiles left there with newer ones")
  void movesBackNewer() throws IOException, InterruptedException {
    final TileStorageImpl storage = open();
    final TileCoordinate tile = TileCoordinate.topDown("minecraft:overworld", 0, 0, 0);
    storage.saveTile(tile, ImageUtils.createEmptyTile(4));
    storage.move(
        storage.prepare(StorageSettings.of(new YamlConfiguration())),
        Set.of("minecraft:overworld"));
    awaitMoved(storage);
    Thread.sleep(20);
    storage.saveTile(tile, ImageUtils.createEmptyTile(8));

    storage.move(storage.prepare(settings()), Set.of("minecraft:overworld"));
    awaitMoved(storage);

    assertEquals(8, open().loadTile(tile).orElseThrow().getWidth());
  }

  private static void awaitMoved(final TileStorageImpl storage) throws InterruptedException {
    for (int i = 0; i < 200 && storage.moving(); i++) {
      Thread.sleep(50);
    }
    assertFalse(storage.moving());
  }

  private long pngs() throws IOException {
    try (final Stream<Path> paths = Files.walk(this.folder)) {
      return paths.filter(path -> path.toString().endsWith(".png")).count();
    }
  }
}
