package net.aerulion.taerra.mod.map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.data.AtlasIds;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.DyeColor;
import org.jetbrains.annotations.NotNull;

public final class MapIcons {

  private MapIcons() {}

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
