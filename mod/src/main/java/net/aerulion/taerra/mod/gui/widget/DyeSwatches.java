package net.aerulion.taerra.mod.gui.widget;

import java.util.function.Consumer;
import java.util.function.Supplier;
import net.aerulion.taerra.mod.util.Colors;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.DyeColor;
import org.jetbrains.annotations.NotNull;

public final class DyeSwatches extends AbstractWidget {

  public static final int SWATCH = 12;
  public static final int PER_ROW = 8;
  private static final int ROW = 10;

  private final Supplier<String> color;
  private final Consumer<String> onPick;

  public DyeSwatches(final Supplier<String> color, final Consumer<String> onPick) {
    super(0, 0, PER_ROW * SWATCH, 20, Component.translatable("taerra.waypoint.color"));
    this.color = color;
    this.onPick = onPick;
  }

  private static @NotNull String hex(final @NotNull DyeColor dye) {
    return Colors.hex(dye.getTextureDiffuseColor());
  }

  @Override
  protected void extractWidgetRenderState(
      final GuiGraphicsExtractor graphics,
      final int mouseX,
      final int mouseY,
      final float partialTick) {
    final DyeColor @NotNull [] dyes = DyeColor.values();
    for (int i = 0; i < dyes.length; i++) {
      final int x = getX() + (i % PER_ROW) * SWATCH;
      final int y = getY() + (i / PER_ROW) * ROW;
      final boolean chosen = hex(dyes[i]).equals(this.color.get());
      final boolean hovered = mouseX >= x && mouseX < x + SWATCH && mouseY >= y && mouseY < y + ROW;
      graphics.fill(
          x,
          y,
          x + SWATCH - 1,
          y + ROW - 1,
          chosen ? 0xFFFFFFFF : hovered ? 0xFFA0A0A0 : 0xFF000000);
      graphics.fill(
          x + 1, y + 1, x + SWATCH - 2, y + ROW - 2, 0xFF000000 | dyes[i].getTextureDiffuseColor());
      if (hovered) {
        graphics.setTooltipForNextFrame(
            Component.translatable("color.minecraft." + dyes[i].getName()), mouseX, mouseY);
      }
    }
  }

  @Override
  public void onClick(final @NotNull MouseButtonEvent event, final boolean doubleClick) {
    final int column = (int) (event.x() - getX()) / SWATCH;
    final int row = (int) (event.y() - getY()) / ROW;
    final int index = row * PER_ROW + column;
    if (column >= 0 && column < PER_ROW && index >= 0 && index < DyeColor.values().length) {
      this.onPick.accept(hex(DyeColor.values()[index]));
    }
  }

  @Override
  protected void updateWidgetNarration(final NarrationElementOutput output) {
    defaultButtonNarrationText(output);
  }
}
