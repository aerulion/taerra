package net.aerulion.taerra.mod.tile;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.aerulion.taerra.mod.config.Settings;
import net.aerulion.taerra.mod.util.Texture;
import net.aerulion.taerra.mod.web.WebClient;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Util;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

public final class TileCacheImpl implements TileCache {

  private static final Logger LOGGER = LogUtils.getLogger();
  private static final int MAX_TEXTURES = 512;
  private static final long MAX_BYTES = 64L * 1024 * 1024;
  private static final int MAX_ENTRIES = 16_384;
  private static final int MAX_IN_FLIGHT = 8;
  private static final long RETRY_MILLIS = 5_000;
  private static final long MAX_BACKOFF_MILLIS = 60_000;

  private final WebClient client;
  private final Settings settings;
  private final LinkedHashMap<TileKey, Tile> cache = new LinkedHashMap<>(256, 0.75f, true);
  private int inFlight;
  private int textures;
  private long bytes;
  private long backoff;
  private long pausedUntil;

  public TileCacheImpl(final WebClient client, final Settings settings) {
    this.client = client;
    this.settings = settings;
  }

  private static @Nullable NativeImage decode(
      final TileKey key, final byte @NotNull [] png, final boolean gray) {
    try {
      final @NotNull NativeImage image = NativeImage.read(png);
      if (gray) {
        for (int y = 0; y < image.getHeight(); y++) {
          for (int x = 0; x < image.getWidth(); x++) {
            image.setPixel(x, y, GrayStyle.apply(image.getPixel(x, y)));
          }
        }
      }
      return image;
    } catch (final IOException e) {
      LOGGER.warn("TAERRA: unreadable tile {}: {}", key, e.toString());
      return null;
    }
  }

  @Override
  public @Nullable GpuTextureView peek(final TileKey key) {
    final Tile tile = this.cache.get(key);
    return tile == null || tile.texture == null ? null : tile.texture.view();
  }

  @Override
  public @Nullable GpuTextureView get(final TileKey key) {
    final @NotNull Tile tile = this.cache.computeIfAbsent(key, _ -> new Tile());
    if (tile.png != null
        && !tile.decoding
        && (tile.texture == null || tile.gray != this.settings.grayMap())) {
      decodeLocally(key, tile);
    }
    return tile.texture == null ? null : tile.texture.view();
  }

  @Override
  public boolean wanted(final TileKey key) {
    final Tile tile = this.cache.get(key);
    final long now = System.currentTimeMillis();
    return tile != null
        && !tile.loading
        && now >= tile.retryAt
        && now >= this.pausedUntil
        && (tile.stale || (tile.png == null && !tile.missing));
  }

  @Override
  public boolean request(final @NotNull TileKey key) {
    if (this.inFlight >= MAX_IN_FLIGHT) {
      return false;
    }
    final Tile tile = this.cache.get(key);
    if (tile != null && !tile.loading) {
      load(key, tile);
    }
    return true;
  }

  @Override
  public void refresh(final TileKey key) {
    final Tile tile = this.cache.get(key);
    if (tile != null) {
      tile.stale = true;
    }
  }

  @Override
  public void refreshAll() {
    this.cache.values().forEach(tile -> tile.stale = true);
  }

  @Override
  public void refreshWorld(final String world) {
    this.cache.forEach(
        (key, tile) -> {
          if (key.world().equals(world)) {
            tile.stale = true;
          }
        });
  }

  @Override
  public void close() {
    this.cache.values().forEach(this::releaseTexture);
    this.cache.clear();
    this.bytes = 0;
  }

