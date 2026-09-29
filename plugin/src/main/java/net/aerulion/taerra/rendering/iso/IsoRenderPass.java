package net.aerulion.taerra.rendering.iso;

import java.util.Arrays;
import net.aerulion.taerra.model.MapRotation;
import net.aerulion.taerra.rendering.sample.ColumnHeightField;
import net.aerulion.taerra.rendering.style.MapStyle;
import net.aerulion.taerra.rendering.tile.PickBuffer;
import net.aerulion.taerra.rendering.tile.RenderedImage;
import net.aerulion.taerra.util.ImageUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class IsoRenderPass {

  private static final int NO_WATER = Integer.MIN_VALUE;
  private static final int @NotNull [] @NotNull [] SKY_DIRECTIONS = {
    {0, 1, 0}, {1, 1, 0}, {1, 1, 1}, {1, 1, -1}, {0, 1, 1}, {0, 1, -1}, {2, 1, 1}, {2, 1, -1}
  };

  private final int size;
  private final @NotNull ScreenBounds tileBounds;
  private final @NotNull MapRotation rotation;
  private final @NotNull ColumnHeightField solids;
  private final int @NotNull [] pixels;
  private final int @NotNull [] paintedFrom;
  private final int @NotNull [] waterSurface;
  private final int @NotNull [] waterColor;
  private final int @Nullable [] pickDepth;
  private final int @Nullable [] pickHeight;
  private final int @NotNull [] skyRaysX;
  private final int @NotNull [] skyRaysZ;
  private final int sunStepX;
  private final int sunStepZ;

  IsoRenderPass(
      final int size,
      final @NotNull ScreenBounds tileBounds,
      final @NotNull MapRotation rotation,
      final @NotNull ColumnHeightField solids,
      final boolean withPick) {
    super();
    this.size = size;
    this.tileBounds = tileBounds;
    this.rotation = rotation;
    this.solids = solids;
    this.pixels = new int[size * size];
    this.paintedFrom = new int[size];
    Arrays.fill(this.paintedFrom, size);
    this.waterSurface = new int[size * size];
    Arrays.fill(this.waterSurface, NO_WATER);
    this.waterColor = new int[size * size];
    this.pickDepth = withPick ? new int[size * size] : null;
    this.pickHeight = withPick ? new int[size * size] : null;
    if (this.pickDepth != null) {
      Arrays.fill(this.pickDepth, PickBuffer.UNPAINTED);
    }
    this.skyRaysX = skyRays(rotation, true);
    this.skyRaysZ = skyRays(rotation, false);
    this.sunStepX = rotation.worldDeltaX(MapStyle.SUN_STEP_X, MapStyle.SUN_STEP_Z);
    this.sunStepZ = rotation.worldDeltaZ(MapStyle.SUN_STEP_X, MapStyle.SUN_STEP_Z);
  }

  private static int @NotNull [] skyRays(
      final @NotNull MapRotation rotation, final boolean positiveX) {
    final int @NotNull [] rays = new int[SKY_DIRECTIONS.length * 3];
    for (int ray = 0; ray < SKY_DIRECTIONS.length; ray++) {
      final int out = SKY_DIRECTIONS[ray][0];
      final int along = SKY_DIRECTIONS[ray][2];
      final int rotatedDeltaX = positiveX ? out : along;
      final int rotatedDeltaZ = positiveX ? along : out;
      rays[ray * 3] = rotation.worldDeltaX(rotatedDeltaX, rotatedDeltaZ);
      rays[ray * 3 + 1] = SKY_DIRECTIONS[ray][1];
      rays[ray * 3 + 2] = rotation.worldDeltaZ(rotatedDeltaX, rotatedDeltaZ);
    }
    return rays;
  }

  int size() {
    return this.size;
  }

  @NotNull
  ScreenBounds tileBounds() {
    return this.tileBounds;
  }

  @NotNull
  MapRotation rotation() {
    return this.rotation;
  }

  @NotNull
  ColumnHeightField solids() {
    return this.solids;
  }

  int @NotNull [] skyRays(final boolean positiveX) {
    return positiveX ? this.skyRaysX : this.skyRaysZ;
  }

  int sunStepX() {
    return this.sunStepX;
  }

  int sunStepZ() {
    return this.sunStepZ;
  }

  boolean isFullyPainted() {
    return this.paintedFrom[0] == 0 && isPainted(0, this.size - 1, 0);
  }

  boolean isHidden(final @NotNull ScreenBounds bounds) {
    return isPainted(
        (int) Math.max(0, bounds.minX() - this.tileBounds.minX()),
        (int) Math.min(this.size - 1, bounds.maxX() - this.tileBounds.minX()),
        (int) Math.max(0, bounds.minY() - this.tileBounds.minY()));
  }

  boolean isPainted(final int firstX, final int lastX, final int row) {
    for (int x = firstX; x <= lastX; x++) {
      if (this.paintedFrom[x] > row) {
        return false;
      }
    }
    return true;
  }

  void markPainted(final int x, final int firstRow, final int lastRow) {
    if (firstRow >= this.paintedFrom[x] || lastRow + 1 < this.paintedFrom[x]) {
      return;
    }
    int row = firstRow;
    while (row > 0 && this.pixels[(row - 1) * this.size + x] != 0) {
      row--;
    }
    this.paintedFrom[x] = row;
  }

  void wet(final int index, final int color, final int surfaceY) {
    if (this.pixels[index] == 0 && this.waterSurface[index] == NO_WATER) {
      this.waterSurface[index] = surfaceY;
      this.waterColor[index] = color;
    }
  }

  void paint(
      final int index,
      final int color,
      final int depth,
      final int blockY,
      final int submergedExtra) {
    if (this.pixels[index] != 0) {
      return;
    }
    final int surfaceY = this.waterSurface[index];
    this.pixels[index] =
        surfaceY == NO_WATER
            ? color
            : MapStyle.underwater(
                color, this.waterColor[index], surfaceY - blockY + submergedExtra);
    if (this.pickDepth != null && this.pickHeight != null) {
      this.pickDepth[index] = depth;
      this.pickHeight[index] = blockY;
    }
  }

  @NotNull
  RenderedImage finish() {
    for (int index = 0; index < this.size * this.size; index++) {
      if (this.pixels[index] == 0 && this.waterSurface[index] != NO_WATER) {
        this.pixels[index] = MapStyle.depthTinted(this.waterColor[index], Integer.MAX_VALUE);
      }
    }
    return new RenderedImage(
        ImageUtils.fromPixels(this.size, this.pixels),
        this.pickDepth == null || this.pickHeight == null
            ? null
            : new PickBuffer(
                this.size, this.pickDepth, this.pickHeight, PickBuffer.ISOMETRIC_SCALE));
  }
}
