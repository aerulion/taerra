package net.aerulion.taerra.rendering.sample;

import net.aerulion.taerra.model.WorldLimit;
import net.aerulion.taerra.rendering.block.BlockShape;
import net.aerulion.taerra.rendering.block.BlockShapeResolver;
import net.aerulion.taerra.rendering.block.Fluids;
import net.aerulion.taerra.rendering.source.ColumnSource;
import org.bukkit.Material;
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
  private static final int BLOCKED_Y = 63;
  private static final int STRIPE_WIDTH = 4;

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
          builder.completeColumn(null, null);
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
      final @NotNull ColumnSource source, final int chunkX, final int chunkZ) {
    final @NotNull ChunkColumnsBuilder builder = ChunkColumns.builder(chunkX, chunkZ);
    final int y = Math.clamp(BLOCKED_Y, source.minHeight(), source.maxHeight() - 1);
    for (int localX = 0; localX < 16; localX++) {
      for (int localZ = 0; localZ < 16; localZ++) {
        final int diagonal = (chunkX << 4) + localX + (chunkZ << 4) + localZ;
        builder
            .addRun(
                y,
                y,
                Math.floorMod(diagonal, 2 * STRIPE_WIDTH) < STRIPE_WIDTH
                    ? Material.YELLOW_CONCRETE
                    : Material.BLACK_CONCRETE)
            .completeColumn(null, null);
      }
    }
    return builder.build();
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
      builder.completeColumn(null, null);
      return;
    }

    @Nullable Material capMaterial = null;
    @Nullable Material runMaterial = null;
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
        if (runMaterial != null) {
          builder.addRun(runTopY, runBottomY, runMaterial);
          runMaterial = null;
        }
        y &= ~15;
        continue;
      }

      if (interior
          && (y & 15) == 15
          && runMaterial != null
          && !Fluids.isFluid(runMaterial)
          && runBottomY == y + 1
          && source.isSectionAllFull(y, shapes)) {
        y &= ~15;
        runBottomY = y;
        continue;
      }

      final @NotNull Material material =
          Fluids.substituteSubmerged(source.materialAt(localX, y, localZ));
      final @NotNull BlockShape shape = shapes.shapeOf(material);

      if (thinTopY != NO_THIN && shape != BlockShape.THIN) {
        if (shape == BlockShape.FULL && thinTopY - y <= MAX_GROUND_COVER_HEIGHT) {
          if (!sawSolid && capMaterial == null) {
            capMaterial = Fluids.substituteSubmerged(source.materialAt(localX, thinTopY, localZ));
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
        if (runMaterial != null) {
          builder.addRun(runTopY, runBottomY, runMaterial);
          runMaterial = null;
        }
        continue;
      }

      if (!sawSolid) {
        sawSolid = true;
        if (biome == null) {
          biome = source.biomeAt(localX, y, localZ);
        }
      }

      if (runMaterial == material && runBottomY == y + 1) {
        runBottomY = y;
        continue;
      }
      if (runMaterial != null) {
        builder.addRun(runTopY, runBottomY, runMaterial);
      }
      runMaterial = material;
      runTopY = y;
      runBottomY = y;
    }

    if (runMaterial != null) {
      builder.addRun(runTopY, runBottomY, runMaterial);
    }
    if (thinTopY != NO_THIN) {
      addThinAsRuns(source, builder, localX, localZ, thinTopY, source.minHeight());
      if (biome == null) {
        biome = source.biomeAt(localX, thinTopY, localZ);
      }
    }
    builder.completeColumn(biome, capMaterial);
  }

  private static void addThinAsRuns(
      final @NotNull ColumnSource source,
      final @NotNull ChunkColumnsBuilder builder,
      final int localX,
      final int localZ,
      final int topY,
      final int bottomY) {
    @Nullable Material runMaterial = null;
    int runTopY = topY;
    for (int y = topY; y >= bottomY; y--) {
      final @NotNull Material material =
          Fluids.substituteSubmerged(source.materialAt(localX, y, localZ));
      if (material != runMaterial) {
        if (runMaterial != null) {
          builder.addRun(runTopY, y + 1, runMaterial);
        }
        runMaterial = material;
        runTopY = y;
      }
    }
    if (runMaterial != null) {
      builder.addRun(runTopY, bottomY, runMaterial);
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

      final @NotNull Material material = source.materialAt(localX, y, localZ);
      if (shapes.shapeOf(material) != BlockShape.FULL) {
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
