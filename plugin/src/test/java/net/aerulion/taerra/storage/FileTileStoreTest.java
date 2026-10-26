package net.aerulion.taerra.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.File;
import java.nio.file.Path;
import net.aerulion.taerra.model.MapRotation;
import net.aerulion.taerra.model.MapView;
import net.aerulion.taerra.model.TileCoordinate;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileTileStoreTest extends TileStorageContract {

  @TempDir Path folder;

  @Override
  TileStore store() {
    return new FileTileStore(this.folder.toFile(), LOGGER);
  }

  @Override
  StorageSettings settings() {
    return StorageSettings.of(new YamlConfiguration());
  }

  @Override
  File dataDirectory() {
    return this.folder.toFile();
  }

  @Test
  @DisplayName("stored tile paths parse back to their coordinates, anything else is ignored")
  void parsesStoredTilePaths() {
    assertEquals(
        TileCoordinate.topDown("minecraft:overworld", 2, -3, 7),
        FileTileStore.parseTile("minecraft:overworld", MapView.TOP_DOWN, Path.of("2/-3/7.png")));
    assertEquals(
        TileCoordinate.isometric("minecraft:overworld", MapRotation.ofIndex(1), 0, 4, -5),
        FileTileStore.parseTile(
            "minecraft:overworld",
            MapView.ISOMETRIC,
            Path.of(MapRotation.ofIndex(1).id() + "/0/4/-5.png")));

    assertNull(FileTileStore.parseTile("w", MapView.TOP_DOWN, Path.of("0/1/x.png")));
    assertNull(FileTileStore.parseTile("w", MapView.TOP_DOWN, Path.of("0/1.png")));
    assertNull(FileTileStore.parseTile("w", MapView.ISOMETRIC, Path.of("9/0/1/2.png")));
  }
}
