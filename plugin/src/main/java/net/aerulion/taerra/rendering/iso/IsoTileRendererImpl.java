package net.aerulion.taerra.rendering.iso;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.aerulion.taerra.model.ChunkCoordinate;
import net.aerulion.taerra.model.MapRotation;
import net.aerulion.taerra.rendering.block.Fluids;
import net.aerulion.taerra.rendering.color.MapColorResolver;
import net.aerulion.taerra.rendering.sample.ChunkColumns;
import net.aerulion.taerra.rendering.sample.ChunkColumnsSource;
import net.aerulion.taerra.rendering.sample.ColumnHeightField;
import net.aerulion.taerra.rendering.style.Lighting;
import net.aerulion.taerra.rendering.style.MapStyle;
import net.aerulion.taerra.rendering.style.SunShadow;
import net.aerulion.taerra.rendering.tile.RenderedImage;
import org.bukkit.Material;
import org.bukkit.block.Biome;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class IsoTileRendererImpl implements IsoTileRenderer {

  private static final int CHUNK_SIZE = IsoTileGeometry.CHUNK_SIZE;
  private static final int LAST_LOCAL = CHUNK_SIZE - 1;
  private static final int MAX_LOCAL_SUM = 2 * LAST_LOCAL;
  private static final int SKY_REACH = 12;
  private final @NotNull IsoProjection projection = IsoTileGeometry.projection();
  private final @NotNull MapColorResolver colors;
  private final int shadowDistance;
  private final float topFaceWeight;

  public IsoTileRendererImpl(final @NotNull MapColorResolver colors) {
    this(colors, 0, MapStyle.TOP_FACE_WEIGHT);
  }

  public IsoTileRendererImpl(
      final @NotNull MapColorResolver colors, final int shadowDistance, final float topFaceWeight) {
    super();
    this.colors = colors;
    this.shadowDistance = shadowDistance;
    this.topFaceWeight = topFaceWeight;
  }

  private static int columnIndexAt(
      final @NotNull MapRotation rotation, final int localX, final int localZ) {
    final int insideX = Math.floorMod(localX, CHUNK_SIZE);
    final int insideZ = Math.floorMod(localZ, CHUNK_SIZE);
    return ChunkColumns.columnIndex(
        rotation.localX(insideX, insideZ), rotation.localZ(insideX, insideZ));
  }

  private static int bedYAt(
      final @NotNull MapRotation rotation,
      final ChunkColumns @NotNull [] @NotNull [] around,
      final int localX,
      final int localZ) {
    final @Nullable ChunkColumns columns = columnsAt(around, localX, localZ);
    return columns == null
        ? ChunkColumns.EMPTY_COLUMN
        : columns.bedY(columnIndexAt(rotation, localX, localZ));
  }

  private static @Nullable ChunkColumns columnsAt(
      final ChunkColumns @NotNull [] @NotNull [] around, final int localX, final int localZ) {
    return around[Math.floorDiv(localX, CHUNK_SIZE) + 1][Math.floorDiv(localZ, CHUNK_SIZE) + 1];
  }

  private static void neighborTops(
      final @NotNull MapRotation rotation,
      final ChunkColumns @NotNull [] @NotNull [] around,
      final int localX,
      final int localZ,
      final int @NotNull [] into) {
    int slot = 0;
    for (int offsetX = -1; offsetX <= 1; offsetX++) {
      for (int offsetZ = -1; offsetZ <= 1; offsetZ++) {
        if (offsetX == 0 && offsetZ == 0) {
          continue;
        }
        into[slot] = bedYAt(rotation, around, localX + offsetX, localZ + offsetZ);
        slot++;
      }
    }
  }

  private static int tallerThan(final int @NotNull [] neighborTops, final int topY) {
    int taller = 0;
    for (final int neighborTop : neighborTops) {
      if (neighborTop != ChunkColumns.EMPTY_COLUMN && neighborTop > topY) {
        taller++;
      }
    }
    return taller;
  }

  private static int bedDrop(
      final @NotNull MapRotation rotation,
      final ChunkColumns @NotNull [] @NotNull [] around,
      final int localX,
      final int localZ,
      final int offsetX,
      final int offsetZ) {
    final int here = bedYAt(rotation, around, localX, localZ);
    final int there = bedYAt(rotation, around, localX + offsetX, localZ + offsetZ);
    if (here == ChunkColumns.EMPTY_COLUMN || there == ChunkColumns.EMPTY_COLUMN) {
      return 0;
    }
    return here - there;
  }

  private static long @NotNull [] candidateChunks(
      final @NotNull IsoRenderPass pass, final int minWorldY, final int maxWorldY) {
    final @NotNull MapRotation rotation = pass.rotation();
    final @NotNull List<Long> chunks = new ArrayList<>();
    IsoTileGeometry.forEachCandidateChunk(
        pass.tileBounds(),
        minWorldY,
        maxWorldY,
        rotation,
        (chunkX, chunkZ) -> chunks.add(ChunkCoordinate.key(chunkX, chunkZ)));
    chunks.sort(
        Comparator.comparingInt(
                (final @NotNull Long chunk) -> {
                  final int chunkX = ChunkCoordinate.keyX(chunk);
                  final int chunkZ = ChunkCoordinate.keyZ(chunk);
                  return rotation.rotatedX(chunkX, chunkZ) + rotation.rotatedZ(chunkX, chunkZ);
                })
            .reversed());
    return chunks.stream().mapToLong(Long::longValue).toArray();
  }

  private static boolean isClearWater(final @NotNull Material material) {
    return material == Material.WATER || material == Material.BUBBLE_COLUMN;
  }

  private static int enclosure(
      final @NotNull IsoRenderPass pass,
      final int @NotNull [] rays,
      final int x,
      final int y,
      final int z) {
    int blocked = 0;
    final int ceilingY = pass.solids().ceilingY();
    for (int ray = 0; ray < rays.length; ray += 3) {
      for (int step = 1; step <= SKY_REACH && y + rays[ray + 1] * step <= ceilingY; step++) {
        if (pass.solids()
            .isSolidAt(x + rays[ray] * step, y + rays[ray + 1] * step, z + rays[ray + 2] * step)) {
          blocked++;
          break;
        }
      }
    }
    return blocked;
  }

  @Override
  public @NotNull RenderedImage renderTile(
      final @NotNull MapRotation rotation,
      final int tileX,
      final int tileY,
      final int minWorldY,
      final int maxWorldY,
      final @NotNull ChunkColumnsSource source,
      final boolean withPick) {
    final int size = IsoTileGeometry.TILE_SIZE;
    final @NotNull ScreenBounds tileBounds = IsoTileGeometry.tileBounds(tileX, tileY, 0);
    final @NotNull IsoRenderPass pass =
        new IsoRenderPass(size, tileBounds, rotation, new ColumnHeightField(source), withPick);

    for (final long chunk : candidateChunks(pass, minWorldY, maxWorldY)) {
      if (pass.isFullyPainted()) {
        break;
      }
      final int chunkX = ChunkCoordinate.keyX(chunk);
      final int chunkZ = ChunkCoordinate.keyZ(chunk);
      final long known = source.knownHeights(chunkX, chunkZ);
      if (known == ChunkColumns.MISSING_HEIGHTS
          || (known != ChunkColumns.UNKNOWN_HEIGHTS
              && ChunkColumns.maxTopOf(known) == ChunkColumns.EMPTY_COLUMN)) {
        continue;
      }
      final int maxTopY =
          known == ChunkColumns.UNKNOWN_HEIGHTS ? maxWorldY - 1 : ChunkColumns.maxTopOf(known);
      final @NotNull ScreenBounds bounds =
          IsoTileGeometry.chunkScreenBounds(chunkX, chunkZ, minWorldY, maxTopY, rotation);
      if (!bounds.intersects(tileBounds) || pass.isHidden(bounds)) {
        continue;
      }
      final @Nullable ChunkColumns columns = source.columnsFor(chunkX, chunkZ);
      if (columns != null && !columns.isEmpty()) {
        renderChunk(pass, columns, source);
      }
    }

    return pass.finish();
  }

  private void renderChunk(
      final @NotNull IsoRenderPass pass,
      final @NotNull ChunkColumns chunk,
      final @NotNull ChunkColumnsSource source) {
    final @NotNull MapRotation rotation = pass.rotation();
    final int rotatedChunkX = rotation.rotatedX(chunk.chunkX(), chunk.chunkZ());
    final int rotatedChunkZ = rotation.rotatedZ(chunk.chunkX(), chunk.chunkZ());

    final ChunkColumns @NotNull [] @NotNull [] around = new ChunkColumns[3][3];
    for (int offsetX = -1; offsetX <= 1; offsetX++) {
      for (int offsetZ = -1; offsetZ <= 1; offsetZ++) {
        final int neighborX = rotatedChunkX + offsetX;
        final int neighborZ = rotatedChunkZ + offsetZ;
        around[offsetX + 1][offsetZ + 1] =
            source.columnsFor(
                rotation.worldX(neighborX, neighborZ), rotation.worldZ(neighborX, neighborZ));
      }
    }

    pass.solids().limitRaysTo(rayCeiling(source, chunk.chunkX(), chunk.chunkZ()));
    final int @NotNull [] neighborTops = new int[8];
    for (int localSum = MAX_LOCAL_SUM; localSum >= 0; localSum--) {
      final int firstLocalX = Math.max(0, localSum - LAST_LOCAL);
      final int lastLocalX = Math.min(LAST_LOCAL, localSum);
      for (int localX = firstLocalX; localX <= lastLocalX; localX++) {
        renderColumn(
            pass,
            chunk,
            rotatedChunkX,
            rotatedChunkZ,
            around,
            neighborTops,
            localX,
            localSum - localX);
      }
    }
  }

  private int rayCeiling(
      final @NotNull ChunkColumnsSource source, final int chunkX, final int chunkZ) {
    final int reach =
        1 + Math.max(2 * SKY_REACH, (this.shadowDistance * Math.max(809, 627) >> 10) + 1);
    final int chunks = (LAST_LOCAL + reach) / CHUNK_SIZE + 1;
    int ceiling = Integer.MIN_VALUE;
    for (int offsetX = -chunks; offsetX <= chunks; offsetX++) {
      for (int offsetZ = -chunks; offsetZ <= chunks; offsetZ++) {
        final long known = source.knownHeights(chunkX + offsetX, chunkZ + offsetZ);
        if (known == ChunkColumns.UNKNOWN_HEIGHTS) {
          return Integer.MAX_VALUE;
        }
        if (known != ChunkColumns.MISSING_HEIGHTS) {
          ceiling = Math.max(ceiling, ChunkColumns.maxTopOf(known));
        }
      }
    }
    return ceiling;
  }

  private void renderColumn(
      final @NotNull IsoRenderPass pass,
      final @NotNull ChunkColumns chunk,
      final int rotatedChunkX,
      final int rotatedChunkZ,
      final ChunkColumns @NotNull [] @NotNull [] around,
      final int @NotNull [] neighborTops,
      final int localX,
      final int localZ) {
    final @NotNull MapRotation rotation = pass.rotation();
    final int columnIndex = columnIndexAt(rotation, localX, localZ);
    final int firstRun = chunk.runStart(columnIndex);
    final int lastRun = chunk.runEnd(columnIndex);
    if (firstRun == lastRun) {
      return;
    }

    final int rotatedX = rotatedChunkX * CHUNK_SIZE + localX;
    final int rotatedZ = rotatedChunkZ * CHUNK_SIZE + localZ;
    final int originColumn =
        (int) (this.projection.screenX(rotatedX, rotatedZ) - pass.tileBounds().minX());
    if (originColumn + this.projection.lastColumnOffset() < 0
        || originColumn + this.projection.firstColumnOffset() >= pass.size()) {
      return;
    }

    if (this.projection.screenY(rotatedX, chunk.runTopY(firstRun) + 1, rotatedZ)
            > pass.tileBounds().maxY()
        || this.projection.screenY(rotatedX, chunk.runBottomY(lastRun - 1), rotatedZ)
                + this.projection.blockScreenHeight()
            < pass.tileBounds().minY()) {
      return;
    }
    final int firstX = Math.max(0, originColumn + this.projection.firstColumnOffset());
    final int lastX = Math.min(pass.size() - 1, originColumn + this.projection.lastColumnOffset());

    final int blockX = chunk.chunkX() * CHUNK_SIZE + rotation.localX(localX, localZ);
    final int blockZ = chunk.chunkZ() * CHUNK_SIZE + rotation.localZ(localX, localZ);

    final @Nullable Biome biome = chunk.columnBiome(columnIndex);
    final @Nullable Material cap = chunk.capMaterial(columnIndex);

    neighborTops(rotation, around, localX, localZ, neighborTops);

    final int frontXX = blockX + rotation.worldDeltaX(1, 0);
    final int frontXZ = blockZ + rotation.worldDeltaZ(1, 0);
    final int frontZX = blockX + rotation.worldDeltaX(0, 1);
    final int frontZZ = blockZ + rotation.worldDeltaZ(0, 1);

    final @Nullable ChunkColumns towardX = columnsAt(around, localX + 1, localZ);
    final int towardXColumn = columnIndexAt(rotation, localX + 1, localZ);
    final @Nullable ChunkColumns towardZ = columnsAt(around, localX, localZ + 1);
    final int towardZColumn = columnIndexAt(rotation, localX, localZ + 1);

    for (int runIndex = firstRun; runIndex < lastRun; runIndex++) {
      final int runTopY = chunk.runTopY(runIndex);
      final int runTopRow =
          (int)
              (this.projection.screenY(rotatedX, runTopY + 1, rotatedZ) - pass.tileBounds().minY());
      if (pass.isPainted(firstX, lastX, Math.max(0, runTopRow))) {
        break;
      }
      final int taller = tallerThan(neighborTops, runTopY);
      final @NotNull Material material = chunk.runMaterial(runIndex);
      final boolean water = isClearWater(material);

      final @NotNull Material topMaterial = runIndex == firstRun && cap != null ? cap : material;
      final int rawSideColor = this.colors.sideColor(material, biome, blockX, blockZ);
      final boolean openAbove =
          runIndex == firstRun
              || chunk.runBottomY(runIndex - 1) > runTopY + 1
              || (!water && isClearWater(chunk.runMaterial(runIndex - 1)));

      if (openAbove) {
        final boolean shadowed = sunBlocked(pass, blockX, runTopY + 1, blockZ);

        final int topColor = this.colors.topColor(topMaterial, biome, blockX, blockZ);
        final int faceColor =
            Fluids.isFluid(material) && !water
                ? MapStyle.depthTinted(
                    MapStyle.blendFaces(
                        topColor,
                        rawSideColor,
                        bedDrop(rotation, around, localX, localZ, 1, 0),
                        bedDrop(rotation, around, localX, localZ, 0, 1),
                        this.topFaceWeight),
                    runTopY - chunk.bedY(columnIndex))
                : topColor;
        drawTopFace(
            pass,
            originColumn,
            runTopRow,
            Lighting.face(
                faceColor, Lighting.TOP, water ? 0 : taller, shadowed, Lighting.NO_CONTACT),
            rotatedX + rotatedZ,
            runTopY,
            water);
      }

      final int runBottomY = chunk.runBottomY(runIndex);
      drawSideFace(
          pass,
          originColumn,
          rotatedX,
          rotatedZ,
          runTopY,
          runBottomY,
          true,
          towardX,
          towardXColumn,
          rawSideColor,
          taller,
          water,
          frontXX,
          frontXZ);
      drawSideFace(
          pass,
          originColumn,
          rotatedX,
          rotatedZ,
          runTopY,
          runBottomY,
          false,
          towardZ,
          towardZColumn,
          rawSideColor,
          taller,
          water,
          frontZX,
          frontZZ);
    }
  }

  private void drawTopFace(
      final @NotNull IsoRenderPass pass,
      final int originColumn,
      final int originRow,
      final int color,
      final int depth,
      final int blockY,
      final boolean water) {
    final int size = pass.size();
    for (int offset = this.projection.firstColumnOffset();
        offset <= this.projection.lastColumnOffset();
        offset++) {
      final int x = originColumn + offset;
      if (x < 0 || x >= size) {
        continue;
      }
      final int firstRow = Math.max(0, originRow + this.projection.topFaceFirstRow(offset));
      final int lastRow = Math.min(size - 1, originRow + this.projection.topFaceLastRow(offset));
      for (int y = firstRow; y <= lastRow; y++) {
        if (water) {
          pass.wet(y * size + x, color, blockY);
        } else {
          pass.paint(y * size + x, color, depth, blockY, 0);
        }
      }
      if (!water) {
        pass.markPainted(x, firstRow, lastRow);
      }
    }
  }

  private void drawSideFace(
      final @NotNull IsoRenderPass pass,
      final int originColumn,
      final int rotatedX,
      final int rotatedZ,
      final int runTopY,
      final int runBottomY,
      final boolean positiveX,
      final @Nullable ChunkColumns neighbor,
      final int neighborColumn,
      final int rawColor,
      final int taller,
      final boolean water,
      final int frontX,
      final int frontZ) {

    final int firstOffset = positiveX ? 0 : this.projection.firstColumnOffset();
    final int lastOffset = positiveX ? this.projection.lastColumnOffset() : -1;
    final int face = positiveX ? Lighting.POSITIVE_X : Lighting.POSITIVE_Z;

    final int lastOccluder = neighbor == null ? 0 : neighbor.runEnd(neighborColumn);
    int occluder = neighbor == null ? 0 : neighbor.runStart(neighborColumn);
    int y = runTopY;
    while (y >= runBottomY) {
      while (occluder < lastOccluder
          && (neighbor.runBottomY(occluder) > y
              || (!water && isClearWater(neighbor.runMaterial(occluder))))) {
        occluder++;
      }
      if (occluder < lastOccluder && neighbor.runTopY(occluder) >= y) {

        y = neighbor.runBottomY(occluder) - 1;
        continue;
      }
      final int exposedBottomY =
          occluder < lastOccluder
              ? Math.max(runBottomY, neighbor.runTopY(occluder) + 1)
              : runBottomY;
      int spanBottomY = exposedBottomY;
      if (!water && occluder < lastOccluder && neighbor.runTopY(occluder) + 1 == exposedBottomY) {

        drawLitSpan(
            pass,
            originColumn,
            rotatedX,
            rotatedZ,
            exposedBottomY,
            exposedBottomY,
            firstOffset,
            lastOffset,
            rawColor,
            face,
            taller,
            Lighting.CONTACT_FOOT,
            false,
            frontX,
            frontZ);
        if (y > exposedBottomY) {
          drawLitSpan(
              pass,
              originColumn,
              rotatedX,
              rotatedZ,
              exposedBottomY + 1,
              exposedBottomY + 1,
              firstOffset,
              lastOffset,
              rawColor,
              face,
              taller,
              Lighting.CONTACT_NEAR,
              false,
              frontX,
              frontZ);
        }
        spanBottomY = exposedBottomY + 2;
      }
      if (y >= spanBottomY) {
        drawLitSpan(
            pass,
            originColumn,
            rotatedX,
            rotatedZ,
            y,
            spanBottomY,
            firstOffset,
            lastOffset,
            rawColor,
            face,
            taller,
            Lighting.NO_CONTACT,
            water,
            frontX,
            frontZ);
      }
      y = exposedBottomY - 1;
    }
  }

  private boolean sunBlocked(
      final @NotNull IsoRenderPass pass, final int blockX, final int openY, final int blockZ) {
    return this.shadowDistance > 0
        && SunShadow.isShadowedThrough(
            pass.solids(),
            blockX,
            openY,
            blockZ,
            this.shadowDistance,
            pass.sunStepX(),
            pass.sunStepZ());
  }

  private void drawLitSpan(
      final @NotNull IsoRenderPass pass,
      final int originColumn,
      final int rotatedX,
      final int rotatedZ,
      final int topY,
      final int bottomY,
      final int firstOffset,
      final int lastOffset,
      final int rawColor,
      final int face,
      final int taller,
      final int contact,
      final boolean water,
      final int frontX,
      final int frontZ) {
    if (water) {
      drawSideSpan(
          pass,
          originColumn,
          rotatedX,
          rotatedZ,
          topY,
          bottomY,
          firstOffset,
          lastOffset,
          Lighting.face(rawColor, face, taller, false, contact),
          true);
      return;
    }
    final int firstX = Math.max(0, originColumn + firstOffset);
    final int lastX = Math.min(pass.size() - 1, originColumn + lastOffset);
    final int @NotNull [] rays = pass.skyRays(face == Lighting.POSITIVE_X);
    int spanTopY = topY;
    int spanColor = 0;
    for (int y = topY; y >= bottomY; y--) {
      final int bandRow =
          (int) (this.projection.screenY(rotatedX, y + 1, rotatedZ) - pass.tileBounds().minY());
      if (bandRow >= pass.size() || pass.isPainted(firstX, lastX, Math.max(0, bandRow))) {
        drawRemainingSpan(
            pass,
            originColumn,
            rotatedX,
            rotatedZ,
            spanTopY,
            y + 1,
            firstOffset,
            lastOffset,
            spanColor);
        return;
      }
      final int color =
          bandRow + this.projection.blockScreenHeight() < 0 && y != topY
              ? spanColor
              : Lighting.face(
                  rawColor,
                  face,
                  enclosure(pass, rays, frontX, y, frontZ),
                  face == Lighting.POSITIVE_X && sunBlocked(pass, frontX, y, frontZ),
                  contact);
      if (y == topY) {
        spanColor = color;
      } else if (color != spanColor) {
        drawSideSpan(
            pass,
            originColumn,
            rotatedX,
            rotatedZ,
            spanTopY,
            y + 1,
            firstOffset,
            lastOffset,
            spanColor,
            false);
        spanTopY = y;
        spanColor = color;
      }
    }
    drawRemainingSpan(
        pass,
        originColumn,
        rotatedX,
        rotatedZ,
        spanTopY,
        bottomY,
        firstOffset,
        lastOffset,
        spanColor);
  }

  private void drawRemainingSpan(
      final @NotNull IsoRenderPass pass,
      final int originColumn,
      final int rotatedX,
      final int rotatedZ,
      final int topY,
      final int bottomY,
      final int firstOffset,
      final int lastOffset,
      final int color) {
    if (topY >= bottomY) {
      drawSideSpan(
          pass,
          originColumn,
          rotatedX,
          rotatedZ,
          topY,
          bottomY,
          firstOffset,
          lastOffset,
          color,
          false);
    }
  }

  private void drawSideSpan(
      final @NotNull IsoRenderPass pass,
      final int originColumn,
      final int rotatedX,
      final int rotatedZ,
      final int topY,
      final int bottomY,
      final int firstOffset,
      final int lastOffset,
      final int color,
      final boolean water) {
    final int size = pass.size();
    final int topOriginRow =
        (int) (this.projection.screenY(rotatedX, topY + 1, rotatedZ) - pass.tileBounds().minY());
    final int bandHeight = this.projection.halfWidth();
    final int spanHeight = (topY - bottomY + 1) * bandHeight;
    final int depth = rotatedX + rotatedZ;
    for (int offset = firstOffset; offset <= lastOffset; offset++) {
      final int x = originColumn + offset;
      if (x < 0 || x >= size) {
        continue;
      }
      final int spanStart = topOriginRow + this.projection.sideFaceFirstRow(offset);
      final int firstRow = Math.max(0, spanStart);
      final int lastRow = Math.min(size - 1, spanStart + spanHeight - 1);
      for (int y = firstRow; y <= lastRow; y++) {
        if (water) {
          pass.wet(y * size + x, color, topY);
        } else {
          pass.paint(y * size + x, color, depth, topY - (y - spanStart) / bandHeight, 1);
        }
      }
      if (!water) {
        pass.markPainted(x, firstRow, lastRow);
      }
    }
  }
}
