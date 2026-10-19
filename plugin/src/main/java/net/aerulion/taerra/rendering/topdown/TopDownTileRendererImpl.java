package net.aerulion.taerra.rendering.topdown;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.aerulion.taerra.model.TileCoordinate;
import net.aerulion.taerra.model.TileGeometry;
import net.aerulion.taerra.rendering.block.BlockTypes;
import net.aerulion.taerra.rendering.block.Fluids;
import net.aerulion.taerra.rendering.color.BlockColors;
import net.aerulion.taerra.rendering.color.MapColorResolver;
import net.aerulion.taerra.rendering.color.TintColors;
import net.aerulion.taerra.rendering.sample.BiomeBlend;
import net.aerulion.taerra.rendering.sample.ChunkColumnsSource;
import net.aerulion.taerra.rendering.style.Fog;
import net.aerulion.taerra.rendering.style.Lighting;
import net.aerulion.taerra.rendering.style.MapStyle;
import net.aerulion.taerra.rendering.style.SunShadow;
import net.aerulion.taerra.rendering.tile.PickBuffer;
import net.aerulion.taerra.rendering.tile.RenderedImage;
import net.aerulion.taerra.rendering.tile.RenderedTile;
import net.aerulion.taerra.util.ImageUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class TopDownTileRendererImpl implements TopDownTileRenderer {

  private final @NotNull MapColorResolver colors;
  private final float topFaceWeight;
  private final int shadowDistance;

  public TopDownTileRendererImpl(final @NotNull MapColorResolver colors) {
    this(colors, MapStyle.TOP_FACE_WEIGHT, 0);
  }

  public TopDownTileRendererImpl(
      final @NotNull MapColorResolver colors, final float topFaceWeight, final int shadowDistance) {
    super();
    this.colors = colors;
    this.topFaceWeight = topFaceWeight;
    this.shadowDistance = shadowDistance;
  }

  @Override
  public @NotNull RenderedImage render(
      final @NotNull ChunkColumnsSource source,
      final int startBlockX,
      final int startBlockZ,
      final int tileSize) {
    return render(source, startBlockX, startBlockZ, tileSize, false);
  }

  @Override
  public @NotNull RenderedImage render(
      final @NotNull ChunkColumnsSource source,
      final int startBlockX,
      final int startBlockZ,
      final int tileSize,
      final boolean overlay) {
    final @NotNull SurfaceGrid surface =
        SurfaceGrid.sample(
            source, startBlockX, startBlockZ, tileSize, SunShadow.reach(this.shadowDistance));
    if (overlay) {
      return overlay(surface, startBlockX, startBlockZ, tileSize);
    }
    final @NotNull BiomeBlend biomes = new BiomeBlend(source, this.colors);
    final int @NotNull [] pixels = new int[tileSize * tileSize];
    final int @NotNull [] pickDepth = new int[tileSize * tileSize];
    final int @NotNull [] pickHeight = new int[tileSize * tileSize];
    Arrays.fill(pickDepth, PickBuffer.UNPAINTED);
    int @Nullable [] unveiled = null;

    for (int x = 0; x < tileSize; x++) {
      for (int z = 0; z < tileSize; z++) {
        final int index = surface.index(x, z);
        final int blockId = surface.blockId(index);
        if (blockId == BlockTypes.NONE) {
          pixels[z * tileSize + x] = 0;
          continue;
        }
        final int capBlockId = surface.capBlockId(index);
        final @NotNull BlockColors blockColors = this.colors.colorsOf(blockId);
        final @NotNull BlockColors topColors =
            capBlockId == BlockTypes.NONE ? blockColors : this.colors.colorsOf(capBlockId);
        final @Nullable TintColors tint = biomes.at(startBlockX + x, startBlockZ + z);
        final int baseRgb = topColors.topColor(tint);
        final boolean underwater = surface.surfaceY(index) > surface.floorY(index);
        final int @NotNull [] shadingHeights =
            underwater ? surface.floorHeights() : surface.surfaceHeights();
        final int taller = surface.tallerNeighbors(shadingHeights, x, z);
        final boolean glows = topColors.emitsLight();
        final boolean shadowed =
            !glows
                && this.shadowDistance > 0
                && SunShadow.isShadowed(
                    surface, x, z, surface.surfaceY(index), this.shadowDistance);
        final int topFace =
            glows
                ? Lighting.EMISSIVE
                : Fluids.isClearWater(blockId) ? Lighting.WATER : Lighting.TOP;
        int shaded =
            applyEdgeShading(
                Lighting.face(baseRgb, topFace, taller, shadowed, Lighting.NO_CONTACT),
                blockColors.sideColor(tint),
                taller,
                glows ? Lighting.EMISSIVE : -1,
                shadowed,
                surface,
                shadingHeights,
                x,
                z);
        if (underwater) {
          final int depth = surface.surfaceY(index) - surface.floorY(index);
          shaded = MapStyle.shallow(MapStyle.depthTinted(shaded, depth), depth);
        }
        if (unveiled == null && surface.mist(index) != Fog.NO_MIST) {
          unveiled = pixels.clone();
        }
        if (unveiled != null) {
          unveiled[z * tileSize + x] = shaded;
        }
        pixels[z * tileSize + x] =
            Fog.veil(
                shaded,
                surface.mist(index),
                startBlockX + x + 0.5f,
                startBlockZ + z + 0.5f,
                surface.surfaceY(index));
        pickDepth[z * tileSize + x] = 0;
        pickHeight[z * tileSize + x] = surface.surfaceY(index);
      }
    }

    return new RenderedImage(
        ImageUtils.fromPixels(tileSize, pixels),
        new PickBuffer(tileSize, pickDepth, pickHeight),
        unveiled == null ? null : ImageUtils.fromPixels(tileSize, unveiled));
  }

  private static @NotNull RenderedImage overlay(
      final @NotNull SurfaceGrid surface,
      final int startBlockX,
      final int startBlockZ,
      final int tileSize) {
    final int @NotNull [] pixels = new int[tileSize * tileSize];
    final Fog.@NotNull Cell cell = new Fog.Cell();
    for (int x = 0; x < tileSize; x++) {
      for (int z = 0; z < tileSize; z++) {
        final int index = surface.index(x, z);
        if (surface.blockId(index) != BlockTypes.NONE) {
          pixels[z * tileSize + x] =
              Fog.overlay(
                  surface.mist(index),
                  startBlockX + x + 0.5f,
                  startBlockZ + z + 0.5f,
                  surface.surfaceY(index),
                  cell);
        }
      }
    }
    return new RenderedImage(ImageUtils.fromPixels(tileSize, pixels), null);
  }

  @Override
  public @NotNull List<RenderedTile> renderBatch(
      final @NotNull ChunkColumnsSource source,
      final @NotNull List<TileCoordinate> tileCoordinates) {
    final @NotNull List<RenderedTile> renderedTiles = new ArrayList<>(tileCoordinates.size());
    for (final @NotNull TileCoordinate tileCoordinate : tileCoordinates) {
      final @NotNull RenderedImage image =
          render(
              source,
              tileCoordinate.x() * TileGeometry.TILE_SIZE,
              tileCoordinate.z() * TileGeometry.TILE_SIZE,
              TileGeometry.TILE_SIZE);
      if (ImageUtils.hasVisiblePixels(image.image())) {
        renderedTiles.add(new RenderedTile(tileCoordinate, image));
      }
    }
    return renderedTiles;
  }

  private int applyEdgeShading(
      final int litTop,
      final int sideRgb,
      final int taller,
      final int sideFace,
      final boolean shadowed,
      final @NotNull SurfaceGrid surface,
      final int @NotNull [] heights,
      final int x,
      final int z) {
    final int current = heights[surface.index(x, z)];
    final int dropX = surface.hasData(x + 1, z) ? current - heights[surface.index(x + 1, z)] : 0;
    final int dropZ = surface.hasData(x, z + 1) ? current - heights[surface.index(x, z + 1)] : 0;
    if (dropX <= 0 && dropZ <= 0) {
      return litTop;
    }
    return MapStyle.blendFaces(
        litTop,
        Lighting.face(
            sideRgb,
            sideFace < 0 ? Lighting.POSITIVE_X : sideFace,
            taller,
            shadowed,
            Lighting.NO_CONTACT),
        Lighting.face(
            sideRgb,
            sideFace < 0 ? Lighting.POSITIVE_Z : sideFace,
            taller,
            shadowed,
            Lighting.NO_CONTACT),
        dropX,
        dropZ,
        this.topFaceWeight);
  }
}
