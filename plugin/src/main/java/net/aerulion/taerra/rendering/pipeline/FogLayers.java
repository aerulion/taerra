package net.aerulion.taerra.rendering.pipeline;

import it.unimi.dsi.fastutil.ints.IntArraySet;
import it.unimi.dsi.fastutil.ints.IntSet;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.aerulion.taerra.model.TileCoordinate;
import net.aerulion.taerra.model.TileGeometry;
import net.aerulion.taerra.model.WorldKeys;
import net.aerulion.taerra.region.MapRegion;
import net.aerulion.taerra.region.MapRegions;
import net.aerulion.taerra.region.RegionFog;
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
      final @NotNull IntSet near = new IntArraySet();
      final @NotNull IntSet bodies = new IntArraySet();
      final @NotNull IntSet close = new IntArraySet();
      for (int chunkX = minChunkX; chunkX < minChunkX + CHUNKS_PER_TILE; chunkX++) {
        for (int chunkZ = minChunkZ; chunkZ < minChunkZ + CHUNKS_PER_TILE; chunkZ++) {
          for (final int region : regions.foggedNear(worldName, chunkX, chunkZ)) {
            near.add(region);
          }
          for (final int region : regions.foggedClose(worldName, chunkX, chunkZ)) {
            close.add(region);
          }
          final @Nullable MapRegion owner = regions.foggedAt(worldName, chunkX, chunkZ);
          if (owner != null) {
            bodies.add(owner.id());
          }
        }
      }
      if (close.size() <= 1 && !bodies.isEmpty()) {
        final int region = bodies.iterator().nextInt();
        final @Nullable MapRegion owner = regions.get(region);
        final @Nullable RenderedImage clear =
            owner != null && owner.fog() == RegionFog.VEIL
                ? base.unveiled() == null ? null : new RenderedImage(base.unveiled(), base.pick())
                : render(renderer, sources, world, provider, columns, FogView.CLEAR, tile, false);
        final @Nullable BufferedImage image =
            clear == null
                ? null
                : changed(clear.image(), base.image(), tile, worldName, regions, region);
        if (clear != null && image != null && ImageUtils.hasVisiblePixels(image)) {
          final @Nullable PickBuffer pick = clear.pick();
          layers.add(
              new RenderedTile(
                  tile, FogView.body(region), image, pick == null ? null : pick.masked(image)));
          layers.add(new RenderedTile(tile, FogView.CLEAR, clear.image(), null));
        }
      } else {
        for (final int region : bodies) {
          final @NotNull FogView body = FogView.body(region);
          final @NotNull RenderedImage rendered =
              render(renderer, sources, world, provider, columns, body, tile, false);
          final @NotNull BufferedImage image =
              changed(rendered.image(), base.image(), tile, worldName, regions, region);
          if (ImageUtils.hasVisiblePixels(image)) {
            final @Nullable PickBuffer pick = rendered.pick();
            layers.add(
                new RenderedTile(tile, body, image, pick == null ? null : pick.masked(image)));
          }
        }
      }
      for (final int region : near) {
        final @NotNull FogView spill = FogView.spill(region);
        final @NotNull RenderedImage overlay =
            render(renderer, sources, world, provider, columns, spill, tile, true);
        if (ImageUtils.hasVisiblePixels(overlay.image())) {
          layers.add(new RenderedTile(tile, spill, overlay.image(), null));
        }
      }
    }
    return layers;
  }

  private static @NotNull RenderedImage render(
      final @NotNull TopDownTileRenderer renderer,
      final @NotNull Map<FogView, CachedChunkColumnsSource> sources,
      final @NotNull World world,
      final @NotNull ChunkSourceProvider provider,
      final @NotNull ChunkColumnsCache columns,
      final @NotNull FogView view,
      final @NotNull TileCoordinate tile,
      final boolean overlay) {
    return renderer.render(
        sources.computeIfAbsent(view, _ -> columns.sourceFor(world, provider, view)),
        tile.x() * TileGeometry.TILE_SIZE,
        tile.z() * TileGeometry.TILE_SIZE,
        TileGeometry.TILE_SIZE,
        overlay);
  }

  private static @NotNull BufferedImage changed(
      final @NotNull BufferedImage layer,
      final @NotNull BufferedImage base,
      final @NotNull TileCoordinate tile,
      final @NotNull String worldName,
      final @NotNull MapRegions regions,
      final int region) {
    final int size = layer.getWidth();
    final int @NotNull [] pixels = ImageUtils.pixels(layer);
    final int @NotNull [] under = ImageUtils.pixels(base);
    final int startX = tile.x() * size;
    final int startZ = tile.z() * size;
    final boolean @NotNull [] inside = new boolean[CHUNKS_PER_TILE * CHUNKS_PER_TILE];
    for (int chunkX = 0; chunkX < CHUNKS_PER_TILE; chunkX++) {
      for (int chunkZ = 0; chunkZ < CHUNKS_PER_TILE; chunkZ++) {
        final @Nullable MapRegion owner =
            regions.foggedAt(worldName, (startX >> 4) + chunkX, (startZ >> 4) + chunkZ);
        inside[chunkZ * CHUNKS_PER_TILE + chunkX] = owner != null && owner.id() == region;
      }
    }
    for (int z = 0; z < size; z++) {
      for (int x = 0; x < size; x++) {
        final int index = z * size + x;
        pixels[index] =
            inside[(z >> 4) * CHUNKS_PER_TILE + (x >> 4)]
                ? difference(pixels[index], under[index])
                : 0;
      }
    }
    return ImageUtils.fromPixels(size, pixels);
  }

  static @NotNull List<RenderedTile> clear(final @NotNull List<RenderedTile> layers) {
    final @NotNull Map<TileCoordinate, BufferedImage> bases = new HashMap<>();
    final @NotNull Map<TileCoordinate, List<BufferedImage>> bodies = new HashMap<>();
    final @NotNull Set<TileCoordinate> rendered = new HashSet<>();
    for (final @NotNull RenderedTile tile : layers) {
      if (tile.layer().kind() == FogView.Kind.CLEAR) {
        rendered.add(tile.coordinate());
      } else if (tile.layer().base()) {
        bases.put(tile.coordinate(), tile.image());
      } else if (tile.layer().kind() == FogView.Kind.BODY) {
        bodies.computeIfAbsent(tile.coordinate(), _ -> new ArrayList<>()).add(tile.image());
      }
    }
    final @NotNull List<RenderedTile> clear = new ArrayList<>();
    bodies.forEach(
        (coordinate, images) -> {
          final @Nullable BufferedImage base = bases.get(coordinate);
          if (base != null && !rendered.contains(coordinate)) {
            clear.add(new RenderedTile(coordinate, FogView.CLEAR, stacked(base, images), null));
          }
        });
    return clear;
  }

  public static @NotNull BufferedImage stacked(
      final @NotNull BufferedImage base, final @NotNull List<BufferedImage> bodies) {
    final int size = base.getWidth();
    final int @NotNull [] pixels = ImageUtils.pixels(base);
    for (final @NotNull BufferedImage body : bodies) {
      final int @NotNull [] over = ImageUtils.pixels(body);
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
    final int @NotNull [] pixels = ImageUtils.pixels(layer);
    final int @NotNull [] under = ImageUtils.pixels(base);
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
