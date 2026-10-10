package net.aerulion.taerra.rendering.topdown;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.aerulion.taerra.model.TileCoordinate;
import net.aerulion.taerra.model.TileGeometry;
import net.aerulion.taerra.rendering.block.Fluids;
import net.aerulion.taerra.rendering.color.MapColorResolver;
import net.aerulion.taerra.rendering.sample.ChunkColumnsSource;
import net.aerulion.taerra.rendering.style.Lighting;
import net.aerulion.taerra.rendering.style.MapStyle;
import net.aerulion.taerra.rendering.style.SunShadow;
import net.aerulion.taerra.rendering.tile.PickBuffer;
import net.aerulion.taerra.rendering.tile.RenderedImage;
import net.aerulion.taerra.rendering.tile.RenderedTile;
import net.aerulion.taerra.util.ImageUtils;
import org.bukkit.Material;
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
    final @NotNull SurfaceGrid surface =
        SurfaceGrid.sample(
            source, startBlockX, startBlockZ, tileSize, SunShadow.reach(this.shadowDistance));
    final int @NotNull [] pixels = new int[tileSize * tileSize];
    final int @NotNull [] pickDepth = new int[tileSize * tileSize];
    final int @NotNull [] pickHeight = new int[tileSize * tileSize];
    Arrays.fill(pickDepth, PickBuffer.UNPAINTED);

    for (int x = 0; x < tileSize; x++) {
      for (int z = 0; z < tileSize; z++) {
        final int index = surface.index(x, z);
        final @Nullable Material material = surface.material(index);
        if (material == null) {
          pixels[z * tileSize + x] = 0;
          continue;
        }
        final @Nullable Material cap = surface.cap(index);
        final int baseRgb =
            this.colors.topColor(
                cap == null ? material : cap,
                surface.biome(index),
                startBlockX + x,
                startBlockZ + z);
        final boolean underwater = surface.surfaceY(index) > surface.floorY(index);
        final int @NotNull [] shadingHeights =
            underwater ? surface.floorHeights() : surface.surfaceHeights();
        final int taller = surface.tallerNeighbors(shadingHeights, x, z);
        final @NotNull Material topMaterial = cap == null ? material : cap;
        final boolean glows = this.colors.emitsLight(topMaterial);
        final boolean shadowed =
            !glows
                && this.shadowDistance > 0
                && SunShadow.isShadowed(
                    surface, x, z, surface.surfaceY(index), this.shadowDistance);
        final int topFace =
            glows
                ? Lighting.EMISSIVE
                : Fluids.isClearWater(material) ? Lighting.WATER : Lighting.TOP;
        int shaded =
            applyEdgeShading(
                Lighting.face(baseRgb, topFace, taller, shadowed, Lighting.NO_CONTACT),
                this.colors.sideColor(
                    material, surface.biome(index), startBlockX + x, startBlockZ + z),
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
        pixels[z * tileSize + x] = shaded;
        pickDepth[z * tileSize + x] = 0;
        pickHeight[z * tileSize + x] = surface.surfaceY(index);
      }
    }

    return new RenderedImage(
        ImageUtils.fromPixels(tileSize, pixels), new PickBuffer(tileSize, pickDepth, pickHeight));
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
