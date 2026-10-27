package net.aerulion.taerra.mod.gui.widget;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.NotNull;

public record Glyph(@NotNull Identifier sprite, int width, int height) {

  public static @NotNull Glyph of(final @NotNull String path, final int width, final int height) {
    return new Glyph(Identifier.fromNamespaceAndPath("taerra", path), width, height);
  }

  public void draw(
      final @NotNull GuiGraphicsExtractor graphics, final int x, final int y, final int color) {
    graphics.blitSprite(
        RenderPipelines.GUI_TEXTURED, this.sprite, x, y, this.width, this.height, color);
  }
}
