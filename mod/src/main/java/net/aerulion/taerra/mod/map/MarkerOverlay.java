package net.aerulion.taerra.mod.map;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.aerulion.taerra.mod.util.Texture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Util;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3x2fStack;
import org.slf4j.Logger;

final class MarkerOverlay {

  private static final Logger LOGGER = LogUtils.getLogger();
  private static final int MAX_TEXTURES = 192;
  private static final int MAX_ENTRIES = 4096;
  private static final int MAX_JOBS = 4;
  private static final int FALLBACK_LEVELS = 3;

  private final LinkedHashMap<OverlayKey, Entry> cache = new LinkedHashMap<>(64, 0.75f, true);
  private @Nullable OverlayScene scene;
  private int jobs;
  private int textures;

  void setScene(final @NotNull OverlayScene scene) {
    if (this.scene != null && !this.scene.sameSpace(scene)) {
      clear();
    }
    this.scene = scene;
  }

  void draw(
      final @NotNull GuiGraphicsExtractor graphics,
      final @NotNull GpuSampler sampler,
      final int level,
      final double originX,
      final double originY,
      final double unitPixels,
      final double[] view,
      final boolean settled) {
    final @Nullable OverlayScene scene = this.scene;
    if (scene == null) {
      return;
    }
    final double units = Projection.tileUnits(level);
    final int firstX = (int) Math.floor(view[0] / units);
    final int lastX = (int) Math.floor(view[2] / units);
    final int firstY = (int) Math.floor(view[1] / units);
    final int lastY = (int) Math.floor(view[3] / units);
    final @NotNull List<OverlayKey> wanted = new ArrayList<>();
    for (int x = firstX; x <= lastX; x++) {
      for (int y = firstY; y <= lastY; y++) {
        final @NotNull OverlayKey key = new OverlayKey(level, x, y);
        final @NotNull Entry entry = this.cache.computeIfAbsent(key, _ -> new Entry());
        if (entry.version != scene.version() && !entry.rendering) {
          wanted.add(key);
        }
        if (entry.texture != null) {
          blit(graphics, sampler, entry.texture, key, originX, originY, unitPixels, 0, 1, 0, 1);
        } else if (entry.version < 0) {
          fallback(graphics, sampler, key, originX, originY, unitPixels);
        }
      }
    }
    if (settled) {
      final double middleX = (view[0] + view[2]) / 2 / units - .5;
      final double middleY = (view[1] + view[3]) / 2 / units - .5;
      wanted.sort(
          Comparator.comparingDouble(key -> Math.hypot(key.x() - middleX, key.y() - middleY)));
      for (final @NotNull OverlayKey key : wanted) {
        if (this.jobs >= MAX_JOBS) {
          break;
        }
        render(scene, key);
      }
    }
    trim();
  }

  void clear() {
    this.cache.values().forEach(this::release);
    this.cache.clear();
  }

  private void fallback(
      final @NotNull GuiGraphicsExtractor graphics,
      final @NotNull GpuSampler sampler,
      final @NotNull OverlayKey key,
      final double originX,
      final double originY,
      final double unitPixels) {
    for (int up = 1; up <= FALLBACK_LEVELS; up++) {
      final Entry parent =
          this.cache.get(new OverlayKey(key.level() - up, key.x() >> up, key.y() >> up));
      if (parent != null && parent.texture != null) {
        final float part = 1f / (1 << up);
        final float u = (key.x() - ((key.x() >> up) << up)) * part;
        final float v = (key.y() - ((key.y() >> up) << up)) * part;
        blit(
            graphics,
            sampler,
            parent.texture,
            key,
            originX,
            originY,
            unitPixels,
            u,
            u + part,
            v,
            v + part);
        return;
      }
    }
    for (int child = 0; child < 4; child++) {
      final @NotNull OverlayKey below =
          new OverlayKey(key.level() + 1, 2 * key.x() + (child & 1), 2 * key.y() + (child >> 1));
      final Entry entry = this.cache.get(below);
      if (entry != null && entry.texture != null) {
        blit(graphics, sampler, entry.texture, below, originX, originY, unitPixels, 0, 1, 0, 1);
      }
    }
  }

  private void blit(
      final @NotNull GuiGraphicsExtractor graphics,
      final @NotNull GpuSampler sampler,
      final @NotNull Texture texture,
      final @NotNull OverlayKey key,
      final double originX,
      final double originY,
      final double unitPixels,
      final float u0,
      final float u1,
      final float v0,
      final float v1) {
    final double units = Projection.tileUnits(key.level());
    final @NotNull Matrix3x2fStack pose = graphics.pose();
    pose.pushMatrix();
    pose.translate(
        (float) (originX + key.x() * units * unitPixels),
        (float) (originY + key.y() * units * unitPixels));
    pose.scale((float) (units * unitPixels / OverlayRaster.SIZE));
    graphics.blit(
        texture.view(), sampler, 0, 0, OverlayRaster.SIZE, OverlayRaster.SIZE, u0, u1, v0, v1);
    pose.popMatrix();
  }

  private void render(final @NotNull OverlayScene scene, final @NotNull OverlayKey key) {
    final Entry entry = this.cache.get(key);
    if (entry == null) {
      return;
    }
    entry.rendering = true;
    this.jobs++;
    CompletableFuture.supplyAsync(() -> OverlayRaster.render(scene, key), Util.backgroundExecutor())
        .whenComplete(
            (image, error) ->
                Minecraft.getInstance().execute(() -> rendered(scene, key, entry, image, error)));
  }

  private void rendered(
      final @NotNull OverlayScene scene,
      final OverlayKey key,
      final @NotNull Entry entry,
      final @Nullable NativeImage image,
      final @Nullable Throwable error) {
    this.jobs--;
    entry.rendering = false;
    if (error != null) {
      LOGGER.warn("Taerra: could not draw markers for {}: {}", key, error.toString());
    }
    if (this.cache.get(key) != entry
        || this.scene == null
        || !this.scene.sameSpace(scene)
        || entry.version > scene.version()) {
      if (image != null) {
        image.close();
      }
      return;
    }
    release(entry);
    entry.version = scene.version();
    if (image != null) {
      entry.texture = Texture.upload("Taerra markers " + key, image);
      this.textures++;
    }
  }

  private void release(final @NotNull Entry entry) {
    if (entry.texture != null) {
      entry.texture.close();
      entry.texture = null;
      this.textures--;
    }
  }

  private void trim() {
    final @NotNull Iterator<Map.Entry<OverlayKey, Entry>> entries =
        this.cache.entrySet().iterator();
    while (entries.hasNext() && (this.textures > MAX_TEXTURES || this.cache.size() > MAX_ENTRIES)) {
      final Entry entry = entries.next().getValue();
      if (entry.rendering) {
        continue;
      }
      release(entry);
      entries.remove();
    }
  }

  private static final class Entry {

    private @Nullable Texture texture;
    private long version = -1;
    private boolean rendering;
  }
}
