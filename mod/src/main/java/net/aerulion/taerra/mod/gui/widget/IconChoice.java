package net.aerulion.taerra.mod.gui.widget;

import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class IconChoice extends AbstractButton {

  private static final int ICON = 8;

  private final @NotNull Supplier<@Nullable Identifier> icon;
  private final @NotNull Runnable onPress;

  public IconChoice(
      final int width,
      final @NotNull Component message,
      final @NotNull Supplier<@Nullable Identifier> icon,
      final @NotNull Runnable onPress) {
    super(0, 0, width, 20, message);
    this.icon = icon;
    this.onPress = onPress;
  }

  @Override
  public void onPress(final InputWithModifiers input) {
    this.onPress.run();
  }

  @Override
  protected void extractContents(
      final GuiGraphicsExtractor graphics,
      final int mouseX,
      final int mouseY,
      final float partialTick) {
    extractDefaultSprite(graphics);
    final boolean bare = getWidth() < 40;
    final int left = bare ? getX() + (getWidth() - ICON) / 2 : getX() + 6;
    final int top = getY() + (getHeight() - ICON) / 2;
    final int color = this.active ? 0xFFFFFFFF : 0xFFA0A0A0;
    final @Nullable Identifier icon = this.icon.get();
    if (icon != null) {
      graphics.blit(
          RenderPipelines.GUI_TEXTURED,
          icon,
          left + 1,
          top + 1,
          0,
          0,
          ICON,
          ICON,
          ICON,
          ICON,
          ARGB.scaleRGB(color, .25F));
      graphics.blit(
          RenderPipelines.GUI_TEXTURED, icon, left, top, 0, 0, ICON, ICON, ICON, ICON, color);
    }
    if (bare) {
      return;
    }
    final @NotNull Font font = Minecraft.getInstance().font;
    final int x = left + ICON + 5;
    final int room = getRight() - 6 - x;
    final @NotNull String text = getMessage().getString();
    graphics.text(
        font,
        font.width(text) <= room
            ? text
            : font.plainSubstrByWidth(text, Math.max(0, room - font.width("…"))) + "…",
        x,
        getY() + (getHeight() - 8) / 2,
        color,
        true);
  }

  @Override
  protected void updateWidgetNarration(final NarrationElementOutput output) {
    defaultButtonNarrationText(output);
  }
}
