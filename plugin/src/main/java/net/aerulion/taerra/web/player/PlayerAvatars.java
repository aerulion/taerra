package net.aerulion.taerra.web.player;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import javax.imageio.ImageIO;
import net.aerulion.taerra.util.ImageUtils;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class PlayerAvatars {

  private static final String TEXTURE_HOST = "textures.minecraft.net";
  private static final Duration TIMEOUT = Duration.ofSeconds(5);
  private static final byte @NotNull [] MISSING = new byte[0];

  private final @NotNull HttpClient http = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();

  private final @NotNull Cache<String, byte[]> faces =
      Caffeine.newBuilder().maximumSize(1024).expireAfterWrite(Duration.ofMinutes(10)).build();

  static @NotNull BufferedImage face(final @NotNull BufferedImage skin) {
    final @NotNull BufferedImage face = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB);
    for (int y = 0; y < 8; y++) {
      for (int x = 0; x < 8; x++) {
        face.setRGB(x, y, skin.getRGB(8 + x, 8 + y) | 0xFF000000);
      }
    }
    if (skin.getHeight() == 64 || hasTransparentHat(skin)) {
      final @NotNull Graphics2D graphics = face.createGraphics();
      graphics.drawImage(skin.getSubimage(40, 8, 8, 8), 0, 0, null);
      graphics.dispose();
    }
    return face;
  }

  private static boolean hasTransparentHat(final @NotNull BufferedImage skin) {
    for (int y = 0; y < 32; y++) {
      for (int x = 32; x < 64; x++) {
        if ((skin.getRGB(x, y) >>> 24) < 128) {
          return true;
        }
      }
    }
    return false;
  }

  public static @Nullable String skin(final @NotNull Player player) {
    final @Nullable URL skin = player.getPlayerProfile().getTextures().getSkin();
    return skin == null ? null : skin.toString();
  }

  public byte @Nullable [] png(final @NotNull Player player) {
    final @Nullable String skin = skin(player);
    return skin == null ? null : png(skin);
  }

  public byte @Nullable [] png(final @NotNull String skinUrl) {
    final @NotNull URI skin;
    try {
      skin = URI.create(skinUrl);
    } catch (final IllegalArgumentException e) {
      return null;
    }
    if (!TEXTURE_HOST.equals(skin.getHost())) {
      return null;
    }
    final byte @NotNull [] face = this.faces.get(skin.getPath(), this::fetch);
    return face == MISSING ? null : face;
  }

  private byte @NotNull [] fetch(final @NotNull String path) {
    try {
      final HttpResponse<byte[]> response =
          this.http.send(
              HttpRequest.newBuilder(URI.create("https://" + TEXTURE_HOST + path))
                  .timeout(TIMEOUT)
                  .build(),
              HttpResponse.BodyHandlers.ofByteArray());
      if (response.statusCode() != 200) {
        return MISSING;
      }
      final @Nullable BufferedImage skin = ImageIO.read(new ByteArrayInputStream(response.body()));
      if (skin == null
          || skin.getWidth() != 64
          || (skin.getHeight() != 32 && skin.getHeight() != 64)) {
        return MISSING;
      }
      return ImageUtils.encodePNG(face(skin));
    } catch (final IOException e) {
      return MISSING;
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
      return MISSING;
    }
  }
}