  private void load(final @NotNull TileKey key, final @NotNull Tile tile) {
    final boolean gray = this.settings.grayMap();
    tile.loading = true;
    tile.stale = false;
    this.inFlight++;
    this.client
        .get(key.path(), tile.etag)
        .handle(
            (response, error) -> {
              final int status = error == null ? response.statusCode() : 0;
              final byte[] png = status == 200 ? response.body() : null;
              final @Nullable NativeImage image = png == null ? null : decode(key, png, gray);
              final @Nullable String etag =
                  status == 200 ? response.headers().firstValue("ETag").orElse(null) : null;
              Minecraft.getInstance()
                  .execute(() -> loaded(key, tile, status, png, image, etag, gray));
              return null;
            });
  }

  private void loaded(
      final TileKey key,
      final @NotNull Tile tile,
      final int status,
      final byte @Nullable [] png,
      final @Nullable NativeImage image,
      final @Nullable String etag,
      final boolean gray) {
    this.inFlight--;
    tile.loading = false;
    if (this.cache.get(key) != tile) {
      if (image != null) {
        image.close();
      }
      return;
    }
    if (status == 200 || status == 304 || status == 404) {
      this.backoff = 0;
    }
    if (status == 200 && png != null && image != null) {
      tile.missing = false;
      setPng(tile, png, etag);
      upload(key, tile, image, gray);
    } else if (status == 404 || status == 200) {
      releaseTexture(tile);
      setPng(tile, null, null);
      tile.missing = true;
    } else if (status != 304) {
      if (image != null) {
        image.close();
      }
      tile.stale = true;
      tile.retryAt = System.currentTimeMillis() + RETRY_MILLIS;
      this.backoff = Math.min(MAX_BACKOFF_MILLIS, Math.max(RETRY_MILLIS, this.backoff * 2));
      this.pausedUntil = System.currentTimeMillis() + this.backoff;
    }
    trim();
  }

  private void decodeLocally(final TileKey key, final @NotNull Tile tile) {
    final byte @Nullable [] png = tile.png;
    final boolean gray = this.settings.grayMap();
    tile.decoding = true;
    CompletableFuture.supplyAsync(() -> decode(key, png, gray), Util.backgroundExecutor())
        .thenAccept(
            image ->
                Minecraft.getInstance()
                    .execute(
                        () -> {
                          tile.decoding = false;
                          if (this.cache.get(key) != tile || tile.png != png || image == null) {
                            if (image != null) {
                              image.close();
                            }
                            return;
                          }
                          upload(key, tile, image, gray);
                          trim();
                        }));
  }

  private void setPng(
      final @NotNull Tile tile, final byte @Nullable [] png, final @Nullable String etag) {
    this.bytes += (png == null ? 0 : png.length) - (tile.png == null ? 0 : tile.png.length);
    tile.png = png;
    tile.etag = etag;
  }

  private void upload(
      final TileKey key,
      final @NotNull Tile tile,
      final @NotNull NativeImage image,
      final boolean gray) {
    releaseTexture(tile);
    tile.texture = Texture.upload("TAERRA tile " + key, image);
    tile.gray = gray;
    this.textures++;
  }

  private void releaseTexture(final @NotNull Tile tile) {
    if (tile.texture != null) {
      tile.texture.close();
      tile.texture = null;
      this.textures--;
    }
  }

  private void trim() {
    final @NotNull Iterator<Map.Entry<TileKey, Tile>> entries = this.cache.entrySet().iterator();
    while (entries.hasNext()
        && (this.textures > MAX_TEXTURES
            || this.bytes > MAX_BYTES
            || this.cache.size() > MAX_ENTRIES)) {
      final Tile tile = entries.next().getValue();
      if (tile.loading || tile.decoding) {
        continue;
      }
      if (this.bytes > MAX_BYTES || this.cache.size() > MAX_ENTRIES) {
        releaseTexture(tile);
        setPng(tile, null, null);
        entries.remove();
      } else {
        releaseTexture(tile);
      }
    }
  }

  private static final class Tile {

    private @Nullable Texture texture;
    private byte @Nullable [] png;
    private @Nullable String etag;
    private boolean gray;
    private boolean loading;
    private boolean decoding;
    private boolean missing;
    private boolean stale;
    private long retryAt;
  }
}
