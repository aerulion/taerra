package net.aerulion.taerra.mod.map;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.textures.FilterMode;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import net.aerulion.taerra.mod.util.Colors;
import net.aerulion.taerra.mod.util.Pixels;
import net.aerulion.taerra.mod.web.WebMap;
import net.aerulion.taerra.mod.web.model.Layer;
import net.aerulion.taerra.mod.web.model.MapIcon;
import net.aerulion.taerra.mod.web.model.Marker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.data.AtlasIds;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class MapIcons {

  public static final String PERSONAL = "◆";
  private static final int OUTLINE = 0xFF111111;
  private static final int GOLD = 0xFFFFCF3F;
  private static final int WHITE = 0xFFFFFFFF;
  private static final Shape BADGE =
      new Shape(
          12,
          15,
          new int[][] {
            {1, 0, 10, 1},
            {0, 1, 12, 10},
            {1, 11, 10, 1},
            {3, 12, 6, 1},
            {4, 13, 4, 1},
            {5, 14, 2, 1}
          },
          new int[][] {{1, 1, 10, 10}, {3, 11, 6, 1}, {4, 12, 4, 1}, {5, 13, 2, 1}},
          new int[][] {{1, 1, 2, 1}, {1, 2, 1, 1}},
          .35);
  private static final int[][] RIM = {
    {2, 0, 10, 1},
    {1, 1, 12, 1},
    {0, 2, 14, 10},
    {1, 12, 12, 1},
    {2, 13, 10, 1},
    {4, 14, 6, 1},
    {5, 15, 4, 1},
    {6, 16, 2, 1}
  };
  private static final String MARKER = "taerra:marker";
  private static final String GEM_ICON = "taerra:gem";
  private static final double MIN_CONTRAST = 3;
  private static final int ICON_AT = 2;
  private static final Map<String, Sprite> SPRITES = new HashMap<>();
  private static final Map<String, Identifier> ICONS = new HashMap<>();
  private static int next;

  private MapIcons() {}

  public static @NotNull TextureAtlasSprite decoration(final @NotNull String name) {
    return Minecraft.getInstance()
        .getAtlasManager()
        .getAtlasOrThrow(AtlasIds.MAP_DECORATIONS)
        .getSprite(Identifier.withDefaultNamespace(name));
  }

  public static @Nullable String iconOf(
      final @NotNull WebMap map, final @NotNull Layer layer, final @NotNull Marker marker) {
    return map.icon(marker.icon()) != null ? marker.icon() : layer.icon();
  }

  public static @NotNull Sprite sprite(
      final @NotNull WebMap map, final @NotNull Layer layer, final @NotNull Marker marker) {
    return sprite(map, iconOf(map, layer, marker), layer.personal(), Colors.argb(marker.color()));
  }

  public static @NotNull Sprite sprite(
      final @NotNull WebMap map,
      final @Nullable String iconId,
      final boolean personal,
      final int color) {
    final @Nullable MapIcon icon = map.icon(resolve(map, iconId, personal));
    final int @Nullable [] pixels = icon == null ? null : map.iconPixels(icon);
    final @NotNull String key =
        (personal ? "p" : "")
            + Integer.toHexString(color)
            + (pixels == null ? "" : "/" + icon.id() + "@" + icon.version());
    final @Nullable Sprite known = SPRITES.get(key);
    if (known != null) {
      return known;
    }
    final @NotNull Badge badge = badge(pixels, pixels != null && icon.tint(), personal, color);
    final @NotNull NativeImage image = new NativeImage(badge.width(), badge.height(), true);
    for (int i = 0; i < badge.argb().length; i++) {
      image.setPixel(i % badge.width(), i / badge.width(), badge.argb()[i]);
    }
    final @NotNull Sprite sprite =
        new Sprite(register("marker", image), badge.width(), badge.height(), badge.height());
    SPRITES.put(key, sprite);
    return sprite;
  }

  public static @NotNull Badge badge(
      final int @Nullable [] pixels,
      final boolean tinted,
      final boolean personal,
      final int color) {
    final int edge = personal ? 1 : 0;
    final int width = BADGE.width() + 2 * edge;
    final int @NotNull [] argb = new int[width * (BADGE.height() + 2 * edge)];
    if (personal) {
      fill(argb, width, RIM, 0, GOLD);
    }
    fill(argb, width, BADGE.outline(), edge, OUTLINE);
    fill(argb, width, BADGE.body(), edge, 0xFF000000 | color);
    fill(
        argb,
        width,
        BADGE.shine(),
        edge,
        Colors.mix(0xFF000000 | color, WHITE, BADGE.shineAlpha()));
    if (pixels != null) {
      final int at = ICON_AT + edge;
      final int tint =
          !tinted ? 0 : Colors.contrast(WHITE, color) < MIN_CONTRAST ? 0xFF000000 : WHITE;
      for (int y = 0; y < Pixels.ICON; y++) {
        for (int x = 0; x < Pixels.ICON; x++) {
          final int pixel = pixels[y * Pixels.ICON + x];
          final int index = (at + y) * width + at + x;
          argb[index] = over(argb[index], tint == 0 ? pixel : pixel & 0xFF000000 | tint & 0xFFFFFF);
        }
      }
    }
    return new Badge(width, argb.length / width, argb);
  }

  public static @Nullable Identifier icon(
      final @NotNull WebMap map, final @Nullable String id, final boolean personal) {
    return icon(map, resolve(map, id, personal));
  }

  private static @NotNull String resolve(
      final @NotNull WebMap map, final @Nullable String id, final boolean personal) {
    return map.hasIcon(id) ? id : personal ? GEM_ICON : MARKER;
  }

  public static @Nullable Identifier icon(final @NotNull WebMap map, final @Nullable String id) {
    final @Nullable MapIcon icon = map.icon(id);
    final int @Nullable [] pixels = icon == null ? null : map.iconPixels(icon);
    if (pixels == null) {
      return null;
    }
    return ICONS.computeIfAbsent(
        icon.id() + "@" + icon.version(),
        _ -> {
          final @NotNull NativeImage image = new NativeImage(Pixels.ICON, Pixels.ICON, true);
          for (int i = 0; i < pixels.length; i++) {
            image.setPixel(
                i % Pixels.ICON,
                i / Pixels.ICON,
                icon.tint() ? pixels[i] & 0xFF000000 | WHITE & 0xFFFFFF : pixels[i]);
          }
          return register("icon", image);
        });
  }

  public static void retain(final @NotNull Set<String> icons) {
    SPRITES
        .entrySet()
        .removeIf(
            entry -> {
              final int at = entry.getKey().indexOf('/');
              return at >= 0
                  && !icons.contains(entry.getKey().substring(at + 1))
                  && release(entry.getValue().texture());
            });
    ICONS
        .entrySet()
        .removeIf(entry -> !icons.contains(entry.getKey()) && release(entry.getValue()));
  }

  public static void clear() {
    SPRITES.values().forEach(sprite -> release(sprite.texture()));
    ICONS.values().forEach(MapIcons::release);
    SPRITES.clear();
    ICONS.clear();
  }

  private static boolean release(final @NotNull Identifier texture) {
    Minecraft.getInstance().getTextureManager().release(texture);
    return true;
  }

  private static @NotNull Identifier register(
      final @NotNull String kind, final @NotNull NativeImage image) {
    final @NotNull Identifier id = Identifier.fromNamespaceAndPath("taerra", kind + "/" + next++);
    Minecraft.getInstance().getTextureManager().register(id, new Nearest(id.toString(), image));
    return id;
  }

  private static void fill(
      final int @NotNull [] argb,
      final int width,
      final int @NotNull [][] rects,
      final int at,
      final int color) {
    for (final int[] rect : rects) {
      for (int y = at + rect[1]; y < at + rect[1] + rect[3]; y++) {
        for (int x = at + rect[0]; x < at + rect[0] + rect[2]; x++) {
          argb[y * width + x] = color;
        }
      }
    }
  }

  private static int over(final int below, final int above) {
    final int alpha = above >>> 24;
    if (alpha == 255 || below >>> 24 == 0) {
      return above;
    }
    return alpha == 0 ? below : Colors.mix(below, above | 0xFF000000, alpha / 255.0);
  }

  public record Sprite(@NotNull Identifier texture, int width, int height, int tip) {}

  public record Badge(int width, int height, int @NotNull [] argb) {}

  private record Shape(
      int width, int height, int[][] outline, int[][] body, int[][] shine, double shineAlpha) {}

  private static final class Nearest extends DynamicTexture {

    private Nearest(final @NotNull String label, final @NotNull NativeImage image) {
      super(() -> label, image);
      this.sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
    }
  }
}
