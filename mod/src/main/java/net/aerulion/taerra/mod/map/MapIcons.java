package net.aerulion.taerra.mod.map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.data.AtlasIds;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.DyeColor;
import org.jetbrains.annotations.NotNull;

public final class MapIcons {

  public static final String GEM = "◆";
  private static final int[] GEM_WIDTHS = {0, 1, 2, 3, 3, 2, 1, 0};
  private static final int GEM_OUTLINE = 0xFF111111;
  private static final int GEM_SHINE = 0x99FFFFFF;

  private MapIcons() {}

  static void gem(final @NotNull GuiGraphicsExtractor graphics, final int color) {
    for (int y = 0; y < GEM_WIDTHS.length; y++) {
      final int width = GEM_WIDTHS[y];
      graphics.fill(3 - width, y, 4 + width, y + 1, GEM_OUTLINE);
      if (width > 0 && y > 0 && y < GEM_WIDTHS.length - 1) {
        graphics.fill(4 - width, y, 3 + width, y + 1, color);
      }
    }
    graphics.fill(2, 2, 3, 3, GEM_SHINE);
    graphics.fill(1, 3, 2, 4, GEM_SHINE);
  }

  static @NotNull TextureAtlasSprite decoration(final @NotNull String name) {
    return Minecraft.getInstance()
        .getAtlasManager()
        .getAtlasOrThrow(AtlasIds.MAP_DECORATIONS)
        .getSprite(Identifier.withDefaultNamespace(name));
  }

  static @NotNull TextureAtlasSprite banner(final int color) {
    return decoration(bannerName(color));
  }

  public static @NotNull String bannerName(final int color) {
    @NotNull DyeColor best = DyeColor.WHITE;
    long bestDistance = Long.MAX_VALUE;
    for (final @NotNull DyeColor dye : DyeColor.values()) {
      final int other = dye.getTextureDiffuseColor();
      long distance = 0;
      for (int shift = 0; shift <= 16; shift += 8) {
        final long channel = ((color >> shift) & 0xFF) - ((other >> shift) & 0xFF);
        distance += channel * channel;
      }
      if (distance < bestDistance) {
        bestDistance = distance;
        best = dye;
      }
    }
    return best.getSerializedName() + "_banner";
  }
}
