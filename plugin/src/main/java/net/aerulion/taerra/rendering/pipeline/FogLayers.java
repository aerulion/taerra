package net.aerulion.taerra.rendering.pipeline;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.aerulion.taerra.model.TileCoordinate;
import net.aerulion.taerra.model.TileGeometry;
import net.aerulion.taerra.model.WorldKeys;
import net.aerulion.taerra.region.MapRegion;
import net.aerulion.taerra.region.MapRegions;
import net.aerulion.taerra.rendering.sample.CachedChunkColumnsSource;
import net.aerulion.taerra.rendering.sample.ChunkColumnsCache;
import net.aerulion.taerra.rendering.sample.FogView;
import net.aerulion.taerra.rendering.source.ChunkSourceProvider;
import net.aerulion.taerra.rendering.style.Fog;
import net.aerulion.taerra.rendering.tile.PickBuffer;
import net.aerulion.taerra.rendering.tile.RenderedImage;
import net.aerulion.taerra.rendering.tile.RenderedTile;
import net.aerulion.taerra.rendering.topdown.TopDownTileRenderer;
import net.aerulion.taerra.util.ImageUtils;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class FogLayers {

  public static final int ERASE = 0x01000000;

  public static final @NotNull Comparator<FogView> ORDER =
      Comparator.comparingInt(FogView::region).thenComparing(FogView::kind);

  private static final int CHUNKS_PER_TILE = TileGeometry.TILE_SIZE / 16;

  private FogLayers() {
    super();
  }

  static @NotNull List<RenderedTile> topDown(
      final @NotNull World world,
      final @NotNull ChunkSourceProvider provider,
      final @NotNull MapRegions regions,
      final @NotNull ChunkColumnsCache columns,
      final @NotNull TopDownTileRenderer renderer,
      final @NotNull List<RenderedTile> bases) {
    final @NotNull String worldName = WorldKeys.id(world);
    if (!regions.anyFogged(worldName)) {
      return List.of();
    }
    final @NotNull Map<FogView, CachedChunkColumnsSource> sources = new HashMap<>();
    final @NotNull List<RenderedTile> layers = new ArrayList<>();
    for (final @NotNull RenderedTile base : bases) {
      final @NotNull TileCoordinate tile = base.coordinate();
      final int minChunkX = tile.x() * CHUNKS_PER_TILE;
      final int minChunkZ = tile.z() * CHUNKS_PER_TILE;
      final int maxChunkX = minChunkX + CHUNKS_PER_TILE - 1;
      final int maxChunkZ = minChunkZ + CHUNKS_PER_TILE - 1;
      for (final @NotNull MapRegion region :
          regions.fogged(worldName, minChunkX, minChunkZ, maxChunkX, maxChunkZ, Fog.REACH_CHUNKS)) {
        if (region.reaches(minChunkX, minChunkZ, maxChunkX, maxChunkZ, 0)) {
          final @NotNull FogView body = FogView.body(region.id());
          final @NotNull RenderedImage rendered =
              renderer.render(
                  sources.computeIfAbsent(body, view -> columns.sourceFor(world, provider, view)),
                  tile.x() * TileGeometry.TILE_SIZE,
                  tile.z() * TileGeometry.TILE_SIZE,
                  TileGeometry.TILE_SIZE);
          final @NotNull BufferedImage image =
              changed(rendered.image(), base.image(), tile, region);
          if (ImageUtils.hasVisiblePixels(image)) {
            final @Nullable PickBuffer pick = rendered.pick();
            layers.add(
                new RenderedTile(tile, body, image, pick == null ? null : pick.masked(image)));
          }
        }
        final @NotNull FogView spill = FogView.spill(region.id());
        final @NotNull RenderedImage overlay =
            renderer.render(
                sources.computeIfAbsent(spill, view -> columns.sourceFor(world, provider, view)),
                tile.x() * TileGeometry.TILE_SIZE,
                tile.z() * TileGeometry.TILE_SIZE,
                TileGeometry.TILE_SIZE,
                true);
        if (ImageUtils.hasVisiblePixels(overlay.image())) {
          layers.add(new RenderedTile(tile, spill, overlay.image(), null));
        }
      }
    }
    return layers;
  }

  private static @NotNull BufferedImage changed(
      final @NotNull BufferedImage layer,
      final @NotNull BufferedImage base,
      final @NotNull TileCoordinate tile,
      final @NotNull MapRegion region) {
    final int size = layer.getWidth();
    final int @NotNull [] pixels = layer.getRGB(0, 0, size, size, null, 0, size);
    final int @NotNull [] under = base.getRGB(0, 0, size, size, null, 0, size);
    final int startX = tile.x() * size;
    final int startZ = tile.z() * size;
    for (int z = 0; z < size; z++) {
      for (int x = 0; x < size; x++) {
        final int index = z * size + x;
        pixels[index] =
            region.contains((startX + x) >> 4, (startZ + z) >> 4)
                ? difference(pixels[index], under[index])
                : 0;
      }
    }
    return ImageUtils.fromPixels(size, pixels);
  }

  static @NotNull List<RenderedTile> clear(final @NotNull List<RenderedTile> layers) {
    final @NotNull Map<TileCoordinate, BufferedImage> bases = new HashMap<>();
    final @NotNull Map<TileCoordinate, List<BufferedImage>> bodies = new HashMap<>();
    for (final @NotNull RenderedTile tile : layers) {
      if (tile.layer().base()) {
        bases.put(tile.coordinate(), tile.image());
      } else if (tile.layer().kind() == FogView.Kind.BODY) {
        bodies.computeIfAbsent(tile.coordinate(), _ -> new ArrayList<>()).add(tile.image());
      }
    }
    final @NotNull List<RenderedTile> clear = new ArrayList<>();
    bodies.forEach(
        (coordinate, images) -> {
          final @Nullable BufferedImage base = bases.get(coordinate);
          if (base != null) {
            clear.add(new RenderedTile(coordinate, FogView.CLEAR, stacked(base, images), null));
          }
        });
    return clear;
  }

  public static @NotNull BufferedImage stacked(
      final @NotNull BufferedImage base, final @NotNull List<BufferedImage> bodies) {
    final int size = base.getWidth();
    final int @NotNull [] pixels = base.getRGB(0, 0, size, size, null, 0, size);
    for (final @NotNull BufferedImage body : bodies) {
      final int @NotNull [] over = body.getRGB(0, 0, size, size, null, 0, size);
      for (int index = 0; index < pixels.length; index++) {
        final int alpha = over[index] >>> 24;
        if (over[index] == ERASE) {
          pixels[index] = 0;
        } else if (alpha > 0) {
          pixels[index] = Fog.over(over[index], pixels[index]);
        }
      }
    }
    return ImageUtils.fromPixels(size, pixels);
  }

  public static @NotNull BufferedImage changed(
      final @NotNull BufferedImage layer, final @NotNull BufferedImage base) {
    final int size = layer.getWidth();
    final int @NotNull [] pixels = layer.getRGB(0, 0, size, size, null, 0, size);
    final int @NotNull [] under = base.getRGB(0, 0, size, size, null, 0, size);
    for (int index = 0; index < pixels.length; index++) {
      pixels[index] = difference(pixels[index], under[index]);
    }
    return ImageUtils.fromPixels(size, pixels);
  }

  private static int difference(final int pixel, final int under) {
    if (pixel == under) {
      return 0;
    }
    return (pixel >>> 24) == 0 ? (under >>> 24) == 0 ? 0 : ERASE : pixel;
  }
}
