package net.aerulion.taerra.mod.gui.widget;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

public final class IconButton extends AbstractButton {

  public static final String[] CLOCKWISE = {
    "..####.#", ".#....##", "#....###", "#.......", "#......#", "#......#", ".#....#.", "..####.."
  };
  public static final String[] COUNTER_CLOCKWISE = mirrored(CLOCKWISE);
  public static final String[] PLUS = {
    "...#...", "...#...", "...#...", "#######", "...#...", "...#...", "...#..."
  };
  public static final String[] MINUS = {"#######"};

  private static final int COLOR = 0xFFFFFFFF;
  private static final int INACTIVE = 0xFFA0A0A0;
  private static final int SHADOW = 0xFF3F3F3F;

  private final String[] icon;
  private final Runnable onPress;

  public IconButton(
      final int size,
      final String @NotNull [] icon,
      final @NotNull Component message,
      final @NotNull Runnable onPress) {
    super(0, 0, size, size, message);
    this.icon = icon;
    this.onPress = onPress;
  }

  private static String[] mirrored(final String @NotNull [] icon) {
    final String @NotNull [] mirrored = new String[icon.length];
    for (int row = 0; row < icon.length; row++) {
      mirrored[row] = new StringBuilder(icon[row]).reverse().toString();
    }
    return mirrored;
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
    final int left = getX() + (getWidth() - this.icon[0].length()) / 2;
    final int top = getY() + (getHeight() - this.icon.length) / 2;
    draw(graphics, left + 1, top + 1, SHADOW);
    draw(graphics, left, top, this.active ? COLOR : INACTIVE);
  }

  @Override
  protected void updateWidgetNarration(final NarrationElementOutput output) {
    defaultButtonNarrationText(output);
  }

  private void draw(
      final @NotNull GuiGraphicsExtractor graphics,
      final int left,
      final int top,
      final int color) {
    for (int row = 0; row < this.icon.length; row++) {
      for (int column = 0; column < this.icon[row].length(); column++) {
        if (this.icon[row].charAt(column) == '#') {
          graphics.fill(left + column, top + row, left + column + 1, top + row + 1, color);
        }
      }
    }
  }
}
