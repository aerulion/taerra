package net.aerulion.taerra.rendering.sample;

import net.aerulion.taerra.model.WorldLimit;
import net.aerulion.taerra.rendering.block.BlockShape;
import net.aerulion.taerra.rendering.block.BlockShapeResolver;
import net.aerulion.taerra.rendering.block.BlockTypes;
import net.aerulion.taerra.rendering.block.Fluids;
import net.aerulion.taerra.rendering.source.ColumnSource;
import net.aerulion.taerra.rendering.style.Fog;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class ColumnRunSampler {

  public static final int NO_CEILING = Integer.MAX_VALUE;

  private static final int NETHER_REQUIRED_OPEN_SPACE = 2;
  private static final int ROOF_THICKNESS = 5;
  private static final int MAX_GROUND_COVER_HEIGHT = 2;
  private static final int NO_THIN = Integer.MIN_VALUE;
  private static final int FOG_GROUND = BlockTypes.id(Fog.GROUND);

  private ColumnRunSampler() {
    super();
  }

  public static int ceilingY(final @NotNull World world) {
    return world.hasCeiling()
        ? world.getMinHeight() + world.getLogicalHeight() - ROOF_THICKNESS - 1
        : NO_CEILING;
  }

  public static @NotNull ChunkColumns sampleChunk(
      final @NotNull ColumnSource source,
      final int ceilingY,
      final @NotNull BlockShapeResolver shapes,
      final int chunkX,
      final int chunkZ,
      final @Nullable WorldLimit limit) {
    final @NotNull ChunkColumnsBuilder builder = ChunkColumns.builder(chunkX, chunkZ);
    final int blockX = chunkX << 4;
    final int blockZ = chunkZ << 4;
    final boolean clipped =
        limit != null && !limit.covers(blockX, blockZ, blockX + 15, blockZ + 15);
    for (int localX = 0; localX < 16; localX++) {
      for (int localZ = 0; localZ < 16; localZ++) {
        if (clipped && !limit.contains(blockX + localX, blockZ + localZ)) {
          builder.completeColumn(null, BlockTypes.NONE);
          continue;
        }
        sampleColumn(
            source,
            shapes,
            builder,
            localX,
            localZ,
            !clipped && localX > 0 && localX < 15 && localZ > 0 && localZ < 15,
            findColumnTop(source, ceilingY, shapes, localX, localZ));
      }
    }
    return builder.build();
  }

  public static @NotNull ChunkColumns blockedChunk(
      final @NotNull ColumnSource source,
      final int @NotNull [] groundAround,
      final byte @NotNull [] hiddenAround,
      final int chunkX,
      final int chunkZ) {
    final @NotNull ChunkColumnsBuilder builder = ChunkColumns.builder(chunkX, chunkZ);
    for (int localX = 0; localX < 16; localX++) {
      for (int localZ = 0; localZ < 16; localZ++) {
        final int y =
            Math.clamp(
                Fog.groundAt(groundAround, localX, localZ),
                source.minHeight(),
                source.maxHeight() - 1);
        builder.addRun(y, source.minHeight(), FOG_GROUND).completeColumn(null, BlockTypes.NONE);
      }
    }
    return builder.build().withMist(Fog.hidden(chunkX, chunkZ, groundAround, hiddenAround, false));
  }

  public static int @NotNull [] columnTops(
      final @NotNull ColumnSource source,
      final int ceilingY,
      final @NotNull BlockShapeResolver shapes) {
    final int @NotNull [] tops = new int[ChunkColumns.COLUMN_COUNT];
    for (int localX = 0; localX < 16; localX++) {
      for (int localZ = 0; localZ < 16; localZ++) {
        tops[ChunkColumns.columnIndex(localX, localZ)] =
            findColumnTop(source, ceilingY, shapes, localX, localZ);
      }
    }
    return tops;
  }

  private static void sampleColumn(
      final @NotNull ColumnSource source,
      final @NotNull BlockShapeResolver shapes,
      final @NotNull ChunkColumnsBuilder builder,
      final int localX,
      final int localZ,
      final boolean interior,
      final int startY) {
    if (startY == ColumnSource.EMPTY_COLUMN) {
      builder.completeColumn(null, BlockTypes.NONE);
      return;
    }

    int capBlockId = BlockTypes.NONE;
    int runBlockId = BlockTypes.NONE;
    int runTopY = 0;
    int runBottomY = 0;
    boolean sawSolid = false;
    @Nullable Biome biome = null;
    int thinTopY = NO_THIN;

    for (int y = startY; y >= source.minHeight(); y--) {
      if (source.isSectionAllAir(y)) {
        if (thinTopY != NO_THIN) {
          addThinAsRuns(source, builder, localX, localZ, thinTopY, y + 1);
          if (!sawSolid) {
            sawSolid = true;
            biome = source.biomeAt(localX, thinTopY, localZ);
          }
          thinTopY = NO_THIN;
        }
        if (runBlockId != BlockTypes.NONE) {
          builder.addRun(runTopY, runBottomY, runBlockId);
          runBlockId = BlockTypes.NONE;
        }
        y &= ~15;
        continue;
      }

      if (interior
          && (y & 15) == 15
          && runBlockId != BlockTypes.NONE
          && !Fluids.isFluid(runBlockId)
          && runBottomY == y + 1
          && source.isSectionAllFull(y, shapes)) {
        y &= ~15;
        runBottomY = y;
        continue;
      }

      final int blockId = Fluids.substituteSubmerged(source.blockIdAt(localX, y, localZ));
      final @NotNull BlockShape shape = shapes.shapeOf(blockId);

      if (thinTopY != NO_THIN && shape != BlockShape.THIN) {
        if (shape == BlockShape.FULL && thinTopY - y <= MAX_GROUND_COVER_HEIGHT) {
          if (!sawSolid && capBlockId == BlockTypes.NONE) {
            capBlockId = Fluids.substituteSubmerged(source.blockIdAt(localX, thinTopY, localZ));
            biome = source.biomeAt(localX, thinTopY, localZ);
          }
        } else {
          addThinAsRuns(source, builder, localX, localZ, thinTopY, y + 1);
          if (!sawSolid) {
            sawSolid = true;
            biome = source.biomeAt(localX, thinTopY, localZ);
          }
        }
        thinTopY = NO_THIN;
      }

      if (shape != BlockShape.FULL) {
        if (shape == BlockShape.THIN && thinTopY == NO_THIN) {
          thinTopY = y;
        }
        if (runBlockId != BlockTypes.NONE) {
          builder.addRun(runTopY, runBottomY, runBlockId);
          runBlockId = BlockTypes.NONE;
        }
        continue;
      }

      if (!sawSolid) {
        sawSolid = true;
        if (biome == null) {
          biome = source.biomeAt(localX, y, localZ);
        }
      }

      if (runBlockId == blockId && runBottomY == y + 1) {
        runBottomY = y;
        continue;
      }
      if (runBlockId != BlockTypes.NONE) {
        builder.addRun(runTopY, runBottomY, runBlockId);
      }
      runBlockId = blockId;
      runTopY = y;
      runBottomY = y;
    }

    if (runBlockId != BlockTypes.NONE) {
      builder.addRun(runTopY, runBottomY, runBlockId);
    }
    if (thinTopY != NO_THIN) {
      addThinAsRuns(source, builder, localX, localZ, thinTopY, source.minHeight());
      if (biome == null) {
        biome = source.biomeAt(localX, thinTopY, localZ);
      }
    }
    builder.completeColumn(biome, capBlockId);
  }

  private static void addThinAsRuns(
      final @NotNull ColumnSource source,
      final @NotNull ChunkColumnsBuilder builder,
      final int localX,
      final int localZ,
      final int topY,
      final int bottomY) {
    int runBlockId = BlockTypes.NONE;
    int runTopY = topY;
    for (int y = topY; y >= bottomY; y--) {
      final int blockId = Fluids.substituteSubmerged(source.blockIdAt(localX, y, localZ));
      if (blockId != runBlockId) {
        if (runBlockId != BlockTypes.NONE) {
          builder.addRun(runTopY, y + 1, runBlockId);
        }
        runBlockId = blockId;
        runTopY = y;
      }
    }
    if (runBlockId != BlockTypes.NONE) {
      builder.addRun(runTopY, bottomY, runBlockId);
    }
  }

  private static int findColumnTop(
      final @NotNull ColumnSource source,
      final int ceilingY,
      final @NotNull BlockShapeResolver shapes,
      final int localX,
      final int localZ) {
    if (ceilingY == NO_CEILING) {
      return source.highestNonAirY(localX, localZ);
    }
    return findNetherColumnTop(source, ceilingY, shapes, localX, localZ);
  }

  private static int findNetherColumnTop(
      final @NotNull ColumnSource source,
      final int ceilingY,
      final @NotNull BlockShapeResolver shapes,
      final int localX,
      final int localZ) {
    int highestSolidY = ColumnSource.EMPTY_COLUMN;
    int openSpaceRun = 0;
    for (int y = Math.min(source.maxHeight() - 1, ceilingY); y >= source.minHeight(); y--) {
      if (source.isSectionAllAir(y)) {
        openSpaceRun = NETHER_REQUIRED_OPEN_SPACE;
        y &= ~15;
        continue;
      }

      if (shapes.shapeOf(source.blockIdAt(localX, y, localZ)) != BlockShape.FULL) {
        openSpaceRun = Math.min(NETHER_REQUIRED_OPEN_SPACE, openSpaceRun + 1);
        continue;
      }

      if (highestSolidY == ColumnSource.EMPTY_COLUMN) {
        highestSolidY = y;
      }

      if (openSpaceRun == NETHER_REQUIRED_OPEN_SPACE) {
        return y;
      }
      openSpaceRun = 0;
    }
    return highestSolidY;
  }
}
