package net.aerulion.taerra.storage;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import net.aerulion.taerra.model.MapRotation;
import net.aerulion.taerra.model.TileCoordinate;
import net.aerulion.taerra.model.WorldKeys;
import net.aerulion.taerra.util.Throwables;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class MapImageExports {

  public static final int LAYERS = 1 + MapRotation.values().length;

  private static final @NotNull DateTimeFormatter STAMP =
      DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

  private final @NotNull Plugin plugin;
  private final @NotNull TileStorage storage;
  private final @NotNull AtomicBoolean exporting = new AtomicBoolean();

  public MapImageExports(final @NotNull Plugin plugin, final @NotNull TileStorage storage) {
    super();
    this.plugin = plugin;
    this.storage = storage;
  }

  private static @NotNull List<TileCoordinate> layers(
      final @NotNull String worldName, final int zoom) {
    final @NotNull List<TileCoordinate> layers = new ArrayList<>(LAYERS);
    layers.add(TileCoordinate.topDown(worldName, zoom, 0, 0));
    for (final @NotNull MapRotation rotation : MapRotation.values()) {
      layers.add(TileCoordinate.isometric(worldName, rotation, zoom, 0, 0));
    }
    return layers;
  }

  private static @NotNull String layerName(final @NotNull TileCoordinate layer, final int zoom) {
    return (layer.view().rotated()
            ? layer.view().id() + "-" + layer.rotation().id()
            : layer.view().id())
        + (zoom > 0 ? "-z" + zoom : "");
  }

  public @Nullable CompletableFuture<Void> start(
      final @NotNull World world, final int zoom, final @NotNull Listener listener) {
    if (!this.exporting.compareAndSet(false, true)) {
      return null;
    }
    final @NotNull String worldName = WorldKeys.id(world);
    return CompletableFuture.runAsync(
            () -> {
              try {
                exportAll(worldName, zoom, listener);
              } catch (final Exception e) {
                this.plugin
                    .getLogger()
                    .log(
                        Level.SEVERE,
                        "Failed to export images of " + worldName + ": " + Throwables.describe(e),
                        e);
                throw new CompletionException(e);
              }
            },
            task -> this.plugin.getServer().getScheduler().runTaskAsynchronously(this.plugin, task))
        .whenComplete((ignored, error) -> this.exporting.set(false));
  }

  private void exportAll(
      final @NotNull String worldName, final int zoom, final @NotNull Listener listener)
      throws Exception {
    final @NotNull Path directory = this.plugin.getDataFolder().toPath().resolve("exports");
    Files.createDirectories(directory);
    final @NotNull String prefix =
        worldName.replaceAll("[^a-zA-Z0-9_-]", "_") + "-" + LocalDateTime.now().format(STAMP);
    for (final @NotNull TileCoordinate layer : layers(worldName, zoom)) {
      final @NotNull String name = layerName(layer, zoom);
      final long startedAt = System.nanoTime();
      final @Nullable WorldImageExport export =
          WorldImageExporter.export(
              this.storage, layer, directory.resolve(prefix + "-" + name + ".png"));
      listener.exported(name, export, (System.nanoTime() - startedAt) / 1_000_000L);
    }
  }

  @FunctionalInterface
  public interface Listener {

    void exported(@NotNull String layer, @Nullable WorldImageExport export, long millis);
  }
}
