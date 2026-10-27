package net.aerulion.taerra.mod.gui.widget;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;
import net.minecraft.util.CommonColors;
import org.jetbrains.annotations.NotNull;

public final class IconButton extends AbstractButton {

  public static final Glyph CLOCKWISE = Glyph.of("icon/rotate_clockwise", 8, 8);
  public static final Glyph COUNTER_CLOCKWISE = Glyph.of("icon/rotate_counter_clockwise", 8, 8);
  public static final Glyph PLUS = Glyph.of("icon/zoom_in", 8, 8);
  public static final Glyph MINUS = Glyph.of("icon/zoom_out", 8, 8);
  public static final Glyph UNDO = Glyph.of("icon/undo", 8, 8);
  public static final Glyph REDO = Glyph.of("icon/redo", 8, 8);

  private static final int COLOR = 0xFFFFFFFF;
  private static final int INACTIVE = CommonColors.LIGHT_GRAY;
  private static final int SHADOW = 0xFF3F3F3F;

  private final Glyph icon;
  private final Runnable onPress;

  public IconButton(
      final int size,
      final @NotNull Glyph icon,
      final @NotNull Component message,
      final @NotNull Runnable onPress) {
    super(0, 0, size, size, message);
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
    final int left = getX() + (getWidth() - this.icon.width()) / 2;
    final int top = getY() + (getHeight() - this.icon.height()) / 2;
    this.icon.draw(graphics, left + 1, top + 1, SHADOW);
    this.icon.draw(graphics, left, top, this.active ? COLOR : INACTIVE);
  }

  @Override
  protected void updateWidgetNarration(final NarrationElementOutput output) {
    defaultButtonNarrationText(output);
  }
}
