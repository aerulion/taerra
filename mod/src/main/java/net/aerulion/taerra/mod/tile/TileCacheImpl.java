package net.aerulion.taerra.mod.tile;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
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

  private static final String LAYERS_HEADER = "X-Taerra-Layers";
  private static final int ERASE_ALPHA = 1;

  private final WebClient client;
  private final Settings settings;
  private final Function<String, Set<String>> revealed;
  private final LinkedHashMap<TileKey, Tile> cache = new LinkedHashMap<>(256, 0.75f, true);
  private int inFlight;
  private int textures;
  private long bytes;
  private long backoff;
  private long pausedUntil;

  public TileCacheImpl(
      final WebClient client,
      final Settings settings,
      final Function<String, Set<String>> revealed) {
    this.client = client;
    this.settings = settings;
    this.revealed = revealed;
  }

  static @NotNull List<String> wanted(
      final @Nullable String header, final @NotNull Set<String> revealed) {
    final @NotNull List<String> bodies = new ArrayList<>();
    final @NotNull List<String> spills = new ArrayList<>();
    for (final @NotNull String id : header == null ? new String[0] : header.split(",")) {
      if (id.length() < 2) {
        continue;
      }
      final @NotNull String region = id.substring(1);
      if (id.charAt(0) == 'b' && revealed.contains(region)) {
        bodies.add(id);
      } else if (id.charAt(0) == 's' && !revealed.contains(region)) {
        spills.add(id);
      }
    }
    bodies.addAll(spills);
    return bodies;
  }

  static int blend(final int over, final int under, final boolean body) {
    final int alpha = over >>> 24;
    if (body && alpha == ERASE_ALPHA) {
      return 0;
    }
    if (alpha == 0) {
      return under;
    }
    final int keep = (255 - alpha) * (under >>> 24) / 255;
    final int total = alpha + keep;
    int argb = total << 24;
    for (int shift = 16; shift >= 0; shift -= 8) {
      argb |=
          (((over >> shift) & 0xFF) * alpha + ((under >> shift) & 0xFF) * keep) / total << shift;
    }
    return argb;
  }

  private static @Nullable NativeImage decode(
      final TileKey key,
      final byte @NotNull [] png,
      final @NotNull List<Piece> pieces,
      final boolean gray) {
    try {
      final @NotNull NativeImage image = NativeImage.read(png);
      for (final @NotNull Piece piece : pieces) {
        try (final NativeImage layer = NativeImage.read(piece.png())) {
          if (layer.getWidth() != image.getWidth() || layer.getHeight() != image.getHeight()) {
            continue;
          }
          for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
              image.setPixel(x, y, blend(layer.getPixel(x, y), image.getPixel(x, y), piece.body()));
            }
          }
        }
      }
      if (gray) {
        for (int y = 0; y < image.getHeight(); y++) {
          for (int x = 0; x < image.getWidth(); x++) {
            image.setPixel(x, y, GrayStyle.apply(image.getPixel(x, y)));
          }
        }
      }
      return image;
    } catch (final IOException | RuntimeException e) {
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
    final @NotNull Set<String> revealed = this.revealed.apply(key.world());
    final byte @Nullable [] cached = tile.png;
    tile.loading = true;
    tile.stale = false;
    this.inFlight++;
    this.client
        .get(key.path(), cached == null ? null : tile.etag)
        .thenCompose(
            response -> {
              final int status = response.statusCode();
              final byte @Nullable [] png =
                  status == 200 ? response.body() : status == 304 ? cached : null;
              if (png == null) {
                return CompletableFuture.completedFuture(
                    new Fetched(status, null, List.of(), null));
              }
              final @Nullable String etag =
                  status == 200 ? response.headers().firstValue("ETag").orElse(null) : tile.etag;
              final @NotNull List<String> wanted =
                  wanted(response.headers().firstValue(LAYERS_HEADER).orElse(null), revealed);
              if (status == 304 && wanted.equals(tile.pieces.stream().map(Piece::id).toList())) {
                return CompletableFuture.completedFuture(
                    new Fetched(status, null, List.of(), null));
              }
              final @NotNull List<CompletableFuture<@Nullable Piece>> pieces =
                  wanted.stream()
                      .map(
                          id ->
                              this.client
                                  .get(key.path() + "?layer=" + id, null)
                                  .handle(
                                      (layer, error) ->
                                          error == null && layer.statusCode() == 200
                                              ? new Piece(id, layer.body())
                                              : null))
                      .toList();
              return CompletableFuture.allOf(pieces.toArray(CompletableFuture[]::new))
                  .thenApply(
                      _ ->
                          new Fetched(
                              200,
                              png,
                              pieces.stream()
                                  .map(CompletableFuture::join)
                                  .filter(piece -> piece != null)
                                  .toList(),
                              etag));
            })
        .handle(
            (fetched, error) -> {
              final int status = error == null ? fetched.status() : 0;
              final byte @Nullable [] png = status == 200 ? fetched.png() : null;
              final @NotNull List<Piece> pieces = png == null ? List.of() : fetched.pieces();
              final @Nullable NativeImage image =
                  png == null ? null : decode(key, png, pieces, gray);
              final @Nullable String etag = png == null ? null : fetched.etag();
              Minecraft.getInstance()
                  .execute(() -> loaded(key, tile, status, png, pieces, image, etag, gray));
              return null;
            });
  }

  private void loaded(
      final TileKey key,
      final @NotNull Tile tile,
      final int status,
      final byte @Nullable [] png,
      final @NotNull List<Piece> pieces,
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
      setPng(tile, png, pieces, etag);
      upload(key, tile, image, gray);
    } else if (status == 404 || status == 200) {
      releaseTexture(tile);
      setPng(tile, null, List.of(), null);
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
    final @NotNull List<Piece> pieces = tile.pieces;
    final boolean gray = this.settings.grayMap();
    tile.decoding = true;
    CompletableFuture.supplyAsync(() -> decode(key, png, pieces, gray), Util.backgroundExecutor())
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
      final @NotNull Tile tile,
      final byte @Nullable [] png,
      final @NotNull List<Piece> pieces,
      final @Nullable String etag) {
    this.bytes += size(png, pieces) - size(tile.png, tile.pieces);
    tile.png = png;
    tile.pieces = pieces;
    tile.etag = etag;
  }

  private static long size(final byte @Nullable [] png, final @NotNull List<Piece> pieces) {
    long size = png == null ? 0 : png.length;
    for (final @NotNull Piece piece : pieces) {
      size += piece.png().length;
    }
    return size;
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
        setPng(tile, null, List.of(), null);
        entries.remove();
      } else {
        releaseTexture(tile);
      }
    }
  }

  private record Piece(@NotNull String id, byte @NotNull [] png) {

    boolean body() {
      return this.id.charAt(0) == 'b';
    }
  }

  private record Fetched(
      int status, byte @Nullable [] png, @NotNull List<Piece> pieces, @Nullable String etag) {}

  private static final class Tile {

    private @Nullable Texture texture;
    private byte @Nullable [] png;
    private @NotNull List<Piece> pieces = List.of();
    private @Nullable String etag;
    private boolean gray;
    private boolean loading;
    private boolean decoding;
    private boolean missing;
    private boolean stale;
    private long retryAt;
  }
}
