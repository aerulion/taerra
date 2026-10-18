package net.aerulion.taerra.rendering.iso;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.aerulion.taerra.model.MapRotation;
import net.aerulion.taerra.rendering.sample.BiomeBlend;
import net.aerulion.taerra.rendering.sample.ColumnHeightField;
import net.aerulion.taerra.rendering.style.Fog;
import net.aerulion.taerra.rendering.style.MapStyle;
import net.aerulion.taerra.rendering.tile.PickBuffer;
import net.aerulion.taerra.rendering.tile.RenderedImage;
import net.aerulion.taerra.util.ImageUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class IsoRenderPass {

  private static final int NO_WATER = Integer.MIN_VALUE;
  private static final float HALF_WIDTH = IsoTileGeometry.projection().halfWidth();
  private static final float QUARTER_WIDTH = IsoTileGeometry.projection().quarterWidth();
  private static final int @NotNull [] @NotNull [] SKY_DIRECTIONS = {
    {0, 0}, {1, 0}, {1, 1}, {1, -1}, {0, 1}, {0, -1}, {2, 1}, {2, -1}
  };

  private final int size;
  private final @NotNull ScreenBounds tileBounds;
  private final @NotNull MapRotation rotation;
  private final @NotNull ColumnHeightField solids;
  private final @NotNull BiomeBlend biomes;
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
  private final int @NotNull [] veilMist;
  private final int @NotNull [] veilY;
  private final int @NotNull [] cloud;
  private final Fog.@NotNull Cell fogCell = new Fog.Cell();
  private final boolean @NotNull [] overlays;
  private final int @NotNull [] viewMist;
  private final int @NotNull [] @NotNull [] viewVeilMist;
  private final int @NotNull [] @NotNull [] viewCloud;
  private int mist = Fog.NO_MIST;
  private boolean misted;
  private int lastSunX = Integer.MIN_VALUE;
  private int lastSunY;
  private int lastSunZ;
  private boolean lastSunBlocked;

  IsoRenderPass(
      final int size,
      final @NotNull ScreenBounds tileBounds,
      final @NotNull MapRotation rotation,
      final @NotNull ColumnHeightField solids,
      final @NotNull BiomeBlend biomes,
      final boolean withPick,
      final boolean @NotNull [] overlays) {
    super();
    this.overlays = overlays;
    this.viewMist = new int[overlays.length];
    this.viewVeilMist = new int[overlays.length][size * size];
    this.viewCloud = new int[overlays.length][size * size];
    this.size = size;
    this.tileBounds = tileBounds;
    this.rotation = rotation;
    this.solids = solids;
    this.biomes = biomes;
    this.pixels = new int[size * size];
    this.paintedFrom = new int[size];
    Arrays.fill(this.paintedFrom, size);
    this.waterSurface = new int[size * size];
    Arrays.fill(this.waterSurface, NO_WATER);
    this.waterColor = new int[size * size];
    this.veilMist = new int[size * size];
    this.veilY = new int[size * size];
    this.cloud = new int[size * size];
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
    final int @NotNull [] rays = new int[SKY_DIRECTIONS.length * 2];
    for (int ray = 0; ray < SKY_DIRECTIONS.length; ray++) {
      final int out = SKY_DIRECTIONS[ray][0];
      final int along = SKY_DIRECTIONS[ray][1];
      final int rotatedDeltaX = positiveX ? out : along;
      final int rotatedDeltaZ = positiveX ? along : out;
      rays[ray * 2] = rotation.worldDeltaX(rotatedDeltaX, rotatedDeltaZ);
      rays[ray * 2 + 1] = rotation.worldDeltaZ(rotatedDeltaX, rotatedDeltaZ);
    }
    return rays;
  }

  int size() {
    return this.size;
  }

  int views() {
    return this.overlays.length;
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

  @NotNull
  BiomeBlend biomes() {
    return this.biomes;
  }

  int @NotNull [] skyRays(final boolean positiveX) {
    return positiveX ? this.skyRaysX : this.skyRaysZ;
  }

  boolean isLastSun(final int blockX, final int openY, final int blockZ) {
    return blockX == this.lastSunX && openY == this.lastSunY && blockZ == this.lastSunZ;
  }

  boolean lastSunBlocked() {
    return this.lastSunBlocked;
  }

  boolean rememberSun(final int blockX, final int openY, final int blockZ, final boolean blocked) {
    this.lastSunX = blockX;
    this.lastSunY = openY;
    this.lastSunZ = blockZ;
    this.lastSunBlocked = blocked;
    return blocked;
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

  void mist(final int mist) {
    this.mist = mist;
    this.misted |= mist != Fog.NO_MIST;
  }

  void viewMist(final int view, final int mist) {
    this.viewMist[view] = mist;
  }

  void cloud(final int view, final int index, final int argb) {
    final int @NotNull [] cloud = view < 0 ? this.cloud : this.viewCloud[view];
    if (this.pixels[index] == 0 && this.waterSurface[index] == NO_WATER && cloud[index] == 0) {
      cloud[index] = argb;
    }
  }

  private void veiled(final int index) {
    this.veilMist[index] = this.mist;
    for (int view = 0; view < this.viewMist.length; view++) {
      this.viewVeilMist[view][index] = this.viewMist[view];
    }
  }

  void wet(final int index, final int color, final int surfaceY) {
    if (this.pixels[index] == 0 && this.waterSurface[index] == NO_WATER) {
      this.waterSurface[index] = surfaceY;
      this.waterColor[index] = color;
      veiled(index);
      this.veilY[index] = surfaceY;
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
            : submergedExtra == 0
                ? MapStyle.shallow(
                    MapStyle.underwater(color, this.waterColor[index], surfaceY - blockY),
                    surfaceY - blockY)
                : MapStyle.underwater(
                    color, this.waterColor[index], surfaceY - blockY + submergedExtra);
    if (surfaceY == NO_WATER) {
      veiled(index);
      this.veilY[index] = blockY;
    }
    if (this.pickDepth != null && this.pickHeight != null) {
      this.pickDepth[index] = depth;
      this.pickHeight[index] = blockY;
    }
  }

  private int fog(final int index, final int argb, final int mist, final boolean overlay) {
    final float screenX = this.tileBounds.minX() + index % this.size + 0.5f;
    final float screenY = this.tileBounds.minY() + index / this.size + 0.5f;
    final int layerY = Fog.layerY(mist);
    final float worldX = worldX(screenX, screenY, layerY);
    final float worldZ = worldZ(screenX, screenY, layerY);
    int blockY = this.veilY[index];
    if (layerY > blockY
        && !this.solids.hasColumn((int) Math.floor(worldX), (int) Math.floor(worldZ))) {
      final double reach =
          reach(worldX(screenX, screenY, blockY), worldZ(screenX, screenY, blockY), worldX, worldZ);
      blockY = (int) Math.round(layerY - reach * (layerY - blockY));
    }
    return Fog.skyShadow(
        overlay
            ? Fog.overlay(mist, worldX, worldZ, blockY, this.fogCell)
            : Fog.veil(argb, mist, worldX, worldZ, blockY, this.fogCell),
        mist);
  }

  private double reach(final double fromX, final double fromZ, final double toX, final double toZ) {
    final int steps = (int) Math.ceil(Math.max(Math.abs(toX - fromX), Math.abs(toZ - fromZ)));
    for (int step = 1; step <= steps; step++) {
      final double along = (double) step / steps;
      if (!this.solids.hasColumn(
          (int) Math.floor(fromX + (toX - fromX) * along),
          (int) Math.floor(fromZ + (toZ - fromZ) * along))) {
        return (step - 1) / (double) steps;
      }
    }
    return 1;
  }

  private float worldX(final float screenX, final float screenY, final int y) {
    final float across = screenX / HALF_WIDTH;
    final float along = (screenY + y * HALF_WIDTH) / QUARTER_WIDTH;
    return this.rotation.worldDeltaX(1, 0) * (along + across) / 2.0f
        + this.rotation.worldDeltaX(0, 1) * (along - across) / 2.0f;
  }

  private float worldZ(final float screenX, final float screenY, final int y) {
    final float across = screenX / HALF_WIDTH;
    final float along = (screenY + y * HALF_WIDTH) / QUARTER_WIDTH;
    return this.rotation.worldDeltaZ(1, 0) * (along + across) / 2.0f
        + this.rotation.worldDeltaZ(0, 1) * (along - across) / 2.0f;
  }

  private static int clouded(final int argb, final int cloud) {
    return MapStyle.blend(
        argb == 0 ? cloud | 0xFF000000 : argb, cloud | 0xFF000000, (cloud >>> 24) / 255.0f);
  }

  @NotNull
  RenderedImage finish() {
    int @Nullable [] unveiled = null;
    if (this.misted || this.overlays.length > 0) {
      for (int index = 0; index < this.size * this.size; index++) {
        if (this.pixels[index] == 0 && this.waterSurface[index] != NO_WATER) {
          this.pixels[index] = MapStyle.depthTinted(this.waterColor[index], Integer.MAX_VALUE);
        }
      }
      unveiled = this.pixels.clone();
    }
    for (int index = 0; index < this.size * this.size; index++) {
      if (this.pixels[index] == 0 && this.waterSurface[index] != NO_WATER) {
        this.pixels[index] = MapStyle.depthTinted(this.waterColor[index], Integer.MAX_VALUE);
      }
      if (this.veilMist[index] != Fog.NO_MIST) {
        this.pixels[index] = fog(index, this.pixels[index], this.veilMist[index], false);
      }
      if (this.cloud[index] != 0) {
        this.pixels[index] = clouded(this.pixels[index], this.cloud[index]);
      }
    }
    final @NotNull List<BufferedImage> views = new ArrayList<>(this.overlays.length);
    for (int view = 0; view < this.overlays.length; view++) {
      views.add(ImageUtils.fromPixels(this.size, finishView(view, unveiled)));
    }
    return new RenderedImage(
        ImageUtils.fromPixels(this.size, this.pixels),
        this.pickDepth == null || this.pickHeight == null
            ? null
            : new PickBuffer(
                this.size,
                this.pickDepth,
                this.pickHeight,
                PickBuffer.ISOMETRIC_SCALE,
                PickBuffer.ISOMETRIC_DEPTH_PER_HEIGHT),
        unveiled == null || !this.misted ? null : ImageUtils.fromPixels(this.size, unveiled),
        views);
  }

  private int @NotNull [] finishView(final int view, final int @NotNull [] unveiled) {
    final boolean overlay = this.overlays[view];
    final int @NotNull [] mists = this.viewVeilMist[view];
    final int @NotNull [] clouds = this.viewCloud[view];
    final int @NotNull [] out = new int[this.size * this.size];
    for (int index = 0; index < out.length; index++) {
      final int mist = mists[index];
      int argb =
          mist == Fog.NO_MIST
              ? overlay ? 0 : unveiled[index]
              : fog(index, unveiled[index], mist, overlay);
      if (clouds[index] != 0) {
        argb = overlay ? Fog.over(clouds[index], argb) : clouded(argb, clouds[index]);
      }
      out[index] = argb;
    }
    return out;
  }
}
